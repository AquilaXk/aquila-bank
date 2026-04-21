package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcCommandIdempotencyOpsRepositoryIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-21T00:00:00Z");

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcCommandIdempotencyOpsRepository repository;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void summarizesAndListsStaleStartedRows() {
    commit(
        transactionManager,
        () -> {
          insertIdempotency("started-stale-001", "STARTED", BASE.minusSeconds(60));
          insertIdempotency("started-fresh-001", "STARTED", BASE.plusSeconds(30));
          insertIdempotency("completed-old-001", "COMPLETED", BASE.minusSeconds(120));
          insertIdempotency("failed-old-001", "FAILED", BASE.minusSeconds(90));
          insertIdempotency("completed-fresh-001", "COMPLETED", BASE.minusSeconds(5));
        });

    CommandIdempotencyOpsSummary summary =
        repository.getSummary(Duration.ofSeconds(30), Duration.ofSeconds(20), BASE);
    List<StaleCommandIdempotencyRecord> items =
        repository.findStaleStarted(Duration.ofSeconds(30), BASE, 10);

    assertThat(summary.startedCount()).isEqualTo(2);
    assertThat(summary.staleStartedCount()).isEqualTo(1);
    assertThat(summary.completedCount()).isEqualTo(2);
    assertThat(summary.failedCount()).isEqualTo(1);
    assertThat(summary.cleanupCandidateCount()).isEqualTo(2);
    assertThat(items)
        .extracting(StaleCommandIdempotencyRecord::idempotencyKey)
        .containsExactly("started-stale-001");
  }

  @Test
  void recoversOnlyStaleStartedRows() {
    commit(
        transactionManager,
        () -> {
          insertIdempotency("started-stale-002", "STARTED", BASE.minusSeconds(60));
          insertIdempotency("started-fresh-002", "STARTED", BASE.plusSeconds(30));
          insertIdempotency("completed-old-002", "COMPLETED", BASE.minusSeconds(120));
        });

    int recovered = repository.recoverStaleStarted(Duration.ofSeconds(30), BASE, 10);

    assertThat(recovered).isEqualTo(1);
    assertThat(idempotencyState("started-stale-002").get("processing_status")).isEqualTo("FAILED");
    assertThat(idempotencyState("started-stale-002").get("response_payload")).isNull();
    assertThat(idempotencyState("started-fresh-002").get("processing_status")).isEqualTo("STARTED");
    assertThat(idempotencyState("completed-old-002").get("processing_status"))
        .isEqualTo("COMPLETED");
  }

  @Test
  void cleansCompletedAndFailedRowsByRetentionBatch() {
    commit(
        transactionManager,
        () -> {
          insertIdempotency("completed-old-003", "COMPLETED", BASE.minusSeconds(120));
          insertIdempotency("failed-old-003", "FAILED", BASE.minusSeconds(90));
          insertIdempotency("started-old-003", "STARTED", BASE.minusSeconds(120));
          insertIdempotency("completed-fresh-003", "COMPLETED", BASE.minusSeconds(5));
        });

    int first = repository.cleanupCompletedOrFailedBefore(BASE.minusSeconds(20), 1);
    int second = repository.cleanupCompletedOrFailedBefore(BASE.minusSeconds(20), 10);

    assertThat(first).isEqualTo(1);
    assertThat(second).isEqualTo(1);
    assertThat(idempotencyExists("completed-old-003")).isFalse();
    assertThat(idempotencyExists("failed-old-003")).isFalse();
    assertThat(idempotencyExists("started-old-003")).isTrue();
    assertThat(idempotencyExists("completed-fresh-003")).isTrue();
  }

  private void insertIdempotency(String key, String status, Instant lockedUntil) {
    Instant updatedAt = key.contains("fresh") ? BASE.minusSeconds(5) : lockedUntil.minusSeconds(10);
    jdbcTemplate.update(
        """
        INSERT INTO command_idempotency (
            idempotency_key,
            request_fingerprint,
            processing_status,
            response_code,
            response_payload,
            locked_until,
            created_at,
            updated_at
        )
        VALUES (
            :key,
            :fingerprint,
            :status,
            :responseCode,
            CAST(:responsePayload AS jsonb),
            :lockedUntil,
            :createdAt,
            :updatedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("key", key)
            .addValue("fingerprint", "fp-" + key)
            .addValue("status", status)
            .addValue("responseCode", "COMPLETED".equals(status) ? 200 : null)
            .addValue("responsePayload", "COMPLETED".equals(status) ? "{\"ok\":true}" : null)
            .addValue("lockedUntil", Timestamp.from(lockedUntil))
            .addValue("createdAt", Timestamp.from(updatedAt.minusSeconds(30)))
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  private Map<String, Object> idempotencyState(String key) {
    return jdbcTemplate.queryForMap(
        """
        SELECT processing_status,
               response_payload::text AS response_payload
        FROM command_idempotency
        WHERE idempotency_key = :key
        """,
        new MapSqlParameterSource().addValue("key", key));
  }

  private boolean idempotencyExists(String key) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM command_idempotency
            WHERE idempotency_key = :key
            """,
            new MapSqlParameterSource().addValue("key", key),
            Long.class);
    return count != null && count > 0;
  }
}
