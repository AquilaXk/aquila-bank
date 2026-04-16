package com.aquilabank.global.web.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

  private MockMvc mockMvc;

  @BeforeEach
  void setUpDatabase() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    commit(
        transactionManager,
        () -> {
          resetBankingTables(jdbcTemplate);
          seedSnapshot(101L, 10_000L, "KRW");
          seedSnapshot(202L, 5_000L, "KRW");
        });
  }

  @Test
  void createsTransferAndPersistsLedgerReadModelOutboxAndIdempotency() throws Exception {
    TransferResponseView response =
        invokeTransfer(
            "transfer-001",
            """
            {
              "targetAccountId": 202,
              "amountMinor": 1500,
              "currencyCode": "KRW",
              "summary": "rent"
            }
            """);

    assertEquals(101L, response.sourceAccountId());
    assertEquals(202L, response.targetAccountId());
    assertEquals(8_500L, response.availableBalanceAfterMinor());
    assertEquals(2L, countRows("ledger_entry", response.transactionReference()));
    assertEquals(2L, countRows("transaction_read_model", response.transactionReference()));
    assertEquals(8_500L, balanceOf(101L));
    assertEquals(6_500L, balanceOf(202L));

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
    TransferResponseView first =
        invokeTransfer(
            "transfer-002",
            """
            {
              "targetAccountId": 202,
              "amountMinor": 900,
              "currencyCode": "KRW",
              "summary": "utilities"
            }
            """);
    TransferResponseView second =
        invokeTransfer(
            "transfer-002",
            """
            {
              "targetAccountId": 202,
              "amountMinor": 900,
              "currencyCode": "KRW",
              "summary": "utilities"
            }
            """);

    assertEquals(first.transactionReference(), second.transactionReference());
    assertEquals(2L, countRows("ledger_entry", first.transactionReference()));
    assertEquals(2L, countRows("transaction_read_model", first.transactionReference()));
    assertEquals(1L, outboxCount(first.transactionReference()));
    assertEquals(1L, idempotencyCount("transfer-002"));
  }

  @Test
  void rollsBackEntireTransactionWhenBalanceIsInsufficient() throws Exception {
    commit(transactionManager, () -> updateBalance(101L, 100L));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", "101")
                    .header("Idempotency-Key", "transfer-003")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "targetAccountId": 202,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent"
                    }
                    """))
            .andReturn();

    assertEquals(409, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    assertEquals(
        "available balance is not enough",
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .get("message")
            .asText());

    assertEquals(0L, totalCount("ledger_entry"));
    assertEquals(0L, totalCount("transaction_read_model"));
    assertEquals(0L, totalCount("outbox_event"));
    assertEquals(0L, totalCount("command_idempotency"));
    assertEquals(100L, balanceOf(101L));
    assertEquals(5_000L, balanceOf(202L));
  }

  private TransferResponseView invokeTransfer(String idempotencyKey, String body) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", "101")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();

    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());

    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(), TransferResponseView.class);
  }

  private void seedSnapshot(long accountId, long availableBalanceMinor, String currencyCode) {
    jdbcTemplate.update(
        """
        INSERT INTO account_balance_snapshot (
            account_id,
            last_applied_ledger_entry_id,
            available_balance_minor,
            pending_balance_minor,
            currency_code,
            updated_at
        )
        VALUES (
            :accountId,
            0,
            :availableBalanceMinor,
            0,
            :currencyCode,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("availableBalanceMinor", availableBalanceMinor)
            .addValue("currencyCode", currencyCode));
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

  private record TransferResponseView(
      String transactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      long availableBalanceAfterMinor,
      Instant bookedAt,
      String status) {}
}
