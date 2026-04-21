package com.aquilabank.global.web.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "ledger.transfer-limit.single-transfer-limit-minor=2000",
      "ledger.transfer-limit.daily-transfer-limit-minor=3000",
      "ledger.transfer-limit.business-zone-id=Asia/Seoul"
    })
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
  void rejectsTransferWhenSingleLimitIsExceededWithoutWriteSideEffects() throws Exception {
    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        invokeTransferRaw("transfer-limit-single-001", targetAccountId, 2_001L, "single limit");

    assertTransferLimitExceeded(result, "single transfer limit exceeded");
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(0L, idempotencyCount("transfer-limit-single-001"));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
  }

  @Test
  void rejectsTransferWhenDailyLimitIsExceededWithoutWriteSideEffects() throws Exception {
    invokeTransfer("transfer-limit-daily-001", targetAccountId, 2_000L, "daily base");
    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        invokeTransferRaw("transfer-limit-daily-002", targetAccountId, 1_100L, "daily limit");

    assertTransferLimitExceeded(result, "daily transfer limit exceeded");
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(0L, idempotencyCount("transfer-limit-daily-002"));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
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
  void partiallyReversesTransferAndKeepsOriginalPartiallyReversed() throws Exception {
    TransferResponseView booked =
        invokeTransfer("transfer-partial-001", targetAccountId, 1_500L, "rent");

    TransferReversalResponseView reversed =
        invokeReversal(
            booked.transactionReference(),
            "reversal-partial-001",
            500L,
            "CORRECTION",
            "partial rent");

    assertEquals(booked.transactionReference(), reversed.originalTransactionReference());
    assertEquals(500L, reversed.amountMinor());
    assertEquals(9_000L, reversed.availableBalanceAfterMinor());
    assertEquals("PARTIALLY_REVERSED", reversed.status());
    assertEquals(2L, countRows("ledger_entry", booked.transactionReference()));
    assertEquals(2L, countRows("ledger_entry", reversed.reversalTransactionReference()));
    assertEquals(2L, countRows("transaction_read_model", reversed.reversalTransactionReference()));
    assertEquals(2L, transactionStatusCount(booked.transactionReference(), "PARTIALLY_REVERSED"));
    assertEquals(9_000L, balanceOf(sourceAccountId));
    assertEquals(1_000L, balanceOf(targetAccountId));
    assertEquals(1L, transferReversalCount(booked.transactionReference()));

    Map<String, Object> outboxState = outboxState(reversed.reversalTransactionReference());
    assertEquals("TransferReversed", outboxState.get("event_type"));
    assertEquals(
        500,
        objectMapper
            .readTree(String.valueOf(outboxState.get("payload")))
            .get("amountMinor")
            .asInt());
  }

  @Test
  void completesOriginalStatusWhenPartialReversalsReachOriginalAmount() throws Exception {
    TransferResponseView booked =
        invokeTransfer("transfer-partial-002", targetAccountId, 1_500L, "rent");

    TransferReversalResponseView first =
        invokeReversal(
            booked.transactionReference(),
            "reversal-partial-002",
            500L,
            "CORRECTION",
            "partial rent");
    TransferReversalResponseView second =
        invokeReversal(
            booked.transactionReference(),
            "reversal-partial-003",
            1_000L,
            "CANCEL",
            "remaining rent");

    assertEquals("PARTIALLY_REVERSED", first.status());
    assertEquals("REVERSED", second.status());
    assertEquals(2L, transactionStatusCount(booked.transactionReference(), "REVERSED"));
    assertEquals(10_000L, balanceOf(sourceAccountId));
    assertEquals(0L, balanceOf(targetAccountId));
    assertEquals(2L, transferReversalCount(booked.transactionReference()));
  }

  @Test
  void rejectsPartialReversalWhenAmountExceedsRemainingAmount() throws Exception {
    TransferResponseView booked =
        invokeTransfer("transfer-partial-003", targetAccountId, 1_500L, "rent");
    invokeReversal(
        booked.transactionReference(), "reversal-partial-004", 1_000L, "CORRECTION", "part");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers/%s/reversal".formatted(booked.transactionReference()))
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "reversal-partial-005-request")
                    .header("Idempotency-Key", "reversal-partial-005")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "amountMinor": 600,
                          "reversalReason": "CANCEL",
                          "summary": "too much"
                        }
                        """
                            .formatted(sourceAccountId)))
            .andReturn();

    assertEquals(409, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        "reversal amount exceeds remaining amount",
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());
    assertEquals(1L, transferReversalCount(booked.transactionReference()));
    assertEquals(9_500L, balanceOf(sourceAccountId));
    assertEquals(500L, balanceOf(targetAccountId));
  }

  @Test
  void reversesRemainingAmountWhenAmountIsOmittedAfterPartialReversal() throws Exception {
    TransferResponseView booked =
        invokeTransfer("transfer-partial-004", targetAccountId, 1_500L, "rent");
    invokeReversal(
        booked.transactionReference(), "reversal-partial-006", 500L, "CORRECTION", "part");

    TransferReversalResponseView second =
        invokeReversal(booked.transactionReference(), "reversal-partial-007", "CANCEL", "rest");

    assertEquals(1_000L, second.amountMinor());
    assertEquals("REVERSED", second.status());
    assertEquals(10_000L, balanceOf(sourceAccountId));
    assertEquals(0L, balanceOf(targetAccountId));
    assertEquals(2L, transferReversalCount(booked.transactionReference()));
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

  @Test
  void rejectsTransferWhenSourceAccountIsLockedWithoutWriteSideEffects() throws Exception {
    updateAccountStatus(sourceAccountId, "LOCKED", "transfer-locked-request");

    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "transfer-locked-001-request")
                    .header("Idempotency-Key", "transfer-locked-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "locked"
                    }
                    """
                            .formatted(sourceAccountId, targetAccountId)))
            .andReturn();

    assertEquals(403, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        "account access is denied",
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
  }

  @Test
  void rejectsTransferWhenTargetAccountIsLockedWithoutWriteSideEffects() throws Exception {
    updateAccountStatus(targetAccountId, "LOCKED", "transfer-target-locked-request");

    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "transfer-target-locked-001-request")
                    .header("Idempotency-Key", "transfer-target-locked-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "target locked"
                    }
                    """
                            .formatted(sourceAccountId, targetAccountId)))
            .andReturn();

    assertAccountAccessDenied(result);
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
  }

  @Test
  void rejectsTransferWhenTargetAccountIsClosedWithoutWriteSideEffects() throws Exception {
    updateAccountStatus(targetAccountId, "CLOSED", "transfer-target-closed-request");

    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "transfer-target-closed-001-request")
                    .header("Idempotency-Key", "transfer-target-closed-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "target closed"
                    }
                    """
                            .formatted(sourceAccountId, targetAccountId)))
            .andReturn();

    assertAccountAccessDenied(result);
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
  }

  @Test
  void rejectsReversalWhenSourceAccountIsClosedWithoutWriteSideEffects() throws Exception {
    TransferResponseView booked = invokeTransfer("transfer-006", targetAccountId, 1_500L, "rent");

    updateAccountStatus(sourceAccountId, "CLOSED", "reversal-closed-request");

    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long reversalCountBefore = transferReversalCount(booked.transactionReference());
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers/%s/reversal".formatted(booked.transactionReference()))
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "reversal-closed-001-request")
                    .header("Idempotency-Key", "reversal-closed-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "reversalReason": "CANCEL",
                          "summary": "closed"
                        }
                        """
                            .formatted(sourceAccountId)))
            .andReturn();

    assertEquals(403, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        "account access is denied",
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(reversalCountBefore, transferReversalCount(booked.transactionReference()));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
  }

  @Test
  void rejectsReversalWhenTargetAccountIsLockedWithoutWriteSideEffects() throws Exception {
    TransferResponseView booked = invokeTransfer("transfer-007", targetAccountId, 1_500L, "rent");

    updateAccountStatus(targetAccountId, "LOCKED", "reversal-target-locked-request");

    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long reversalCountBefore = transferReversalCount(booked.transactionReference());
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers/%s/reversal".formatted(booked.transactionReference()))
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "reversal-target-locked-001-request")
                    .header("Idempotency-Key", "reversal-target-locked-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "reversalReason": "CANCEL",
                          "summary": "target locked"
                        }
                        """
                            .formatted(sourceAccountId)))
            .andReturn();

    assertAccountAccessDenied(result);
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(reversalCountBefore, transferReversalCount(booked.transactionReference()));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
  }

  @Test
  void rejectsReversalWhenTargetAccountIsClosedWithoutWriteSideEffects() throws Exception {
    TransferResponseView booked = invokeTransfer("transfer-008", targetAccountId, 1_500L, "rent");

    updateAccountStatus(targetAccountId, "CLOSED", "reversal-target-closed-request");

    long ledgerCountBefore = totalCount("ledger_entry");
    long transactionCountBefore = totalCount("transaction_read_model");
    long outboxCountBefore = totalCount("outbox_event");
    long idempotencyCountBefore = totalCount("command_idempotency");
    long reversalCountBefore = transferReversalCount(booked.transactionReference());
    long sourceBalanceBefore = balanceOf(sourceAccountId);
    long targetBalanceBefore = balanceOf(targetAccountId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers/%s/reversal".formatted(booked.transactionReference()))
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "reversal-target-closed-001-request")
                    .header("Idempotency-Key", "reversal-target-closed-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "reversalReason": "CANCEL",
                          "summary": "target closed"
                        }
                        """
                            .formatted(sourceAccountId)))
            .andReturn();

    assertAccountAccessDenied(result);
    assertEquals(ledgerCountBefore, totalCount("ledger_entry"));
    assertEquals(transactionCountBefore, totalCount("transaction_read_model"));
    assertEquals(outboxCountBefore, totalCount("outbox_event"));
    assertEquals(idempotencyCountBefore, totalCount("command_idempotency"));
    assertEquals(reversalCountBefore, transferReversalCount(booked.transactionReference()));
    assertEquals(sourceBalanceBefore, balanceOf(sourceAccountId));
    assertEquals(targetBalanceBefore, balanceOf(targetAccountId));
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
    MvcResult result = invokeTransferRaw(idempotencyKey, targetAccountId, amountMinor, summary);

    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());

    return new TransferResponseView(
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), TransferResponseBody.class),
        result.getResponse().getHeader("X-Request-Id"));
  }

  private MvcResult invokeTransferRaw(
      String idempotencyKey, long targetAccountId, long amountMinor, String summary)
      throws Exception {
    String requestId = idempotencyKey + "-request";
    return mockMvc
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

  private TransferReversalResponseView invokeReversal(
      String originalTransactionReference,
      String idempotencyKey,
      long amountMinor,
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
                          "amountMinor": %d,
                          "reversalReason": "%s",
                          "summary": "%s"
                        }
                        """
                            .formatted(sourceAccountId, amountMinor, reversalReason, summary)))
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

  private void updateAccountStatus(long accountId, String accountStatus, String requestId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                put("/internal/api/v1/accounts/%d/status".formatted(accountId))
                    .header("X-Request-Id", requestId)
                    .header(
                        "Authorization",
                        "Bearer "
                            + internalServiceTokenIssuer.issue(
                                "account-admin",
                                java.util.Set.of(InternalServiceScope.ACCOUNT_ADMIN)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountStatus": "%s"
                        }
                        """
                            .formatted(accountStatus)))
            .andReturn();

    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  private void assertAccountAccessDenied(MvcResult result) throws Exception {
    assertEquals(403, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        "account access is denied",
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());
  }

  private void assertTransferLimitExceeded(MvcResult result, String message) throws Exception {
    assertEquals(403, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        message,
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());
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

    private long amountMinor() {
      return body.amountMinor();
    }

    private String status() {
      return body.status();
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
