package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LoginThrottleGuardAndMemoryStoreTest {

  @AfterEach
  void tearDownRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void separatesLoginAndPasswordRecoveryIpBucketsWhileSharingStore() {
    MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
    LoginThrottlingProperties properties =
        new LoginThrottlingProperties(
            16,
            new LoginThrottlingProperties.ScopeProperties(2, 60),
            new LoginThrottlingProperties.ScopeProperties(10, 60),
            LoginThrottlingProperties.StoreType.MEMORY,
            false,
            new LoginThrottlingProperties.RedisProperties("auth:login:throttle:"));
    MemoryLoginThrottleStore store = new MemoryLoginThrottleStore(properties, clock);
    LoginThrottleGuard guard =
        new LoginThrottleGuard(
            store,
            new LoginThrottlingMetricsRecorder(
                new SimpleMeterRegistry(), LoginThrottlingProperties.StoreType.MEMORY));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/auth/password-recovery/request");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    assertDoesNotThrow(() -> guard.checkPasswordRecovery("203.0.113.10"));
    assertDoesNotThrow(() -> guard.checkPasswordRecovery("203.0.113.10"));
    LoginThrottledException recoveryBlocked =
        assertThrows(
            LoginThrottledException.class, () -> guard.checkPasswordRecovery("203.0.113.10"));
    assertThat(recoveryBlocked.scope()).isEqualTo(LoginThrottleScope.IP);

    // Password recovery bucket exhaustion must not block login from the same IP.
    assertDoesNotThrow(() -> guard.check("203.0.113.10"));
    assertDoesNotThrow(() -> guard.check("203.0.113.10"));

    RequestContextHolder.resetRequestAttributes();
    LoginThrottledException loginBlocked =
        assertThrows(LoginThrottledException.class, () -> guard.check("203.0.113.10"));
    assertThat(loginBlocked.scope()).isEqualTo(LoginThrottleScope.IP);

    guard.clear();
    assertDoesNotThrow(() -> guard.check(null));
    assertDoesNotThrow(() -> guard.check(" "));
  }

  @Test
  void doesNotPolluteOrEvictTrackedIpsWhenGlobalWindowBlocksRequest() {
    MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
    LoginThrottlingProperties properties =
        new LoginThrottlingProperties(
            2,
            new LoginThrottlingProperties.ScopeProperties(2, 60),
            new LoginThrottlingProperties.ScopeProperties(2, 60),
            LoginThrottlingProperties.StoreType.MEMORY,
            false,
            new LoginThrottlingProperties.RedisProperties("auth:login:throttle:"));
    MemoryLoginThrottleStore store = new MemoryLoginThrottleStore(properties, clock);

    assertThat(store.check("203.0.113.1").throttled()).isFalse();
    assertThat(store.check("203.0.113.1").throttled()).isFalse();

    // Global window is now at maxAttempts (2). A new IP must be blocked by GLOBAL without
    // evicting "203.0.113.1" from ipWindows.
    LoginThrottleStore.ThrottleDecision globalBlocked = store.check("203.0.113.2");
    assertThat(globalBlocked.throttled()).isTrue();
    assertThat(globalBlocked.scope()).isEqualTo(LoginThrottleScope.GLOBAL);
    assertThat(globalBlocked.trackedIpCount()).isEqualTo(1);

    // "203.0.113.1" must still be tracked and blocked by IP scope.
    LoginThrottleStore.ThrottleDecision ipBlocked = store.check("203.0.113.1");
    assertThat(ipBlocked.throttled()).isTrue();
    assertThat(ipBlocked.scope()).isEqualTo(LoginThrottleScope.IP);
  }

  @Test
  void evictsOldestTrackedIpWhenMaxTrackedIpsExceededAndPrunesExpiredEntries() {
    MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
    LoginThrottlingProperties properties =
        new LoginThrottlingProperties(
            2,
            new LoginThrottlingProperties.ScopeProperties(2, 10),
            new LoginThrottlingProperties.ScopeProperties(10, 10),
            LoginThrottlingProperties.StoreType.MEMORY,
            false,
            new LoginThrottlingProperties.RedisProperties("auth:login:throttle:"));
    MemoryLoginThrottleStore store = new MemoryLoginThrottleStore(properties, clock);

    assertThat(store.check("203.0.113.1").throttled()).isFalse();
    assertThat(store.check("203.0.113.2").throttled()).isFalse();
    assertThat(store.check("203.0.113.3").trackedIpCount()).isEqualTo(2);

    clock.plusSeconds(11);
    assertThat(store.check("203.0.113.4").trackedIpCount()).isEqualTo(1);
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
}
