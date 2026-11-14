package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationDlqEvent;
import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import com.aquilabank.domain.notification.port.NotificationOpsReadPort;
import com.aquilabank.global.config.NotificationInboxConsumerProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
import java.util.function.Supplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.KafkaHeaders;

/** Kafka admin 조회를 on-demand로만 수행해 consumer lag 와 DLQ preview 를 운영 surface에 올립니다. */
public final class KafkaNotificationOpsRepository
    implements NotificationOpsReadPort, AutoCloseable {

  private static final int PAYLOAD_PREVIEW_LIMIT = 160;
  private static final Duration TOPIC_METADATA_CACHE_TTL = Duration.ofSeconds(30);

  private final NotificationInboxConsumerProperties properties;
  private final Clock clock;
  private final Supplier<AdminClient> adminClientFactory;
  private final Object adminClientLock = new Object();

  private volatile AdminClient sharedAdminClient;
  private volatile boolean closed;
  private volatile CachedSummary cachedSummary;
  private volatile CachedTopicPartitions cachedTopicPartitions;

  public KafkaNotificationOpsRepository(NotificationInboxConsumerProperties properties) {
    this(properties, Clock.systemUTC(), () -> createAdminClient(properties));
  }

  KafkaNotificationOpsRepository(
      NotificationInboxConsumerProperties properties,
      Clock clock,
      Supplier<AdminClient> adminClientFactory) {
    this.properties = properties;
    this.clock = clock;
    this.adminClientFactory = adminClientFactory;
  }

  @Override
  public NotificationOpsSummary getSummary() {
    Instant now = clock.instant();
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
    Instant now = clock.instant();
    AdminClient adminClient = adminClient();
    List<TopicPartition> partitions = resolveTopicPartitions(adminClient, now).dlqPartitions();
    Map<TopicPartition, Long> latestOffsets = latestOffsets(adminClient, partitions);
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

  @Override
  public void close() {
    AdminClient clientToClose;
    synchronized (adminClientLock) {
      closed = true;
      clientToClose = sharedAdminClient;
      sharedAdminClient = null;
    }
    if (clientToClose != null) {
      clientToClose.close(Duration.ofSeconds(2));
    }
  }

  private NotificationOpsSummary loadSummary(Instant observedAt) {
    AdminClient adminClient = adminClient();
    TopicPartitionSnapshot topicSnapshot = resolveTopicPartitions(adminClient, observedAt);
    List<TopicPartition> mainPartitions = topicSnapshot.mainPartitions();
    List<TopicPartition> dlqPartitions = topicSnapshot.dlqPartitions();
    List<TopicPartition> allPartitions =
        new ArrayList<>(mainPartitions.size() + dlqPartitions.size());
    allPartitions.addAll(mainPartitions);
    allPartitions.addAll(dlqPartitions);

    Map<TopicPartition, Long> allLatestOffsets = latestOffsets(adminClient, allPartitions);
    Map<TopicPartition, Long> committedOffsets =
        committedOffsets(adminClient, properties.groupId(), mainPartitions);
    long lagCount = sumLag(mainPartitions, allLatestOffsets, committedOffsets);
    long dlqCount = totalCount(dlqPartitions, allLatestOffsets);
    return new NotificationOpsSummary(
        observedAt,
        properties.groupId(),
        properties.mainTopicLabel(),
        properties.dlq().topic(),
        lagCount,
        dlqCount);
  }

  private TopicPartitionSnapshot resolveTopicPartitions(AdminClient adminClient, Instant now) {
    CachedTopicPartitions cached = cachedTopicPartitions;
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.snapshot();
    }
    List<String> mainTopics = properties.mainTopics();
    String dlqTopic = properties.dlq().topic();
    List<String> allTopics = new ArrayList<>(mainTopics.size() + 1);
    allTopics.addAll(mainTopics);
    allTopics.add(dlqTopic);
    Map<String, List<TopicPartition>> byTopic = describeTopicPartitions(adminClient, allTopics);
    List<TopicPartition> mainPartitions = new ArrayList<>();
    for (String topic : mainTopics) {
      mainPartitions.addAll(byTopic.getOrDefault(topic, List.of()));
    }
    List<TopicPartition> dlqPartitions = List.copyOf(byTopic.getOrDefault(dlqTopic, List.of()));
    TopicPartitionSnapshot snapshot =
        new TopicPartitionSnapshot(List.copyOf(mainPartitions), dlqPartitions);
    cachedTopicPartitions = new CachedTopicPartitions(snapshot, now.plus(TOPIC_METADATA_CACHE_TTL));
    return snapshot;
  }

  private Map<String, List<TopicPartition>> describeTopicPartitions(
      AdminClient adminClient, List<String> topics) {
    try {
      Map<String, KafkaFuture<TopicDescription>> futures =
          adminClient.describeTopics(topics).topicNameValues();
      Map<String, List<TopicPartition>> result = new HashMap<>(topics.size());
      for (String topic : topics) {
        TopicDescription description = futures.get(topic).get();
        List<TopicPartition> partitions =
            description.partitions().stream()
                .map(partition -> new TopicPartition(topic, partition.partition()))
                .toList();
        result.put(topic, partitions);
      }
      return result;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("notification ops topic describe interrupted", ex);
    } catch (ExecutionException ex) {
      throw new IllegalStateException("notification ops topic describe failed", rootCause(ex));
    }
  }

  private Map<TopicPartition, Long> latestOffsets(
      AdminClient adminClient, List<TopicPartition> partitions) {
    if (partitions.isEmpty()) {
      return Map.of();
    }
    Map<TopicPartition, OffsetSpec> request = new HashMap<>(partitions.size());
    for (TopicPartition partition : partitions) {
      request.put(partition, OffsetSpec.latest());
    }
    try {
      Map<TopicPartition, ListOffsetsResultInfo> rows =
          adminClient.listOffsets(request).all().get();
      Map<TopicPartition, Long> items = new HashMap<>(rows.size());
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
    if (partitions.isEmpty()) {
      return Map.of();
    }
    try {
      Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets =
          adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
      Map<TopicPartition, Long> committed = new HashMap<>(partitions.size());
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
        Map<TopicPartition, Long> committed = new HashMap<>(partitions.size());
        for (TopicPartition partition : partitions) {
          committed.put(partition, 0L);
        }
        return committed;
      }
      throw new IllegalStateException("notification consumer group offset lookup failed", cause);
    }
  }

  private long sumLag(
      List<TopicPartition> partitions,
      Map<TopicPartition, Long> latestOffsets,
      Map<TopicPartition, Long> committedOffsets) {
    long lagCount = 0L;
    for (TopicPartition partition : partitions) {
      long latestOffset = latestOffsets.getOrDefault(partition, 0L);
      long committedOffset = committedOffsets.getOrDefault(partition, 0L);
      lagCount += Math.max(0L, latestOffset - committedOffset);
    }
    return lagCount;
  }

  private long totalCount(
      List<TopicPartition> partitions, Map<TopicPartition, Long> latestOffsets) {
    long count = 0L;
    for (TopicPartition partition : partitions) {
      count += Math.max(0L, latestOffsets.getOrDefault(partition, 0L));
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
    AdminClient current = sharedAdminClient;
    if (current != null && !closed) {
      return current;
    }
    synchronized (adminClientLock) {
      if (closed) {
        throw new IllegalStateException("notification ops admin client is closed");
      }
      if (sharedAdminClient == null) {
        sharedAdminClient = adminClientFactory.get();
      }
      return sharedAdminClient;
    }
  }

  private static AdminClient createAdminClient(NotificationInboxConsumerProperties properties) {
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

  private record TopicPartitionSnapshot(
      List<TopicPartition> mainPartitions, List<TopicPartition> dlqPartitions) {}

  private record CachedTopicPartitions(TopicPartitionSnapshot snapshot, Instant expiresAt) {}
}
