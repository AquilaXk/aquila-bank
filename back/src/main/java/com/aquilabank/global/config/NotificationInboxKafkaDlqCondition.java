package com.aquilabank.global.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** consumer DLQ topic 이 설정된 경우에만 DLQ publish bean 을 올립니다. */
final class NotificationInboxKafkaDlqCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return new NotificationInboxKafkaConsumerCondition().matches(context, metadata)
        && StringUtils.hasText(
            context.getEnvironment().getProperty("notification.inbox.consumer.dlq.topic"));
  }
}
