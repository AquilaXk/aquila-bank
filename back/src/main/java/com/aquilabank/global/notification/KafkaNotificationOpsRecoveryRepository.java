package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.exception.NotificationNotFoundException;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveAuditEntry;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveCommand;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveOutcome;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveResult;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveTarget;
import com.aquilabank.domain.notification.port.NotificationDlqRedriveAuditPort;
import com.aquilabank.domain.notification.port.NotificationOpsRecoveryPort;
import com.aquilabank.global.config.NotificationInboxConsumerProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

/** preview 좌표 기준 exact seek 만 허용해 DLQ redrive 범위를 넓히지 않습니다. */
public final class KafkaNotificationOpsRecoveryRepository implements NotificationOpsRecoveryPort {

  private static final Logger log =
      LoggerFactory.getLogger(KafkaNotificationOpsRecoveryRepository.class);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
  private static final String REDRIVE_SOURCE_TOPIC_HEADER = "notification-redrive-source-topic";
  private static final String REDRIVE_SOURCE_PARTITION_HEADER =
      "notification-redrive-source-partition";
  private static final String REDRIVE_SOURCE_OFFSET_HEADER = "notification-redrive-source-offset";

  private final NotificationInboxConsumerProperties properties;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final NotificationDlqRedriveAuditPort auditPort;

  public KafkaNotificationOpsRecoveryRepository(
      NotificationInboxConsumerProperties properties,
      KafkaTemplate<String, String> kafkaTemplate,
      NotificationDlqRedriveAuditPort auditPort) {
    this.properties = properties;
    this.kafkaTemplate = kafkaTemplate;
    this.auditPort = auditPort;
  }

  @Override
  public NotificationDlqRedriveResult redrive(NotificationDlqRedriveCommand command) {
    NotificationDlqRedriveTarget target = command.target();
    ConsumerRecord<String, String> sourceRecord;
    try {
      sourceRecord = findDlqRecord(target);
    } catch (NotificationNotFoundException ex) {
      appendAudit(
          command,
          properties.dlq().topic(),
          target.partition(),
          target.offset(),
          null,
          null,
          null,
          null,
          NotificationDlqRedriveOutcome.NOT_FOUND,
          ex.getMessage(),
          Instant.now());
      throw ex;
    } catch (RuntimeException ex) {
      appendAudit(
          command,
          properties.dlq().topic(),
          target.partition(),
          target.offset(),
          null,
          null,
          null,
          null,
          NotificationDlqRedriveOutcome.FAILED,
          ex.getMessage(),
          Instant.now());
      throw ex;
    }

    String originalTopic;
    Integer originalPartition;
    try {
      originalTopic = requiredHeaderValue(sourceRecord, KafkaHeaders.DLT_ORIGINAL_TOPIC);
      originalPartition = headerInteger(sourceRecord, KafkaHeaders.DLT_ORIGINAL_PARTITION);
    } catch (RuntimeException ex) {
      appendAudit(
          command,
          sourceRecord.topic(),
          sourceRecord.partition(),
          sourceRecord.offset(),
          sourceRecord.key(),
          null,
          null,
          null,
          NotificationDlqRedriveOutcome.FAILED,
          ex.getMessage(),
          Instant.now());
      throw ex;
    }

    ProducerRecord<String, String> redriveRecord =
        new ProducerRecord<>(
            originalTopic, originalPartition, sourceRecord.key(), sourceRecord.value());
    addRedriveSourceHeaders(redriveRecord.headers(), sourceRecord.topic(), target);

    try {
      var sendResult = kafkaTemplate.send(redriveRecord).get(5, TimeUnit.SECONDS);
      Instant redrivenAt = Instant.now();
      NotificationDlqRedriveResult result =
          new NotificationDlqRedriveResult(
              sourceRecord.topic(),
              sourceRecord.partition(),
              sourceRecord.offset(),
              sourceRecord.key(),
              originalTopic,
              sendResult.getRecordMetadata().partition(),
              sendResult.getRecordMetadata().offset(),
              redrivenAt);
      appendAudit(
          command,
          sourceRecord.topic(),
          sourceRecord.partition(),
          sourceRecord.offset(),
          sourceRecord.key(),
          originalTopic,
          sendResult.getRecordMetadata().partition(),
          sendResult.getRecordMetadata().offset(),
          NotificationDlqRedriveOutcome.SUCCESS,
          null,
          redrivenAt);
      return result;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      appendAudit(
          command,
          sourceRecord.topic(),
          sourceRecord.partition(),
          sourceRecord.offset(),
          sourceRecord.key(),
          originalTopic,
          originalPartition,
          null,
          NotificationDlqRedriveOutcome.FAILED,
          "notification DLQ redrive interrupted",
          Instant.now());
      throw new IllegalStateException("notification DLQ redrive interrupted", ex);
    } catch (Exception ex) {
      appendAudit(
          command,
          sourceRecord.topic(),
          sourceRecord.partition(),
          sourceRecord.offset(),
          sourceRecord.key(),
          originalTopic,
          originalPartition,
          null,
          NotificationDlqRedriveOutcome.FAILED,
          ex.getMessage(),
          Instant.now());
      throw new IllegalStateException("notification DLQ redrive publish failed", ex);
    }
  }

