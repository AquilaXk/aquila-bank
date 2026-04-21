package com.aquilabank.global.web;

import com.aquilabank.domain.account.exception.AccountStatusChangeAuditNotFoundException;
import com.aquilabank.domain.account.exception.AccountSummaryNotFoundException;
import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.exception.AuthStatusChangeAuditNotFoundException;
import com.aquilabank.domain.auth.exception.AuthUserNotFoundException;
import com.aquilabank.domain.auth.exception.DuplicateExternalIdentityMappingException;
import com.aquilabank.domain.auth.exception.DuplicateLoginIdException;
import com.aquilabank.domain.auth.exception.ExternalIdentityAuditNotFoundException;
import com.aquilabank.domain.auth.exception.ExternalIdentityMappingNotFoundException;
import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.exception.PasswordRecoveryTokenNotFoundException;
import com.aquilabank.domain.auth.exception.UserAccountMembershipNotFoundException;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.ledger.exception.CommandConflictException;
import com.aquilabank.domain.ledger.exception.CurrencyMismatchException;
import com.aquilabank.domain.ledger.exception.InsufficientBalanceException;
import com.aquilabank.domain.ledger.exception.SnapshotNotFoundException;
import com.aquilabank.domain.ledger.exception.TransferAccountStatusBlockedException;
import com.aquilabank.domain.ledger.exception.TransferReversalNotFoundException;
import com.aquilabank.domain.notification.exception.NotificationNotFoundException;
import com.aquilabank.domain.transaction.exception.TransactionDetailNotFoundException;
import com.aquilabank.global.notification.NotificationSseOverloadException;
import com.aquilabank.global.security.BootstrapApiAccessDeniedException;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceTokenClaims;
import com.aquilabank.global.security.LoginThrottledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** domain/web 예외를 공통 API error response로 바꾸는 handler */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final Pattern USER_STATUS_PATH_PATTERN =
      Pattern.compile("^/internal/api/v1/auth/users/(\\d+)/status$");
  private static final Pattern MEMBERSHIP_STATUS_PATH_PATTERN =
      Pattern.compile("^/internal/api/v1/auth/users/(\\d+)/memberships/(\\d+)/status$");

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("validation failed");
    return response(HttpStatus.BAD_REQUEST, message, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiErrorResponse> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
  }

  @ExceptionHandler(BootstrapApiAccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> handleBootstrapUnauthorized(
      BootstrapApiAccessDeniedException ex, HttpServletRequest request) {
    return response(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ApiErrorResponse> handleUnauthorized(
      InvalidCredentialsException ex, HttpServletRequest request) {
    return response(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
  }

  @ExceptionHandler(LoginThrottledException.class)
  ResponseEntity<ApiErrorResponse> handleTooManyRequests(
      LoginThrottledException ex, HttpServletRequest request) {
    logInternalAuthStatusFailure(HttpStatus.TOO_MANY_REQUESTS, request, ex.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", Long.toString(ex.retryAfterSeconds()))
        .body(
            new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
  }

  @ExceptionHandler({
    AccountAccessDeniedException.class,
    TransferAccountStatusBlockedException.class
  })
  ResponseEntity<ApiErrorResponse> handleForbidden(
      RuntimeException ex, HttpServletRequest request) {
    return response(HttpStatus.FORBIDDEN, ex.getMessage(), request);
  }

  @ExceptionHandler(NotificationSseOverloadException.class)
  ResponseEntity<ApiErrorResponse> handleServiceUnavailable(
      NotificationSseOverloadException ex, HttpServletRequest request) {
    return response(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
  }

  @ExceptionHandler({
    AccountSummaryNotFoundException.class,
    AccountStatusChangeAuditNotFoundException.class,
    SnapshotNotFoundException.class,
    TransferReversalNotFoundException.class,
    AuthStatusChangeAuditNotFoundException.class,
    AuthUserNotFoundException.class,
    ExternalIdentityAuditNotFoundException.class,
    ExternalIdentityMappingNotFoundException.class,
    PasswordRecoveryTokenNotFoundException.class,
    UserAccountMembershipNotFoundException.class,
    NotificationNotFoundException.class,
    TransactionDetailNotFoundException.class
  })
  ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler({
    DuplicateExternalIdentityMappingException.class,
    DuplicateLoginIdException.class,
    CommandConflictException.class,
    CurrencyMismatchException.class,
    InsufficientBalanceException.class
  })
  ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, ex.getMessage(), request);
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> handleIllegalState(
      IllegalStateException ex, HttpServletRequest request) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
  }

  private ResponseEntity<ApiErrorResponse> response(
      HttpStatus status, String message, HttpServletRequest request) {
    logInternalAuthStatusFailure(status, request, message);
    return ResponseEntity.status(status)
        .body(
            new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()));
  }

  private void logInternalAuthStatusFailure(
      HttpStatus status, HttpServletRequest request, String error) {
    AuditFailureContext context = extractAuditFailureContext(request);
    if (context == null) {
      return;
    }
    log.warn(
        "internal auth status update failed requestId={} httpStatus={} actorSubject={} targetUserId={} targetAccountId={} requestedStatus={} reasonCode={} reasonDetail={} path={} error={}",
        RequestTraceContext.currentRequestId().orElse("-"),
        status.value(),
        context.actorSubject(),
        context.targetUserId(),
        context.targetAccountId() == null ? "-" : context.targetAccountId(),
        context.requestedStatus(),
        context.reasonCode(),
        context.reasonDetail(),
        request.getRequestURI(),
        error);
  }

  private AuditFailureContext extractAuditFailureContext(HttpServletRequest request) {
    String path = request.getRequestURI();
    Matcher membershipMatcher = MEMBERSHIP_STATUS_PATH_PATTERN.matcher(path);
    if (membershipMatcher.matches()) {
      return new AuditFailureContext(
          resolveActorSubject(request),
          parseLong(membershipMatcher.group(1)),
          parseLong(membershipMatcher.group(2)),
          extractJsonField(request, "membershipStatus"),
          extractReasonCode(request),
          extractReasonDetail(request));
    }

    Matcher userMatcher = USER_STATUS_PATH_PATTERN.matcher(path);
    if (userMatcher.matches()) {
      return new AuditFailureContext(
          resolveActorSubject(request),
          parseLong(userMatcher.group(1)),
          null,
          extractJsonField(request, "userStatus"),
          extractReasonCode(request),
          extractReasonDetail(request));
    }
    return null;
  }

  private String resolveActorSubject(HttpServletRequest request) {
    Object value = request.getAttribute(InternalServiceRequestAuthorizer.REQUEST_ATTRIBUTE);
    if (value instanceof InternalServiceTokenClaims claims) {
      return claims.subject();
    }
    return "-";
  }

  private Long parseLong(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String extractJsonField(HttpServletRequest request, String fieldName) {
    if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
      return "-";
    }
    byte[] content = wrapper.getContentAsByteArray();
    if (content.length == 0) {
      return "-";
    }
    String body = new String(content, StandardCharsets.UTF_8);
    Pattern fieldPattern =
        Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
    Matcher matcher = fieldPattern.matcher(body);
    if (!matcher.find()) {
      return "-";
    }
    String value = matcher.group(1);
    return value == null || value.isBlank() ? "-" : value;
  }

  private String extractReasonCode(HttpServletRequest request) {
    String reasonCode = extractJsonField(request, "reasonCode");
    if (!"-".equals(reasonCode)) {
      return reasonCode;
    }
    String legacyReason = extractJsonField(request, "reason");
    if ("-".equals(legacyReason)) {
      return "-";
    }
    return AuthStatusChangeReasonCode.LEGACY_FREE_TEXT.name();
  }

  private String extractReasonDetail(HttpServletRequest request) {
    String reasonDetail = extractJsonField(request, "reasonDetail");
    if (!"-".equals(reasonDetail)) {
      return reasonDetail;
    }
    return extractJsonField(request, "reason");
  }

  private record AuditFailureContext(
      String actorSubject,
      Long targetUserId,
      Long targetAccountId,
      String requestedStatus,
      String reasonCode,
      String reasonDetail) {

    private AuditFailureContext {
      actorSubject = actorSubject == null || actorSubject.isBlank() ? "-" : actorSubject;
      requestedStatus =
          requestedStatus == null || requestedStatus.isBlank() ? "-" : requestedStatus;
      reasonCode = reasonCode == null || reasonCode.isBlank() ? "-" : reasonCode;
      reasonDetail = reasonDetail == null || reasonDetail.isBlank() ? "-" : reasonDetail;
    }
  }

  /** 모든 API가 공유하는 기본 error body */
  public record ApiErrorResponse(
      Instant timestamp, int status, String error, String message, String path) {}
}
