package com.aquilabank.global.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** 필수 consumer 설정이 있을 때만 Kafka listener bean 을 올려 테스트/로컬 fallback 을 단순화합니다. */
final class NotificationInboxKafkaConsumerCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return isConsumerEnabled(context)
        && hasBootstrapServers(context)
        && (hasConfiguredTopic(context, "notification.inbox.consumer.transfer-booked.topic")
            || hasConfiguredTopic(context, "notification.inbox.consumer.transfer-reversed.topic"));
  }

  static boolean hasConfiguredTopic(ConditionContext context, String propertyName) {
    return StringUtils.hasText(context.getEnvironment().getProperty(propertyName));
  }

  private boolean isConsumerEnabled(ConditionContext context) {
    return context
        .getEnvironment()
        .getProperty("notification.inbox.consumer.enabled", Boolean.class, false);
  }

  private boolean hasBootstrapServers(ConditionContext context) {
    return StringUtils.hasText(
        context.getEnvironment().getProperty("notification.inbox.consumer.bootstrap-servers"));
  }
}

final class NotificationTransferBookedConsumerCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return new NotificationInboxKafkaConsumerCondition().matches(context, metadata)
        && NotificationInboxKafkaConsumerCondition.hasConfiguredTopic(
            context, "notification.inbox.consumer.transfer-booked.topic");
  }
}

final class NotificationTransferReversedConsumerCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return new NotificationInboxKafkaConsumerCondition().matches(context, metadata)
        && NotificationInboxKafkaConsumerCondition.hasConfiguredTopic(
            context, "notification.inbox.consumer.transfer-reversed.topic");
  }
}
