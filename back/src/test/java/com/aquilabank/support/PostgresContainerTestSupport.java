package com.aquilabank.support;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresContainerTestSupport {

  @Container
  private static final PostgreSQLContainer<?> POSTGRESQL =
      new PostgreSQLContainer<>("postgres:18")
          .withDatabaseName("aquila_bank_test")
          .withUsername("postgres")
          .withPassword("postgres");

  @DynamicPropertySource
  static void registerPostgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("management.health.db.enabled", () -> true);
  }

  protected void resetBankingTables(NamedParameterJdbcTemplate jdbcTemplate) {
    migrateSchema(jdbcTemplate.getJdbcTemplate().getDataSource());
    jdbcTemplate
        .getJdbcTemplate()
        .execute(
            (ConnectionCallback<Void>)
                connection -> {
                  try (java.sql.Statement statement = connection.createStatement()) {
                    statement.execute(
                        """
                        TRUNCATE TABLE
                            auth_status_change_audit,
                            notification_unread_count_projection,
                            notification_preference,
                            notification_user_read_state,
                            notification_inbox,
                            transaction_read_model,
                            ledger_entry,
                            command_idempotency,
                            outbox_event,
                            account_balance_snapshot,
                            user_account_membership,
                            bank_user,
                            bank_account
                        RESTART IDENTITY CASCADE
                        """);
                    statement.execute("ALTER SEQUENCE bank_account_number_seq RESTART WITH 1");
                    connection.commit();
                    return null;
                  } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                  }
                });
  }

  protected void commit(PlatformTransactionManager transactionManager, Runnable callback) {
    commit(transactionManager, null, callback);
  }

  protected void commit(
      PlatformTransactionManager transactionManager, Integer timeoutSeconds, Runnable callback) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    if (timeoutSeconds != null) {
      transactionTemplate.setTimeout(timeoutSeconds);
    }
    transactionTemplate.executeWithoutResult(status -> callback.run());
  }

  private void migrateSchema(DataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalStateException("test datasource is not configured");
    }
    // Integration tests must execute the same PostgreSQL migrations as runtime instead of
    // relying on an in-memory fallback.
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
  }
}
