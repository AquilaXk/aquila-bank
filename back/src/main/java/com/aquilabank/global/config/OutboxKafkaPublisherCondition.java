package com.aquilabank.global.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** 필수 Kafka 설정이 모두 있을 때만 producer bean 을 올려 fallback 선택을 단순화합니다. */
final class OutboxKafkaPublisherCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return context.getEnvironment().getProperty("outbox.kafka.enabled", Boolean.class, false)
        && StringUtils.hasText(
            context.getEnvironment().getProperty("outbox.kafka.bootstrap-servers"))
        && StringUtils.hasText(
            context.getEnvironment().getProperty("outbox.kafka.topic.default-name"));
  }
}
