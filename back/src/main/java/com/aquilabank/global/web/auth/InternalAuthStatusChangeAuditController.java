package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditCursor;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditItem;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchQuery;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchResult;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeType;
import com.aquilabank.domain.auth.usecase.AuthStatusChangeAuditQueryUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내부 auth 감사 requestId exact lookup만 분리해 운영 조회 경계를 고정합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/auth/status-change-audits")
@ConditionalOnProperty(name = "security.auth-bootstrap-api.enabled", havingValue = "true")
public class InternalAuthStatusChangeAuditController {

  private final AuthStatusChangeAuditQueryUseCase authStatusChangeAuditQueryUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public InternalAuthStatusChangeAuditController(
      AuthStatusChangeAuditQueryUseCase authStatusChangeAuditQueryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.authStatusChangeAuditQueryUseCase = authStatusChangeAuditQueryUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @GetMapping("/by-request-id")
  public AuthStatusChangeAuditResponse getByRequestId(
      HttpServletRequest httpServletRequest,
      @RequestParam @NotBlank(message = "requestId is required") String requestId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return AuthStatusChangeAuditResponse.from(
        authStatusChangeAuditQueryUseCase.getByRequestId(requestId));
  }

  @GetMapping
  public AuthStatusChangeAuditListResponse search(
      HttpServletRequest httpServletRequest,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant fromCreatedAt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant toCreatedAt,
      @RequestParam(required = false) Long targetUserId,
      @RequestParam(required = false) Long targetAccountId,
      @RequestParam(required = false) AuthStatusChangeType changeType,
      @RequestParam(required = false) AuthStatusChangeReasonCode reasonCode,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    AuthStatusChangeAuditSearchResult result =
        authStatusChangeAuditQueryUseCase.search(
            new AuthStatusChangeAuditSearchQuery(
                fromCreatedAt,
                toCreatedAt,
                targetUserId,
                targetAccountId,
                changeType,
                reasonCode,
                AuthStatusChangeAuditCursor.decode(cursor),
                size));
    return AuthStatusChangeAuditListResponse.from(result);
  }

  /** 내부 auth status change audit exact lookup 응답 */
  public record AuthStatusChangeAuditResponse(
      String requestId,
      String actorSubject,
      long targetUserId,
      Long targetAccountId,
      String changeType,
      String beforeStatus,
      String afterStatus,
      String reasonCode,
      String reasonDetail,
      String reason,
      String outcome,
      Instant createdAt) {

    static AuthStatusChangeAuditResponse from(AuthStatusChangeAuditSummary summary) {
      return new AuthStatusChangeAuditResponse(
          summary.requestId(),
          summary.actorSubject(),
          summary.targetUserId(),
          summary.targetAccountId(),
          summary.changeType().name(),
          summary.beforeStatus(),
          summary.afterStatus(),
          summary.reasonCode().name(),
          summary.reasonDetail(),
          summary.reason(),
          summary.outcome().name(),
          summary.createdAt());
    }
  }

  public record AuthStatusChangeAuditListResponse(
      List<AuthStatusChangeAuditResponse> items, String nextCursor) {

    static AuthStatusChangeAuditListResponse from(AuthStatusChangeAuditSearchResult result) {
      return new AuthStatusChangeAuditListResponse(
          result.items().stream().map(InternalAuthStatusChangeAuditController::from).toList(),
          result.nextCursor());
    }
  }

  private static AuthStatusChangeAuditResponse from(AuthStatusChangeAuditItem item) {
    return new AuthStatusChangeAuditResponse(
        item.requestId(),
        item.actorSubject(),
        item.targetUserId(),
        item.targetAccountId(),
        item.changeType().name(),
        item.beforeStatus(),
        item.afterStatus(),
        item.reasonCode().name(),
        item.reasonDetail(),
        item.reasonDetail(),
        item.outcome().name(),
        item.createdAt());
  }
}
