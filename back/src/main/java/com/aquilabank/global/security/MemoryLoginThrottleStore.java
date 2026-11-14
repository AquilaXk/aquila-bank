package com.aquilabank.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** memory 기본 경로에서 스코프별 전역/IP 버킷 상태를 bounded하게 격리 유지합니다. */
public final class MemoryLoginThrottleStore implements LoginThrottleStore {

  private static final int MAX_TRACKED_IPS_HARD_LIMIT = 10_000;

  private final LoginThrottlingProperties properties;
  private final Clock clock;
  private final Map<String, Deque<Instant>> globalWindows = new HashMap<>();
  private final LinkedHashMap<String, AttemptWindow> ipWindows =
      new LinkedHashMap<>(16, 0.75f, true);

  public MemoryLoginThrottleStore(LoginThrottlingProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public synchronized ThrottleDecision check(String ipAddress) {
    Instant now = Instant.now(clock);
    LoginThrottleScopeKey scopeKey = LoginThrottleScopeKey.parse(ipAddress);
    String scopedIpKey = scopeKey.scope() + ":" + scopeKey.ipAddress();
    Deque<Instant> activeGlobalWindow =
        globalWindows.computeIfAbsent(scopeKey.scope(), ignored -> new ArrayDeque<>());

    pruneWindow(activeGlobalWindow, now, properties.global().windowSeconds());

    ThrottleDecision ipDecision = evaluateIpWindow(scopedIpKey, now);
    if (ipDecision.throttled()) {
      return ipDecision;
    }

    ThrottleDecision globalDecision = evaluateGlobalWindow(activeGlobalWindow, now);
    if (globalDecision.throttled()) {
      return globalDecision;
    }

    ipWindow(scopedIpKey, now).attempts().addLast(now);
    activeGlobalWindow.addLast(now);
    return ThrottleDecision.allowed(
        properties.ip().maxAttempts(), properties.ip().windowSeconds(), ipWindows.size());
  }

  @Override
  public synchronized void clear() {
    globalWindows.clear();
    ipWindows.clear();
  }

  private ThrottleDecision evaluateIpWindow(String ipAddress, Instant now) {
    AttemptWindow window = ipWindows.get(ipAddress);
    if (window == null) {
      return ThrottleDecision.allowed(
          properties.ip().maxAttempts(), properties.ip().windowSeconds(), ipWindows.size());
    }
    pruneWindow(window.attempts(), now, properties.ip().windowSeconds());
    if (window.attempts().isEmpty()) {
      ipWindows.remove(ipAddress);
      return ThrottleDecision.allowed(
          properties.ip().maxAttempts(), properties.ip().windowSeconds(), ipWindows.size());
    }
    if (window.attempts().size() >= properties.ip().maxAttempts()) {
      return ThrottleDecision.blocked(
          LoginThrottleScope.IP,
          retryAfterSeconds(window.attempts(), properties.ip().windowSeconds(), now),
          properties.ip().maxAttempts(),
          properties.ip().windowSeconds(),
          ipWindows.size());
    }
    return ThrottleDecision.allowed(
        properties.ip().maxAttempts(), properties.ip().windowSeconds(), ipWindows.size());
  }

  private ThrottleDecision evaluateGlobalWindow(Deque<Instant> activeGlobalWindow, Instant now) {
    if (activeGlobalWindow.size() >= properties.global().maxAttempts()) {
      return ThrottleDecision.blocked(
          LoginThrottleScope.GLOBAL,
          retryAfterSeconds(activeGlobalWindow, properties.global().windowSeconds(), now),
          properties.global().maxAttempts(),
          properties.global().windowSeconds(),
          ipWindows.size());
    }
    return ThrottleDecision.allowed(
        properties.global().maxAttempts(), properties.global().windowSeconds(), ipWindows.size());
  }

  private AttemptWindow ipWindow(String ipAddress, Instant now) {
    AttemptWindow window = ipWindows.get(ipAddress);
    if (window != null) {
      return window;
    }
    int maxTrackedIps = Math.min(properties.maxTrackedIps(), MAX_TRACKED_IPS_HARD_LIMIT);
    if (ipWindows.size() >= maxTrackedIps) {
      pruneIpWindows(now);
    }
    while (ipWindows.size() >= maxTrackedIps) {
      Iterator<Map.Entry<String, AttemptWindow>> iterator = ipWindows.entrySet().iterator();
      if (!iterator.hasNext()) {
        break;
      }
      iterator.next();
      iterator.remove();
    }
    AttemptWindow newWindow = new AttemptWindow(new ArrayDeque<>());
    ipWindows.put(ipAddress, newWindow);
    return newWindow;
  }

  private void pruneIpWindows(Instant now) {
    long windowSeconds = properties.ip().windowSeconds();
    ipWindows
        .entrySet()
        .removeIf(
            entry -> {
              Deque<Instant> attempts = entry.getValue().attempts();
              pruneWindow(attempts, now, windowSeconds);
              return attempts.isEmpty();
            });
  }

  private void pruneWindow(Deque<Instant> window, Instant now, long windowSeconds) {
    while (!window.isEmpty() && !window.peekFirst().plusSeconds(windowSeconds).isAfter(now)) {
      window.removeFirst();
    }
  }

  private long retryAfterSeconds(Deque<Instant> window, long windowSeconds, Instant now) {
    Instant retryAt = window.peekFirst().plusSeconds(windowSeconds);
    long millis = Math.max(Duration.between(now, retryAt).toMillis(), 0L);
    return Math.max(1L, (millis + 999L) / 1000L);
  }

  private record AttemptWindow(Deque<Instant> attempts) {}
}
