package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsRecoveryUseCase;
import com.aquilabank.global.config.NotificationChannelOutboxOpsProperties;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** channel outbox 격리 row 조회와 단건 redrive를 내부 ops surface로 묶습니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/outbox/notification-channel")
@ConditionalOnBean(NotificationChannelOutboxOpsQueryUseCase.class)
public class NotificationChannelOutboxOpsController {

  private final NotificationChannelOutboxOpsQueryUseCase queryUseCase;
  private final NotificationChannelOutboxOpsRecoveryUseCase recoveryUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;
  private final NotificationChannelOutboxOpsProperties properties;

  public NotificationChannelOutboxOpsController(
      NotificationChannelOutboxOpsQueryUseCase queryUseCase,
      NotificationChannelOutboxOpsRecoveryUseCase recoveryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer,
      NotificationChannelOutboxOpsProperties properties) {
    this.queryUseCase = queryUseCase;
    this.recoveryUseCase = recoveryUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
    this.properties = properties;
  }

  @GetMapping("/quarantined-events")
  public NotificationChannelOutboxQuarantinedListResponse getQuarantinedEvents(
      HttpServletRequest request,
      @RequestParam(required = false) @Positive(message = "limit must be positive") Integer limit) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.OUTBOX_OPS);
    int resolvedLimit =
        limit == null
            ? properties.quarantinedListLimit()
            : Math.min(limit, properties.quarantinedListLimit());
    return NotificationChannelOutboxQuarantinedListResponse.from(
        queryUseCase.getQuarantinedItems(resolvedLimit), resolvedLimit);
  }

  @PostMapping("/quarantined-events/{id}/redrive")
  public NotificationChannelOutboxRedriveResponse redriveQuarantinedEvent(
      HttpServletRequest request,
      @PathVariable @Positive(message = "id must be positive") long id) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.OUTBOX_OPS);
    return NotificationChannelOutboxRedriveResponse.from(recoveryUseCase.redrive(id));
  }
}
