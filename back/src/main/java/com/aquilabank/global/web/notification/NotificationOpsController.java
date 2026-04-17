package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.usecase.NotificationOpsQueryUseCase;
import com.aquilabank.global.config.NotificationInboxConsumerProperties;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** outbox runbook 아래에서 notification consumer lag 와 DLQ preview 를 같이 조회합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/outbox/notification")
@ConditionalOnBean(NotificationOpsQueryUseCase.class)
public class NotificationOpsController {

  private final NotificationOpsQueryUseCase notificationOpsQueryUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;
  private final NotificationInboxConsumerProperties notificationInboxConsumerProperties;

  public NotificationOpsController(
      NotificationOpsQueryUseCase notificationOpsQueryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer,
      NotificationInboxConsumerProperties notificationInboxConsumerProperties) {
    this.notificationOpsQueryUseCase = notificationOpsQueryUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
    this.notificationInboxConsumerProperties = notificationInboxConsumerProperties;
  }

  @GetMapping("/summary")
  public NotificationOpsSummaryResponse getSummary(HttpServletRequest request) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.OUTBOX_OPS);
    return NotificationOpsSummaryResponse.from(notificationOpsQueryUseCase.getSummary());
  }

  @GetMapping("/dlq-events")
  public NotificationDlqEventListResponse getDlqEvents(
      HttpServletRequest request,
      @RequestParam(required = false) @Positive(message = "limit must be positive") Integer limit) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.OUTBOX_OPS);
    int configuredLimit = notificationInboxConsumerProperties.ops().dlqPreviewLimit();
    int resolvedLimit = limit == null ? configuredLimit : Math.min(limit, configuredLimit);
    return NotificationDlqEventListResponse.from(
        notificationOpsQueryUseCase.getDlqEvents(resolvedLimit), resolvedLimit);
  }
}
