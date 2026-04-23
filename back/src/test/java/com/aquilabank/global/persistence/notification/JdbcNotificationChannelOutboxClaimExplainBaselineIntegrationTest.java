package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.support.NotificationExplainPlan;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcNotificationChannelOutboxClaimExplainBaselineIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-22T06:00:00Z");
  private static final int CLAIM_BATCH_SIZE = 20;
  private static final int DUE_ROWS = 20_000;
  private static final int FUTURE_ROWS = 4_000;
  private static final int SENT_ROWS = 4_000;
  private static final Object SEED_LOCK = new Object();
  private static boolean baselineSeeded;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    if (baselineSeeded) {
      return;
    }

    synchronized (SEED_LOCK) {
      if (!baselineSeeded) {
        resetBankingTables(jdbcTemplate);
        // EXPLAIN baseline은 bulk insert와 ANALYZE가 있어 기본 transaction timeout보다 넉넉히 둡니다.
        commit(
            transactionManager,
            30,
            () -> {
              long userId = insertUser("channel-claim-baseline-user");
              long accountId = insertAccount("channel claim baseline account");
              insertNotifications(accountId, "claim-due", DUE_ROWS, BASE);
              insertNotifications(accountId, "claim-future", FUTURE_ROWS, BASE);
              insertNotifications(accountId, "claim-sent", SENT_ROWS, BASE);
              insertChannelOutbox(userId, accountId, "claim-due", BASE.minusSeconds(60), true);
              insertChannelOutbox(userId, accountId, "claim-future", BASE.plusSeconds(3600), false);
              insertSentChannelOutbox(userId, accountId, "claim-sent", BASE.minusSeconds(120));
            });
        analyzeTables();
        baselineSeeded = true;
      }
    }
  }

  @Test
  void claimCandidatePlanUsesQueueIndexWithoutSeqScanOrSort() {
    NotificationExplainPlan plan = explainClaimCandidates(BASE, CLAIM_BATCH_SIZE);

    assertThat(plan.usesIndex("idx_notification_channel_outbox_due_claim")).isTrue();
    assertThat(plan.seqScanRelations()).doesNotContain("notification_channel_outbox");
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  private NotificationExplainPlan explainClaimCandidates(Instant now, int limit) {
    String explainJson =
        jdbcTemplate.queryForObject(
            """
            EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
            SELECT id
            FROM notification_channel_outbox
            WHERE delivery_status IN ('PENDING', 'FAILED')
              AND available_at <= :now
            ORDER BY available_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("limit", limit),
            (rs, rowNum) -> rs.getString(1));
    if (explainJson == null) {
      throw new IllegalStateException("EXPLAIN did not return JSON");
    }
    return NotificationExplainPlan.fromJson(objectMapper, explainJson);
  }

  private long insertUser(String loginId) {
    Long userId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_user (
                login_id,
                password_hash,
                display_name,
                user_status,
                created_at,
                updated_at
            )
            VALUES (
                :loginId,
                '$2a$10$abcdefghijklmnopqrstuv',
                :loginId,
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            Long.class);
    if (userId == null) {
      throw new IllegalStateException("bank_user insert did not return id");
    }
    return userId;
  }

  private long insertAccount(String displayName) {
    Long accountId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            VALUES (
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("displayName", displayName),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return accountId;
  }

  private void insertNotifications(long accountId, String prefix, int count, Instant base) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_inbox (
            account_id,
            event_key,
            event_type,
            title,
            message,
            created_at
        )
        SELECT :accountId,
               :prefix || '-' || gs::text,
               'TransferBooked',
               'claim baseline title ' || gs::text,
               'claim baseline message ' || gs::text,
               CAST(:base AS timestamptz) - (gs * INTERVAL '1 second')
        FROM generate_series(1, :count) gs
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("prefix", prefix)
            .addValue("count", count)
            .addValue("base", Timestamp.from(base)));
  }

  private void insertChannelOutbox(
      long userId, long accountId, String prefix, Instant availableAt, boolean mixFailed) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_channel_outbox (
            notification_id,
            user_id,
            account_id,
            category,
            channel,
            event_type,
            event_key,
            payload,
            delivery_status,
            available_at,
            retry_count,
            created_at,
            updated_at
        )
        SELECT n.id,
               :userId,
               :accountId,
               'TRANSACTIONAL',
               CASE WHEN n.id % 2 = 0 THEN 'EMAIL' ELSE 'SMS' END,
               n.event_type,
               n.event_key,
               jsonb_build_object('kind', 'claim-baseline', 'eventKey', n.event_key),
               CASE WHEN :mixFailed AND n.id % 5 = 0 THEN 'FAILED' ELSE 'PENDING' END,
               CAST(:availableAt AS timestamptz),
               CASE WHEN :mixFailed AND n.id % 5 = 0 THEN 2 ELSE 0 END,
               n.created_at,
               n.created_at
        FROM notification_inbox n
        WHERE n.account_id = :accountId
          AND n.event_key LIKE :prefixLike
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("availableAt", Timestamp.from(availableAt))
            .addValue("mixFailed", mixFailed)
            .addValue("prefixLike", prefix + "-%"));
  }

  private void insertSentChannelOutbox(long userId, long accountId, String prefix, Instant sentAt) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_channel_outbox (
            notification_id,
            user_id,
            account_id,
            category,
            channel,
            event_type,
            event_key,
            payload,
            delivery_status,
            available_at,
            sent_at,
            retry_count,
            created_at,
            updated_at
        )
        SELECT n.id,
               :userId,
               :accountId,
               'TRANSACTIONAL',
               'EMAIL',
               n.event_type,
               n.event_key,
               jsonb_build_object('kind', 'claim-baseline-sent', 'eventKey', n.event_key),
               'SENT',
               CAST(:sentAt AS timestamptz),
               CAST(:sentAt AS timestamptz),
               0,
               n.created_at,
               CAST(:sentAt AS timestamptz)
        FROM notification_inbox n
        WHERE n.account_id = :accountId
          AND n.event_key LIKE :prefixLike
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("sentAt", Timestamp.from(sentAt))
            .addValue("prefixLike", prefix + "-%"));
  }

  private void analyzeTables() {
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE notification_inbox");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE notification_channel_outbox");
  }
}
