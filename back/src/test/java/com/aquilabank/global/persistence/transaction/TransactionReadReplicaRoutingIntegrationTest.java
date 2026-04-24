package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.port.TransactionArchiveReadPort;
import com.aquilabank.domain.transaction.port.TransactionDetailReadPort;
import com.aquilabank.domain.transaction.port.TransactionReadPort;
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

  private Instant hotBookedAt;
  private Instant coldBookedAt;
  private Instant archiveBookedAt;

  @Autowired private TransactionReadPort readPort;

  @Autowired private TransactionDetailReadPort detailReadPort;

  @Autowired private TransactionArchiveReadPort archiveReadPort;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private TransactionReadReplicaProbe replicaProbe;

  private long accountId;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
    hotBookedAt = Instant.now().minusSeconds(5);
    coldBookedAt = hotBookedAt.minusSeconds(120);
    archiveBookedAt = hotBookedAt.minusSeconds(3600);
    commit(
        transactionManager,
        () -> {
          accountId = insertAccount("replica account");
          long hotLedgerEntryId =
              insertLedgerEntry(
                  accountId, "trx-hot-1", "entry-hot-1", hotBookedAt, hotBookedAt, "hot");
          insertReadModel(
              hotLedgerEntryId, accountId, "trx-hot-1", "BOOKED", hotBookedAt, "COUNTERPARTY");
          long coldLedgerEntryId =
              insertLedgerEntry(
                  accountId, "trx-cold-1", "entry-cold-1", coldBookedAt, coldBookedAt, "cold");
          insertReadModel(
              coldLedgerEntryId, accountId, "trx-cold-1", "BOOKED", coldBookedAt, "COUNTERPARTY");
          long archiveLedgerEntryId =
              insertLedgerEntry(
                  accountId,
                  "trx-archive-1",
                  "entry-archive-1",
                  archiveBookedAt,
                  archiveBookedAt,
                  "archive");
          insertArchiveReadModel(archiveLedgerEntryId, accountId, "trx-archive-1", archiveBookedAt);
        });
    replicaProbe.reset();
  }

  @Test
  void hotFirstPageQueryUsesPrimaryDataSource() {
    TransactionQuery query =
        new TransactionQuery(
            accountId,
            coldBookedAt.minusSeconds(60),
            Instant.now().plusSeconds(30),
            20,
            null,
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = readPort.fetch(query);

    assertThat(slice.items()).hasSize(2);
    assertThat(slice.items().getFirst().transactionReference()).isEqualTo("trx-hot-1");
    assertThat(replicaProbe.primaryConnections()).isGreaterThan(0);
    assertThat(replicaProbe.replicaConnections()).isZero();
  }

  @Test
  void referenceExactQueryUsesPrimaryDataSource() {
    TransactionQuery query =
        new TransactionQuery(
            accountId,
            hotBookedAt.minusSeconds(60),
            hotBookedAt.plusSeconds(60),
            20,
            null,
            null,
            null,
            null,
            null,
            "trx-hot-1");

    TransactionSlice slice = readPort.fetch(query);

    assertThat(slice.items()).hasSize(1);
    assertThat(slice.items().getFirst().transactionReference()).isEqualTo("trx-hot-1");
    assertThat(replicaProbe.primaryConnections()).isGreaterThan(0);
    assertThat(replicaProbe.replicaConnections()).isZero();
  }

  @Test
  void detailQueryUsesPrimaryDataSource() {
    var detail = detailReadPort.find(new TransactionDetailQuery(accountId, "trx-hot-1"));

    assertThat(detail).isPresent();
    assertThat(detail.orElseThrow().entryReference()).isEqualTo("entry-hot-1");
    assertThat(replicaProbe.primaryConnections()).isGreaterThan(0);
    assertThat(replicaProbe.replicaConnections()).isZero();
  }

  @Test
  void oldFirstPageQueryUsesReplicaDataSource() {
    TransactionQuery query =
        new TransactionQuery(
            accountId,
            coldBookedAt.minusSeconds(60),
            coldBookedAt.plusSeconds(60),
            20,
            null,
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = readPort.fetch(query);

    assertThat(slice.items()).hasSize(1);
    assertThat(slice.items().getFirst().transactionReference()).isEqualTo("trx-cold-1");
    assertThat(replicaProbe.replicaConnections()).isGreaterThan(0);
    assertThat(replicaProbe.primaryConnections()).isZero();
  }

  @Test
  void cursorPageQueryUsesReplicaDataSource() {
    TransactionQuery query =
        new TransactionQuery(
            accountId,
            coldBookedAt.minusSeconds(60),
            hotBookedAt.plusSeconds(60),
            20,
            new TransactionCursor(hotBookedAt.plusSeconds(1), Long.MAX_VALUE),
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = readPort.fetch(query);

    assertThat(slice.items())
        .extracting("transactionReference")
        .contains("trx-hot-1", "trx-cold-1");
    assertThat(replicaProbe.replicaConnections()).isGreaterThan(0);
    assertThat(replicaProbe.primaryConnections()).isZero();
  }

  @Test
  void archiveQueryUsesReplicaDataSource() {
    TransactionQuery query =
        new TransactionQuery(
            accountId,
            archiveBookedAt.minusSeconds(60),
            archiveBookedAt.plusSeconds(60),
            20,
            null,
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = archiveReadPort.fetchArchived(query);

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
                .addValue("createdAt", Timestamp.from(hotBookedAt)),
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
