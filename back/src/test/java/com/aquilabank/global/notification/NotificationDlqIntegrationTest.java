package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

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
}
