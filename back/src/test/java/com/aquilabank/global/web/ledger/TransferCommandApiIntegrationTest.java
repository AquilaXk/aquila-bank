package com.aquilabank.global.web.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
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
class TransferCommandApiIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;
  private long sourceAccountId;
  private long targetAccountId;

  @BeforeEach
  void setUpDatabase() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    resetBankingTables(jdbcTemplate);

    AccountBootstrapResponseView sourceAccount = bootstrapAccount("source account", 10_000L);
    AccountBootstrapResponseView targetAccount = bootstrapAccount("target account", 0L);

    sourceAccountId = sourceAccount.accountId();
    targetAccountId = targetAccount.accountId();
  }

  @Test
  void createsTransferAndPersistsLedgerReadModelOutboxAndIdempotency() throws Exception {
    TransferResponseView response = invokeTransfer("transfer-001", targetAccountId, 1_500L, "rent");

    assertEquals(sourceAccountId, response.sourceAccountId());
    assertEquals(targetAccountId, response.targetAccountId());
    assertEquals(8_500L, response.availableBalanceAfterMinor());
    assertEquals("transfer-001-request", response.requestId());
    assertEquals(2L, countRows("ledger_entry", response.transactionReference()));
    assertEquals(2L, countRows("transaction_read_model", response.transactionReference()));
    assertEquals(8_500L, balanceOf(sourceAccountId));
    assertEquals(1_500L, balanceOf(targetAccountId));
    assertEquals(2L, countTraceRows(response.requestId()));

    Map<String, Object> commandState = idempotencyState("transfer-001");
    assertEquals("COMPLETED", commandState.get("processing_status"));
    assertEquals(200, ((Number) commandState.get("response_code")).intValue());

    Map<String, Object> outboxState = outboxState(response.transactionReference());
    assertEquals("TRANSFER", outboxState.get("aggregate_type"));
    assertEquals("TransferBooked", outboxState.get("event_type"));
    assertEquals("PENDING", outboxState.get("publish_status"));
    assertTrue(
        String.valueOf(outboxState.get("payload")).contains(response.transactionReference()));
  }

  @Test
  void reusesCompletedResponseForSameIdempotencyKeyWithoutDuplicatingWriteRows() throws Exception {
    TransferResponseView first = invokeTransfer("transfer-002", targetAccountId, 900L, "utilities");
    TransferResponseView second =
        invokeTransfer("transfer-002", targetAccountId, 900L, "utilities");

    assertEquals(first.transactionReference(), second.transactionReference());
    assertEquals(2L, countRows("ledger_entry", first.transactionReference()));
    assertEquals(2L, countRows("transaction_read_model", first.transactionReference()));
    assertEquals(1L, outboxCount(first.transactionReference()));
    assertEquals(1L, idempotencyCount("transfer-002"));
  }

  @Test
  void rollsBackEntireTransactionWhenBalanceIsInsufficient() throws Exception {
    commit(transactionManager, () -> updateBalance(sourceAccountId, 100L));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "transfer-003-request")
                    .header("Idempotency-Key", "transfer-003")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent"
                    }
                    """
                            .formatted(sourceAccountId, targetAccountId)))
            .andReturn();

    assertEquals(409, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        "available balance is not enough",
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());

    assertEquals(1L, totalCount("ledger_entry"));
    assertEquals(1L, totalCount("transaction_read_model"));
    assertEquals(0L, totalCount("outbox_event"));
    assertEquals(0L, totalCount("command_idempotency"));
    assertEquals(100L, balanceOf(sourceAccountId));
    assertEquals(0L, balanceOf(targetAccountId));
  }

  @Test
  void reversesTransferAndPersistsReversalLedgerReadModelOutboxAndIdempotency() throws Exception {
    TransferResponseView booked = invokeTransfer("transfer-004", targetAccountId, 1_500L, "rent");

    TransferReversalResponseView reversed =
        invokeReversal(booked.transactionReference(), "reversal-001", "CANCEL", "cancel rent");

    assertEquals(booked.transactionReference(), reversed.originalTransactionReference());
    assertEquals(sourceAccountId, reversed.sourceAccountId());
    assertEquals(targetAccountId, reversed.targetAccountId());
    assertEquals(10_000L, reversed.availableBalanceAfterMinor());
    assertEquals("reversal-001-request", reversed.requestId());
    assertEquals(2L, countRows("ledger_entry", booked.transactionReference()));
    assertEquals(2L, countRows("ledger_entry", reversed.reversalTransactionReference()));
    assertEquals(2L, countRows("transaction_read_model", reversed.reversalTransactionReference()));
    assertEquals(2L, transactionStatusCount(booked.transactionReference(), "REVERSED"));
    assertEquals(10_000L, balanceOf(sourceAccountId));
    assertEquals(0L, balanceOf(targetAccountId));
    assertEquals(1L, transferReversalCount(booked.transactionReference()));
    assertEquals(2L, countTraceRows(reversed.requestId()));

    Map<String, Object> commandState = idempotencyState("reversal-001");
    assertEquals("COMPLETED", commandState.get("processing_status"));
    assertEquals(200, ((Number) commandState.get("response_code")).intValue());

    Map<String, Object> outboxState = outboxState(reversed.reversalTransactionReference());
    assertEquals("TRANSFER", outboxState.get("aggregate_type"));
    assertEquals("TransferReversed", outboxState.get("event_type"));
    assertEquals("PENDING", outboxState.get("publish_status"));
    assertTrue(String.valueOf(outboxState.get("payload")).contains(booked.transactionReference()));
    assertTrue(
        String.valueOf(outboxState.get("payload"))
            .contains(reversed.reversalTransactionReference()));
  }

  @Test
  void rejectsDuplicateReversalForSameOriginalTransfer() throws Exception {
    TransferResponseView booked =
        invokeTransfer("transfer-005", targetAccountId, 1_200L, "tuition");
    TransferReversalResponseView reversed =
        invokeReversal(booked.transactionReference(), "reversal-002", "CORRECTION", "fix tuition");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers/%s/reversal".formatted(booked.transactionReference()))
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "reversal-003-request")
                    .header("Idempotency-Key", "reversal-003")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "reversalReason": "CANCEL",
                          "summary": "duplicate cancel"
                        }
                        """
                            .formatted(sourceAccountId)))
            .andReturn();

    assertEquals(409, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        "transfer is already reversed",
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());
    assertEquals(1L, transferReversalCount(booked.transactionReference()));
    assertEquals(2L, countRows("ledger_entry", reversed.reversalTransactionReference()));
    assertEquals(1L, outboxCount(reversed.reversalTransactionReference()));
    assertEquals(0L, idempotencyCount("reversal-003"));
  }

  private AccountBootstrapResponseView bootstrapAccount(
      String displayName, long initialBalanceMinor) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/internal/api/v1/accounts/bootstrap")
                    .header("X-Request-Id", "bootstrap-%s".formatted(displayName.replace(" ", "-")))
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

    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());

    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(), AccountBootstrapResponseView.class);
  }

  private TransferResponseView invokeTransfer(
      String idempotencyKey, long targetAccountId, long amountMinor, String summary)
      throws Exception {
    String requestId = idempotencyKey + "-request";
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", requestId)
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

    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());

    return new TransferResponseView(
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), TransferResponseBody.class),
        result.getResponse().getHeader("X-Request-Id"));
  }

  private TransferReversalResponseView invokeReversal(
      String originalTransactionReference,
      String idempotencyKey,
      String reversalReason,
      String summary)
      throws Exception {
    String requestId = idempotencyKey + "-request";
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers/%s/reversal".formatted(originalTransactionReference))
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", requestId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "reversalReason": "%s",
                          "summary": "%s"
                        }
                        """
                            .formatted(sourceAccountId, reversalReason, summary)))
            .andReturn();

    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());

    return new TransferReversalResponseView(
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), TransferReversalResponseBody.class),
        result.getResponse().getHeader("X-Request-Id"));
  }

  private void updateBalance(long accountId, long availableBalanceMinor) {
    jdbcTemplate.update(
        """
        UPDATE account_balance_snapshot
        SET available_balance_minor = :availableBalanceMinor,
            updated_at = CURRENT_TIMESTAMP
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("availableBalanceMinor", availableBalanceMinor));
  }

  private long countRows(String tableName, String transactionReference) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM "
            + tableName
            + " WHERE transaction_reference = :transactionReference",
        new MapSqlParameterSource().addValue("transactionReference", transactionReference),
        Long.class);
  }

  private long totalCount(String tableName) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Map.of(), Long.class);
  }

  private long balanceOf(long accountId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT available_balance_minor
        FROM account_balance_snapshot
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId),
        Long.class);
  }

  private long outboxCount(String transactionReference) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM outbox_event
        WHERE aggregate_id = :transactionReference
        """,
        new MapSqlParameterSource().addValue("transactionReference", transactionReference),
        Long.class);
  }

  private long transferReversalCount(String originalTransactionReference) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM transfer_reversal
        WHERE original_transaction_reference = :originalTransactionReference
        """,
        new MapSqlParameterSource()
            .addValue("originalTransactionReference", originalTransactionReference),
        Long.class);
  }

  private long transactionStatusCount(String transactionReference, String transactionStatus) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM transaction_read_model
        WHERE transaction_reference = :transactionReference
          AND transaction_status = :transactionStatus
        """,
        new MapSqlParameterSource()
            .addValue("transactionReference", transactionReference)
            .addValue("transactionStatus", transactionStatus),
        Long.class);
  }

  private long idempotencyCount(String idempotencyKey) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM command_idempotency
        WHERE idempotency_key = :idempotencyKey
        """,
        new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey),
        Long.class);
  }

  private long countTraceRows(String requestId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM ledger_entry
        WHERE trace_id = :requestId
        """,
        new MapSqlParameterSource().addValue("requestId", requestId),
        Long.class);
  }

  private Map<String, Object> idempotencyState(String idempotencyKey) {
    return jdbcTemplate.queryForMap(
        """
        SELECT processing_status, response_code, response_payload::text AS response_payload
        FROM command_idempotency
        WHERE idempotency_key = :idempotencyKey
        """,
        new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey));
  }

  private Map<String, Object> outboxState(String transactionReference) {
    return jdbcTemplate.queryForMap(
        """
        SELECT aggregate_type,
               event_type,
               publish_status,
               payload::text AS payload
        FROM outbox_event
        WHERE aggregate_id = :transactionReference
        """,
        new MapSqlParameterSource().addValue("transactionReference", transactionReference));
  }

  private record TransferResponseView(TransferResponseBody body, String requestId) {

    private String transactionReference() {
      return body.transactionReference();
    }

    private long sourceAccountId() {
      return body.sourceAccountId();
    }

    private long targetAccountId() {
      return body.targetAccountId();
    }

    private long availableBalanceAfterMinor() {
      return body.availableBalanceAfterMinor();
    }
  }

  private record TransferReversalResponseView(TransferReversalResponseBody body, String requestId) {

    private String originalTransactionReference() {
      return body.originalTransactionReference();
    }

    private String reversalTransactionReference() {
      return body.reversalTransactionReference();
    }

    private long sourceAccountId() {
      return body.sourceAccountId();
    }

    private long targetAccountId() {
      return body.targetAccountId();
    }

    private long availableBalanceAfterMinor() {
      return body.availableBalanceAfterMinor();
    }
  }

  private record TransferResponseBody(
      String transactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      long availableBalanceAfterMinor,
      Instant bookedAt,
      String status) {}

  private record TransferReversalResponseBody(
      String originalTransactionReference,
      String reversalTransactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      long availableBalanceAfterMinor,
      Instant bookedAt,
      String status) {}

  private record AccountBootstrapResponseView(
      long accountId,
      String accountNumber,
      String displayName,
      String currencyCode,
      long availableBalanceMinor,
      String accountStatus,
      Instant createdAt) {}
}
