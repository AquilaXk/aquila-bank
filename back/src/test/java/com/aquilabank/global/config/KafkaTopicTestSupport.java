package com.aquilabank.global.config;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

final class KafkaTopicTestSupport {

  private static final DockerImageName KAFKA_IMAGE =
      DockerImageName.parse("apache/kafka-native:3.8.0");

  private static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

  private KafkaTopicTestSupport() {}

  static synchronized String kafkaBootstrapServers() {
    if (!KAFKA.isRunning()) {
      KAFKA.start();
    }
    return KAFKA.getBootstrapServers();
  }
}
