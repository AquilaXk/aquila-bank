package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxEntry;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.global.persistence.notification.JdbcNotificationChannelOutboxRepository;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "outbox.ops.enabled=true",
      "outbox.ops.token-header=X-Outbox-Ops-Token",
      "outbox.ops.token=test-outbox-ops-token",
      "notification.channel-provider.ops.enabled=true",
      "notification.channel-provider.ops.quarantined-list-limit=2"
    })
class NotificationChannelOutboxOpsApiIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private WebApplicationContext context;

  @Autowired private JdbcNotificationChannelOutboxRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void returnsQuarantinedEventsWithConfiguredLimitCap() throws Exception {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant base = Instant.parse("2026-04-23T01:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-ops-list-user");
          accountId[0] = insertAccount("channel ops list account");
        });
    List<NotificationChannelOutboxEntry> items = new ArrayList<>();
    commit(
        transactionManager,
        () -> {
          items.add(cleanupItem(userId[0], accountId[0], "evt-channel-ops-old", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-channel-ops-new", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-channel-ops-skip", base));
        });
    repository.appendAllIfAbsent(items);
    commit(
        transactionManager,
        () -> {
          updateDeliveryState(
              "evt-channel-ops-old",
              NotificationChannelDeliveryStatus.QUARANTINED,
              base.minusSeconds(20),
              4,
              "provider timeout");
          updateDeliveryState(
              "evt-channel-ops-new",
              NotificationChannelDeliveryStatus.QUARANTINED,
              base.minusSeconds(5),
              7,
              "provider rejected");
          updateDeliveryState(
              "evt-channel-ops-skip",
              NotificationChannelDeliveryStatus.FAILED,
              base.minusSeconds(1),
              1,
              "provider failed");
        });

    mockMvc
        .perform(
            get("/internal/api/v1/outbox/notification-channel/quarantined-events")
                .header("Authorization", outboxOpsAuthorization())
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].eventKey").value("evt-channel-ops-new"))
        .andExpect(jsonPath("$.items[0].retryCount").value(7))
        .andExpect(jsonPath("$.items[1].eventKey").value("evt-channel-ops-old"));
  }

  @Test
  void redrivesQuarantinedEventThroughInternalOpsApi() throws Exception {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant base = Instant.parse("2026-04-23T01:10:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-ops-redrive-user");
          accountId[0] = insertAccount("channel ops redrive account");
        });
    List<NotificationChannelOutboxEntry> items = new ArrayList<>();
    commit(
        transactionManager,
        () -> items.add(cleanupItem(userId[0], accountId[0], "evt-channel-ops-redrive", base)));
    repository.appendAllIfAbsent(items);
    commit(
        transactionManager,
        () ->
            updateDeliveryState(
                "evt-channel-ops-redrive",
                NotificationChannelDeliveryStatus.QUARANTINED,
                base.minusSeconds(10),
                9,
                "provider rejected"));
    long rowId = findIdByEventKey("evt-channel-ops-redrive");

    mockMvc
        .perform(
            post(
                    "/internal/api/v1/outbox/notification-channel/quarantined-events/{id}/redrive",
                    rowId)
                .header("Authorization", outboxOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(rowId))
        .andExpect(jsonPath("$.outcome").value("REDRIVEN"))
        .andExpect(jsonPath("$.currentStatus").value("PENDING"))
        .andExpect(jsonPath("$.retryCount").value(9));

    DeliveryRow row = findDeliveryRow(rowId);
    assertThat(row.deliveryStatus()).isEqualTo(NotificationChannelDeliveryStatus.PENDING);
    assertThat(row.retryCount()).isEqualTo(9);
    assertThat(row.lastError()).isNull();
  }

  @Test
  void returnsExplicitOutcomeForMissingOrDifferentStatusRows() throws Exception {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant base = Instant.parse("2026-04-23T01:20:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-ops-skip-user");
          accountId[0] = insertAccount("channel ops skip account");
        });
    List<NotificationChannelOutboxEntry> items = new ArrayList<>();
    commit(
        transactionManager,
        () -> items.add(cleanupItem(userId[0], accountId[0], "evt-channel-ops-pending", base)));
    repository.appendAllIfAbsent(items);
    long pendingId = findIdByEventKey("evt-channel-ops-pending");

    mockMvc
        .perform(
            post(
                    "/internal/api/v1/outbox/notification-channel/quarantined-events/{id}/redrive",
                    999_999L)
                .header("Authorization", outboxOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("NOT_FOUND"))
        .andExpect(jsonPath("$.currentStatus").doesNotExist());

    mockMvc
        .perform(
            post(
                    "/internal/api/v1/outbox/notification-channel/quarantined-events/{id}/redrive",
                    pendingId)
                .header("Authorization", outboxOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("NOT_QUARANTINED"))
        .andExpect(jsonPath("$.currentStatus").value("PENDING"));
  }

  @Test
  void rejectsMissingInternalServiceToken() throws Exception {
    mockMvc
        .perform(get("/internal/api/v1/outbox/notification-channel/quarantined-events"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  private String outboxOpsAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue("outbox-ops", Set.of(InternalServiceScope.OUTBOX_OPS));
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

  private long insertNotification(
      long accountId,
      String eventKey,
      String eventType,
      String title,
      String message,
      Instant createdAt) {
    Long notificationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO notification_inbox (
                account_id,
                event_key,
                event_type,
                title,
                message,
                created_at
            )
            VALUES (
                :accountId,
                :eventKey,
                :eventType,
                :title,
                :message,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("eventKey", eventKey)
                .addValue("eventType", eventType)
                .addValue("title", title)
                .addValue("message", message)
                .addValue("createdAt", Timestamp.from(createdAt)),
            Long.class);
    if (notificationId == null) {
      throw new IllegalStateException("notification_inbox insert did not return id");
    }
    return notificationId;
  }

  private NotificationChannelOutboxEntry cleanupItem(
      long userId, long accountId, String eventKey, Instant base) {
    long notificationId =
        insertNotification(
            accountId, eventKey, "TransferBooked", "이체 완료", eventKey + " message", base);
    return new NotificationChannelOutboxEntry(
        notificationId,
        userId,
        accountId,
        NotificationPreferenceCategory.TRANSACTIONAL,
        NotificationPreferenceChannel.EMAIL,
        "TransferBooked",
        eventKey,
        "{\"kind\":\"ops\"}",
        base.minusSeconds(10),
        base);
  }

  private void updateDeliveryState(
      String eventKey,
      NotificationChannelDeliveryStatus status,
      Instant updatedAt,
      int retryCount,
      String lastError) {
    jdbcTemplate.update(
        """
        UPDATE notification_channel_outbox
        SET delivery_status = :status,
            retry_count = :retryCount,
            last_error = :lastError,
            updated_at = :updatedAt
        WHERE event_key = :eventKey
        """,
        new MapSqlParameterSource()
            .addValue("eventKey", eventKey)
            .addValue("status", status.name())
            .addValue("retryCount", retryCount)
            .addValue("lastError", lastError)
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  private long findIdByEventKey(String eventKey) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM notification_channel_outbox
            WHERE event_key = :eventKey
            """,
            new MapSqlParameterSource().addValue("eventKey", eventKey),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("notification_channel_outbox row not found");
    }
    return id;
  }

  private DeliveryRow findDeliveryRow(long id) {
    return jdbcTemplate.queryForObject(
        """
        SELECT delivery_status,
               available_at,
               retry_count,
               last_error
        FROM notification_channel_outbox
        WHERE id = :id
        """,
        new MapSqlParameterSource().addValue("id", id),
        (rs, rowNum) ->
            new DeliveryRow(
                NotificationChannelDeliveryStatus.valueOf(rs.getString("delivery_status")),
                rs.getObject("available_at", OffsetDateTime.class).toInstant(),
                rs.getInt("retry_count"),
                rs.getString("last_error")));
  }

  private record DeliveryRow(
      NotificationChannelDeliveryStatus deliveryStatus,
      Instant availableAt,
      int retryCount,
      String lastError) {}
}
