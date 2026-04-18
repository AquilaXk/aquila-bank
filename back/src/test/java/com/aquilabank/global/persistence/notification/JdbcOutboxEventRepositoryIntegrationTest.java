package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
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

  private void insertOutboxEvent(
      String eventKey,
      String publishStatus,
      Instant createdAt,
      Instant publishedAt,
      Instant updatedAt) {
    jdbcTemplate.update(
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
            0,
            NULL,
            :createdAt,
            :updatedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("eventKey", eventKey)
            .addValue("publishStatus", publishStatus)
            .addValue("createdAt", Timestamp.from(createdAt))
            .addValue("publishedAt", publishedAt == null ? null : Timestamp.from(publishedAt))
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  private List<String> findEventKeys() {
    return jdbcTemplate.queryForList(
        "SELECT event_key FROM outbox_event ORDER BY id ASC",
        new MapSqlParameterSource(),
        String.class);
  }
}
