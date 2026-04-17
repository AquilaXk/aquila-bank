package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresKafkaContainerTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class NotificationKafkaE2eIntegrationTest extends PostgresKafkaContainerTestSupport {

  private static final Duration NOTIFICATION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OutboxDispatchUseCase outboxDispatchUseCase;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;
  private long sourceAccountId;
  private long targetAccountId;

  @BeforeEach
  void setUpDatabase() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    commit(transactionManager, () -> resetBankingTables(jdbcTemplate));

    sourceAccountId = bootstrapAccount("notification-source", 10_000L).accountId();
    targetAccountId = bootstrapAccount("notification-target", 0L).accountId();
  }

  @Test
  void deliversTransferBookedEventToNotificationInboxThroughKafka() throws Exception {
    TransferResponseBody response =
        invokeTransfer("notification-e2e-001", targetAccountId, 1500L, "rent");
    OutboxEventRow outboxEvent = findOutboxEvent(response.transactionReference());

    int dispatched = outboxDispatchUseCase.dispatchPendingEvents();
    OutboxEventRow publishedEvent = findOutboxEvent(response.transactionReference());

    assertThat(dispatched).isEqualTo(1);
    assertThat(publishedEvent.publishStatus())
        .withFailMessage("outbox lastError=%s", publishedEvent.lastError())
        .isEqualTo("PUBLISHED");

    awaitCondition(
        "notification inbox rows",
        NOTIFICATION_TIMEOUT,
        POLL_INTERVAL,
        () -> countNotificationRows(outboxEvent.eventKey()) == 2L);

    List<NotificationInboxRow> items = findNotificationRows(outboxEvent.eventKey());
    assertThat(items)
        .extracting(NotificationInboxRow::accountId)
        .containsExactlyInAnyOrder(sourceAccountId, targetAccountId);
    assertThat(items).extracting(NotificationInboxRow::eventType).containsOnly("TransferBooked");
    assertThat(items).extracting(NotificationInboxRow::title).containsOnly("이체 완료");
    assertThat(items)
        .extracting(NotificationInboxRow::message)
        .containsExactlyInAnyOrder("1500 KRW 출금 · rent", "1500 KRW 입금 · rent");
  }

  private AccountBootstrapResponseBody bootstrapAccount(
      String displayName, long initialBalanceMinor) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/internal/api/v1/accounts/bootstrap")
                    .header("X-Request-Id", "bootstrap-" + displayName)
                    .header(
                        "Authorization",
                        "Bearer "
                            + internalServiceTokenIssuer.issue(
                                "account-bootstrap",
                                java.util.Set.of(InternalServiceScope.ACCOUNT_BOOTSTRAP)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "displayName": "%s",
                          "currencyCode": "KRW",
                          "initialBalanceMinor": %d
                        }
                        """
                            .formatted(displayName, initialBalanceMinor)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(), AccountBootstrapResponseBody.class);
  }

  private TransferResponseBody invokeTransfer(
      String idempotencyKey, long targetAccountId, long amountMinor, String summary)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", idempotencyKey + "-request")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "targetAccountId": %d,
                          "amountMinor": %d,
                          "currencyCode": "KRW",
                          "summary": "%s"
                        }
                        """
                            .formatted(sourceAccountId, targetAccountId, amountMinor, summary)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(), TransferResponseBody.class);
  }

  private OutboxEventRow findOutboxEvent(String transactionReference) {
    return jdbcTemplate.queryForObject(
        """
        SELECT event_key,
               publish_status,
               payload::text AS payload,
               last_error
        FROM outbox_event
        WHERE aggregate_id = :transactionReference
        """,
        new MapSqlParameterSource().addValue("transactionReference", transactionReference),
        (rs, rowNum) ->
            new OutboxEventRow(
                rs.getString("event_key"),
                rs.getString("publish_status"),
                rs.getString("payload"),
                rs.getString("last_error")));
  }

  private long countNotificationRows(String eventKey) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM notification_inbox
        WHERE event_key IN (:eventKeys)
        """,
        new MapSqlParameterSource().addValue("eventKeys", notificationEventKeys(eventKey)),
        Long.class);
  }

  private List<NotificationInboxRow> findNotificationRows(String eventKey) {
    return jdbcTemplate.query(
        """
        SELECT account_id,
               event_type,
               title,
               message,
               created_at
        FROM notification_inbox
        WHERE event_key IN (:eventKeys)
        ORDER BY account_id ASC
        """,
        new MapSqlParameterSource().addValue("eventKeys", notificationEventKeys(eventKey)),
        (rs, rowNum) ->
            new NotificationInboxRow(
                rs.getLong("account_id"),
                rs.getString("event_type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()));
  }

  private List<String> notificationEventKeys(String eventKey) {
    return List.of(
        accountScopedEventKey(eventKey, sourceAccountId),
        accountScopedEventKey(eventKey, targetAccountId));
  }

  private String accountScopedEventKey(String eventKey, long accountId) {
    return eventKey + ":ACCOUNT-" + accountId;
  }

  private record AccountBootstrapResponseBody(
      long accountId,
      String accountNumber,
      String displayName,
      String currencyCode,
      long availableBalanceMinor,
      String accountStatus,
      Instant createdAt) {}

  private record TransferResponseBody(
      String transactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      long availableBalanceAfterMinor,
      Instant bookedAt,
      String status) {}

  private record OutboxEventRow(
      String eventKey, String publishStatus, String payload, String lastError) {}

  private record NotificationInboxRow(
      long accountId, String eventType, String title, String message, Instant createdAt) {}
}
