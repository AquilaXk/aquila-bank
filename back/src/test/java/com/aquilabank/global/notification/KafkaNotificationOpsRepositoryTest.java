package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import com.aquilabank.global.config.NotificationInboxConsumerProperties;
import com.aquilabank.global.config.NotificationInboxConsumerProperties.DlqProperties;
import com.aquilabank.global.config.NotificationInboxConsumerProperties.Health;
import com.aquilabank.global.config.NotificationInboxConsumerProperties.OpsProperties;
import com.aquilabank.global.config.NotificationInboxConsumerProperties.TopicProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;

class KafkaNotificationOpsRepositoryTest {

  @Test
  void reusesSingleAdminClientAcrossTtlRefreshesAndClosesOnShutdown() {
    NotificationInboxConsumerProperties properties =
        new NotificationInboxConsumerProperties(
            true,
            false,
            1,
            "localhost:9092",
            "group-1",
            "earliest",
            new TopicProperties("bank.transfer.booked"),
            new TopicProperties("bank.transfer.reversed"),
            new DlqProperties("bank.transfer.dlq"),
            new OpsProperties(true, 10, 5L, new Health(10, 0)));

    AtomicReference<Instant> currentNow =
        new AtomicReference<>(Instant.parse("2026-04-20T00:00:00Z"));
    Clock mutableClock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return currentNow.get();
          }
        };

    AdminClient adminClient = mock(AdminClient.class);
    AtomicInteger clientCreateCount = new AtomicInteger();

    DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
    Node node = new Node(0, "localhost", 9092);
    TopicPartitionInfo partitionInfo =
        new TopicPartitionInfo(0, node, List.of(node), List.of(node));
    when(describeTopicsResult.topicNameValues())
        .thenReturn(
            Map.of(
                "bank.transfer.booked",
                    KafkaFuture.completedFuture(
                        new TopicDescription(
                            "bank.transfer.booked", false, List.of(partitionInfo))),
                "bank.transfer.reversed",
                    KafkaFuture.completedFuture(
                        new TopicDescription(
                            "bank.transfer.reversed", false, List.of(partitionInfo))),
                "bank.transfer.dlq",
                    KafkaFuture.completedFuture(
                        new TopicDescription("bank.transfer.dlq", false, List.of(partitionInfo)))));
    when(adminClient.describeTopics(anyCollection())).thenReturn(describeTopicsResult);

    ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
    when(listOffsetsResult.all())
        .thenReturn(
            KafkaFuture.completedFuture(
                Map.of(
                    new TopicPartition("bank.transfer.booked", 0),
                        new ListOffsetsResultInfo(10L, 0L, java.util.Optional.empty()),
                    new TopicPartition("bank.transfer.reversed", 0),
                        new ListOffsetsResultInfo(5L, 0L, java.util.Optional.empty()),
                    new TopicPartition("bank.transfer.dlq", 0),
                        new ListOffsetsResultInfo(2L, 0L, java.util.Optional.empty()))));
    when(adminClient.listOffsets(anyMap())).thenReturn(listOffsetsResult);

    ListConsumerGroupOffsetsResult committedResult = mock(ListConsumerGroupOffsetsResult.class);
    when(committedResult.partitionsToOffsetAndMetadata())
        .thenReturn(
            KafkaFuture.completedFuture(
                Map.of(
                    new TopicPartition("bank.transfer.booked", 0), new OffsetAndMetadata(7L),
                    new TopicPartition("bank.transfer.reversed", 0), new OffsetAndMetadata(5L))));
    when(adminClient.listConsumerGroupOffsets("group-1")).thenReturn(committedResult);

    KafkaNotificationOpsRepository repository =
        new KafkaNotificationOpsRepository(
            properties,
            mutableClock,
            () -> {
              clientCreateCount.incrementAndGet();
              return adminClient;
            });

    assertThat(repository).isInstanceOf(AutoCloseable.class);

    NotificationOpsSummary first = repository.getSummary();
    assertThat(first.lagCount()).isEqualTo(3L);
    assertThat(first.dlqCount()).isEqualTo(2L);

    currentNow.set(currentNow.get().plusSeconds(3));
    NotificationOpsSummary cached = repository.getSummary();
    assertThat(cached).isEqualTo(first);
    verify(adminClient, times(1)).listOffsets(anyMap());

    currentNow.set(currentNow.get().plusSeconds(10));
    NotificationOpsSummary refreshed = repository.getSummary();
    assertThat(refreshed.lagCount()).isEqualTo(3L);
    verify(adminClient, times(2)).listOffsets(anyMap());
    assertThat(clientCreateCount.get()).isEqualTo(1);

    repository.close();
    verify(adminClient, times(1)).close(any(Duration.class));
  }

  @Test
  void handlesEmptyTopicPartitionsAndFailsClosedAfterClose() {
    NotificationInboxConsumerProperties properties =
        new NotificationInboxConsumerProperties(
            true,
            false,
            1,
            "localhost:9092",
            "group-1",
            "earliest",
            new TopicProperties("bank.transfer.booked"),
            new TopicProperties("bank.transfer.reversed"),
            new DlqProperties("bank.transfer.dlq"),
            new OpsProperties(true, 10, 5L, new Health(10, 0)));
    AdminClient adminClient = mock(AdminClient.class);
    DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
    when(describeTopicsResult.topicNameValues())
        .thenReturn(
            Map.of(
                "bank.transfer.booked",
                    KafkaFuture.completedFuture(
                        new TopicDescription("bank.transfer.booked", false, List.of())),
                "bank.transfer.reversed",
                    KafkaFuture.completedFuture(
                        new TopicDescription("bank.transfer.reversed", false, List.of())),
                "bank.transfer.dlq",
                    KafkaFuture.completedFuture(
                        new TopicDescription("bank.transfer.dlq", false, List.of()))));
    when(adminClient.describeTopics(anyCollection())).thenReturn(describeTopicsResult);

    KafkaNotificationOpsRepository repository =
        new KafkaNotificationOpsRepository(properties, Clock.systemUTC(), () -> adminClient);

    NotificationOpsSummary summary = repository.getSummary();
    assertThat(summary.lagCount()).isZero();
    assertThat(summary.dlqCount()).isZero();

    repository.close();
    repository.close();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.findDlqEvents(5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed");
  }
}
