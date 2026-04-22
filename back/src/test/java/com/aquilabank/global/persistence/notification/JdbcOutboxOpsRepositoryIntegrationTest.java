package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.OutboxFailedEvent;
import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
class JdbcOutboxOpsRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcOutboxOpsRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void returnsFailedEventsInDispatchOrder() {
    Instant base = Instant.parse("2026-04-17T01:00:00Z");
    commit(
        transactionManager,
        () -> {
          insertOutboxEvent("evt-pending", "PENDING", 0, null, base.plusSeconds(5), base);
          insertOutboxEvent("evt-failed-1", "FAILED", 1, "broker down", base.plusSeconds(10), base);
          insertOutboxEvent(
              "evt-failed-2", "FAILED", 2, "timeout", base.plusSeconds(2), base.plusSeconds(1));
          insertOutboxEvent(
              "evt-failed-3",
              "FAILED",
              3,
              "serialization",
              base.plusSeconds(30),
              base.plusSeconds(2));
        });

    List<OutboxFailedEvent> items = repository.findFailedEvents(2);

    assertThat(items).hasSize(2);
    assertThat(items)
        .extracting(OutboxFailedEvent::eventKey)
        .containsExactly("evt-failed-2", "evt-failed-1");
    assertThat(items)
        .extracting(OutboxFailedEvent::lastError)
        .containsExactly("timeout", "broker down");
  }

  @Test
  void summarizesDispatchableLagFailedCountAndStaleSendingCount() {
    Instant observedAt = Instant.parse("2026-04-17T01:00:30Z");
    commit(
        transactionManager,
        () -> {
          insertOutboxEvent(
              "evt-pending-old",
              "PENDING",
              0,
              null,
              observedAt.minusSeconds(20),
              observedAt.minusSeconds(40));
          insertOutboxEvent(
              "evt-failed-now",
              "FAILED",
              2,
              "kafka publish timed out",
              observedAt.minusSeconds(10),
              observedAt.minusSeconds(20));
          insertOutboxEvent(
              "evt-failed-future",
              "FAILED",
              3,
              "retry later",
              observedAt.plusSeconds(30),
              observedAt.minusSeconds(5));
          insertOutboxEvent(
              "evt-sending-stale",
              "SENDING",
              1,
              null,
              observedAt.minusSeconds(15),
              observedAt.minusSeconds(40));
          insertOutboxEvent(
              "evt-sending-fresh",
              "SENDING",
              1,
              null,
              observedAt.minusSeconds(5),
              observedAt.minusSeconds(10));
          insertOutboxEvent(
              "evt-quarantined",
              "QUARANTINED",
              4,
              "invalid payload",
              observedAt.minusSeconds(60),
              observedAt.minusSeconds(15));
        });

    OutboxOpsSummary summary = repository.getSummary(Duration.ofSeconds(30), observedAt);

    assertThat(summary.oldestDispatchableAt()).isEqualTo(observedAt.minusSeconds(20));
    assertThat(summary.oldestDispatchLag()).isEqualTo(Duration.ofSeconds(20));
    assertThat(summary.failedCount()).isEqualTo(2L);
    assertThat(summary.quarantinedCount()).isEqualTo(1L);
    assertThat(summary.producerTimeoutFailedCount()).isEqualTo(1L);
    assertThat(summary.staleSendingCount()).isEqualTo(1L);
  }

  @Test
  void recoversOnlyStaleSendingRowsToPending() {
    Instant recoveredAt = Instant.parse("2026-04-17T01:10:00Z");
    long[] ids = new long[3];
    commit(
        transactionManager,
        () -> {
          ids[0] =
              insertOutboxEvent(
                  "evt-sending-stale-1",
                  "SENDING",
                  1,
                  null,
                  recoveredAt.minusSeconds(20),
                  recoveredAt.minusSeconds(50));
          ids[1] =
              insertOutboxEvent(
                  "evt-sending-fresh",
                  "SENDING",
                  1,
                  null,
                  recoveredAt.minusSeconds(10),
                  recoveredAt.minusSeconds(5));
          ids[2] =
              insertOutboxEvent(
                  "evt-failed",
                  "FAILED",
                  2,
                  "broker down",
                  recoveredAt.minusSeconds(30),
                  recoveredAt.minusSeconds(60));
        });

    int recoveredCount = repository.recoverStaleSending(Duration.ofSeconds(30), recoveredAt);

    assertThat(recoveredCount).isEqualTo(1);
    assertThat(findStatus(ids[0])).isEqualTo("PENDING");
    assertThat(findAvailableAt(ids[0])).isEqualTo(recoveredAt);
    assertThat(findUpdatedAt(ids[0])).isEqualTo(recoveredAt);
    assertThat(findStatus(ids[1])).isEqualTo("SENDING");
    assertThat(findStatus(ids[2])).isEqualTo("FAILED");
  }

  private long insertOutboxEvent(
      String eventKey,
      String publishStatus,
      int retryCount,
      String lastError,
      Instant availableAt,
      Instant updatedAt) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO outbox_event (
                aggregate_type,
                aggregate_id,
                event_type,
                event_key,
                payload,
                publish_status,
                available_at,
                retry_count,
                last_error,
                created_at,
                updated_at
            )
            VALUES (
                'TRANSFER',
                'TRX-' || right(:eventKey, 3),
                'TransferBooked',
                :eventKey,
                '{"transactionReference":"seed"}'::jsonb,
                :publishStatus,
                :availableAt,
                :retryCount,
                :lastError,
                :updatedAt,
                :updatedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("eventKey", eventKey)
                .addValue("publishStatus", publishStatus)
                .addValue("availableAt", Timestamp.from(availableAt))
                .addValue("retryCount", retryCount)
                .addValue("lastError", lastError)
                .addValue("updatedAt", Timestamp.from(updatedAt)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("outbox_event insert did not return id");
    }
    return id;
  }

  private String findStatus(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT publish_status FROM outbox_event WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id),
        String.class);
  }

  private Instant findAvailableAt(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT available_at FROM outbox_event WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id),
        (rs, rowNum) -> rs.getTimestamp("available_at").toInstant());
  }

  private Instant findUpdatedAt(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT updated_at FROM outbox_event WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id),
        (rs, rowNum) -> rs.getTimestamp("updated_at").toInstant());
  }
}
