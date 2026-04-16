package com.aquilabank.global.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class OutboxDispatchIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private OutboxDispatchUseCase outboxDispatchUseCase;

  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private OutboxEventPublishPort outboxEventPublishPort;

  @BeforeEach
  void setUpDatabase() {
    commit(transactionManager, () -> resetBankingTables(jdbcTemplate));
    reset(outboxEventPublishPort);
  }

  @Test
  void marksPendingEventAsPublishedWhenPublisherSucceeds() {
    long eventId =
        commitAndReturn(
            () ->
                insertOutboxEvent(
                    "transfer-booked:TRX-100", "PENDING", Instant.now().minusSeconds(10)));

    int dispatched = outboxDispatchUseCase.dispatchPendingEvents();

    assertEquals(1, dispatched);
    verify(outboxEventPublishPort).publish(argThat(event -> event.id() == eventId));

    OutboxRow row = findOutboxRow(eventId);
    assertEquals("PUBLISHED", row.publishStatus());
    assertEquals(0, row.retryCount());
    assertNotNull(row.publishedAt());
  }

  @Test
  void marksEventAsFailedAndSchedulesRetryWhenPublisherThrows() {
    long eventId =
        commitAndReturn(
            () ->
                insertOutboxEvent(
                    "transfer-booked:TRX-101", "PENDING", Instant.now().minusSeconds(10)));
    doThrow(new RuntimeException("broker down")).when(outboxEventPublishPort).publish(any());

    int dispatched = outboxDispatchUseCase.dispatchPendingEvents();

    assertEquals(1, dispatched);
    OutboxRow row = findOutboxRow(eventId);
    assertEquals("FAILED", row.publishStatus());
    assertEquals(1, row.retryCount());
    assertTrue(row.availableAt().isAfter(row.updatedAt()));
    assertEquals("broker down", row.lastError());
  }

  private long insertOutboxEvent(String eventKey, String publishStatus, Instant availableAt) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO outbox_event (
            aggregate_type,
            aggregate_id,
            event_type,
            event_key,
            payload,
            publish_status,
            available_at,
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
            :availableAt,
            :availableAt
        )
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("eventKey", eventKey)
            .addValue("publishStatus", publishStatus)
            .addValue("availableAt", java.sql.Timestamp.from(availableAt)),
        Long.class);
  }

  private long commitAndReturn(java.util.function.Supplier<Long> callback) {
    final long[] result = new long[1];
    commit(transactionManager, () -> result[0] = callback.get());
    return result[0];
  }

  private OutboxRow findOutboxRow(long id) {
    return jdbcTemplate.queryForObject(
        """
        SELECT publish_status,
               retry_count,
               available_at,
               updated_at,
               published_at,
               last_error
        FROM outbox_event
        WHERE id = :id
        """,
        new MapSqlParameterSource().addValue("id", id),
        (rs, rowNum) ->
            new OutboxRow(
                rs.getString("publish_status"),
                rs.getInt("retry_count"),
                rs.getObject("available_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getObject("published_at", OffsetDateTime.class) == null
                    ? null
                    : rs.getObject("published_at", OffsetDateTime.class).toInstant(),
                rs.getString("last_error")));
  }

  private record OutboxRow(
      String publishStatus,
      int retryCount,
      Instant availableAt,
      Instant updatedAt,
      Instant publishedAt,
      String lastError) {}
}
