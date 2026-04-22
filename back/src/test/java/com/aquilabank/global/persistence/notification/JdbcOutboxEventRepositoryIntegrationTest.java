package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.OutboxEvent;
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
class JdbcOutboxEventRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcOutboxEventRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void deletesOnlyPublishedEventsBeforeCutoffUsingBatchSize() {
    Instant cutoff = Instant.parse("2026-04-18T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          insertOutboxEvent(
              "evt-published-old-1",
              "PUBLISHED",
              cutoff.minusSeconds(200),
              cutoff.minusSeconds(100),
              cutoff.minusSeconds(100));
          insertOutboxEvent(
              "evt-published-old-2",
              "PUBLISHED",
              cutoff.minusSeconds(150),
              cutoff.minusSeconds(50),
              cutoff.minusSeconds(50));
          insertOutboxEvent(
              "evt-published-fresh",
              "PUBLISHED",
              cutoff.plusSeconds(10),
              cutoff.plusSeconds(10),
              cutoff.plusSeconds(10));
          insertOutboxEvent("evt-pending", "PENDING", cutoff.minusSeconds(300), null, cutoff);
          insertOutboxEvent("evt-failed", "FAILED", cutoff.minusSeconds(250), null, cutoff);
          insertOutboxEvent("evt-sending", "SENDING", cutoff.minusSeconds(240), null, cutoff);
        });

    int firstDeleted = repository.deletePublishedEvents(cutoff, 1);

    assertThat(firstDeleted).isEqualTo(1);
    assertThat(findEventKeys())
        .containsExactlyInAnyOrder(
            "evt-published-old-2",
            "evt-published-fresh",
            "evt-pending",
            "evt-failed",
            "evt-sending");

    int secondDeleted = repository.deletePublishedEvents(cutoff, 10);

    assertThat(secondDeleted).isEqualTo(1);
    assertThat(findEventKeys())
        .containsExactlyInAnyOrder(
            "evt-published-fresh", "evt-pending", "evt-failed", "evt-sending");
  }

  @Test
  void marksPoisonEventAsQuarantinedAndExcludesItFromDispatchClaim() {
    Instant now = Instant.parse("2026-04-18T01:00:00Z");
    long[] id = new long[1];
    commit(
        transactionManager,
        () ->
            id[0] =
                insertOutboxEventReturningId(
                    "evt-poison", "FAILED", now.minusSeconds(60), null, now.minusSeconds(30), 2));

    repository.markQuarantined(id[0], now, "invalid payload");

    assertThat(findStatus(id[0])).isEqualTo("QUARANTINED");
    assertThat(findRetryCount(id[0])).isEqualTo(3);
    assertThat(findLastError(id[0])).isEqualTo("invalid payload");
    List<OutboxEvent> claimed =
        repository.claimBatch(10, Duration.ofSeconds(30), now.plusSeconds(1));
    assertThat(claimed).isEmpty();
  }

  private void insertOutboxEvent(
      String eventKey,
      String publishStatus,
      Instant createdAt,
      Instant publishedAt,
      Instant updatedAt) {
    insertOutboxEventReturningId(eventKey, publishStatus, createdAt, publishedAt, updatedAt, 0);
  }

  private long insertOutboxEventReturningId(
      String eventKey,
      String publishStatus,
      Instant createdAt,
      Instant publishedAt,
      Instant updatedAt,
      int retryCount) {
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
            published_at,
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
            :createdAt,
            :publishedAt,
            :retryCount,
            NULL,
            :createdAt,
            :updatedAt
        )
        RETURNING id
        """,
            new MapSqlParameterSource()
                .addValue("eventKey", eventKey)
                .addValue("publishStatus", publishStatus)
                .addValue("createdAt", Timestamp.from(createdAt))
                .addValue("publishedAt", publishedAt == null ? null : Timestamp.from(publishedAt))
                .addValue("updatedAt", Timestamp.from(updatedAt))
                .addValue("retryCount", retryCount),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("outbox_event insert did not return id");
    }
    return id;
  }

  private List<String> findEventKeys() {
    return jdbcTemplate.queryForList(
        "SELECT event_key FROM outbox_event ORDER BY id ASC",
        new MapSqlParameterSource(),
        String.class);
  }

  private String findStatus(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT publish_status FROM outbox_event WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id),
        String.class);
  }

  private int findRetryCount(long id) {
    Integer retryCount =
        jdbcTemplate.queryForObject(
            "SELECT retry_count FROM outbox_event WHERE id = :id",
            new MapSqlParameterSource().addValue("id", id),
            Integer.class);
    return retryCount == null ? -1 : retryCount;
  }

  private String findLastError(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT last_error FROM outbox_event WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id),
        String.class);
  }
}
