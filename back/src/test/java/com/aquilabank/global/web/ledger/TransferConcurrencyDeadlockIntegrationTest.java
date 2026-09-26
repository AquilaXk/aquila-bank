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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "ledger.transfer-limit.single-transfer-limit-minor=100000000",
      "ledger.transfer-limit.daily-transfer-limit-minor=100000000",
      "ledger.transfer-limit.business-zone-id=Asia/Seoul",
      "ops.api-admission-control.enabled=false"
    })
class TransferConcurrencyDeadlockIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private WebApplicationContext context;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;
  private long accountAId;
  private long accountBId;

  @BeforeEach
  void setUpDatabase() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    resetBankingTables(jdbcTemplate);

    AccountBootstrapResponseView accountA = bootstrapAccount("account-a", 100_000L);
    AccountBootstrapResponseView accountB = bootstrapAccount("account-b", 100_000L);
    accountAId = accountA.accountId();
    accountBId = accountB.accountId();
  }

  @Test
  @DisplayName("A->B 및 B->A 동시 20회 교차 송금 시 교착상태 없이 모든 이체가 성공한다")
  void concurrentCrossTransfersSucceedWithoutDeadlock() throws Exception {
    int transferPairs = 20;
    int totalTransfers = transferPairs * 2;
    long transferAmountMinor = 100L;

    ExecutorService executor = Executors.newFixedThreadPool(16);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(totalTransfers);

    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger errorCount = new AtomicInteger();
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    long initialLedgerCount = totalCount("ledger_entry");
    long initialTransactionCount = totalCount("transaction_read_model");

    for (int i = 0; i < transferPairs; i++) {
      final int idx = i;
      // Transfer A -> B
      executor.submit(
          () -> {
            try {
              startLatch.await();
              invokeTransfer(
                  accountAId,
                  accountBId,
                  "cross-a2b-" + idx,
                  transferAmountMinor,
                  "cross transfer A to B " + idx);
              successCount.incrementAndGet();
            } catch (Throwable t) {
              errorCount.incrementAndGet();
              errors.add(t);
            } finally {
              completionLatch.countDown();
            }
          });

      // Transfer B -> A
      executor.submit(
          () -> {
            try {
              startLatch.await();
              invokeTransfer(
                  accountBId,
                  accountAId,
                  "cross-b2a-" + idx,
                  transferAmountMinor,
                  "cross transfer B to A " + idx);
              successCount.incrementAndGet();
            } catch (Throwable t) {
              errorCount.incrementAndGet();
              errors.add(t);
            } finally {
              completionLatch.countDown();
            }
          });
    }

    startLatch.countDown();

    boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
    executor.shutdown();

    assertTrue(completed, "All concurrent transfers must complete within timeout");
    if (!errors.isEmpty()) {
      errors.get(0).printStackTrace();
    }
    assertEquals(0, errorCount.get(), "No deadlock or errors should occur during cross transfers");
    assertEquals(totalTransfers, successCount.get(), "All cross transfers must succeed");

    assertEquals(100_000L, balanceOf(accountAId));
    assertEquals(100_000L, balanceOf(accountBId));

    assertEquals(initialLedgerCount + 80L, totalCount("ledger_entry"));
    assertEquals(initialTransactionCount + 80L, totalCount("transaction_read_model"));
  }

  private void invokeTransfer(
      long fromAccountId, long toAccountId, String idempotencyKey, long amountMinor, String summary)
      throws Exception {
    String requestId = idempotencyKey + "-req";
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(fromAccountId))
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
                            .formatted(fromAccountId, toAccountId, amountMinor, summary)))
            .andReturn();

    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
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

  private long balanceOf(long accountId) {
    return jdbcTemplate.queryForObject(
        "SELECT available_balance_minor FROM account_balance_snapshot WHERE account_id = :accountId",
        new MapSqlParameterSource().addValue("accountId", accountId),
        Long.class);
  }

  private long totalCount(String tableName) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Map.of(), Long.class);
  }

  private record AccountBootstrapResponseView(
      long accountId,
      String accountNumber,
      String displayName,
      String currencyCode,
      long availableBalanceMinor,
      String accountStatus,
      Instant createdAt) {}
}
