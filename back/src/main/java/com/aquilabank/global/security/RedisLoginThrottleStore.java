package com.aquilabank.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis는 login throttling counter 용도로만 쓰고, IP/global reserve는 한 번에 원자 처리합니다. */
public final class RedisLoginThrottleStore implements LoginThrottleStore {

  private static final String IP_KEY_SUFFIX = "ip:";
  private static final String GLOBAL_KEY_SUFFIX = "global";
  private static final String TRACKED_IPS_KEY_SUFFIX = "tracked-ips";
  private static final DefaultRedisScript<List> RESERVE_SCRIPT = reserveScript();

  private final LoginThrottlingProperties properties;
  private final StringRedisTemplate stringRedisTemplate;
  private final Clock clock;

  public RedisLoginThrottleStore(
      LoginThrottlingProperties properties, StringRedisTemplate stringRedisTemplate) {
    this(properties, stringRedisTemplate, Clock.systemUTC());
  }

  public RedisLoginThrottleStore(
      LoginThrottlingProperties properties, StringRedisTemplate stringRedisTemplate, Clock clock) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public ThrottleDecision check(String ipAddress) {
    String normalizedIpAddress = normalizeIpAddress(ipAddress);
    String ipHash = hashIpAddress(normalizedIpAddress);
    String ipKey = key(IP_KEY_SUFFIX + ipHash);
    String globalKey = key(GLOBAL_KEY_SUFFIX);
    String trackedIpsKey = key(TRACKED_IPS_KEY_SUFFIX);

    LoginThrottlingProperties.ScopeProperties ip = properties.ip();
    LoginThrottlingProperties.ScopeProperties global = properties.global();
    long nowMillis = Instant.now(clock).toEpochMilli();

    Reservation reservation = reserve(ipKey, globalKey, nowMillis, ip, global);
    if (reservation.throttled()) {
      LoginThrottlingProperties.ScopeProperties blockedScope =
          reservation.scope() == LoginThrottleScope.IP ? ip : global;
      return ThrottleDecision.blocked(
          reservation.scope(),
          reservation.retryAfterSeconds(),
          blockedScope.maxAttempts(),
          blockedScope.windowSeconds(),
          trackedIpCount(trackedIpsKey, nowMillis));
    }

    trackIp(trackedIpsKey, ipHash, ip.windowSeconds(), nowMillis);
    return ThrottleDecision.allowed(
        ip.maxAttempts(), ip.windowSeconds(), trackedIpCount(trackedIpsKey, nowMillis));
  }

  @Override
  public void clear() {
    // 테스트 편의용 정리 경로다. 운영 핫패스에서는 사용하지 않는다.
    String prefix = key("");
    Set<String> keys = stringRedisTemplate.keys(prefix + "*");
    if (keys != null && !keys.isEmpty()) {
      stringRedisTemplate.delete(keys);
    }
  }

  private Reservation reserve(
      String ipKey,
      String globalKey,
      long nowMillis,
      LoginThrottlingProperties.ScopeProperties ip,
      LoginThrottlingProperties.ScopeProperties global) {
    List<?> result =
        stringRedisTemplate.execute(
            RESERVE_SCRIPT,
            List.of(ipKey, globalKey),
            Long.toString(nowMillis),
            Long.toString(Duration.ofSeconds(ip.windowSeconds()).toMillis()),
            Long.toString(Duration.ofSeconds(global.windowSeconds()).toMillis()),
            Integer.toString(ip.maxAttempts()),
            Integer.toString(global.maxAttempts()),
            reservationMember(nowMillis),
            reservationMember(nowMillis));
    if (result == null || result.size() < 2) {
      throw new IllegalStateException("failed to reserve redis login throttle slot");
    }

    long status = toLong(result.get(0));
    long retryAfterMillis = toLong(result.get(1));
    if (status == 0L) {
      return Reservation.allowed();
    }
    if (status == 1L) {
      return Reservation.blocked(LoginThrottleScope.IP, toRetryAfterSeconds(retryAfterMillis));
    }
    if (status == 2L) {
      return Reservation.blocked(LoginThrottleScope.GLOBAL, toRetryAfterSeconds(retryAfterMillis));
    }
    throw new IllegalStateException("unexpected redis login throttle status: " + status);
  }

  private void trackIp(String trackedIpsKey, String ipHash, long windowSeconds, long nowMillis) {
    pruneTrackedIps(trackedIpsKey, nowMillis);
    if (stringRedisTemplate.opsForZSet().score(trackedIpsKey, ipHash) == null) {
      evictIfNeeded(trackedIpsKey);
    }
    long expiresAtMillis = nowMillis + Duration.ofSeconds(windowSeconds).toMillis();
    stringRedisTemplate.opsForZSet().add(trackedIpsKey, ipHash, expiresAtMillis);
    stringRedisTemplate.expire(trackedIpsKey, Duration.ofSeconds(windowSeconds));
  }