  private void appendAudit(
      NotificationDlqRedriveCommand command,
      String sourceTopic,
      int sourcePartition,
      long sourceOffset,
      String eventKey,
      String targetTopic,
      Integer targetPartition,
      Long targetOffset,
      NotificationDlqRedriveOutcome outcome,
      String errorMessage,
      Instant redrivenAt) {
    try {
      auditPort.append(
          new NotificationDlqRedriveAuditEntry(
              command.actor(),
              command.requestId(),
              sourceTopic,
              sourcePartition,
              sourceOffset,
              eventKey,
              targetTopic,
              targetPartition,
              targetOffset,
              outcome,
              errorMessage,
              redrivenAt));
    } catch (RuntimeException auditEx) {
      log.warn(
          "notification DLQ redrive audit append failed. sourceTopic={} partition={} offset={} outcome={}",
          sourceTopic,
          sourcePartition,
          sourceOffset,
          outcome,
          auditEx);
    }
  }

  private ConsumerRecord<String, String> findDlqRecord(NotificationDlqRedriveTarget target) {
    TopicPartition topicPartition =
        new TopicPartition(properties.dlq().topic(), target.partition());
    try (KafkaConsumer<String, String> consumer = redriveConsumer()) {
      consumer.assign(List.of(topicPartition));
      long beginningOffset = consumer.beginningOffsets(List.of(topicPartition)).get(topicPartition);
      long endOffset = consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
      if (target.offset() < beginningOffset || target.offset() >= endOffset) {
        throw notFound(target);
      }

      consumer.seek(topicPartition, target.offset());
      long deadline = System.nanoTime() + READ_TIMEOUT.toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(POLL_INTERVAL)) {
          if (record.offset() == target.offset()) {
            return record;
          }
          if (record.offset() > target.offset()) {
            throw notFound(target);
          }
        }
      }
      throw notFound(target);
    } catch (NotificationNotFoundException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("notification DLQ redrive source lookup failed", ex);
    }
  }

  private KafkaConsumer<String, String> redriveConsumer() {
    Properties config = new Properties();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
    config.put(
        ConsumerConfig.GROUP_ID_CONFIG, properties.groupId() + "-ops-redrive-" + UUID.randomUUID());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new KafkaConsumer<>(config);
  }

  private void addRedriveSourceHeaders(
      Headers headers, String sourceTopic, NotificationDlqRedriveTarget target) {
    headers.add(REDRIVE_SOURCE_TOPIC_HEADER, sourceTopic.getBytes(StandardCharsets.UTF_8));
    headers.add(
        REDRIVE_SOURCE_PARTITION_HEADER, ByteBuffer.allocate(4).putInt(target.partition()).array());
    headers.add(
        REDRIVE_SOURCE_OFFSET_HEADER, ByteBuffer.allocate(8).putLong(target.offset()).array());
  }

  private String requiredHeaderValue(ConsumerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      throw new IllegalArgumentException("notification DLQ record original topic is missing");
    }
    String value = new String(header.value(), StandardCharsets.UTF_8);
    if (value.isBlank()) {
      throw new IllegalArgumentException("notification DLQ record original topic is missing");
    }
    return value;
  }

  private Integer headerInteger(ConsumerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return null;
    }
    byte[] value = header.value();
    if (value.length != Integer.BYTES) {
      return null;
    }
    return ByteBuffer.wrap(value).getInt();
  }

  private NotificationNotFoundException notFound(NotificationDlqRedriveTarget target) {
    return new NotificationNotFoundException(
        "notification DLQ record is not found: partition=%d offset=%d"
            .formatted(target.partition(), target.offset()));
  }
}
