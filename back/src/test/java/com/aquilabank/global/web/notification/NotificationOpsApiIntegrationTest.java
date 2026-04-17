package com.aquilabank.global.web.notification;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.isOneOf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.notification.usecase.NotificationOpsQueryUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresKafkaContainerTestSupport;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "outbox.ops.enabled=true",
      "outbox.ops.token-header=X-Outbox-Ops-Token",
      "outbox.ops.token=test-outbox-ops-token",
      "notification.inbox.consumer.enabled=true",
      "notification.inbox.consumer.auto-startup=false",
      "notification.inbox.consumer.group-id=aquila-bank-notification-ops-api",
      "notification.inbox.consumer.ops.enabled=true",
      "notification.inbox.consumer.ops.dlq-preview-limit=5",
      "notification.inbox.consumer.ops.health.max-lag-messages=0",
      "notification.inbox.consumer.ops.health.max-dlq-count=0"
    })
class NotificationOpsApiIntegrationTest extends PostgresKafkaContainerTestSupport {

  private static final Duration OPS_QUERY_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration OPS_QUERY_INTERVAL = Duration.ofMillis(200);

  @Autowired private WebApplicationContext context;

  @Autowired
  @Qualifier("notificationInboxDlqKafkaTemplate") private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired private NotificationOpsQueryUseCase notificationOpsQueryUseCase;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void returnsNotificationLagAndDlqPreview() throws Exception {
    String lagEventKey = "transfer-booked:LAG-" + System.currentTimeMillis();
    String dlqEventKey = "transfer-booked:DLQ-" + System.currentTimeMillis();

    kafkaTemplate
        .send(TRANSFER_BOOKED_TOPIC, lagEventKey, "{\"transactionReference\":\"lag\"}")
        .get(5, TimeUnit.SECONDS);
    kafkaTemplate.send(dlqRecord(dlqEventKey)).get(5, TimeUnit.SECONDS);

    awaitCondition(
        "notification ops summary",
        OPS_QUERY_TIMEOUT,
        OPS_QUERY_INTERVAL,
        () ->
            notificationOpsQueryUseCase.getSummary().lagCount() >= 1
                && notificationOpsQueryUseCase.getSummary().dlqCount() >= 1
                && notificationOpsQueryUseCase.getDlqEvents(5).stream()
                    .anyMatch(item -> dlqEventKey.equals(item.eventKey())));

    mockMvc
        .perform(
            get("/internal/api/v1/outbox/notification/summary")
                .header("Authorization", outboxOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.consumerGroupId").isString())
        .andExpect(jsonPath("$.lagCount", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.dlqCount", greaterThanOrEqualTo(1)));

    mockMvc
        .perform(
            get("/internal/api/v1/outbox/notification/dlq-events")
                .header("Authorization", outboxOpsAuthorization())
                .param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(5))
        .andExpect(jsonPath("$.items[0].eventKey").value(dlqEventKey))
        .andExpect(jsonPath("$.items[0].originalTopic").value(TRANSFER_BOOKED_TOPIC))
        .andExpect(jsonPath("$.items[0].errorClass").value("java.lang.IllegalArgumentException"))
        .andExpect(jsonPath("$.items[0].errorMessage").value("TransferBooked payload is invalid"));
  }

  @Test
  void degradesHealthWhenNotificationLagOrDlqExists() throws Exception {
    String lagEventKey = "transfer-booked:HEALTH-" + System.currentTimeMillis();
    kafkaTemplate
        .send(TRANSFER_BOOKED_TOPIC, lagEventKey, "{\"transactionReference\":\"lag\"}")
        .get(5, TimeUnit.SECONDS);

    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(isOneOf("OUT_OF_SERVICE", "DOWN")));
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
        .add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(8).putLong(12L).array());
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

  private String outboxOpsAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue(
            "outbox-ops", java.util.Set.of(InternalServiceScope.OUTBOX_OPS));
  }
}
