package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.aquilabank.domain.notification.usecase.NotificationOpsQueryUseCase;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.global.persistence.transaction.JdbcTransactionReadRepository;
import com.aquilabank.support.PostgresKafkaContainerTestSupport;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "notification.sse.max-total-sessions=1",
      "notification.sse.max-user-sessions=1",
      "notification.inbox.consumer.enabled=true",
      "notification.inbox.consumer.auto-startup=false",
      "notification.inbox.consumer.group-id=aquila-bank-prometheus-metrics",
      "notification.inbox.consumer.ops.enabled=true"
    })
class PrometheusMetricsIntegrationTest extends PostgresKafkaContainerTestSupport {

  private static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration SUMMARY_INTERVAL = Duration.ofMillis(200);

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private NotificationSseBroker notificationSseBroker;

  @Autowired private NotificationOpsQueryUseCase notificationOpsQueryUseCase;

  @Autowired private JdbcTransactionReadRepository jdbcTransactionReadRepository;

  @Autowired
  @Qualifier("notificationInboxDlqKafkaTemplate") private KafkaTemplate<String, String> kafkaTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void exportsOutboxNotificationTransactionAndSseMetrics() throws Exception {
    Instant base = Instant.now();
    commit(
        transactionManager,
        () ->
            insertOutboxEvent(
                "evt-prometheus-failed",
                "FAILED",
                1,
                "kafka publish timed out",
                base.minusSeconds(10),
                base.minusSeconds(20)));

    SseEmitter emitter = notificationSseBroker.subscribeAccount(1L, "metrics");
    try {
      assertThatThrownBy(() -> notificationSseBroker.subscribeAccount(2L, "metrics-overload"))
          .isInstanceOf(NotificationSseOverloadException.class)
          .hasMessage("notification SSE stream is temporarily overloaded");

      jdbcTransactionReadRepository.fetch(
          new TransactionQuery(
              1L,
              Instant.parse("2026-04-01T00:00:00Z"),
              Instant.parse("2026-04-30T00:00:00Z"),
              50,
              null,
              null,
              null,
              null,
              null,
              null));

      String lagEventKey = "transfer-booked:PROM-LAG-" + System.currentTimeMillis();
      String dlqEventKey = "transfer-booked:PROM-DLQ-" + System.currentTimeMillis();
      kafkaTemplate
          .send(TRANSFER_BOOKED_TOPIC, lagEventKey, "{\"transactionReference\":\"lag\"}")
          .get();
      kafkaTemplate.send(dlqRecord(dlqEventKey)).get();

      awaitCondition(
          "notification metrics summary",
          SUMMARY_TIMEOUT,
          SUMMARY_INTERVAL,
          () ->
              notificationOpsQueryUseCase.getSummary().lagCount() >= 1
                  && notificationOpsQueryUseCase.getSummary().dlqCount() >= 1);

      String body =
          mockMvc
              .perform(get("/actuator/prometheus"))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(body).contains("# HELP aquila_outbox_dispatch_lag_seconds");
      assertThat(body).containsPattern("aquila_outbox_failed_count\\s+1\\.0");
      assertThat(body).containsPattern("aquila_outbox_failed_producer_timeout_count\\s+1\\.0");
      assertThat(body)
          .containsPattern(
              "aquila_notification_consumer_lag_count\\{[^\\n]*topic=\""
                  + Pattern.quote(TRANSFER_BOOKED_TOPIC + "," + TRANSFER_REVERSED_TOPIC)
                  + "\"[^\\n]*\\}\\s+[1-9][0-9]*\\.0");
      assertThat(body)
          .containsPattern(
              "aquila_notification_consumer_dlq_count\\{[^\\n]*topic=\""
                  + Pattern.quote(TRANSFER_BOOKED_DLQ_TOPIC)
                  + "\"[^\\n]*\\}\\s+[1-9][0-9]*\\.0");
      assertThat(body)
          .containsPattern(
              "aquila_notification_sse_sessions\\{[^\\n]*principal_type=\"account\"[^\\n]*\\}\\s+1\\.0");
      assertThat(body)
          .containsPattern(
              "aquila_notification_sse_sessions\\{[^\\n]*principal_type=\"total\"[^\\n]*\\}\\s+1\\.0");
      assertThat(body)
          .containsPattern(
              "aquila_notification_sse_subscription_rejected_count\\{[^\\n]*reason=\"session_limit\"[^\\n]*\\}\\s+1\\.0");
      assertThat(body)
          .containsPattern(
              "aquila_notification_sse_session_dropped_count\\{[^\\n]*reason=\"pending_overflow\"[^\\n]*\\}\\s+0\\.0");
      assertThat(body)
          .containsPattern(
              "aquila_transaction_query_latency_seconds_count\\{[^\\n]*outcome=\"success\"[^\\n]*query_shape=\"first_page\"[^\\n]*\\}\\s+1");
      assertThat(body)
          .containsPattern(
              "aquila_transaction_query_latency_seconds_bucket\\{(?=[^\\n]*le=\"0\\.12\")(?=[^\\n]*outcome=\"success\")(?=[^\\n]*query_shape=\"first_page\")[^\\n]*\\}\\s+1");
      assertThat(body)
          .containsPattern(
              "aquila_transaction_query_latency_seconds_bucket\\{(?=[^\\n]*le=\"0\\.75\")(?=[^\\n]*outcome=\"success\")(?=[^\\n]*query_shape=\"first_page\")[^\\n]*\\}\\s+1");
    } finally {
      emitter.complete();
    }
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

  private ProducerRecord<String, String> dlqRecord(String eventKey) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(TRANSFER_BOOKED_DLQ_TOPIC, eventKey, "{\"broken\":true}");
    record
        .headers()
        .add(
            KafkaHeaders.DLT_ORIGINAL_TOPIC,
            TRANSFER_BOOKED_TOPIC.getBytes(StandardCharsets.UTF_8));
    record
        .headers()
        .add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(4).putInt(0).array());
    record
        .headers()
        .add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(8).putLong(7L).array());
    record
        .headers()
        .add(
            KafkaHeaders.DLT_EXCEPTION_FQCN,
            "java.lang.IllegalArgumentException".getBytes(StandardCharsets.UTF_8));
    record
        .headers()
        .add(
            KafkaHeaders.DLT_EXCEPTION_MESSAGE,
            "TransferBooked payload is invalid".getBytes(StandardCharsets.UTF_8));
    return record;
  }
}
