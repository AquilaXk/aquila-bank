package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import com.aquilabank.domain.notification.port.OutboxEventStore;
import com.aquilabank.domain.notification.usecase.OutboxDispatchService;
import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring adapter와 scheduler를 domain outbox use case에 연결 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

  @Bean
  OutboxDispatchUseCase outboxDispatchUseCase(
      OutboxEventStore outboxEventStore,
      OutboxEventPublishPort outboxEventPublishPort,
      OutboxProperties outboxProperties) {
    // poller가 바뀌어도 retry 기준과 batch 크기는 설정값에서만 제어되게 둡니다.
    return new OutboxDispatchService(
        outboxEventStore,
        outboxEventPublishPort,
        outboxProperties.batchSize(),
        Duration.ofSeconds(outboxProperties.staleAfterSeconds()),
        Duration.ofSeconds(outboxProperties.maxRetryDelaySeconds()));
  }
}
