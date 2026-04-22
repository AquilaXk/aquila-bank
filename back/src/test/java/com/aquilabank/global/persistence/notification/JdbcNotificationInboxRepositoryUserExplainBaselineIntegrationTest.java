package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.support.NotificationExplainPlan;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
class JdbcNotificationInboxRepositoryUserExplainBaselineIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-18T00:00:00Z");
  private static final int PAGE_SIZE = 50;
  private static final int ROWS_PER_ACCOUNT = 5_000;
  private static final int NOISE_ACCOUNT_COUNT = 24;
  private static final Object SEED_LOCK = new Object();
  private static BaselineWindow sharedBaselineWindow;

  @Autowired private JdbcNotificationInboxRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  private BaselineWindow baselineWindow;

  @BeforeEach
  void setUpDatabase() {
    if (sharedBaselineWindow != null) {
      baselineWindow = sharedBaselineWindow;
      return;
    }

    synchronized (SEED_LOCK) {
      if (sharedBaselineWindow == null) {
        resetBankingTables(jdbcTemplate);
        // baseline seed는 bulk insert/analyze가 포함되어 기본 transaction timeout보다 넉넉히 둡니다.
        commit(transactionManager, 30, () -> sharedBaselineWindow = seedBaseline());
      }
      baselineWindow = sharedBaselineWindow;
    }
  }

  @Test
  void userFirstPageUsesVisibleCursorIndexWithoutInboxSeqScan() {
    NotificationListQuery query = new NotificationListQuery(PAGE_SIZE, null);

    NotificationSlice slice = repository.fetchByUserId(baselineWindow.userId(), query);
    NotificationExplainPlan plan = explain(baselineWindow.userId(), query);

    assertThat(slice.items()).hasSize(PAGE_SIZE);
    assertThat(slice.hasNext()).isTrue();
    assertThat(plan.rootNodeType()).isEqualTo("Limit");
    assertThat(plan.seqScanRelations()).doesNotContain("notification_inbox");
    assertThat(plan.indexNames()).contains("idx_notification_inbox_account_visible_cursor");
  }

  @Test
  void userCursorPageKeepsVisibleCursorIndexWithoutInboxSeqScan() {
    NotificationListQuery firstPageQuery = new NotificationListQuery(PAGE_SIZE, null);
    NotificationSlice firstPage = repository.fetchByUserId(baselineWindow.userId(), firstPageQuery);
    NotificationListQuery cursorQuery =
        new NotificationListQuery(PAGE_SIZE, firstPage.nextCursor());

    NotificationSlice slice = repository.fetchByUserId(baselineWindow.userId(), cursorQuery);
    NotificationExplainPlan plan = explain(baselineWindow.userId(), cursorQuery);

    assertThat(firstPage.nextCursor()).isNotNull();
    assertThat(slice.items()).hasSize(PAGE_SIZE);
    assertThat(slice.items().getFirst().createdAt())
        .isBeforeOrEqualTo(firstPage.items().getLast().createdAt());
    assertThat(plan.seqScanRelations()).doesNotContain("notification_inbox");
    assertThat(plan.indexNames()).contains("idx_notification_inbox_account_visible_cursor");
  }

  private BaselineWindow seedBaseline() {
    long userId = insertUser("user-inbox-baseline");
    List<Long> targetAccountIds = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      long accountId = insertAccount("baseline target " + i);
      targetAccountIds.add(accountId);
      insertMembership(userId, accountId, "VIEWER", "ACTIVE");
      insertNotifications(accountId, "target-" + i, ROWS_PER_ACCOUNT);
    }
    long revokedAccountId = insertAccount("baseline revoked");
    insertMembership(userId, revokedAccountId, "VIEWER", "REVOKED");
    insertNotifications(revokedAccountId, "revoked", ROWS_PER_ACCOUNT);

    for (int i = 1; i <= NOISE_ACCOUNT_COUNT; i++) {
      long noiseUserId = insertUser("noise-user-inbox-" + i);
      long noiseAccountId = insertAccount("noise inbox " + i);
      insertMembership(noiseUserId, noiseAccountId, "OWNER", "ACTIVE");
      insertNotifications(noiseAccountId, "noise-" + i, ROWS_PER_ACCOUNT);
    }

    hideEveryNthTargetNotification(userId, targetAccountIds, 47);
    analyzeTables();
    return new BaselineWindow(userId);
  }

  private NotificationExplainPlan explain(long userId, NotificationListQuery query) {
    NotificationUserInboxQueryStatement statement =
        NotificationUserInboxQueryStatement.from(userId, query);
    String explainJson =
        jdbcTemplate.queryForObject(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)\n" + statement.sql(),
            statement.params(),
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

  private void insertMembership(long userId, long accountId, String role, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO user_account_membership (
            user_id,
            account_id,
            membership_role,
            membership_status,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :accountId,
            :role,
            :status,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("role", role)
            .addValue("status", status));
  }

  private void insertNotifications(long accountId, String prefix, int count) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_inbox (
            account_id,
            event_key,
            event_type,
            title,
            message,
            archived_at,
            created_at
        )
        SELECT :accountId,
               :prefix || '-' || gs::text,
               CASE WHEN gs % 7 = 0 THEN 'TransferReversed' ELSE 'TransferBooked' END,
               'title ' || gs::text,
               'message ' || gs::text,
               CASE WHEN gs % 113 = 0 THEN CAST(:base AS timestamptz) ELSE NULL::timestamptz END,
               CAST(:base AS timestamptz) - (gs * INTERVAL '1 second')
        FROM generate_series(1, :count) gs
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("prefix", prefix)
            .addValue("count", count)
            .addValue("base", Timestamp.from(BASE)));
  }

  private void hideEveryNthTargetNotification(
      long userId, List<Long> targetAccountIds, int modulo) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_user_read_state (
            user_id,
            notification_id,
            read_at,
            archived_at,
            deleted_at
        )
        SELECT :userId,
               n.id,
               NULL,
               NULL,
               :deletedAt
        FROM notification_inbox n
        WHERE n.account_id IN (:accountIds)
          AND n.id % :modulo = 0
        ON CONFLICT (user_id, notification_id) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountIds", targetAccountIds)
            .addValue("modulo", modulo)
            .addValue("deletedAt", Timestamp.from(BASE)));
  }

  private void analyzeTables() {
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE bank_user");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE user_account_membership");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE notification_inbox");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE notification_user_read_state");
  }

  private record BaselineWindow(long userId) {}
}
