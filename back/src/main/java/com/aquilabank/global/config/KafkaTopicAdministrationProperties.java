package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** broker auto-create 대신 app 기준 topic 준비/검증 정책을 한 곳에서 제어합니다. */
@ConfigurationProperties(prefix = "kafka.topic")
public record KafkaTopicAdministrationProperties(
    ProvisioningProperties provisioning, StartupValidationProperties startupValidation) {

  public KafkaTopicAdministrationProperties {
    provisioning = provisioning == null ? new ProvisioningProperties(true, 1, 1, 1) : provisioning;
    startupValidation =
        startupValidation == null ? new StartupValidationProperties(true) : startupValidation;
  }

  public record ProvisioningProperties(
      boolean enabled, int partitions, int replicationFactor, int minInSyncReplicas) {

    public ProvisioningProperties {
      partitions = partitions > 0 ? partitions : 1;
      replicationFactor = replicationFactor > 0 ? replicationFactor : 1;
      minInSyncReplicas = minInSyncReplicas > 0 ? minInSyncReplicas : 1;
      if (minInSyncReplicas > replicationFactor) {
        throw new IllegalArgumentException(
            "Kafka topic provisioning min.in.sync.replicas must not exceed replication factor");
      }
    }
  }

  public record StartupValidationProperties(boolean enabled) {}
}
