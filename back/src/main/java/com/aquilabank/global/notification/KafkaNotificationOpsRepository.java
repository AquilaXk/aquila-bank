package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationDlqEvent;
import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import com.aquilabank.domain.notification.port.NotificationOpsReadPort;
import com.aquilabank.global.config.NotificationInboxConsumerProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.KafkaHeaders;

/** Kafka admin 조회를 on-demand로만 수행해 consumer lag 와 DLQ preview 를 운영 surface에 올립니다. */
public final class KafkaNotificationOpsRepository implements NotificationOpsReadPort {

  private static final int PAYLOAD_PREVIEW_LIMIT = 160;

  private final NotificationInboxConsumerProperties properties;

  private volatile CachedSummary cachedSummary;

  public KafkaNotificationOpsRepository(NotificationInboxConsumerProperties properties) {
    this.properties = properties;
  }

  @Override
  public NotificationOpsSummary getSummary() {
    Instant now = Instant.now();
    CachedSummary current = cachedSummary;
    if (current != null && current.expiresAt().isAfter(now)) {
      return current.summary();
    }
    synchronized (this) {
      CachedSummary refreshed = cachedSummary;
      if (refreshed != null && refreshed.expiresAt().isAfter(now)) {
        return refreshed.summary();
      }
      NotificationOpsSummary summary = loadSummary(now);
      cachedSummary =
          new CachedSummary(summary, now.plusSeconds(properties.ops().summaryCacheSeconds()));
      return summary;
    }
  }

