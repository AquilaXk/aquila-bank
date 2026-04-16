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

/** Wires the domain outbox use case with Spring-managed adapters and scheduler support. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

  @Bean
  OutboxDispatchUseCase outboxDispatchUseCase(
      OutboxEventStore outboxEventStore,
      OutboxEventPublishPort outboxEventPublishPort,
      OutboxProperties outboxProperties) {
    return new OutboxDispatchService(
        outboxEventStore,
        outboxEventPublishPort,
        outboxProperties.batchSize(),
        Duration.ofSeconds(outboxProperties.staleAfterSeconds()),
        Duration.ofSeconds(outboxProperties.maxRetryDelaySeconds()));
  }
}
