package com.aquilabank.global.security;

import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis 선택 시 연결만 성립하도록 최소 스캐폴드만 둡니다. */
public final class RedisLoginThrottleStore implements LoginThrottleStore {

  private final LoginThrottlingProperties properties;
  private final StringRedisTemplate stringRedisTemplate;

  public RedisLoginThrottleStore(
      LoginThrottlingProperties properties, StringRedisTemplate stringRedisTemplate) {
    this.properties = properties;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public ThrottleDecision check(String ipAddress) {
    throw new IllegalStateException("redis login throttling counter is not implemented yet");
  }

  @Override
  public void clear() {
    // 현재 단계에서는 Redis 경로의 bean 조립만 보장합니다.
  }
}
