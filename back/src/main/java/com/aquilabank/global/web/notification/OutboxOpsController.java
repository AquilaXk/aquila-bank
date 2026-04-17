package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.usecase.OutboxOpsQueryUseCase;
import com.aquilabank.domain.notification.usecase.OutboxOpsRecoveryUseCase;
import com.aquilabank.global.config.OutboxOpsProperties;
import com.aquilabank.global.security.OutboxOpsTokenGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** outbox backlog 조회와 stale recovery 를 운영 전용 내부 surface 로 묶습니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/outbox")
@ConditionalOnProperty(name = "outbox.ops.enabled", havingValue = "true")
public class OutboxOpsController {

  private final OutboxOpsQueryUseCase outboxOpsQueryUseCase;
  private final OutboxOpsRecoveryUseCase outboxOpsRecoveryUseCase;
  private final OutboxOpsTokenGuard outboxOpsTokenGuard;
  private final OutboxOpsProperties outboxOpsProperties;

  public OutboxOpsController(
      OutboxOpsQueryUseCase outboxOpsQueryUseCase,
      OutboxOpsRecoveryUseCase outboxOpsRecoveryUseCase,
      OutboxOpsTokenGuard outboxOpsTokenGuard,
      OutboxOpsProperties outboxOpsProperties) {
    this.outboxOpsQueryUseCase = outboxOpsQueryUseCase;
    this.outboxOpsRecoveryUseCase = outboxOpsRecoveryUseCase;
    this.outboxOpsTokenGuard = outboxOpsTokenGuard;
    this.outboxOpsProperties = outboxOpsProperties;
  }

  @GetMapping("/summary")
  public OutboxOpsSummaryResponse getSummary(HttpServletRequest request) {
    outboxOpsTokenGuard.validate(request);
    return OutboxOpsSummaryResponse.from(outboxOpsQueryUseCase.getSummary());
  }

  @GetMapping("/failed-events")
  public OutboxFailedEventListResponse getFailedEvents(
      HttpServletRequest request,
      @RequestParam(required = false) @Positive(message = "limit must be positive") Integer limit) {
    outboxOpsTokenGuard.validate(request);
    int resolvedLimit =
        limit == null
            ? outboxOpsProperties.failedListLimit()
            : Math.min(limit, outboxOpsProperties.failedListLimit());
    return OutboxFailedEventListResponse.from(
        outboxOpsQueryUseCase.getFailedEvents(resolvedLimit), resolvedLimit);
  }

  @PostMapping("/recovery/stale-sending")
  public OutboxStaleRecoveryResponse recoverStaleSending(HttpServletRequest request) {
    outboxOpsTokenGuard.validate(request);
    return OutboxStaleRecoveryResponse.from(outboxOpsRecoveryUseCase.recoverStaleSending());
  }
}
