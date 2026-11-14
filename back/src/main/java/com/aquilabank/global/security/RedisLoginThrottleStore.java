package com.aquilabank.global.security;

import com.aquilabank.standard.util.Sha256Hex;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis에서 스코프별 전역/IP 제한과 tracked IP 퇴거를 단일 원자 Lua 스크립트 1회 왕복으로 처리합니다. */
public final class RedisLoginThrottleStore implements LoginThrottleStore {

  private static final String IP_KEY_SUFFIX = "ip:";
  private static final String GLOBAL_KEY_SUFFIX = "global";
  private static final String TRACKED_IPS_KEY_SUFFIX = "tracked-ips";
  private static final AtomicLong MEMBER_SEQUENCE = new AtomicLong();
  private static final String PROCESS_TAG = Long.toHexString(System.nanoTime());
  private static final DefaultRedisScript<List> RESERVE_AND_TRACK_SCRIPT = reserveAndTrackScript();

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
    LoginThrottleScopeKey scopeKey = LoginThrottleScopeKey.parse(ipAddress);
    String scopedPrefix = basePrefix() + "{" + scopeKey.scope() + "}:";
    String ipKeyPrefix = scopedPrefix + IP_KEY_SUFFIX;
    String ipHash = Sha256Hex.hashHex(scopeKey.ipAddress());
    String ipKey = ipKeyPrefix + ipHash;
    String globalKey = scopedPrefix + GLOBAL_KEY_SUFFIX;
    String trackedIpsKey = scopedPrefix + TRACKED_IPS_KEY_SUFFIX;

    LoginThrottlingProperties.ScopeProperties ip = properties.ip();
    LoginThrottlingProperties.ScopeProperties global = properties.global();
    long nowMillis = Instant.now(clock).toEpochMilli();
    long ipWindowMillis = Duration.ofSeconds(ip.windowSeconds()).toMillis();
    long globalWindowMillis = Duration.ofSeconds(global.windowSeconds()).toMillis();
    String member = nowMillis + ":" + PROCESS_TAG + ":" + MEMBER_SEQUENCE.incrementAndGet();

    List<?> result =
        stringRedisTemplate.execute(
            RESERVE_AND_TRACK_SCRIPT,
            List.of(ipKey, globalKey, trackedIpsKey),
            Long.toString(nowMillis),
            Long.toString(ipWindowMillis),
            Long.toString(globalWindowMillis),
            Integer.toString(ip.maxAttempts()),
            Integer.toString(global.maxAttempts()),
            Integer.toString(properties.maxTrackedIps()),
            member,
            ipHash,
            ipKeyPrefix);
    if (result == null || result.size() < 3) {
      throw new IllegalStateException("failed to reserve redis login throttle slot");
    }

