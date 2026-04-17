package com.aquilabank.global.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** lag/DLQ ops 조회는 consumer 기본값과 DLQ/ops 설정이 모두 있을 때만 노출합니다. */
final class NotificationInboxKafkaOpsCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return context
            .getEnvironment()
            .getProperty("notification.inbox.consumer.ops.enabled", Boolean.class, false)
        && new NotificationInboxKafkaDlqCondition().matches(context, metadata)
        && StringUtils.hasText(
            context.getEnvironment().getProperty("notification.inbox.consumer.group-id"));
  }
}
