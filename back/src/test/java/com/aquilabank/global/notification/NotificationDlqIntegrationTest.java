package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveTarget;
import com.aquilabank.domain.notification.usecase.NotificationOpsRecoveryUseCase;
import com.aquilabank.support.PostgresKafkaContainerTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "notification.inbox.consumer.enabled=true",
      "notification.inbox.consumer.auto-startup=true",
      "notification.inbox.consumer.auto-offset-reset=latest",
      "notification.inbox.consumer.group-id=aquila-bank-notification-dlq-test",
      "notification.inbox.consumer.ops.enabled=true"
    })
class NotificationDlqIntegrationTest extends PostgresKafkaContainerTestSupport {

  private static final Duration DLQ_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  @Autowired
  @Qualifier("notificationInboxDlqKafkaTemplate") private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @Autowired private NotificationOpsRecoveryUseCase notificationOpsRecoveryUseCase;

  @BeforeEach
  void waitUntilListenerIsReady() {
    MessageListenerContainer container =
        kafkaListenerEndpointRegistry.getListenerContainer("transferBookedNotificationConsumer");
    awaitCondition(
        "notification consumer assignment",
        DLQ_TIMEOUT,
        POLL_INTERVAL,
        () -> container != null && container.isRunning() && hasAssignedPartitions(container));
  }

  @Test
  void movesPoisonPayloadToDlqWithoutWritingNotificationInbox() throws Exception {
    String eventKey = "transfer-booked:DLQ-" + System.currentTimeMillis();
    kafkaTemplate.send(TRANSFER_BOOKED_TOPIC, eventKey, "{").get(5, TimeUnit.SECONDS);

    awaitCondition(
        "notification dlq record",
        DLQ_TIMEOUT,
        POLL_INTERVAL,
        () -> findDlqRecord(eventKey) != null);

    ConsumerRecord<String, String> dlqRecord = findDlqRecord(eventKey);
    assertThat(dlqRecord).isNotNull();
    assertThat(dlqRecord.key()).isEqualTo(eventKey);
    assertThat(
            headerValue(
                dlqRecord, org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TOPIC))
        .isEqualTo(TRANSFER_BOOKED_TOPIC);
    assertThat(
            headerValue(
                dlqRecord, org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE))
        .contains("TransferBooked payload is invalid");
  }

  @Test
  void redrivesDlqRecordToMainTopicWithSameKeyAndPayload() throws Exception {
    stopListener();
    String eventKey = "transfer-booked:REDRIVE-" + System.currentTimeMillis();
    String payload = validTransferBookedPayload("dlq-redrive");

    ProducerRecord<String, String> record = validDlqRecord(eventKey, payload);
    var sendResult = kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    NotificationDlqRedriveTarget target =
        new NotificationDlqRedriveTarget(
            sendResult.getRecordMetadata().partition(), sendResult.getRecordMetadata().offset());

    var firstResult = notificationOpsRecoveryUseCase.redrive(target);
    ConsumerRecord<String, String> firstRedrivenRecord =
        findTopicRecord(TRANSFER_BOOKED_TOPIC, firstResult.targetOffset());
    assertThat(firstResult.eventKey()).isEqualTo(eventKey);
    assertThat(firstResult.targetTopic()).isEqualTo(TRANSFER_BOOKED_TOPIC);
    assertThat(firstRedrivenRecord).isNotNull();
    assertThat(firstRedrivenRecord.key()).isEqualTo(eventKey);
    assertThat(firstRedrivenRecord.value()).isEqualTo(payload);

    var secondResult = notificationOpsRecoveryUseCase.redrive(target);
    ConsumerRecord<String, String> secondRedrivenRecord =
        findTopicRecord(TRANSFER_BOOKED_TOPIC, secondResult.targetOffset());
    assertThat(secondResult.targetOffset()).isGreaterThan(firstResult.targetOffset());
    assertThat(secondRedrivenRecord).isNotNull();
    assertThat(secondRedrivenRecord.key()).isEqualTo(eventKey);
    assertThat(secondRedrivenRecord.value()).isEqualTo(payload);
  }

  private ConsumerRecord<String, String> findDlqRecord(String eventKey) {
    TopicPartition partition = new TopicPartition(TRANSFER_BOOKED_DLQ_TOPIC, 0);
    long latestOffset = latestOffset(partition);
    long startOffset = Math.max(0L, latestOffset - 10);
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "notification-dlq-preview-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "latest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            false,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.assign(List.of(partition));
      consumer.seek(partition, startOffset);
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
        if (eventKey.equals(record.key())) {
          return record;
        }
      }
      return null;
    }
  }

  private ProducerRecord<String, String> validDlqRecord(String eventKey, String payload) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(TRANSFER_BOOKED_DLQ_TOPIC, eventKey, payload);
    record
        .headers()
        .add(
            KafkaHeaders.DLT_ORIGINAL_TOPIC,
            TRANSFER_BOOKED_TOPIC.getBytes(StandardCharsets.UTF_8));
    record
        .headers()
        .add(
            KafkaHeaders.DLT_ORIGINAL_PARTITION, java.nio.ByteBuffer.allocate(4).putInt(0).array());
    return record;
  }

  private ConsumerRecord<String, String> findTopicRecord(String topic, long offset) {
    TopicPartition partition = new TopicPartition(topic, 0);
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "notification-topic-inspect-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "latest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            false,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.assign(List.of(partition));
      consumer.seek(partition, offset);
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(2))) {
        if (record.offset() == offset) {
          return record;
        }
      }
      return null;
    }
  }

  private String validTransferBookedPayload(String summary) {
    return """
        {
          "transactionReference": "TRX-DLQ-REDRIVE",
          "sourceAccountId": 11,
          "targetAccountId": 22,
          "amountMinor": 1200,
          "currencyCode": "KRW",
          "summary": "%s",
          "bookedAt": "2026-04-17T00:00:00Z"
        }
        """
        .formatted(summary);
  }

  private long latestOffset(TopicPartition partition) {
    try (AdminClient adminClient =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers()))) {
      ListOffsetsResultInfo item =
          adminClient
              .listOffsets(Map.of(partition, OffsetSpec.latest()))
              .all()
              .get()
              .get(partition);
      return item.offset();
    } catch (Exception ex) {
      throw new IllegalStateException("failed to read DLQ latest offset", ex);
    }
  }

  private String headerValue(ConsumerRecord<String, String> record, String headerName) {
    org.apache.kafka.common.header.Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return "";
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private String kafkaBootstrapServers() {
    return kafkaTemplate
        .getProducerFactory()
        .getConfigurationProperties()
        .get("bootstrap.servers")
        .toString();
  }

  private boolean hasAssignedPartitions(MessageListenerContainer container) {
    try {
      Object value = container.getClass().getMethod("getAssignedPartitions").invoke(container);
      return value instanceof Collection<?> items && !items.isEmpty();
    } catch (ReflectiveOperationException ex) {
      return container.isRunning();
    }
  }

  private void stopListener() {
    MessageListenerContainer container =
        kafkaListenerEndpointRegistry.getListenerContainer("transferBookedNotificationConsumer");
    if (container == null) {
      throw new IllegalStateException("notification consumer container is not found");
    }
    container.stop();
    awaitCondition(
        "notification consumer stop", DLQ_TIMEOUT, POLL_INTERVAL, () -> !container.isRunning());
  }
}