    long status = toLong(result.get(0));
    long retryAfterMillis = toLong(result.get(1));
    int trackedIpCount = Math.toIntExact(toLong(result.get(2)));
    if (status == 0L) {
      return ThrottleDecision.allowed(ip.maxAttempts(), ip.windowSeconds(), trackedIpCount);
    }
    if (status == 1L) {
      return ThrottleDecision.blocked(
          LoginThrottleScope.IP,
          toRetryAfterSeconds(retryAfterMillis),
          ip.maxAttempts(),
          ip.windowSeconds(),
          trackedIpCount);
    }
    if (status == 2L) {
      return ThrottleDecision.blocked(
          LoginThrottleScope.GLOBAL,
          toRetryAfterSeconds(retryAfterMillis),
          global.maxAttempts(),
          global.windowSeconds(),
          trackedIpCount);
    }
    throw new IllegalStateException("unexpected redis login throttle status: " + status);
  }

  @Override
  public void clear() {
    // 테스트 편의용 정리 경로다. 운영 핫패스에서는 사용하지 않는다.
    Set<String> keys = stringRedisTemplate.keys(basePrefix() + "*");
    if (keys != null && !keys.isEmpty()) {
      stringRedisTemplate.delete(keys);
    }
  }

  private String basePrefix() {
    String prefix = properties.redis().keyPrefix();
    return prefix.endsWith(":") ? prefix : prefix + ":";
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

  private static DefaultRedisScript<List> reserveAndTrackScript() {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setResultType(List.class);
    script.setScriptText(
        """
        local ipKey = KEYS[1]
        local globalKey = KEYS[2]
        local trackedIpsKey = KEYS[3]
        local nowMillis = tonumber(ARGV[1])
        local ipWindowMillis = tonumber(ARGV[2])
        local globalWindowMillis = tonumber(ARGV[3])
        local ipLimit = tonumber(ARGV[4])
        local globalLimit = tonumber(ARGV[5])
        local maxTrackedIps = tonumber(ARGV[6])
        local member = ARGV[7]
        local ipHash = ARGV[8]
        local ipKeyPrefix = ARGV[9]

        redis.call('ZREMRANGEBYSCORE', ipKey, '-inf', nowMillis - ipWindowMillis)
        redis.call('ZREMRANGEBYSCORE', globalKey, '-inf', nowMillis - globalWindowMillis)
        redis.call('ZREMRANGEBYSCORE', trackedIpsKey, '-inf', nowMillis)

        local ipCount = redis.call('ZCARD', ipKey)
        if ipCount >= ipLimit then
          local oldest = redis.call('ZRANGE', ipKey, 0, 0, 'WITHSCORES')
          local retryAfterMillis = ipWindowMillis
          if oldest[2] ~= nil then
            retryAfterMillis = math.max(1, tonumber(oldest[2]) + ipWindowMillis - nowMillis)
          end
          local trackedCount = redis.call('ZCARD', trackedIpsKey)
          if trackedCount > 0 then
            redis.call('PEXPIRE', trackedIpsKey, ipWindowMillis)
          else
            redis.call('DEL', trackedIpsKey)
          end
          return {1, retryAfterMillis, trackedCount}
        end

        local globalCount = redis.call('ZCARD', globalKey)
        if globalCount >= globalLimit then
          local oldest = redis.call('ZRANGE', globalKey, 0, 0, 'WITHSCORES')
          local retryAfterMillis = globalWindowMillis
          if oldest[2] ~= nil then
            retryAfterMillis = math.max(1, tonumber(oldest[2]) + globalWindowMillis - nowMillis)
          end
          local trackedCount = redis.call('ZCARD', trackedIpsKey)
          if trackedCount > 0 then
            redis.call('PEXPIRE', trackedIpsKey, ipWindowMillis)
          else
            redis.call('DEL', trackedIpsKey)
          end
          return {2, retryAfterMillis, trackedCount}
        end

        redis.call('ZADD', ipKey, nowMillis, member)
        redis.call('PEXPIRE', ipKey, ipWindowMillis)
        redis.call('ZADD', globalKey, nowMillis, member)
        redis.call('PEXPIRE', globalKey, globalWindowMillis)

        if redis.call('ZSCORE', trackedIpsKey, ipHash) == false then
          local trackedCount = redis.call('ZCARD', trackedIpsKey)
          if trackedCount >= maxTrackedIps then
            local overflow = trackedCount - maxTrackedIps + 1
            local oldestIps = redis.call('ZRANGE', trackedIpsKey, 0, overflow - 1)
            for i = 1, #oldestIps do
              redis.call('DEL', ipKeyPrefix .. oldestIps[i])
              redis.call('ZREM', trackedIpsKey, oldestIps[i])
            end
          end
        end

        local expiresAtMillis = nowMillis + ipWindowMillis
        redis.call('ZADD', trackedIpsKey, expiresAtMillis, ipHash)
        redis.call('PEXPIRE', trackedIpsKey, ipWindowMillis)
        local finalTrackedCount = redis.call('ZCARD', trackedIpsKey)
        return {0, 0, finalTrackedCount}
        """);
    return script;
  }
}
