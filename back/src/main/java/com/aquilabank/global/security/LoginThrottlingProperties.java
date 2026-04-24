package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 공개 login entrypoint 전용 IP/global throttling 설정입니다. */
@ConfigurationProperties(prefix = "security.login-throttling")
public record LoginThrottlingProperties(
    int maxTrackedIps,
    ScopeProperties ip,
    ScopeProperties global,
    StoreType store,
    boolean requireRedis,
    RedisProperties redis) {

  public LoginThrottlingProperties {
    maxTrackedIps = maxTrackedIps > 0 ? maxTrackedIps : 1024;
    ip = ip == null ? new ScopeProperties(20, 60) : ip;
    global = global == null ? new ScopeProperties(40, 10) : global;
    store = store == null ? StoreType.MEMORY : store;
    redis = redis == null ? new RedisProperties("auth:login:throttle:") : redis;
  }

  public record ScopeProperties(int maxAttempts, long windowSeconds) {

    public ScopeProperties {
      if (maxAttempts <= 0) {
        throw new IllegalArgumentException(
            "security.login-throttling max-attempts must be positive");
      }
      if (windowSeconds <= 0) {
        throw new IllegalArgumentException(
            "security.login-throttling window-seconds must be positive");
      }
    }
  }

  public enum StoreType {
    MEMORY,
    REDIS
  }

  public record RedisProperties(String keyPrefix) {

    public RedisProperties {
      if (keyPrefix == null || keyPrefix.isBlank()) {
        throw new IllegalArgumentException(
            "security.login-throttling.redis.key-prefix must not be blank");
      }
    }
  }
}
