package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.global.security.LoginThrottleStore;
import com.aquilabank.global.security.LoginThrottlingProperties;
import com.aquilabank.global.security.MemoryLoginThrottleStore;
import com.aquilabank.global.security.RedisLoginThrottleStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class LoginThrottlingConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(TestConfiguration.class);

  @Test
  void usesMemoryStoreByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(LoginThrottleGuard.class);
          assertThat(context).hasSingleBean(LoginThrottleStore.class);
          assertThat(context.getBean(LoginThrottleStore.class))
              .isInstanceOf(MemoryLoginThrottleStore.class);
          assertThat(context).doesNotHaveBean(RedisLoginThrottleStore.class);
        });
  }

  @Test
  void failsFastWhenRedisStoreIsSelectedWithoutStringRedisTemplate() {
    contextRunner
        .withPropertyValues("security.login-throttling.store=redis")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "security.login-throttling.store=redis requires StringRedisTemplate");
            });
  }

  @Test
  void createsRedisStoreWhenStringRedisTemplateIsPresent() {
    contextRunner
        .withPropertyValues("security.login-throttling.store=redis")
        .withBean(
            org.springframework.data.redis.core.StringRedisTemplate.class,
            () -> mock(org.springframework.data.redis.core.StringRedisTemplate.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LoginThrottleStore.class);
              assertThat(context.getBean(LoginThrottleStore.class))
                  .isInstanceOf(RedisLoginThrottleStore.class);
            });
  }

  @Test
  void failsClosedWhenRedisThrottleCounterIsNotImplementedYet() {
    RedisLoginThrottleStore loginThrottleStore =
        new RedisLoginThrottleStore(
            new LoginThrottlingProperties(
                16,
                new LoginThrottlingProperties.ScopeProperties(2, 60),
                new LoginThrottlingProperties.ScopeProperties(4, 10),
                LoginThrottlingProperties.StoreType.REDIS,
                new LoginThrottlingProperties.RedisProperties("auth:login:throttle:")),
            mock(org.springframework.data.redis.core.StringRedisTemplate.class));

    assertThatThrownBy(() -> loginThrottleStore.check("203.0.113.10"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("redis login throttling counter is not implemented yet");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(LoginThrottlingProperties.class)
  @Import(LoginThrottlingConfiguration.class)
  static class TestConfiguration {

    @Bean
    Clock authClock() {
      return Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}