  private void evictIfNeeded(String trackedIpsKey) {
    Long trackedIpCount = stringRedisTemplate.opsForZSet().zCard(trackedIpsKey);
    long trackedCount = trackedIpCount == null ? 0L : trackedIpCount;
    if (trackedCount < properties.maxTrackedIps()) {
      return;
    }
    long overflow = trackedCount - properties.maxTrackedIps() + 1L;
    Set<String> oldestIps = stringRedisTemplate.opsForZSet().range(trackedIpsKey, 0, overflow - 1L);
    if (oldestIps == null || oldestIps.isEmpty()) {
      return;
    }
    for (String ipHash : oldestIps) {
      stringRedisTemplate.delete(key(IP_KEY_SUFFIX + ipHash));
    }
    stringRedisTemplate.opsForZSet().remove(trackedIpsKey, oldestIps.toArray());
  }

  private int trackedIpCount(String trackedIpsKey, long nowMillis) {
    pruneTrackedIps(trackedIpsKey, nowMillis);
    Long trackedCount = stringRedisTemplate.opsForZSet().zCard(trackedIpsKey);
    refreshTrackedIpsTtl(trackedIpsKey, trackedCount);
    return trackedCount == null ? 0 : trackedCount.intValue();
  }

  private void pruneTrackedIps(String trackedIpsKey, long nowMillis) {
    stringRedisTemplate.opsForZSet().removeRangeByScore(trackedIpsKey, 0.0d, (double) nowMillis);
  }

  private void refreshTrackedIpsTtl(String trackedIpsKey, Long trackedCount) {
    if (trackedCount == null || trackedCount <= 0L) {
      stringRedisTemplate.delete(trackedIpsKey);
      return;
    }
    stringRedisTemplate.expire(trackedIpsKey, Duration.ofSeconds(properties.ip().windowSeconds()));
  }

  private String reservationMember(long nowMillis) {
    return nowMillis + ":" + UUID.randomUUID();
  }

  private long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new IllegalStateException("unexpected redis login throttle script result: " + value);
  }

  private long toRetryAfterSeconds(long retryAfterMillis) {
    return Math.max(1L, (Math.max(retryAfterMillis, 0L) + 999L) / 1000L);
  }

  private String key(String suffix) {
    String prefix = properties.redis().keyPrefix();
    if (prefix.endsWith(":")) {
      return prefix + suffix;
    }
    if (suffix.isEmpty()) {
      return prefix;
    }
    return prefix + ":" + suffix;
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

  private static DefaultRedisScript<List> reserveScript() {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setResultType(List.class);
    script.setScriptText(
        """
        local ipKey = KEYS[1]
        local globalKey = KEYS[2]
        local nowMillis = tonumber(ARGV[1])
        local ipWindowMillis = tonumber(ARGV[2])
        local globalWindowMillis = tonumber(ARGV[3])
        local ipLimit = tonumber(ARGV[4])
        local globalLimit = tonumber(ARGV[5])
        local ipMember = ARGV[6]
        local globalMember = ARGV[7]

        redis.call('ZREMRANGEBYSCORE', ipKey, '-inf', nowMillis - ipWindowMillis)
        redis.call('ZREMRANGEBYSCORE', globalKey, '-inf', nowMillis - globalWindowMillis)

        local ipCount = redis.call('ZCARD', ipKey)
        if ipCount >= ipLimit then
          local oldest = redis.call('ZRANGE', ipKey, 0, 0, 'WITHSCORES')
          local retryAfterMillis = ipWindowMillis
          if oldest[2] ~= nil then
            retryAfterMillis = math.max(1, tonumber(oldest[2]) + ipWindowMillis - nowMillis)
          end
          return {1, retryAfterMillis}
        end

        local globalCount = redis.call('ZCARD', globalKey)
        if globalCount >= globalLimit then
          local oldest = redis.call('ZRANGE', globalKey, 0, 0, 'WITHSCORES')
          local retryAfterMillis = globalWindowMillis
          if oldest[2] ~= nil then
            retryAfterMillis = math.max(1, tonumber(oldest[2]) + globalWindowMillis - nowMillis)
          end
          return {2, retryAfterMillis}
        end

        redis.call('ZADD', ipKey, nowMillis, ipMember)
        redis.call('PEXPIRE', ipKey, ipWindowMillis)
        redis.call('ZADD', globalKey, nowMillis, globalMember)
        redis.call('PEXPIRE', globalKey, globalWindowMillis)
        return {0, 0}
        """);
    return script;
  }

  private record Reservation(boolean throttled, LoginThrottleScope scope, long retryAfterSeconds) {

    private static Reservation allowed() {
      return new Reservation(false, null, 0L);
    }

    private static Reservation blocked(LoginThrottleScope scope, long retryAfterSeconds) {
      return new Reservation(true, scope, retryAfterSeconds);
    }
  }
}
