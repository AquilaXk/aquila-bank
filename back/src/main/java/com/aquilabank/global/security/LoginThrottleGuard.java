package com.aquilabank.global.security;

import com.aquilabank.global.web.RequestTraceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** login entrypoint 직전에서 IP/global burst를 빠르게 차단합니다. */
@Component
public class LoginThrottleGuard {

  private static final Logger log = LoggerFactory.getLogger(LoginThrottleGuard.class);

  private final LoginThrottlingProperties properties;
  private final Clock clock;
  private final Deque<Instant> globalWindow = new ArrayDeque<>();
  private final LinkedHashMap<String, AttemptWindow> ipWindows =
      new LinkedHashMap<>(16, 0.75f, true);

  public LoginThrottleGuard(LoginThrottlingProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public synchronized void check(String ipAddress) {
    Instant now = Instant.now(clock);
    String normalizedIpAddress = normalizeIpAddress(ipAddress);

    // 공개 login QPS는 낮고 tracked IP 수도 작게 제한하므로 단일 monitor로 window 정합성을 유지합니다.
    pruneWindow(globalWindow, now, properties.global().windowSeconds());
    pruneIpWindows(now);

    ThrottleDecision ipDecision = evaluateIpWindow(normalizedIpAddress, now);
    if (ipDecision.blocked()) {
      logThrottle(
          LoginThrottleScope.IP,
          normalizedIpAddress,
          ipDecision.retryAfterSeconds(),
          properties.ip().maxAttempts(),
          properties.ip().windowSeconds());
      throw new LoginThrottledException(LoginThrottleScope.IP, ipDecision.retryAfterSeconds());
    }

    ThrottleDecision globalDecision = evaluateGlobalWindow(now);
    if (globalDecision.blocked()) {
      logThrottle(
          LoginThrottleScope.GLOBAL,
          normalizedIpAddress,
          globalDecision.retryAfterSeconds(),
          properties.global().maxAttempts(),
          properties.global().windowSeconds());
      throw new LoginThrottledException(
          LoginThrottleScope.GLOBAL, globalDecision.retryAfterSeconds());
    }

    ipWindow(normalizedIpAddress).attempts().addLast(now);
    globalWindow.addLast(now);
  }

  /** 테스트마다 singleton window state를 비웁니다. */
  public synchronized void clear() {
    globalWindow.clear();
    ipWindows.clear();
  }

  private ThrottleDecision evaluateIpWindow(String ipAddress, Instant now) {
    AttemptWindow window = ipWindow(ipAddress);
    pruneWindow(window.attempts(), now, properties.ip().windowSeconds());
    if (window.attempts().size() >= properties.ip().maxAttempts()) {
      return ThrottleDecision.blocked(
          retryAfterSeconds(window.attempts(), properties.ip().windowSeconds(), now));
    }
    return ThrottleDecision.allowed();
  }

  private ThrottleDecision evaluateGlobalWindow(Instant now) {
    if (globalWindow.size() >= properties.global().maxAttempts()) {
      return ThrottleDecision.blocked(
          retryAfterSeconds(globalWindow, properties.global().windowSeconds(), now));
    }
    return ThrottleDecision.allowed();
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

  private void logThrottle(
      LoginThrottleScope scope,
      String ipAddress,
      long retryAfterSeconds,
      int maxAttempts,
      long windowSeconds) {
    log.warn(
        "auth login throttled requestId={} scope={} ipHash={} retryAfterSeconds={} maxAttempts={} windowSeconds={} trackedIpCount={} path={}",
        RequestTraceContext.currentRequestId().orElse("-"),
        scope.name(),
        hashIpAddress(ipAddress),
        retryAfterSeconds,
        maxAttempts,
        windowSeconds,
        ipWindows.size(),
        currentPath());
  }

  private String currentPath() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return "-";
    }
    return attributes.getRequest().getRequestURI();
  }

  private String hashIpAddress(String ipAddress) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(ipAddress.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private String normalizeIpAddress(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return "unknown";
    }
    return ipAddress;
  }

  private record AttemptWindow(Deque<Instant> attempts) {}

  private record ThrottleDecision(boolean blocked, long retryAfterSeconds) {

    private static ThrottleDecision allowed() {
      return new ThrottleDecision(false, 0L);
    }

    private static ThrottleDecision blocked(long retryAfterSeconds) {
      return new ThrottleDecision(true, retryAfterSeconds);
    }
  }
}
