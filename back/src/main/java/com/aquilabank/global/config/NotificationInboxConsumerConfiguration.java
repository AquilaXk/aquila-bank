package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import com.aquilabank.domain.notification.usecase.NotificationInboxIngestService;
import com.aquilabank.domain.notification.usecase.NotificationInboxIngestUseCase;
import com.aquilabank.global.notification.TransferBookedNotificationConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** TransferBooked consumer 와 inbox 적재 use case wiring */
@Configuration
@EnableKafka
@EnableConfigurationProperties(NotificationInboxConsumerProperties.class)
public class NotificationInboxConsumerConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationInboxConsumerConfiguration.class);

  @Bean
  NotificationInboxIngestUseCase notificationInboxIngestUseCase(
      NotificationInboxAppendPort notificationInboxAppendPort) {
    return new NotificationInboxIngestService(notificationInboxAppendPort);
  }

  @Bean
  @Conditional(NotificationInboxKafkaConsumerCondition.class)
  ConsumerFactory<String, String> notificationInboxConsumerFactory(
      NotificationInboxConsumerProperties properties) {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.groupId());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.autoOffsetReset());
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new DefaultKafkaConsumerFactory<>(config);
  }

  @Bean
  @ConditionalOnBean(name = "notificationInboxConsumerFactory")
  ConcurrentKafkaListenerContainerFactory<String, String>
      notificationInboxKafkaListenerContainerFactory(
          ConsumerFactory<String, String> notificationInboxConsumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(notificationInboxConsumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.setCommonErrorHandler(notificationInboxKafkaErrorHandler());
    return factory;
  }

  @Bean
  @ConditionalOnBean(name = "notificationInboxKafkaListenerContainerFactory")
  TransferBookedNotificationConsumer transferBookedNotificationConsumer(
      NotificationInboxIngestUseCase notificationInboxIngestUseCase,
      ObjectMapper objectMapper,
      NotificationInboxConsumerProperties properties) {
    log.info(
        "notification inbox consumer enabled. groupId={}, topic={}",
        properties.groupId(),
        properties.transferBooked().topic());
    return new TransferBookedNotificationConsumer(notificationInboxIngestUseCase, objectMapper);
  }

  private DefaultErrorHandler notificationInboxKafkaErrorHandler() {
    // duplicate 외 실패는 skip 하지 않고 listener failure 로 남겨 최소 한 번 전달 전제를 유지합니다.
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            (record, ex) -> {
              throw ex instanceof RuntimeException runtimeException
                  ? runtimeException
                  : new IllegalStateException("notification inbox consume failed", ex);
            },
            new FixedBackOff(0L, 0L));
    errorHandler.setAckAfterHandle(false);
    errorHandler.setCommitRecovered(false);
    return errorHandler;
  }
}
