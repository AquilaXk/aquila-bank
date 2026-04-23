package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "transaction.read-replica.enabled=true",
      "transaction.read-replica.url=${spring.datasource.url}",
      "transaction.read-replica.username=${spring.datasource.username}",
      "transaction.read-replica.password=${spring.datasource.password}",
      "transaction.read-replica.maximum-pool-size=2",
      "transaction.read-replica.minimum-idle=0"
    })
@Import(TransactionReadReplicaRoutingIntegrationTest.ReplicaProbeConfiguration.class)
class TransactionReadReplicaRoutingIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant HOT_BOOKED_AT = Instant.parse("2026-04-23T12:00:00Z");
  private static final Instant ARCHIVE_BOOKED_AT = Instant.parse("2025-03-01T12:00:00Z");

  @Autowired private JdbcTransactionReadRepository readRepository;

  @Autowired private JdbcTransactionDetailRepository detailRepository;

  @Autowired private JdbcTransactionArchiveReadRepository archiveRepository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private TransactionReadReplicaProbe replicaProbe;

  private long accountId;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
    commit(
        transactionManager,
        () -> {
          accountId = insertAccount("replica account");
          long hotLedgerEntryId =
              insertLedgerEntry(
                  accountId, "trx-hot-1", "entry-hot-1", HOT_BOOKED_AT, HOT_BOOKED_AT, "hot");
          insertReadModel(
              hotLedgerEntryId, accountId, "trx-hot-1", "BOOKED", HOT_BOOKED_AT, "COUNTERPARTY");
          long archiveLedgerEntryId =
              insertLedgerEntry(
                  accountId,
                  "trx-archive-1",
                  "entry-archive-1",
                  ARCHIVE_BOOKED_AT,
                  ARCHIVE_BOOKED_AT,
                  "archive");
          insertArchiveReadModel(
              archiveLedgerEntryId, accountId, "trx-archive-1", ARCHIVE_BOOKED_AT);
        });
    replicaProbe.reset();
  }

  @Test
  void listQueryUsesReplicaDataSource() {
    TransactionQuery query =
        new TransactionQuery(
            accountId,
            HOT_BOOKED_AT.minusSeconds(60),
            HOT_BOOKED_AT.plusSeconds(60),
            20,
            null,
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = readRepository.fetch(query);

    assertThat(slice.items()).hasSize(1);
    assertThat(slice.items().getFirst().transactionReference()).isEqualTo("trx-hot-1");
    assertThat(replicaProbe.replicaConnections()).isGreaterThan(0);
    assertThat(replicaProbe.primaryConnections()).isZero();
  }

  @Test
  void detailQueryUsesReplicaDataSource() {
    var detail = detailRepository.find(new TransactionDetailQuery(accountId, "trx-hot-1"));

    assertThat(detail).isPresent();
    assertThat(detail.orElseThrow().entryReference()).isEqualTo("entry-hot-1");
    assertThat(replicaProbe.replicaConnections()).isGreaterThan(0);
    assertThat(replicaProbe.primaryConnections()).isZero();
  }

  @Test
  void archiveQueryUsesReplicaDataSource() {
    TransactionQuery query =
        new TransactionQuery(
            accountId,
            ARCHIVE_BOOKED_AT.minusSeconds(60),
            ARCHIVE_BOOKED_AT.plusSeconds(60),
            20,
            null,
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = archiveRepository.fetchArchived(query);

    assertThat(slice.items()).hasSize(1);
    assertThat(slice.items().getFirst().transactionReference()).isEqualTo("trx-archive-1");
    assertThat(replicaProbe.replicaConnections()).isGreaterThan(0);
    assertThat(replicaProbe.primaryConnections()).isZero();
  }

  private long insertAccount(String displayName) {
    Long id =
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
                :createdAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("displayName", displayName)
                .addValue("createdAt", Timestamp.from(HOT_BOOKED_AT)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("account insert did not return id");
    }
    return id;
  }

  private long insertLedgerEntry(
      long accountId,
      String transactionReference,
      String entryReference,
      Instant bookedAt,
      Instant occurredAt,
      String description) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO ledger_entry (
                account_id,
                transaction_reference,
                entry_reference,
                direction,
                entry_status,
                amount_minor,
                currency_code,
                booked_at,
                occurred_at,
                description,
                metadata,
                created_at,
                updated_at
            )
            VALUES (
                :accountId,
                :transactionReference,
                :entryReference,
                'DEBIT',
                'BOOKED',
                1500,
                'KRW',
                :bookedAt,
                :occurredAt,
                :description,
                '{}'::jsonb,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("transactionReference", transactionReference)
                .addValue("entryReference", entryReference)
                .addValue("bookedAt", Timestamp.from(bookedAt))
                .addValue("occurredAt", Timestamp.from(occurredAt))
                .addValue("description", description),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("ledger entry insert did not return id");
    }
    return id;
  }

  private void insertReadModel(
      long ledgerEntryId,
      long accountId,
      String transactionReference,
      String status,
      Instant bookedAt,
      String counterparty) {
    jdbcTemplate.update(
        """
        INSERT INTO transaction_read_model (
            ledger_entry_id,
            account_id,
            transaction_reference,
            direction,
            transaction_status,
            amount_minor,
            balance_after_minor,
            currency_code,
            summary,
            counterparty_masked_name,
            booked_at,
            created_at
        )
        VALUES (
            :ledgerEntryId,
            :accountId,
            :transactionReference,
            'DEBIT',
            :status,
            1500,
            8500,
            'KRW',
            :transactionReference,
            :counterparty,
            :bookedAt,
            :bookedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("transactionReference", transactionReference)
            .addValue("status", status)
            .addValue("counterparty", counterparty)
            .addValue("bookedAt", Timestamp.from(bookedAt)));
  }

  private void insertArchiveReadModel(
      long ledgerEntryId, long accountId, String transactionReference, Instant bookedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO transaction_read_model_archive (
            id,
            ledger_entry_id,
            account_id,
            transaction_reference,
            direction,
            transaction_status,
            amount_minor,
            balance_after_minor,
            currency_code,
            summary,
            counterparty_masked_name,
            booked_at,
            created_at,
            archived_at
        )
        VALUES (
            :id,
            :ledgerEntryId,
            :accountId,
            :transactionReference,
            'DEBIT',
            'BOOKED',
            1500,
            8500,
            'KRW',
            :transactionReference,
            'ARCHIVE',
            :bookedAt,
            :bookedAt,
            :archivedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("id", ledgerEntryId)
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("transactionReference", transactionReference)
            .addValue("bookedAt", Timestamp.from(bookedAt))
            .addValue("archivedAt", Timestamp.from(bookedAt.plusSeconds(30))));
  }

  static final class TransactionReadReplicaProbe {
    private final AtomicInteger primaryConnections = new AtomicInteger();
    private final AtomicInteger replicaConnections = new AtomicInteger();

    void markPrimary() {
      primaryConnections.incrementAndGet();
    }

    void markReplica() {
      replicaConnections.incrementAndGet();
    }

    void reset() {
      primaryConnections.set(0);
      replicaConnections.set(0);
    }

    int primaryConnections() {
      return primaryConnections.get();
    }

    int replicaConnections() {
      return replicaConnections.get();
    }
  }

  static final class TrackingDataSource implements DataSource {
    private final DataSource delegate;
    private final Runnable onBorrow;

    TrackingDataSource(DataSource delegate, Runnable onBorrow) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.onBorrow = Objects.requireNonNull(onBorrow, "onBorrow");
    }

    @Override
    public Connection getConnection() throws SQLException {
      onBorrow.run();
      return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      onBorrow.run();
      return delegate.getConnection(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
      return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
      delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
      delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
      return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
      try {
        return delegate.getParentLogger();
      } catch (java.sql.SQLFeatureNotSupportedException ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  @TestConfiguration
  static class ReplicaProbeConfiguration {

    @Bean
    TransactionReadReplicaProbe transactionReadReplicaProbe() {
      return new TransactionReadReplicaProbe();
    }

    @Bean
    static BeanPostProcessor transactionReadReplicaProbePostProcessor(
        TransactionReadReplicaProbe probe) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (!(bean instanceof DataSource dataSource)) {
            return bean;
          }
          if ("dataSource".equals(beanName)) {
            return new TrackingDataSource(dataSource, probe::markPrimary);
          }
          if ("transactionReadReplicaDataSource".equals(beanName)) {
            return new TrackingDataSource(dataSource, probe::markReplica);
          }
          return bean;
        }
      };
    }
  }
}
