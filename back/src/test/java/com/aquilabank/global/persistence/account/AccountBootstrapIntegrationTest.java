package com.aquilabank.global.persistence.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class AccountBootstrapIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private AccountBootstrapUseCase accountBootstrapUseCase;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void createsBankAccountSnapshotAndOpeningLedgerFromSingleBootstrapCommand() {
    AccountBootstrapResult result =
        accountBootstrapUseCase.bootstrap(
            new AccountBootstrapCommand("main account", "KRW", 10_000L));

    Map<String, Object> accountRow = loadAccountRow(result.accountId());
    Map<String, Object> snapshotRow = loadSnapshotRow(result.accountId());
    long openingLedgerEntryId = openingLedgerEntryId(result.accountNumber());

    assertTrue(result.accountNumber().startsWith("100"));
    assertEquals(14, result.accountNumber().length());
    assertEquals("main account", accountRow.get("display_name"));
    assertEquals("ACTIVE", accountRow.get("account_status"));
    assertEquals("KRW", accountRow.get("currency_code"));
    assertEquals(10_000L, ((Number) snapshotRow.get("available_balance_minor")).longValue());
    assertEquals(0L, ((Number) snapshotRow.get("pending_balance_minor")).longValue());
    assertEquals(
        openingLedgerEntryId,
        ((Number) snapshotRow.get("last_applied_ledger_entry_id")).longValue());
    assertEquals(1L, countOpeningRows("ledger_entry", result.accountNumber()));
    assertEquals(1L, countOpeningRows("transaction_read_model", result.accountNumber()));
  }

  @Test
  void createsZeroBalanceAccountWithoutOpeningLedgerRows() {
    AccountBootstrapResult result =
        accountBootstrapUseCase.bootstrap(new AccountBootstrapCommand("empty account", "KRW", 0L));

    Map<String, Object> snapshotRow = loadSnapshotRow(result.accountId());

    assertEquals(0L, ((Number) snapshotRow.get("available_balance_minor")).longValue());
    assertEquals(0L, ((Number) snapshotRow.get("last_applied_ledger_entry_id")).longValue());
    assertEquals(0L, totalCount("ledger_entry"));
    assertEquals(0L, totalCount("transaction_read_model"));
  }

  private Map<String, Object> loadAccountRow(long accountId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT account_number, display_name, account_status, currency_code
        FROM bank_account
        WHERE id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  private Map<String, Object> loadSnapshotRow(long accountId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT last_applied_ledger_entry_id,
               available_balance_minor,
               pending_balance_minor
        FROM account_balance_snapshot
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  private long openingLedgerEntryId(String accountNumber) {
    return jdbcTemplate.queryForObject(
        """
        SELECT id
        FROM ledger_entry
        WHERE transaction_reference = :transactionReference
        """,
        new MapSqlParameterSource()
            .addValue("transactionReference", openingReference(accountNumber)),
        Long.class);
  }

  private long countOpeningRows(String tableName, String accountNumber) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM "
            + tableName
            + " WHERE transaction_reference = :transactionReference",
        new MapSqlParameterSource()
            .addValue("transactionReference", openingReference(accountNumber)),
        Long.class);
  }

  private long totalCount(String tableName) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Map.of(), Long.class);
  }

  private String openingReference(String accountNumber) {
    return "OPEN-" + accountNumber;
  }
}
