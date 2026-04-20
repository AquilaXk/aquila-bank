package com.aquilabank.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** memory 기본 경로에서 login burst 상태를 bounded하게 유지합니다. */
public final class MemoryLoginThrottleStore implements LoginThrottleStore {

  private final LoginThrottlingProperties properties;
  private final Clock clock;
  private final Deque<Instant> globalWindow = new ArrayDeque<>();
  private final LinkedHashMap<String, AttemptWindow> ipWindows =
      new LinkedHashMap<>(16, 0.75f, true);

  public MemoryLoginThrottleStore(LoginThrottlingProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public synchronized ThrottleDecision check(String ipAddress) {
    Instant now = Instant.now(clock);

    pruneWindow(globalWindow, now, properties.global().windowSeconds());
    pruneIpWindows(now);

    ThrottleDecision ipDecision = evaluateIpWindow(ipAddress, now);
    if (ipDecision.throttled()) {
      return ipDecision;
    }

    ThrottleDecision globalDecision = evaluateGlobalWindow(now);
    if (globalDecision.throttled()) {
      return globalDecision;
    }

    ipWindow(ipAddress).attempts().addLast(now);
    globalWindow.addLast(now);
    return ThrottleDecision.allowed(
        properties.ip().maxAttempts(), properties.ip().windowSeconds(), ipWindows.size());
  }

  @Override
  public synchronized void clear() {
    globalWindow.clear();
    ipWindows.clear();
  }

  private ThrottleDecision evaluateIpWindow(String ipAddress, Instant now) {
    AttemptWindow window = ipWindow(ipAddress);
    pruneWindow(window.attempts(), now, properties.ip().windowSeconds());
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

  private ThrottleDecision evaluateGlobalWindow(Instant now) {
    if (globalWindow.size() >= properties.global().maxAttempts()) {
      return ThrottleDecision.blocked(
          LoginThrottleScope.GLOBAL,
          retryAfterSeconds(globalWindow, properties.global().windowSeconds(), now),
          properties.global().maxAttempts(),
          properties.global().windowSeconds(),
          ipWindows.size());
    }
    return ThrottleDecision.allowed(
        properties.global().maxAttempts(), properties.global().windowSeconds(), ipWindows.size());
  }

  private AttemptWindow ipWindow(String ipAddress) {
    AttemptWindow window = ipWindows.get(ipAddress);
    if (window != null) {
      return window;
    }
    while (ipWindows.size() >= properties.maxTrackedIps()) {
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
    Iterator<Map.Entry<String, AttemptWindow>> iterator = ipWindows.entrySet().iterator();
    while (iterator.hasNext()) {
      AttemptWindow window = iterator.next().getValue();
      pruneWindow(window.attempts(), now, properties.ip().windowSeconds());
      if (window.attempts().isEmpty()) {
        iterator.remove();
      }
    }
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
