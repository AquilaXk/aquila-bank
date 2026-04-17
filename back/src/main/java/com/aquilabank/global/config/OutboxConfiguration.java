package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import com.aquilabank.domain.notification.port.OutboxEventStore;
import com.aquilabank.domain.notification.port.OutboxOpsReadPort;
import com.aquilabank.domain.notification.port.OutboxOpsRecoveryPort;
import com.aquilabank.domain.notification.usecase.OutboxDispatchService;
import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import com.aquilabank.domain.notification.usecase.OutboxOpsQueryService;
import com.aquilabank.domain.notification.usecase.OutboxOpsQueryUseCase;
import com.aquilabank.domain.notification.usecase.OutboxOpsRecoveryService;
import com.aquilabank.domain.notification.usecase.OutboxOpsRecoveryUseCase;
import com.aquilabank.global.notification.KafkaOutboxEventPublisher;
import com.aquilabank.global.notification.LoggingOutboxEventPublisher;
import com.aquilabank.global.notification.OutboxHealthIndicator;
import com.aquilabank.global.notification.OutboxTopicResolver;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ClassUtils;

/** Spring adapter와 scheduler를 domain outbox use case에 연결 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
  OutboxProperties.class,
  OutboxKafkaProperties.class,
  OutboxOpsProperties.class
})
public class OutboxConfiguration {

  private static final Logger log = LoggerFactory.getLogger(OutboxConfiguration.class);

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

  @Bean
  OutboxOpsQueryUseCase outboxOpsQueryUseCase(
      OutboxOpsReadPort outboxOpsReadPort, OutboxProperties outboxProperties) {
    return new OutboxOpsQueryService(
        outboxOpsReadPort, Duration.ofSeconds(outboxProperties.staleAfterSeconds()));
  }

  @Bean
  OutboxOpsRecoveryUseCase outboxOpsRecoveryUseCase(
      OutboxOpsRecoveryPort outboxOpsRecoveryPort, OutboxProperties outboxProperties) {
    return new OutboxOpsRecoveryService(
        outboxOpsRecoveryPort, Duration.ofSeconds(outboxProperties.staleAfterSeconds()));
  }

  @Bean
  HealthIndicator outboxHealthIndicator(
      OutboxOpsQueryUseCase outboxOpsQueryUseCase, OutboxOpsProperties outboxOpsProperties) {
    return new OutboxHealthIndicator(outboxOpsQueryUseCase, outboxOpsProperties.health());
  }

  @Bean
  LoggingOutboxEventPublisher loggingOutboxEventPublisher() {
    return new LoggingOutboxEventPublisher();
  }

  @Bean
  OutboxTopicResolver outboxTopicResolver(OutboxKafkaProperties outboxKafkaProperties) {
    return new OutboxTopicResolver(outboxKafkaProperties.topic());
  }

  @Bean
  @Conditional(OutboxKafkaPublisherCondition.class)
  ProducerFactory<String, String> outboxKafkaProducerFactory(
      OutboxKafkaProperties outboxKafkaProperties) {
    return new DefaultKafkaProducerFactory<>(kafkaProducerConfig(outboxKafkaProperties));
  }

  @Bean
  @ConditionalOnBean(name = "outboxKafkaProducerFactory")
  KafkaTemplate<String, String> outboxKafkaTemplate(
      ProducerFactory<String, String> outboxKafkaProducerFactory) {
    return new KafkaTemplate<>(outboxKafkaProducerFactory);
  }

  @Bean
  @ConditionalOnBean(name = "outboxKafkaTemplate")
  KafkaOutboxEventPublisher kafkaOutboxEventPublisher(
      KafkaTemplate<String, String> outboxKafkaTemplate,
      OutboxTopicResolver outboxTopicResolver,
      OutboxKafkaProperties outboxKafkaProperties) {
    return new KafkaOutboxEventPublisher(
        outboxKafkaTemplate,
        outboxTopicResolver,
        Duration.ofMillis(outboxKafkaProperties.sendTimeoutMs()));
  }

  @Bean
  OutboxEventPublishPort outboxEventPublishPort(
      LoggingOutboxEventPublisher loggingOutboxEventPublisher,
      OutboxKafkaProperties outboxKafkaProperties,
      ObjectProvider<KafkaOutboxEventPublisher> kafkaOutboxEventPublisherProvider) {
    KafkaOutboxEventPublisher kafkaOutboxEventPublisher =
        kafkaOutboxEventPublisherProvider.getIfAvailable();
    if (kafkaOutboxEventPublisher != null) {
      log.info(
          "outbox Kafka publisher enabled. defaultTopic={}, bootstrapServers={}",
          outboxKafkaProperties.topic().defaultName(),
          outboxKafkaProperties.bootstrapServers());
      return kafkaOutboxEventPublisher;
    }
    log.info(
        "outbox Kafka publisher unavailable. using logging fallback. reason={}",
        outboxKafkaProperties.disabledReason());
    return loggingOutboxEventPublisher;
  }

  private Map<String, Object> kafkaProducerConfig(OutboxKafkaProperties outboxKafkaProperties) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, outboxKafkaProperties.bootstrapServers());
    config.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        resolveSerializerClass(outboxKafkaProperties.serializer().keyClass()));
    config.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        resolveSerializerClass(outboxKafkaProperties.serializer().valueClass()));
    config.put(ProducerConfig.CLIENT_ID_CONFIG, outboxKafkaProperties.producer().clientId());
    config.put(ProducerConfig.ACKS_CONFIG, outboxKafkaProperties.producer().acks());
    config.put(
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
        outboxKafkaProperties.producer().enableIdempotence());
    config.put(
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
        outboxKafkaProperties.producer().maxInFlightRequestsPerConnection());
    config.put(ProducerConfig.RETRIES_CONFIG, outboxKafkaProperties.retry().retries());
    config.put(
        ProducerConfig.RETRY_BACKOFF_MS_CONFIG,
        toKafkaInt(
            "outbox.kafka.retry.retry-backoff-ms", outboxKafkaProperties.retry().retryBackoffMs()));
    config.put(
        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
        toKafkaInt(
            "outbox.kafka.retry.delivery-timeout-ms",
            outboxKafkaProperties.retry().deliveryTimeoutMs()));
    config.put(
        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
        toKafkaInt(
            "outbox.kafka.retry.request-timeout-ms",
            outboxKafkaProperties.retry().requestTimeoutMs()));
    return config;
  }

  private int toKafkaInt(String propertyName, long value) {
    // env 바인딩은 long 으로 받고, Kafka client 직전만 int 계약으로 맞춰 설정 오류를 막습니다.
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(propertyName + " must fit into Kafka int config range");
    }
    return (int) value;
  }

  @SuppressWarnings("unchecked")
  private Class<? extends Serializer<?>> resolveSerializerClass(String className) {
    Class<?> serializerClass =
        ClassUtils.resolveClassName(className, OutboxConfiguration.class.getClassLoader());
    if (!Serializer.class.isAssignableFrom(serializerClass)) {
      throw new IllegalArgumentException(
          "serializer must implement Kafka Serializer: " + className);
    }
    return (Class<? extends Serializer<?>>) serializerClass;
  }
}
