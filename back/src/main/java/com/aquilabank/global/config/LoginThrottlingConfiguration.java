package com.aquilabank.global.config;

import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.global.security.LoginThrottleStore;
import com.aquilabank.global.security.LoginThrottlingMetricsRecorder;
import com.aquilabank.global.security.LoginThrottlingProperties;
import com.aquilabank.global.security.LoginThrottlingProperties.StoreType;
import com.aquilabank.global.security.MemoryLoginThrottleStore;
import com.aquilabank.global.security.RedisLoginThrottleStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** login throttling 저장소를 memory/redis 중 하나로 조립합니다. */
@Configuration
public class LoginThrottlingConfiguration {

  @Bean
  LoginThrottleStore loginThrottleStore(
      LoginThrottlingProperties properties,
      Clock authClock,
      ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
    if (properties.requireRedis() && properties.store() != StoreType.REDIS) {
      throw new IllegalStateException(
          "security.login-throttling.require-redis=true requires "
              + "security.login-throttling.store=redis");
    }
    if (properties.store() == StoreType.REDIS) {
      StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
      if (stringRedisTemplate == null) {
        throw new IllegalStateException(
            "security.login-throttling.store=redis requires StringRedisTemplate");
      }
      return new RedisLoginThrottleStore(properties, stringRedisTemplate, authClock);
    }
    return new MemoryLoginThrottleStore(properties, authClock);
  }

  @Bean
  LoginThrottlingMetricsRecorder loginThrottlingMetricsRecorder(
      MeterRegistry meterRegistry, LoginThrottlingProperties properties) {
    return new LoginThrottlingMetricsRecorder(meterRegistry, properties.store());
  }

  @Bean
  LoginThrottleGuard loginThrottleGuard(
      LoginThrottleStore loginThrottleStore,
      LoginThrottlingMetricsRecorder loginThrottlingMetricsRecorder) {
    return new LoginThrottleGuard(loginThrottleStore, loginThrottlingMetricsRecorder);
  }
}
