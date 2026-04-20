package com.aquilabank.global.config;

import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.global.security.LoginThrottleStore;
import com.aquilabank.global.security.LoginThrottlingProperties;
import com.aquilabank.global.security.LoginThrottlingProperties.StoreType;
import com.aquilabank.global.security.MemoryLoginThrottleStore;
import com.aquilabank.global.security.RedisLoginThrottleStore;
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
  LoginThrottleGuard loginThrottleGuard(LoginThrottleStore loginThrottleStore) {
    return new LoginThrottleGuard(loginThrottleStore);
  }
}
