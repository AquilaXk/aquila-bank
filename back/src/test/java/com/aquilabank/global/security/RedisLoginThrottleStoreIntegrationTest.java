package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisLoginThrottleStoreIntegrationTest {

  private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

  private final LoginThrottlingProperties properties =
      new LoginThrottlingProperties(
          16,
          new LoginThrottlingProperties.ScopeProperties(2, 2),
          new LoginThrottlingProperties.ScopeProperties(4, 2),
          LoginThrottlingProperties.StoreType.REDIS,
          new LoginThrottlingProperties.RedisProperties("auth:login:throttle:"));

  private StringRedisTemplate stringRedisTemplate;

  @BeforeAll
  void setUpTemplate() {
    stringRedisTemplate = createTemplate();
  }

  @AfterEach
  void clear() {
    new RedisLoginThrottleStore(properties, stringRedisTemplate).clear();
  }

  @Test
  void sharesSameIpCounterAcrossFreshStoreInstances() {
    RedisLoginThrottleStore firstStore =
        new RedisLoginThrottleStore(properties, stringRedisTemplate);
    RedisLoginThrottleStore secondStore =
        new RedisLoginThrottleStore(properties, stringRedisTemplate);

    assertThat(firstStore.check("203.0.113.10").throttled()).isFalse();
    assertThat(firstStore.check("203.0.113.10").throttled()).isFalse();

    LoginThrottleStore.ThrottleDecision blocked = secondStore.check("203.0.113.10");

    assertThat(blocked.throttled()).isTrue();
    assertThat(blocked.scope()).isEqualTo(LoginThrottleScope.IP);
    assertThat(blocked.retryAfterSeconds()).isBetween(1L, 2L);
    assertThat(blocked.maxAttempts()).isEqualTo(2);
    assertThat(blocked.windowSeconds()).isEqualTo(2);
    assertThat(blocked.trackedIpCount()).isEqualTo(1);
  }

  @Test
  void sharesGlobalCounterAcrossDifferentIps() {
    RedisLoginThrottleStore firstStore =
        new RedisLoginThrottleStore(properties, stringRedisTemplate);
    RedisLoginThrottleStore secondStore =
        new RedisLoginThrottleStore(properties, stringRedisTemplate);

    assertThat(firstStore.check("203.0.113.11").throttled()).isFalse();
    assertThat(secondStore.check("203.0.113.12").throttled()).isFalse();
    assertThat(firstStore.check("203.0.113.13").throttled()).isFalse();
    assertThat(secondStore.check("203.0.113.14").throttled()).isFalse();

    LoginThrottleStore.ThrottleDecision blocked = firstStore.check("203.0.113.15");

    assertThat(blocked.throttled()).isTrue();
    assertThat(blocked.scope()).isEqualTo(LoginThrottleScope.GLOBAL);
    assertThat(blocked.retryAfterSeconds()).isBetween(1L, 2L);
    assertThat(blocked.maxAttempts()).isEqualTo(4);
    assertThat(blocked.windowSeconds()).isEqualTo(2);
    assertThat(blocked.trackedIpCount()).isEqualTo(4);
  }

  @Test
  void keepsSlidingWindowSemanticsAcrossBoundary() {
    MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));
    LoginThrottlingProperties slidingWindowProperties =
        new LoginThrottlingProperties(
            16,
            new LoginThrottlingProperties.ScopeProperties(2, 60),
            new LoginThrottlingProperties.ScopeProperties(10, 60),
            LoginThrottlingProperties.StoreType.REDIS,
            new LoginThrottlingProperties.RedisProperties("auth:login:sliding:"));
    RedisLoginThrottleStore store =
        new RedisLoginThrottleStore(slidingWindowProperties, stringRedisTemplate, clock);

    assertThat(store.check("203.0.113.20").throttled()).isFalse();

    clock.plusSeconds(59);
    assertThat(store.check("203.0.113.20").throttled()).isFalse();

    clock.plusSeconds(1);
    assertThat(store.check("203.0.113.20").throttled()).isFalse();

    clock.plusSeconds(1);
    LoginThrottleStore.ThrottleDecision blocked = store.check("203.0.113.20");

    assertThat(blocked.throttled()).isTrue();
    assertThat(blocked.scope()).isEqualTo(LoginThrottleScope.IP);
  }

  @Test
  void enforcesIpCapUnderConcurrentRequests() throws Exception {
    LoginThrottlingProperties concurrentProperties =
        new LoginThrottlingProperties(
            16,
            new LoginThrottlingProperties.ScopeProperties(2, 60),
            new LoginThrottlingProperties.ScopeProperties(10, 60),
            LoginThrottlingProperties.StoreType.REDIS,
            new LoginThrottlingProperties.RedisProperties("auth:login:concurrent-ip:"));

    List<LoginThrottleStore.ThrottleDecision> decisions =
        runConcurrently(
            4,
            index ->
                new RedisLoginThrottleStore(concurrentProperties, stringRedisTemplate)
                    .check("203.0.113.30"));

    assertThat(decisions.stream().filter(decision -> !decision.throttled()).count()).isEqualTo(2);
    assertThat(
            decisions.stream()
                .filter(LoginThrottleStore.ThrottleDecision::throttled)
                .allMatch(decision -> decision.scope() == LoginThrottleScope.IP))
        .isTrue();
  }

  @Test
  void enforcesGlobalCapUnderConcurrentRequestsFromDifferentIps() throws Exception {
    LoginThrottlingProperties concurrentProperties =
        new LoginThrottlingProperties(
            16,
            new LoginThrottlingProperties.ScopeProperties(10, 60),
            new LoginThrottlingProperties.ScopeProperties(4, 60),
            LoginThrottlingProperties.StoreType.REDIS,
            new LoginThrottlingProperties.RedisProperties("auth:login:concurrent-global:"));

    List<LoginThrottleStore.ThrottleDecision> decisions =
        runConcurrently(
            6,
            index ->
                new RedisLoginThrottleStore(concurrentProperties, stringRedisTemplate)
                    .check("203.0.113." + (40 + index)));

    assertThat(decisions.stream().filter(decision -> !decision.throttled()).count()).isEqualTo(4);
    assertThat(
            decisions.stream()
                .filter(LoginThrottleStore.ThrottleDecision::throttled)
                .allMatch(decision -> decision.scope() == LoginThrottleScope.GLOBAL))
        .isTrue();
  }

  private StringRedisTemplate createTemplate() {
    LettuceConnectionFactory connectionFactory =
        new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
    template.afterPropertiesSet();
    return template;
  }

  private List<LoginThrottleStore.ThrottleDecision> runConcurrently(
      int concurrency, ThrowingDecisionSupplier decisionSupplier) throws Exception {
    try (ExecutorService executorService = Executors.newFixedThreadPool(concurrency)) {
      java.util.concurrent.CountDownLatch readyLatch =
          new java.util.concurrent.CountDownLatch(concurrency);
      java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
      List<Future<LoginThrottleStore.ThrottleDecision>> futures = new ArrayList<>();
      for (int index = 0; index < concurrency; index++) {
        int requestIndex = index;
        futures.add(
            executorService.submit(
                () -> {
                  readyLatch.countDown();
                  startLatch.await();
                  return decisionSupplier.get(requestIndex);
                }));
      }
      readyLatch.await();
      startLatch.countDown();

      List<LoginThrottleStore.ThrottleDecision> decisions = new ArrayList<>();
      for (Future<LoginThrottleStore.ThrottleDecision> future : futures) {
        decisions.add(future.get());
      }
      return decisions;
    }
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void plusSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }
  }

  @FunctionalInterface
  private interface ThrowingDecisionSupplier {

    LoginThrottleStore.ThrottleDecision get(int index) throws Exception;
  }
}
