package com.aquilabank.global.notification;

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
class ProviderDeliveryMetricQueryBaselineIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-24T06:00:00Z");
  private static final int STATUS_ROWS = 24_000;
  private static final int BACKLOG_ROWS = 8_000;
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
        // metric baseline은 bulk fixture와 ANALYZE가 있어 runtime statement timeout과 분리합니다.
        commitWithStatementTimeout(
            transactionManager,
            jdbcTemplate,
            90,
            60,
            () -> {
              long userId = insertUser("provider-delivery-metric-baseline@example.com");
              long accountId = insertAccount("provider delivery metric baseline account");
              insertNotifications(accountId, STATUS_ROWS + BACKLOG_ROWS);
              insertNotificationChannelDelivery(userId, accountId);
              insertPasswordRecoveryDelivery(userId);
              analyzeTables();
            });
        baselineSeeded = true;
      }
    }
  }

  @Test
  void summaryMetricQueryDoesNotScanSourceDeliveryTables() {
    NotificationExplainPlan plan = explain(summaryCountSql());

    // status metric은 summary snapshot이 운영 scrape 경로라 원본 outbox count를 강제하지 않습니다.
    assertThat(plan.seqScanRelations())
        .doesNotContain("notification_channel_outbox", "auth_password_recovery_delivery_outbox");
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  private NotificationExplainPlan explain(String sql) {
    String explainJson =
        jdbcTemplate.queryForObject(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)\n" + sql,
            new MapSqlParameterSource(),
            (rs, rowNum) -> rs.getString(1));
    if (explainJson == null) {
      throw new IllegalStateException("EXPLAIN did not return JSON");
    }
    return NotificationExplainPlan.fromJson(objectMapper, explainJson);
  }

  private String summaryCountSql() {
    return """
        SELECT queue_name,
               metric_type,
               metric_name,
               metric_count
        FROM provider_delivery_metric_summary
        WHERE queue_name IN ('notification_channel', 'password_recovery')
          AND metric_type IN ('STATUS', 'SKIP_REASON')
        """;
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

  private void insertNotifications(long accountId, int count) {
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
               'provider-delivery-metric-' || gs::text,
               'TransferBooked',
               'provider delivery metric title ' || gs::text,
               'provider delivery metric message ' || gs::text,
               CAST(:base AS timestamptz) - (gs * INTERVAL '1 second')
        FROM generate_series(1, :count) gs
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("count", count)
            .addValue("base", Timestamp.from(BASE)));
  }

  private void insertNotificationChannelDelivery(long userId, long accountId) {
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
            skip_reason,
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
               jsonb_build_object('kind', 'provider-delivery-metric', 'eventKey', n.event_key),
               CASE
                   WHEN n.id <= :statusRows AND n.id % 4 = 0 THEN 'SENT'
                   WHEN n.id <= :statusRows AND n.id % 4 = 1 THEN 'SKIPPED'
                   WHEN n.id <= :statusRows AND n.id % 4 = 2 THEN 'FAILED'
                   WHEN n.id <= :statusRows THEN 'QUARANTINED'
                   WHEN n.id % 2 = 0 THEN 'PENDING'
                   ELSE 'FAILED'
               END,
               CAST(:base AS timestamptz),
               CASE WHEN n.id <= :statusRows AND n.id % 4 = 0 THEN CAST(:base AS timestamptz) ELSE NULL END,
               CASE WHEN n.id <= :statusRows AND n.id % 4 = 2 THEN 2 ELSE 0 END,
               CASE WHEN n.id <= :statusRows AND n.id % 4 = 1 THEN 'VERIFIED_CONTACT_MISSING' ELSE NULL END,
               n.created_at,
               n.created_at
        FROM notification_inbox n
        WHERE n.account_id = :accountId
          AND n.event_key LIKE 'provider-delivery-metric-%'
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("statusRows", STATUS_ROWS)
            .addValue("base", Timestamp.from(BASE)));
  }

  private void insertPasswordRecoveryDelivery(long userId) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_password_recovery_delivery_outbox (
            request_id,
            user_id,
            login_id,
            delivery_channel,
            provider_destination,
            delivery_status,
            available_at,
            sent_at,
            retry_count,
            skip_reason,
            created_at,
            updated_at
        )
        SELECT 'provider-delivery-metric-request-' || gs::text,
               :userId,
               'provider-delivery-metric-baseline@example.com',
               'EMAIL',
               'provider-delivery-metric-baseline@example.com',
               CASE
                   WHEN gs <= :statusRows AND gs % 4 = 0 THEN 'SENT'
                   WHEN gs <= :statusRows AND gs % 4 = 1 THEN 'SKIPPED'
                   WHEN gs <= :statusRows AND gs % 4 = 2 THEN 'FAILED'
                   WHEN gs <= :statusRows THEN 'QUARANTINED'
                   WHEN gs % 2 = 0 THEN 'PENDING'
                   ELSE 'FAILED'
               END,
               CAST(:base AS timestamptz),
               CASE WHEN gs <= :statusRows AND gs % 4 = 0 THEN CAST(:base AS timestamptz) ELSE NULL END,
               CASE WHEN gs <= :statusRows AND gs % 4 = 2 THEN 2 ELSE 0 END,
               CASE WHEN gs <= :statusRows AND gs % 4 = 1 THEN 'PROVIDER_URL_MISSING' ELSE NULL END,
               CAST(:base AS timestamptz) - (gs * INTERVAL '1 second'),
               CAST(:base AS timestamptz) - (gs * INTERVAL '1 second')
        FROM generate_series(1, :count) gs
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("statusRows", STATUS_ROWS)
            .addValue("count", STATUS_ROWS + BACKLOG_ROWS)
            .addValue("base", Timestamp.from(BASE)));
  }

  private void analyzeTables() {
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE notification_inbox");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE notification_channel_outbox");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE auth_password_recovery_delivery_outbox");
  }
}
