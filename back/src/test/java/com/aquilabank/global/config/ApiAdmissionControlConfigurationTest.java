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
          ApiAdmissionControlProperties.EndpointLimit endpoint = transactionReadEndpoint(context);

          assertThat(endpoint.maxConcurrency()).isEqualTo(6);
          assertThat(endpoint.retryAfterSeconds()).isEqualTo(1);
          assertThat(endpoint.adaptive().minConcurrency()).isEqualTo(6);
          assertThat(endpoint.adaptive().maxConcurrency()).isEqualTo(12);
          assertThat(endpoint.adaptive().increaseEverySuccesses()).isEqualTo(64);
          assertThat(endpoint.adaptive().decreaseOnRejections()).isEqualTo(1);
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
              ApiAdmissionControlProperties.EndpointLimit endpoint =
                  transactionReadEndpoint(context);

              assertThat(endpoint.maxConcurrency()).isEqualTo(16);
              assertThat(endpoint.adaptive().minConcurrency()).isEqualTo(16);
              assertThat(endpoint.adaptive().maxConcurrency()).isEqualTo(16);
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
              ApiAdmissionControlProperties.EndpointLimit endpoint =
                  transactionReadEndpoint(context);

              assertThat(endpoint.maxConcurrency()).isEqualTo(3);
              assertThat(endpoint.adaptive().minConcurrency()).isEqualTo(3);
              assertThat(endpoint.adaptive().maxConcurrency()).isEqualTo(3);
            });
  }

  private ApiAdmissionControlProperties.EndpointLimit transactionReadEndpoint(
      org.springframework.context.ApplicationContext context) {
    return context.getBean(ApiAdmissionControlProperties.class).endpoints().stream()
        .filter(endpoint -> endpoint.group().equals("transaction-read"))
        .findFirst()
        .orElseThrow();
  }
}
