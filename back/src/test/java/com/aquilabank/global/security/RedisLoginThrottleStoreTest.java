package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisLoginThrottleStoreTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneId.of("UTC"));

  @Test
  @SuppressWarnings("unchecked")
  void executesSingleAtomicLuaScriptWithScopedKeysAndMonotonicMember() {
    LoginThrottlingProperties properties =
        new LoginThrottlingProperties(
            64,
            new LoginThrottlingProperties.ScopeProperties(5, 60),
            new LoginThrottlingProperties.ScopeProperties(20, 10),
            LoginThrottlingProperties.StoreType.REDIS,
            true,
            new LoginThrottlingProperties.RedisProperties("auth:login:throttle"));
    StringRedisTemplate template = mock(StringRedisTemplate.class);
    when(template.execute(
            any(RedisScript.class),
            anyList(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(List.of(0L, 0L, 1L), List.of(1L, 1200L, 1L), List.of(2L, 5000L, 2L));

    RedisLoginThrottleStore store = new RedisLoginThrottleStore(properties, template, FIXED_CLOCK);

    LoginThrottleStore.ThrottleDecision allowed = store.check("2001:db8::1");
    assertThat(allowed.throttled()).isFalse();
    assertThat(allowed.trackedIpCount()).isEqualTo(1);

    LoginThrottleStore.ThrottleDecision ipBlocked = store.check("refresh:203.0.113.10");
    assertThat(ipBlocked.throttled()).isTrue();
    assertThat(ipBlocked.scope()).isEqualTo(LoginThrottleScope.IP);
    assertThat(ipBlocked.retryAfterSeconds()).isEqualTo(2L);

    LoginThrottleStore.ThrottleDecision globalBlocked = store.check(" ");
    assertThat(globalBlocked.throttled()).isTrue();
    assertThat(globalBlocked.scope()).isEqualTo(LoginThrottleScope.GLOBAL);
    assertThat(globalBlocked.retryAfterSeconds()).isEqualTo(5L);

    ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
    verify(template, times(3))
        .execute(
            any(RedisScript.class),
            keysCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            memberCaptor.capture(),
            any(),
            any());
    verifyNoMoreInteractions(template);

    assertThat(memberCaptor.getAllValues().get(0)).contains(":");
    assertThat(memberCaptor.getAllValues().get(1)).isNotEqualTo(memberCaptor.getAllValues().get(0));
    assertThat(memberCaptor.getAllValues().get(0).chars().filter(ch -> ch == '-').count()).isZero();

    List<String> ipv6Keys = keysCaptor.getAllValues().get(0);
    assertThat(ipv6Keys).hasSize(3);
    assertThat(ipv6Keys.get(0)).startsWith("auth:login:throttle:{login}:ip:");
    assertThat(ipv6Keys.get(1)).isEqualTo("auth:login:throttle:{login}:global");
    assertThat(ipv6Keys.get(2)).isEqualTo("auth:login:throttle:{login}:tracked-ips");

    List<String> refreshKeys = keysCaptor.getAllValues().get(1);
    assertThat(refreshKeys.get(0)).startsWith("auth:login:throttle:{refresh}:ip:");
    assertThat(refreshKeys.get(1)).isEqualTo("auth:login:throttle:{refresh}:global");
    assertThat(refreshKeys.get(2)).isEqualTo("auth:login:throttle:{refresh}:tracked-ips");
  }

  @Test
  @SuppressWarnings("unchecked")
  void failsClosedOnInvalidLuaScriptOutputs() {
    LoginThrottlingProperties properties =
        new LoginThrottlingProperties(
            64,
            new LoginThrottlingProperties.ScopeProperties(5, 60),
            new LoginThrottlingProperties.ScopeProperties(20, 10),
            LoginThrottlingProperties.StoreType.REDIS,
            true,
            new LoginThrottlingProperties.RedisProperties("auth:login:throttle:"));
    StringRedisTemplate template = mock(StringRedisTemplate.class);
    RedisLoginThrottleStore store = new RedisLoginThrottleStore(properties, template, FIXED_CLOCK);

    when(template.execute(
            any(RedisScript.class),
            anyList(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(null)
        .thenReturn(List.of(99L, 0L, 0L))
        .thenReturn(List.of("bad", 0L, 0L));

    assertThatThrownBy(() -> store.check(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reserve");
    assertThatThrownBy(() -> store.check("login:203.0.113.1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("status");
    assertThatThrownBy(() -> store.check("password_recovery:203.0.113.1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("script result");

    when(template.keys("auth:login:throttle:*")).thenReturn(Set.of("k1"), Set.of(), null);
    store.clear();
    store.clear();
    store.clear();
    verify(template).delete(Set.of("k1"));
  }
}
