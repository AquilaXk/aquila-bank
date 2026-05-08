package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.ops.ApiAdmissionControlProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

class ApiAdmissionControlConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(
              context -> {
                try {
                  new YamlPropertySourceLoader()
                      .load("applicationConfig", new ClassPathResource("application.yml"))
                      .forEach(context.getEnvironment().getPropertySources()::addLast);
                } catch (IOException exception) {
                  throw new IllegalStateException("application.yml load failed", exception);
                }
              })
          .withUserConfiguration(ApiAdmissionControlConfiguration.class)
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

  @Test
  void keepsDefaultOciA1AdaptiveBoundsWhenMaxIsNotOverridden() {
    contextRunner.run(
        context -> {
          ApiAdmissionControlProperties.EndpointLimit archiveEndpoint =
              transactionReadEndpoint(context, "transaction-read-archive");
          ApiAdmissionControlProperties.EndpointLimit hotEndpoint =
              transactionReadEndpoint(context, "transaction-read-hot");

          assertTransactionReadDefaults(archiveEndpoint, 12);
          assertTransactionReadDefaults(hotEndpoint, 12);
        });
  }

  @Test
  void bindsLegacyHigherMaxOverrideWithoutAdaptiveBounds() {
    contextRunner
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            "stagingEnv",
                            Map.of("OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX", "16"))))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              ApiAdmissionControlProperties.EndpointLimit archiveEndpoint =
                  transactionReadEndpoint(context, "transaction-read-archive");
              ApiAdmissionControlProperties.EndpointLimit hotEndpoint =
                  transactionReadEndpoint(context, "transaction-read-hot");

              assertLegacyMaxOverride(archiveEndpoint, 16);
              assertLegacyMaxOverride(hotEndpoint, 16);
            });
  }

  @Test
  void bindsLowerMaxOverrideWithoutAdaptiveBounds() {
    contextRunner
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            "stagingEnv",
                            Map.of("OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX", "3"))))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              ApiAdmissionControlProperties.EndpointLimit archiveEndpoint =
                  transactionReadEndpoint(context, "transaction-read-archive");
              ApiAdmissionControlProperties.EndpointLimit hotEndpoint =
                  transactionReadEndpoint(context, "transaction-read-hot");

              assertLegacyMaxOverride(archiveEndpoint, 3);
              assertLegacyMaxOverride(hotEndpoint, 3);
            });
  }

  @Test
  void bindsTransferWriteDefaultHeadroom() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          ApiAdmissionControlProperties.EndpointLimit endpoint =
              endpoint(context, "transfer-write");

          assertThat(endpoint.maxConcurrency()).isEqualTo(4);
          assertThat(endpoint.pathPrefixes()).containsExactly("/api/v1/transfers");
          assertThat(endpoint.adaptive().enabled()).isFalse();
          assertThat(endpoint.adaptive().maxConcurrency()).isEqualTo(4);
        });
  }

  private ApiAdmissionControlProperties.EndpointLimit transactionReadEndpoint(
      org.springframework.context.ApplicationContext context, String group) {
    return endpoint(context, group);
  }

  private ApiAdmissionControlProperties.EndpointLimit endpoint(
      org.springframework.context.ApplicationContext context, String group) {
    return context.getBean(ApiAdmissionControlProperties.class).endpoints().stream()
        .filter(endpoint -> endpoint.group().equals(group))
        .findFirst()
        .orElseThrow();
  }

  private void assertTransactionReadDefaults(
      ApiAdmissionControlProperties.EndpointLimit endpoint, int adaptiveMaxConcurrency) {
    assertThat(endpoint.maxConcurrency()).isEqualTo(6);
    assertThat(endpoint.retryAfterSeconds()).isEqualTo(1);
    assertThat(endpoint.adaptive().minConcurrency()).isEqualTo(6);
    assertThat(endpoint.adaptive().maxConcurrency()).isEqualTo(adaptiveMaxConcurrency);
    assertThat(endpoint.adaptive().increaseEverySuccesses()).isEqualTo(64);
    assertThat(endpoint.adaptive().decreaseOnRejections()).isEqualTo(1);
  }

  private void assertLegacyMaxOverride(
      ApiAdmissionControlProperties.EndpointLimit endpoint, int expected) {
    assertThat(endpoint.maxConcurrency()).isEqualTo(expected);
    assertThat(endpoint.adaptive().minConcurrency()).isEqualTo(expected);
    assertThat(endpoint.adaptive().maxConcurrency()).isEqualTo(expected);
  }
}