  @Override
  public List<NotificationDlqEvent> findDlqEvents(int limit) {
    List<TopicPartition> partitions = topicPartitions(properties.dlq().topic());
    Map<TopicPartition, Long> latestOffsets = latestOffsets(partitions);
    try (KafkaConsumer<String, String> consumer = previewConsumer()) {
      consumer.assign(partitions);
      for (TopicPartition partition : partitions) {
        long latestOffset = latestOffsets.getOrDefault(partition, 0L);
        long startOffset = Math.max(0L, latestOffset - limit);
        consumer.seek(partition, startOffset);
      }

      List<NotificationDlqEvent> items = new ArrayList<>();
      long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
      while (System.nanoTime() < deadline) {
        boolean consumed = false;
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
          consumed = true;
          items.add(toDlqEvent(record));
        }
        if (!consumed) {
          break;
        }
      }
      return items.stream()
          .sorted(
              Comparator.comparing(NotificationDlqEvent::publishedAt)
                  .reversed()
                  .thenComparing(NotificationDlqEvent::partition)
                  .thenComparing(NotificationDlqEvent::offset, Comparator.reverseOrder()))
          .limit(limit)
          .toList();
    }
  }

  private NotificationOpsSummary loadSummary(Instant observedAt) {
    List<TopicPartition> mainPartitions = topicPartitions(properties.transferBooked().topic());
    List<TopicPartition> dlqPartitions = topicPartitions(properties.dlq().topic());
    try (AdminClient adminClient = adminClient()) {
      Map<TopicPartition, Long> latestMainOffsets = latestOffsets(adminClient, mainPartitions);
      Map<TopicPartition, Long> committedOffsets =
          committedOffsets(adminClient, properties.groupId(), mainPartitions);
      long lagCount = sumLag(latestMainOffsets, committedOffsets);
      long dlqCount = totalCount(latestOffsets(adminClient, dlqPartitions));
      return new NotificationOpsSummary(
          observedAt,
          properties.groupId(),
          properties.transferBooked().topic(),
          properties.dlq().topic(),
          lagCount,
          dlqCount);
    }
  }

  private List<TopicPartition> topicPartitions(String topic) {
    try (AdminClient adminClient = adminClient()) {
      return topicPartitions(adminClient, topic);
    }
  }

  private List<TopicPartition> topicPartitions(AdminClient adminClient, String topic) {
    try {
      return adminClient
          .describeTopics(List.of(topic))
          .topicNameValues()
          .get(topic)
          .get()
          .partitions()
          .stream()
          .map(partition -> new TopicPartition(topic, partition.partition()))
          .toList();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("notification ops topic describe interrupted", ex);
    } catch (ExecutionException ex) {
      throw new IllegalStateException("notification ops topic describe failed", rootCause(ex));
    }
  }

  private Map<TopicPartition, Long> latestOffsets(List<TopicPartition> partitions) {
    try (AdminClient adminClient = adminClient()) {
      return latestOffsets(adminClient, partitions);
    }
  }

  private Map<TopicPartition, Long> latestOffsets(
      AdminClient adminClient, List<TopicPartition> partitions) {
    Map<TopicPartition, OffsetSpec> request = new HashMap<>();
    for (TopicPartition partition : partitions) {
      request.put(partition, OffsetSpec.latest());
    }
    try {
      Map<TopicPartition, ListOffsetsResultInfo> rows =
          adminClient.listOffsets(request).all().get();
      Map<TopicPartition, Long> items = new HashMap<>();
      for (Map.Entry<TopicPartition, ListOffsetsResultInfo> row : rows.entrySet()) {
        items.put(row.getKey(), row.getValue().offset());
      }
      return items;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("notification ops offset lookup interrupted", ex);
    } catch (ExecutionException ex) {
      throw new IllegalStateException("notification ops offset lookup failed", rootCause(ex));
    }
  }

  private Map<TopicPartition, Long> committedOffsets(
      AdminClient adminClient, String groupId, List<TopicPartition> partitions) {
    try {
      Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets =
          adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
      Map<TopicPartition, Long> committed = new HashMap<>();
      for (TopicPartition partition : partitions) {
        org.apache.kafka.clients.consumer.OffsetAndMetadata metadata = offsets.get(partition);
        committed.put(partition, metadata == null ? 0L : metadata.offset());
      }
      return committed;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("notification consumer group offset lookup interrupted", ex);
    } catch (ExecutionException ex) {
      Throwable cause = rootCause(ex);
      if (cause.getClass().getSimpleName().equals("GroupIdNotFoundException")) {
        Map<TopicPartition, Long> committed = new HashMap<>();
        for (TopicPartition partition : partitions) {
          committed.put(partition, 0L);
        }
        return committed;
      }
      throw new IllegalStateException("notification consumer group offset lookup failed", cause);
    }
  }

  private long sumLag(
      Map<TopicPartition, Long> latestOffsets, Map<TopicPartition, Long> committedOffsets) {
    long lagCount = 0L;
    for (Map.Entry<TopicPartition, Long> row : latestOffsets.entrySet()) {
      long committedOffset = committedOffsets.getOrDefault(row.getKey(), 0L);
      lagCount += Math.max(0L, row.getValue() - committedOffset);
    }
    return lagCount;
  }

  private long totalCount(Map<TopicPartition, Long> latestOffsets) {
    long count = 0L;
    for (long value : latestOffsets.values()) {
      count += Math.max(0L, value);
    }
    return count;
  }

  private KafkaConsumer<String, String> previewConsumer() {
    Properties config = new Properties();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
    config.put(
        ConsumerConfig.GROUP_ID_CONFIG, properties.groupId() + "-ops-preview-" + UUID.randomUUID());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new KafkaConsumer<>(config);
  }

  private AdminClient adminClient() {
    return AdminClient.create(
        Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers()));
  }

  private NotificationDlqEvent toDlqEvent(ConsumerRecord<String, String> record) {
    return new NotificationDlqEvent(
        record.topic(),
        record.partition(),
        record.offset(),
        record.key(),
        Instant.ofEpochMilli(record.timestamp()),
        headerValue(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
        headerInteger(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
        headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
        headerValue(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
        headerValue(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
        shorten(record.value()));
  }

  private String headerValue(ConsumerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return "-";
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private Integer headerInteger(ConsumerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return null;
    }
    byte[] value = header.value();
    if (value.length == Integer.BYTES) {
      return ByteBuffer.wrap(value).getInt();
    }
    return Integer.parseInt(new String(value, StandardCharsets.UTF_8));
  }

  private Long headerLong(ConsumerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    if (header == null || header.value() == null) {
      return null;
    }
    byte[] value = header.value();
    if (value.length == Long.BYTES) {
      return ByteBuffer.wrap(value).getLong();
    }
    return Long.parseLong(new String(value, StandardCharsets.UTF_8));
  }

  private String shorten(String payload) {
    if (payload == null || payload.isBlank()) {
      return null;
    }
    return payload.length() <= PAYLOAD_PREVIEW_LIMIT
        ? payload
        : payload.substring(0, PAYLOAD_PREVIEW_LIMIT);
  }

  private Throwable rootCause(ExecutionException ex) {
    return ex.getCause() == null ? ex : ex.getCause();
  }

  private record CachedSummary(NotificationOpsSummary summary, Instant expiresAt) {}
}
