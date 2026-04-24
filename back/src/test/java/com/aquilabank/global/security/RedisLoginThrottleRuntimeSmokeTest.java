package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** compose Redis profile이 실제 login throttling counter 경로와 맞물리는지 확인합니다. */
@EnabledIfEnvironmentVariable(named = "REDIS_LOGIN_THROTTLING_RUNTIME_SMOKE", matches = "true")
class RedisLoginThrottleRuntimeSmokeTest {

  private final LoginThrottlingProperties properties =
      new LoginThrottlingProperties(
          16,
          new LoginThrottlingProperties.ScopeProperties(2, 1),
          new LoginThrottlingProperties.ScopeProperties(4, 1),
          LoginThrottlingProperties.StoreType.REDIS,
          false,
          new LoginThrottlingProperties.RedisProperties(redisKeyPrefix()));

  private final StringRedisTemplate stringRedisTemplate = createTemplate();
  private final RedisLoginThrottleStore store =
      new RedisLoginThrottleStore(properties, stringRedisTemplate);

  @AfterEach
  void clearRedisKeys() {
    store.clear();
  }

  @Test
  void runtimeRedisProfileSharesCountersAndExpiresWindow() throws Exception {
    assertThat(store.check("203.0.113.50").throttled()).isFalse();
    assertThat(store.check("203.0.113.50").throttled()).isFalse();

    LoginThrottleStore.ThrottleDecision ipBlocked = store.check("203.0.113.50");

    assertThat(ipBlocked.throttled()).isTrue();
    assertThat(ipBlocked.scope()).isEqualTo(LoginThrottleScope.IP);
    assertThat(ipBlocked.retryAfterSeconds()).isEqualTo(1L);

    Thread.sleep(1_100L);
    assertThat(store.check("203.0.113.50").throttled()).isFalse();

    store.clear();

    assertThat(store.check("203.0.113.51").throttled()).isFalse();
    assertThat(store.check("203.0.113.52").throttled()).isFalse();
    assertThat(store.check("203.0.113.53").throttled()).isFalse();
    assertThat(store.check("203.0.113.54").throttled()).isFalse();

    LoginThrottleStore.ThrottleDecision globalBlocked = store.check("203.0.113.55");

    assertThat(globalBlocked.throttled()).isTrue();
    assertThat(globalBlocked.scope()).isEqualTo(LoginThrottleScope.GLOBAL);
  }

  private static StringRedisTemplate createTemplate() {
    LettuceConnectionFactory connectionFactory =
        new LettuceConnectionFactory(redisHost(), redisPort());
    connectionFactory.afterPropertiesSet();
    StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
    template.afterPropertiesSet();
    return template;
  }

  private static String redisHost() {
    return System.getenv().getOrDefault("REDIS_HOST", "localhost");
  }

  private static int redisPort() {
    return Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
  }

  private static String redisKeyPrefix() {
    return System.getenv()
        .getOrDefault("SECURITY_LOGIN_THROTTLING_REDIS_KEY_PREFIX", "auth:login:runtime-smoke:");
  }
}
