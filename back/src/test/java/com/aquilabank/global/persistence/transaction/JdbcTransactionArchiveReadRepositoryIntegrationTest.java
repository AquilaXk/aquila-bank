package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.aquilabank.support.TransactionExplainPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcTransactionArchiveReadRepositoryIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2025-01-31T00:00:00Z");

  @Autowired private JdbcTransactionArchiveReadRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  private long accountId;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
    commit(
        transactionManager,
        () -> {
          accountId = insertAccount("archive query account");
          for (int index = 0; index < 120; index++) {
            insertArchivedTransaction(
                accountId,
                "archive-trx-" + index,
                index % 2 == 0 ? "BOOKED" : "REVERSED",
                BASE.minusSeconds(index * 60L));
          }
          insertHotTransaction(accountId, "hot-trx-not-in-archive-response", BASE.minusSeconds(30));
        });
  }

  @Test
  void firstPageReadsArchiveTableOnlyWithArchiveAccountCursorIndex() {
    TransactionQuery query = query(null, null, null);

    TransactionSlice slice = repository.fetchArchived(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.items()).noneMatch(item -> item.transactionReference().startsWith("hot-"));
    assertThat(slice.hasNext()).isTrue();
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void cursorPageKeepsArchiveAccountCursorIndex() {
    TransactionSlice firstSlice = repository.fetchArchived(query(null, null, null));
    TransactionQuery nextPageQuery = query(firstSlice.nextCursor(), null, null);

    TransactionSlice nextSlice = repository.fetchArchived(nextPageQuery);
    TransactionExplainPlan plan = explain(nextPageQuery);

    assertThat(firstSlice.nextCursor()).isNotNull();
    assertThat(nextSlice.items()).hasSize(50);
    assertThat(nextSlice.items().getFirst().bookedAt())
        .isBeforeOrEqualTo(firstSlice.items().getLast().bookedAt());
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void statusFilterUsesArchiveStatusCursorIndex() {
    TransactionQuery query = query(null, TransactionStatus.BOOKED, null);

    TransactionSlice slice = repository.fetchArchived(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.items()).allMatch(item -> item.status() == TransactionStatus.BOOKED);
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_status_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void transactionReferenceUsesArchiveReferenceCursorIndex() {
    TransactionQuery query = query(null, null, "archive-trx-80");

    TransactionSlice slice = repository.fetchArchived(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(1);
    assertThat(slice.items().getFirst().transactionReference()).isEqualTo("archive-trx-80");
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_reference_cursor"))
        .isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  private TransactionQuery query(
      TransactionCursor cursor, TransactionStatus status, String transactionReference) {
    return new TransactionQuery(
        accountId,
        BASE.minusSeconds(30L * 24L * 60L * 60L),
        BASE.plusSeconds(60L),
        50,
        cursor,
        status,
        null,
        null,
        null,
        transactionReference);
  }

  private TransactionExplainPlan explain(TransactionQuery query) {
    TransactionArchiveReadQueryStatement statement =
        TransactionArchiveReadQueryStatement.from(query);
    String explainJson =
        jdbcTemplate.queryForObject(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)\n" + statement.sql(),
            statement.params(),
            (rs, rowNum) -> rs.getString(1));
    if (explainJson == null) {
      throw new IllegalStateException("EXPLAIN did not return JSON");
    }
    return TransactionExplainPlan.fromJson(objectMapper, explainJson);
  }

  private long insertAccount(String displayName) {
    String accountNumber =
        jdbcTemplate.queryForObject(
            "SELECT '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0')",
            new MapSqlParameterSource(),
            String.class);
    if (accountNumber == null) {
      throw new IllegalStateException("account number is not generated");
    }

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
            VALUES (:accountNumber, :displayName, 'ACTIVE', 'KRW', :createdAt, :createdAt)
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountNumber", accountNumber)
                .addValue("displayName", displayName)
                .addValue("createdAt", Timestamp.from(BASE)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("account insert did not return id");
    }
    return id;
  }

  private void insertArchivedTransaction(
      long accountId, String reference, String status, Instant bookedAt) {
    long ledgerEntryId = insertLedgerEntry(accountId, reference, bookedAt);
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
            :reference,
            'CREDIT',
            :status,
            1700,
            1001700,
            'KRW',
            :reference,
            'ATM',
            :bookedAt,
            :bookedAt,
            :archivedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("id", ledgerEntryId)
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("reference", reference)
            .addValue("status", status)
            .addValue("bookedAt", Timestamp.from(bookedAt))
            .addValue("archivedAt", Timestamp.from(BASE.plusSeconds(300))));
  }

  private void insertHotTransaction(long accountId, String reference, Instant bookedAt) {
    long ledgerEntryId = insertLedgerEntry(accountId, reference, bookedAt);
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
            :reference,
            'CREDIT',
            'BOOKED',
            1700,
            1001700,
            'KRW',
            :reference,
            'ATM',
            :bookedAt,
            :bookedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("reference", reference)
            .addValue("bookedAt", Timestamp.from(bookedAt)));
  }

  private long insertLedgerEntry(long accountId, String reference, Instant bookedAt) {
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
                :reference,
                :reference || '-entry',
                'CREDIT',
                'BOOKED',
                1700,
                'KRW',
                :bookedAt,
                :bookedAt,
                :reference,
                '{}'::jsonb,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("reference", reference)
                .addValue("bookedAt", Timestamp.from(bookedAt)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("ledger entry insert did not return id");
    }
    return id;
  }
}
