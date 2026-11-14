package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.model.TransferReversalCommand;
import com.aquilabank.domain.ledger.model.TransferReversalReason;
import com.aquilabank.domain.ledger.model.TransferReversalResult;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcTransferWriteRepositoryBatchIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private DataSource dataSource;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void executesTransferAndReversalAccountingWritesInSingleCteBatch() {
    long[] accounts = new long[2];
    commit(
        transactionManager,
        () -> {
          accounts[0] = createAccountWithBalance("111-111-0001", "Source", 50_000L);
          accounts[1] = createAccountWithBalance("111-111-0002", "Target", 10_000L);
        });

    List<String> executedSqls = Collections.synchronizedList(new ArrayList<>());
    DataSource countingDataSource = countingDataSource(dataSource, executedSqls);
    TransactionTemplate txTemplate =
        new TransactionTemplate(new DataSourceTransactionManager(countingDataSource));
    JdbcTransferWriteRepository countedRepository =
        new JdbcTransferWriteRepository(
            new NamedParameterJdbcTemplate(countingDataSource), objectMapper);

    TransferResult transferResult =
        txTemplate.execute(
            status ->
                countedRepository.transfer(
                    new TransferCommand(
                        accounts[0], accounts[1], 12_500L, "KRW", "배치 테스트 이체", "batch-idem-1")));
    assertThat(transferResult).isNotNull();
    assertThat(transferResult.availableBalanceAfterMinor()).isEqualTo(37_500L);
    assertThat(executedSqls).hasSize(5);

    executedSqls.clear();
    TransferReversalResult reversalResult =
        txTemplate.execute(
            status ->
                countedRepository.reverse(
                    new TransferReversalCommand(
                        transferResult.transactionReference(),
                        accounts[0],
                        12_500L,
                        TransferReversalReason.CANCEL,
                        "배치 테스트 역분개",
                        "batch-rev-idem-1")));
    assertThat(reversalResult).isNotNull();
    assertThat(reversalResult.status()).isEqualTo("REVERSED");
    assertThat(executedSqls).hasSize(9);

    Long sourceBalance =
        jdbcTemplate.queryForObject(
            "SELECT available_balance_minor FROM account_balance_snapshot WHERE account_id = :id",
            new MapSqlParameterSource().addValue("id", accounts[0]),
            Long.class);
    Long targetBalance =
        jdbcTemplate.queryForObject(
            "SELECT available_balance_minor FROM account_balance_snapshot WHERE account_id = :id",
            new MapSqlParameterSource().addValue("id", accounts[1]),
            Long.class);
    assertThat(sourceBalance).isEqualTo(50_000L);
    assertThat(targetBalance).isEqualTo(10_000L);
  }

  private long createAccountWithBalance(String number, String name, long balanceMinor) {
    Long accountId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (account_number, display_name, account_status, currency_code)
            VALUES (:accountNumber, :displayName, 'ACTIVE', 'KRW')
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountNumber", number)
                .addValue("displayName", name),
            Long.class);
    jdbcTemplate.update(
        """
        INSERT INTO account_balance_snapshot (
            account_id,
            last_applied_ledger_entry_id,
            available_balance_minor,
            pending_balance_minor,
            currency_code
        )
        VALUES (:accountId, 0, :balanceMinor, 0, 'KRW')
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("balanceMinor", balanceMinor));
    return accountId;
  }

  private static DataSource countingDataSource(DataSource delegate, List<String> executedSqls) {
    return (DataSource)
        Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
              Object result = method.invoke(delegate, args);
              if ("getConnection".equals(method.getName()) && result instanceof Connection conn) {
                return Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (connProxy, connMethod, connArgs) -> {
                      if ("prepareStatement".equals(connMethod.getName())
                          && connArgs != null
                          && connArgs.length > 0
                          && connArgs[0] instanceof String sql) {
                        executedSqls.add(sql);
                      }
                      return connMethod.invoke(conn, connArgs);
                    });
              }
              return result;
            });
  }
}
