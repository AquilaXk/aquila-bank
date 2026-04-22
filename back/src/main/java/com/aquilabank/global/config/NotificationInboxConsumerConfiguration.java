package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationDlqRedriveAuditPort;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import com.aquilabank.domain.notification.port.NotificationOpsReadPort;
import com.aquilabank.domain.notification.port.NotificationOpsRecoveryPort;
import com.aquilabank.domain.notification.usecase.NotificationInboxIngestService;
import com.aquilabank.domain.notification.usecase.NotificationInboxIngestUseCase;
import com.aquilabank.domain.notification.usecase.NotificationOpsQueryService;
import com.aquilabank.domain.notification.usecase.NotificationOpsQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationOpsRecoveryService;
import com.aquilabank.domain.notification.usecase.NotificationOpsRecoveryUseCase;
import com.aquilabank.global.notification.KafkaNotificationOpsRecoveryRepository;
import com.aquilabank.global.notification.KafkaNotificationOpsRepository;
import com.aquilabank.global.notification.NotificationInboxHealthIndicator;
import com.aquilabank.global.notification.TransferBookedNotificationConsumer;
import com.aquilabank.global.notification.TransferReversedNotificationConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.StringUtils;
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
  @Conditional(NotificationInboxKafkaDlqCondition.class)
  ProducerFactory<String, String> notificationInboxDlqProducerFactory(
      NotificationInboxConsumerProperties properties) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(
        ProducerConfig.CLIENT_ID_CONFIG, properties.groupId() + "-notification-dlq-producer");
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  @ConditionalOnBean(name = "notificationInboxDlqProducerFactory")
  KafkaTemplate<String, String> notificationInboxDlqKafkaTemplate(
      @Qualifier("notificationInboxDlqProducerFactory") ProducerFactory<String, String> notificationInboxDlqProducerFactory) {
    return new KafkaTemplate<>(notificationInboxDlqProducerFactory);
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
          ConsumerFactory<String, String> notificationInboxConsumerFactory,
          NotificationInboxConsumerProperties properties,
          @Qualifier("notificationInboxDlqKafkaTemplate") ObjectProvider<KafkaTemplate<String, String>> notificationInboxDlqKafkaTemplate) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(notificationInboxConsumerFactory);
    factory.setConcurrency(properties.concurrency());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.setCommonErrorHandler(
        notificationInboxKafkaErrorHandler(
            properties, notificationInboxDlqKafkaTemplate.getIfAvailable()));
    return factory;
  }

  @Bean
  @Conditional(NotificationTransferBookedConsumerCondition.class)
  TransferBookedNotificationConsumer transferBookedNotificationConsumer(
      NotificationInboxIngestUseCase notificationInboxIngestUseCase,
      ObjectMapper objectMapper,
      NotificationInboxConsumerProperties properties) {
    log.info(
        "notification inbox transfer-booked consumer enabled. groupId={}, topic={}",
        properties.groupId(),
        properties.transferBooked().topic());
    return new TransferBookedNotificationConsumer(notificationInboxIngestUseCase, objectMapper);
  }

  @Bean
  @Conditional(NotificationTransferReversedConsumerCondition.class)
  TransferReversedNotificationConsumer transferReversedNotificationConsumer(
      NotificationInboxIngestUseCase notificationInboxIngestUseCase,
      ObjectMapper objectMapper,
      NotificationInboxConsumerProperties properties) {
    log.info(
        "notification inbox transfer-reversed consumer enabled. groupId={}, topic={}",
        properties.groupId(),
        properties.transferReversed().topic());
    return new TransferReversedNotificationConsumer(notificationInboxIngestUseCase, objectMapper);
  }

  @Bean
  @Conditional(NotificationInboxKafkaOpsCondition.class)
  NotificationOpsReadPort notificationOpsReadPort(NotificationInboxConsumerProperties properties) {
    return new KafkaNotificationOpsRepository(properties);
  }

  @Bean
  @Conditional(NotificationInboxKafkaOpsCondition.class)
  @ConditionalOnBean(name = "notificationInboxDlqKafkaTemplate")
  NotificationOpsRecoveryPort notificationOpsRecoveryPort(
      NotificationInboxConsumerProperties properties,
      @Qualifier("notificationInboxDlqKafkaTemplate") KafkaTemplate<String, String> notificationInboxDlqKafkaTemplate,
      NotificationDlqRedriveAuditPort notificationDlqRedriveAuditPort) {
    return new KafkaNotificationOpsRecoveryRepository(
        properties, notificationInboxDlqKafkaTemplate, notificationDlqRedriveAuditPort);
  }

  @Bean
  @ConditionalOnBean(NotificationOpsReadPort.class)
  NotificationOpsQueryUseCase notificationOpsQueryUseCase(
      NotificationOpsReadPort notificationOpsReadPort) {
    return new NotificationOpsQueryService(notificationOpsReadPort);
  }

  @Bean
  @ConditionalOnBean(NotificationOpsRecoveryPort.class)
  NotificationOpsRecoveryUseCase notificationOpsRecoveryUseCase(
      NotificationOpsRecoveryPort notificationOpsRecoveryPort) {
    return new NotificationOpsRecoveryService(notificationOpsRecoveryPort);
  }

  @Bean
  @ConditionalOnBean(NotificationOpsQueryUseCase.class)
  HealthIndicator notificationInboxHealthIndicator(
      NotificationOpsQueryUseCase notificationOpsQueryUseCase,
      NotificationInboxConsumerProperties properties) {
    return new NotificationInboxHealthIndicator(
        notificationOpsQueryUseCase, properties.ops().health());
  }

  private DefaultErrorHandler notificationInboxKafkaErrorHandler(
      NotificationInboxConsumerProperties properties,
      KafkaTemplate<String, String> notificationInboxDlqKafkaTemplate) {
    // transient failure 는 그대로 listener failure 로 남기고, poison message 만 DLQ 로 격리합니다.
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            notificationInboxDlqRecoverer(properties, notificationInboxDlqKafkaTemplate),
            new FixedBackOff(0L, 0L));
    errorHandler.setAckAfterHandle(true);
    errorHandler.setCommitRecovered(true);
    return errorHandler;
  }

  private ConsumerRecordRecoverer notificationInboxDlqRecoverer(
      NotificationInboxConsumerProperties properties,
      KafkaTemplate<String, String> notificationInboxDlqKafkaTemplate) {
    DeadLetterPublishingRecoverer deadLetterRecoverer =
        notificationInboxDlqKafkaTemplate == null || !StringUtils.hasText(properties.dlq().topic())
            ? null
            : new DeadLetterPublishingRecoverer(
                notificationInboxDlqKafkaTemplate,
                (record, ex) -> new TopicPartition(properties.dlq().topic(), record.partition()));
    return (record, ex) -> {
      if (deadLetterRecoverer != null && isPoisonMessage(ex)) {
        deadLetterRecoverer.accept(record, ex);
        return;
      }
      throw ex instanceof RuntimeException runtimeException
          ? runtimeException
          : new IllegalStateException("notification inbox consume failed", ex);
    };
  }

  private boolean isPoisonMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof IllegalArgumentException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
