package com.aquilabank.global.config;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.ops.ApiAdmissionControlProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

class OciA1CapacityProfileTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(context -> loadYaml(context.getEnvironment(), "application.yml", false))
          .withInitializer(
              context -> loadYaml(context.getEnvironment(), "application-oci-a1.yml", true));

  @Test
  void exposesExecutorServletAndDatasourceCapsForOciA1Profile() {
    contextRunner.run(
        context -> {
          Environment environment = context.getEnvironment();

          assertThat(
                  environment.getProperty(
                      "spring.datasource.hikari.maximum-pool-size", Integer.class))
              .isEqualTo(8);
          assertThat(
                  environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class))
              .isEqualTo(1);
          assertThat(environment.getProperty("spring.datasource.hikari.max-lifetime", Long.class))
              .isEqualTo(45_000L);
          assertThat(environment.getProperty("spring.datasource.hikari.keepalive-time", Long.class))
              .isEqualTo(30_000L);
          assertThat(environment.getProperty("spring.datasource.hikari.connection-init-sql"))
              .contains("idle_in_transaction_session_timeout TO '300000ms'");
          assertThat(
                  environment.getProperty(
                      "transaction.read-replica.maximum-pool-size", Integer.class))
              .isEqualTo(6);
          assertThat(
                  environment.getProperty(
                      "transaction.read.per-account.max-concurrency", Integer.class))
              .isEqualTo(3);
          assertThat(environment.getProperty("server.tomcat.threads.max", Integer.class))
              .isEqualTo(32);
          assertThat(environment.getProperty("server.tomcat.threads.min-spare", Integer.class))
              .isEqualTo(4);
          assertThat(environment.getProperty("server.tomcat.accept-count", Integer.class))
              .isEqualTo(64);
          assertThat(environment.getProperty("spring.task.execution.pool.core-size", Integer.class))
              .isEqualTo(4);
          assertThat(environment.getProperty("spring.task.execution.pool.max-size", Integer.class))
              .isEqualTo(8);
          assertThat(
                  environment.getProperty(
                      "spring.task.execution.pool.queue-capacity", Integer.class))
              .isEqualTo(200);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].group", String.class))
              .isEqualTo("transaction-read-archive");
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].max-concurrency", Integer.class))
              .isEqualTo(16);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].retry-after-seconds", Integer.class))
              .isZero();
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.min-concurrency",
                      Integer.class))
              .isEqualTo(6);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.max-concurrency",
                      Integer.class))
              .isEqualTo(16);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.increase-every-successes",
                      Integer.class))
              .isEqualTo(32);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.rejection-window-size",
                      Integer.class))
              .isEqualTo(32);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.decrease-rejection-ratio",
                      Double.class))
              .isEqualTo(0.45);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.decrease-cooldown-seconds",
                      Integer.class))
              .isEqualTo(2);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.low-saturation-increase-every-successes",
                      Integer.class))
              .isEqualTo(16);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.low-saturation-recovery-step",
                      Integer.class))
              .isEqualTo(2);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[0].adaptive.low-saturation-max-in-flight",
                      Integer.class))
              .isEqualTo(1);
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[1].group", String.class))
              .isEqualTo("transaction-read-hot");
          assertThat(
                  environment.getProperty(
                      "ops.api-admission-control.endpoints[1].max-concurrency", Integer.class))
              .isEqualTo(16);
        });
  }

  @Test
  void keepsOciA1CapacityCapsExternallyTunable() {
    contextRunner
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            "ociA1Override",
                            Map.ofEntries(
                                entry("OCI_A1_DB_POOL_MAX_SIZE", "7"),
                                entry("OCI_A1_DB_POOL_MIN_IDLE", "2"),
                                entry("OCI_A1_DB_MAX_LIFETIME_MS", "480000"),
                                entry("OCI_A1_DB_KEEPALIVE_TIME_MS", "45000"),
                                entry("OCI_A1_DB_IDLE_IN_TX_TIMEOUT_MS", "240000"),
                                entry("OCI_A1_TRANSACTION_READ_REPLICA_POOL_MAX_SIZE", "5"),
                                entry("OCI_A1_TRANSACTION_READ_PER_ACCOUNT_MAX_CONCURRENCY", "4"),
                                entry("OCI_A1_SERVER_THREADS_MAX", "28"),
                                entry("OCI_A1_SERVER_THREADS_MIN_SPARE", "3"),
                                entry("OCI_A1_SERVER_ACCEPT_COUNT", "48"),
                                entry("OCI_A1_TASK_EXECUTION_CORE_SIZE", "3"),
                                entry("OCI_A1_TASK_EXECUTION_MAX_SIZE", "7"),
                                entry("OCI_A1_TASK_EXECUTION_QUEUE_CAPACITY", "160"),
                                entry("OCI_A1_TRANSACTION_READ_ADMISSION_MAX", "7"),
                                entry("OCI_A1_TRANSACTION_READ_ADMISSION_RETRY_AFTER_SECONDS", "1"),
                                entry("OCI_A1_TRANSACTION_READ_ADMISSION_MIN", "6"),
                                entry("OCI_A1_TRANSACTION_READ_ADMISSION_ADAPTIVE_MAX", "9"),
                                entry(
                                    "OCI_A1_TRANSACTION_READ_ADMISSION_REJECTION_WINDOW_SIZE",
                                    "30"),
                                entry(
                                    "OCI_A1_TRANSACTION_READ_ADMISSION_DECREASE_REJECTION_RATIO",
                                    "0.4"),
                                entry(
                                    "OCI_A1_TRANSACTION_READ_ADMISSION_DECREASE_COOLDOWN_SECONDS",
                                    "3")))))
        .run(
            context -> {
              Environment environment = context.getEnvironment();

              assertThat(
                      environment.getProperty(
                          "spring.datasource.hikari.maximum-pool-size", Integer.class))
                  .isEqualTo(7);
              assertThat(
                      environment.getProperty(
                          "spring.datasource.hikari.minimum-idle", Integer.class))
                  .isEqualTo(2);
              assertThat(
                      environment.getProperty("spring.datasource.hikari.max-lifetime", Long.class))
                  .isEqualTo(480_000L);
              assertThat(
                      environment.getProperty(
                          "spring.datasource.hikari.keepalive-time", Long.class))
                  .isEqualTo(45_000L);
              assertThat(environment.getProperty("spring.datasource.hikari.connection-init-sql"))
                  .contains("idle_in_transaction_session_timeout TO '240000ms'");
              assertThat(
                      environment.getProperty(
                          "transaction.read-replica.maximum-pool-size", Integer.class))
                  .isEqualTo(5);
              assertThat(
                      environment.getProperty(
                          "transaction.read.per-account.max-concurrency", Integer.class))
                  .isEqualTo(4);
              assertThat(environment.getProperty("server.tomcat.threads.max", Integer.class))
                  .isEqualTo(28);
              assertThat(environment.getProperty("server.tomcat.threads.min-spare", Integer.class))
                  .isEqualTo(3);
              assertThat(environment.getProperty("server.tomcat.accept-count", Integer.class))
                  .isEqualTo(48);
              assertThat(
                      environment.getProperty(
                          "spring.task.execution.pool.core-size", Integer.class))
                  .isEqualTo(3);
              assertThat(
                      environment.getProperty("spring.task.execution.pool.max-size", Integer.class))
                  .isEqualTo(7);
              assertThat(
                      environment.getProperty(
                          "spring.task.execution.pool.queue-capacity", Integer.class))
                  .isEqualTo(160);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].group", String.class))
                  .isEqualTo("transaction-read-archive");
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].max-concurrency", Integer.class))
                  .isEqualTo(7);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].retry-after-seconds",
                          Integer.class))
                  .isEqualTo(1);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].adaptive.min-concurrency",
                          Integer.class))
                  .isEqualTo(6);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].adaptive.max-concurrency",
                          Integer.class))
                  .isEqualTo(9);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].adaptive.rejection-window-size",
                          Integer.class))
                  .isEqualTo(30);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].adaptive.decrease-rejection-ratio",
                          Double.class))
                  .isEqualTo(0.4);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[0].adaptive.decrease-cooldown-seconds",
                          Integer.class))
                  .isEqualTo(3);
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[1].group", String.class))
                  .isEqualTo("transaction-read-hot");
              assertThat(
                      environment.getProperty(
                          "ops.api-admission-control.endpoints[1].max-concurrency", Integer.class))
                  .isEqualTo(7);
            });
  }

  @Test
  void bindsLegacyTransactionReadMaxOverrideWithoutAdaptiveProfileConflict() {
    contextRunner
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            "legacyAdmissionEnv",
                            Map.of("OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX", "4"))))
        .withUserConfiguration(ApiAdmissionControlConfiguration.class)
        .withBean(io.micrometer.core.instrument.MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              ApiAdmissionControlProperties.EndpointLimit archiveEndpoint =
                  context.getBean(ApiAdmissionControlProperties.class).endpoints().stream()
                      .filter(item -> item.group().equals("transaction-read-archive"))
                      .findFirst()
                      .orElseThrow();
              ApiAdmissionControlProperties.EndpointLimit hotEndpoint =
                  context.getBean(ApiAdmissionControlProperties.class).endpoints().stream()
                      .filter(item -> item.group().equals("transaction-read-hot"))
                      .findFirst()
                      .orElseThrow();

              assertThat(archiveEndpoint.maxConcurrency()).isEqualTo(4);
              assertThat(archiveEndpoint.adaptive().minConcurrency()).isEqualTo(4);
              assertThat(archiveEndpoint.adaptive().maxConcurrency()).isEqualTo(16);
              assertThat(hotEndpoint.maxConcurrency()).isEqualTo(4);
              assertThat(hotEndpoint.adaptive().minConcurrency()).isEqualTo(4);
              assertThat(hotEndpoint.adaptive().maxConcurrency()).isEqualTo(16);
            });
  }

  private static void loadYaml(
      ConfigurableEnvironment environment, String resourceName, boolean highPrecedence) {
    try {
      new YamlPropertySourceLoader()
          .load(resourceName, new ClassPathResource(resourceName))
          .forEach(
              propertySource -> {
                if (highPrecedence) {
                  environment.getPropertySources().addFirst(propertySource);
                } else {
                  environment.getPropertySources().addLast(propertySource);
                }
              });
    } catch (IOException exception) {
      throw new IllegalStateException(resourceName + " load failed", exception);
    }
  }
}
