package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class KafkaConsumerPartitionConcurrencyIntegrationTest {

  private static final DockerImageName KAFKA_IMAGE =
      DockerImageName.parse("apache/kafka-native:3.8.0");
  private static final int PARTITION_COUNT = 4;
  private static final int RECORD_COUNT = 48;
  private static final Duration PROCESSING_DELAY = Duration.ofMillis(30);
  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

  @Container private static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

  @Test
  void multiPartitionConsumerConcurrencyImprovesThroughputAndClearsLag() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "");

    RunResult single = runScenario("single-" + suffix, 1);
    RunResult multi = runScenario("multi-" + suffix, PARTITION_COUNT);

    assertThat(single.finalLag()).isZero();
    assertThat(multi.finalLag()).isZero();
    assertThat(multi.usedThreadCount())
        .as("all partitions should be processed by distinct listener threads")
        .isGreaterThanOrEqualTo(PARTITION_COUNT);
    assertThat(multi.throughputPerSecond()).isGreaterThan(single.throughputPerSecond() * 1.5);
  }

  private RunResult runScenario(String suffix, int concurrency) throws Exception {
    String topic = "bank.notification.partition-concurrency." + suffix;
    String groupId = "aquila-bank-partition-concurrency-" + suffix;
    createTopic(topic);

    AtomicInteger processedCount = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(RECORD_COUNT);
    Set<String> listenerThreads = ConcurrentHashMap.newKeySet();
    ConcurrentMessageListenerContainer<String, String> container =
        listenerContainer(topic, groupId, concurrency, processedCount, listenerThreads, latch);

    container.start();
    try {
      awaitCondition(
          "partition assignment",
          () ->
              container.getAssignedPartitions().size() >= Math.min(concurrency, PARTITION_COUNT)
                  && assignedConsumerCount(container) >= Math.min(concurrency, PARTITION_COUNT));

      Instant startedAt = Instant.now();
      produceRecords(topic);
      assertThat(latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
          .as("processed all records with concurrency=%s", concurrency)
          .isTrue();
      Duration duration = Duration.between(startedAt, Instant.now());
      awaitCondition("consumer lag zero", () -> lag(topic, groupId) == 0L);
      return new RunResult(
          duration, processedCount.get(), listenerThreads.size(), lag(topic, groupId));
    } finally {
      container.stop();
    }
  }

  private ConcurrentMessageListenerContainer<String, String> listenerContainer(
      String topic,
      String groupId,
      int concurrency,
      AtomicInteger processedCount,
      Set<String> listenerThreads,
      CountDownLatch latch) {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 8);

    ContainerProperties containerProperties = new ContainerProperties(topic);
    containerProperties.setGroupId(groupId);
    containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
    containerProperties.setSyncCommits(true);
    // partition/concurrency 검증은 실제 listener thread 병렬성을 보므로 artificial delay를 고정합니다.
    containerProperties.setMessageListener(
        (MessageListener<String, String>)
            record -> {
              sleep(PROCESSING_DELAY);
              listenerThreads.add(Thread.currentThread().getName());
              processedCount.incrementAndGet();
              latch.countDown();
            });

    ConcurrentMessageListenerContainer<String, String> container =
        new ConcurrentMessageListenerContainer<>(
            new DefaultKafkaConsumerFactory<>(config), containerProperties);
    container.setBeanName("partition-concurrency-" + groupId);
    container.setConcurrency(concurrency);
    return container;
  }

  private long assignedConsumerCount(ConcurrentMessageListenerContainer<String, String> container) {
    return container.getContainers().stream()
        .filter(item -> !item.getAssignedPartitions().isEmpty())
        .count();
  }

  private void createTopic(String topic) throws Exception {
    try (AdminClient adminClient = adminClient()) {
      adminClient
          .createTopics(List.of(new NewTopic(topic, PARTITION_COUNT, (short) 1)))
          .all()
          .get(10, TimeUnit.SECONDS);
    }
  }

  private void produceRecords(String topic) throws Exception {
    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class);
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
      for (int index = 0; index < RECORD_COUNT; index++) {
        producer
            .send(
                new ProducerRecord<>(
                    topic, index % PARTITION_COUNT, "event-" + index, "{\"index\":" + index + "}"))
            .get(5, TimeUnit.SECONDS);
      }
    }
  }

  private long lag(String topic, String groupId) {
    List<TopicPartition> partitions =
        IntStream.range(0, PARTITION_COUNT)
            .mapToObj(partition -> new TopicPartition(topic, partition))
            .toList();
    try (AdminClient adminClient = adminClient()) {
      Map<TopicPartition, Long> latestOffsets = latestOffsets(adminClient, partitions);
      Map<TopicPartition, OffsetAndMetadata> committedOffsets =
          adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
      return latestOffsets.entrySet().stream()
          .mapToLong(
              entry -> {
                OffsetAndMetadata committed = committedOffsets.get(entry.getKey());
                long committedOffset = committed == null ? 0L : committed.offset();
                return Math.max(0L, entry.getValue() - committedOffset);
              })
          .sum();
    } catch (Exception ex) {
      return Long.MAX_VALUE;
    }
  }

  private Map<TopicPartition, Long> latestOffsets(
      AdminClient adminClient, List<TopicPartition> partitions) throws Exception {
    Map<TopicPartition, OffsetSpec> request = new HashMap<>();
    for (TopicPartition partition : partitions) {
      request.put(partition, OffsetSpec.latest());
    }
    Map<TopicPartition, ListOffsetsResultInfo> result =
        adminClient.listOffsets(request).all().get(10, TimeUnit.SECONDS);
    Map<TopicPartition, Long> offsets = new HashMap<>();
    result.forEach((partition, item) -> offsets.put(partition, item.offset()));
    return offsets;
  }

  private AdminClient adminClient() {
    return AdminClient.create(
        Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
  }

  private void awaitCondition(String description, BooleanSupplier condition) {
    long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      sleep(POLL_INTERVAL);
    }
    throw new AssertionError(description + " was not satisfied within " + WAIT_TIMEOUT);
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("partition concurrency test interrupted", ex);
    }
  }

  private record RunResult(
      Duration duration, int processedCount, int usedThreadCount, long finalLag) {

    private double throughputPerSecond() {
      return processedCount / Math.max(duration.toMillis() / 1000.0, 0.001);
    }
  }
}
