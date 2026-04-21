package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsQueryUseCase;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsRecoveryUseCase;
import com.aquilabank.global.config.CommandIdempotencyOpsProperties;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** command idempotency stale lock과 retention 상태를 내부 운영 surface로 노출합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/ledger/command-idempotency")
@ConditionalOnProperty(name = "ledger.command-idempotency.ops.enabled", havingValue = "true")
public class CommandIdempotencyOpsController {

  private final CommandIdempotencyOpsQueryUseCase queryUseCase;
  private final CommandIdempotencyOpsRecoveryUseCase recoveryUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;
  private final CommandIdempotencyOpsProperties opsProperties;

  public CommandIdempotencyOpsController(
      CommandIdempotencyOpsQueryUseCase queryUseCase,
      CommandIdempotencyOpsRecoveryUseCase recoveryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer,
      CommandIdempotencyOpsProperties opsProperties) {
    this.queryUseCase = queryUseCase;
    this.recoveryUseCase = recoveryUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
    this.opsProperties = opsProperties;
  }

  @GetMapping("/summary")
  public CommandIdempotencyOpsSummaryResponse getSummary(HttpServletRequest request) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    return CommandIdempotencyOpsSummaryResponse.from(queryUseCase.getSummary());
  }

  @GetMapping("/stale-started")
  public StaleCommandIdempotencyListResponse getStaleStarted(
      HttpServletRequest request,
      @RequestParam(required = false) @Positive(message = "limit must be positive") Integer limit) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    int resolvedLimit =
        limit == null ? opsProperties.listLimit() : Math.min(limit, opsProperties.listLimit());
    return StaleCommandIdempotencyListResponse.from(
        queryUseCase.findStaleStarted(resolvedLimit), resolvedLimit);
  }

  @PostMapping("/recovery/stale-started")
  public CommandIdempotencyStaleRecoveryResponse recoverStaleStarted(HttpServletRequest request) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    return CommandIdempotencyStaleRecoveryResponse.from(recoveryUseCase.recoverStaleStarted());
  }
}
