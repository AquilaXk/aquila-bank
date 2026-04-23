package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class KafkaTopicRuntimeValidatorTest {

  private final KafkaTopicRuntimeValidator validator = new KafkaTopicRuntimeValidator();

  @Test
  void passesWhenTopicMatchesConfiguredBaseline() {
    KafkaTopicSpec topicSpec = new KafkaTopicSpec("bank.transfer.booked.v1", 1, 3, 2);
    KafkaTopicRuntimeState runtimeState =
        new KafkaTopicRuntimeState("bank.transfer.booked.v1", 3, 3, 2);

    assertThatCode(() -> validator.validateTopic(topicSpec, runtimeState))
        .doesNotThrowAnyException();
  }

  @Test
  void failsWhenReplicationFactorDiffersFromConfiguredBaseline() {
    KafkaTopicSpec topicSpec = new KafkaTopicSpec("bank.transfer.booked.v1", 1, 3, 2);
    KafkaTopicRuntimeState runtimeState =
        new KafkaTopicRuntimeState("bank.transfer.booked.v1", 3, 2, 2);

    assertThatThrownBy(() -> validator.validateTopic(topicSpec, runtimeState))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unexpected replication factor");
  }

  @Test
  void failsWhenMinInSyncReplicasDiffersFromConfiguredBaseline() {
    KafkaTopicSpec topicSpec = new KafkaTopicSpec("bank.transfer.booked.v1", 1, 3, 2);
    KafkaTopicRuntimeState runtimeState =
        new KafkaTopicRuntimeState("bank.transfer.booked.v1", 3, 3, 1);

    assertThatThrownBy(() -> validator.validateTopic(topicSpec, runtimeState))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unexpected min.insync.replicas");
  }

  @Test
  void failsWhenClusterBrokerCountIsLowerThanRequiredReplicationFactor() {
    KafkaTopicTopology topology =
        new KafkaTopicTopology(
            "localhost:9092", List.of(new KafkaTopicSpec("bank.transfer.booked.v1", 1, 3, 2)));

    assertThatThrownBy(() -> validator.validateBrokerCount(topology, 2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Kafka cluster has fewer brokers than required");
  }
}
