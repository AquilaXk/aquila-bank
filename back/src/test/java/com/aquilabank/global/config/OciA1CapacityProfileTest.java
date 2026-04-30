package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;

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
              .isEqualTo(6);
          assertThat(
                  environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class))
              .isEqualTo(1);
          assertThat(
                  environment.getProperty(
                      "transaction.read-replica.maximum-pool-size", Integer.class))
              .isEqualTo(4);
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
                            Map.of(
                                "OCI_A1_DB_POOL_MAX_SIZE",
                                "7",
                                "OCI_A1_DB_POOL_MIN_IDLE",
                                "2",
                                "OCI_A1_TRANSACTION_READ_REPLICA_POOL_MAX_SIZE",
                                "5",
                                "OCI_A1_SERVER_THREADS_MAX",
                                "28",
                                "OCI_A1_SERVER_THREADS_MIN_SPARE",
                                "3",
                                "OCI_A1_SERVER_ACCEPT_COUNT",
                                "48",
                                "OCI_A1_TASK_EXECUTION_CORE_SIZE",
                                "3",
                                "OCI_A1_TASK_EXECUTION_MAX_SIZE",
                                "7",
                                "OCI_A1_TASK_EXECUTION_QUEUE_CAPACITY",
                                "160"))))
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
                      environment.getProperty(
                          "transaction.read-replica.maximum-pool-size", Integer.class))
                  .isEqualTo(5);
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
