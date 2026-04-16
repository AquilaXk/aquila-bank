package com.aquilabank.global.web;

import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.exception.AuthStatusChangeAuditNotFoundException;
import com.aquilabank.domain.auth.exception.AuthUserNotFoundException;
import com.aquilabank.domain.auth.exception.DuplicateLoginIdException;
import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.exception.UserAccountMembershipNotFoundException;
import com.aquilabank.domain.ledger.exception.CommandConflictException;
import com.aquilabank.domain.ledger.exception.CurrencyMismatchException;
import com.aquilabank.domain.ledger.exception.InsufficientBalanceException;
import com.aquilabank.domain.ledger.exception.SnapshotNotFoundException;
import com.aquilabank.global.security.BootstrapApiAccessDeniedException;
import com.aquilabank.global.security.BootstrapHeaderAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final String DEFAULT_ACTOR_SUBJECT_HEADER = "X-Subject";
  private static final Pattern USER_STATUS_PATH_PATTERN =
      Pattern.compile("^/internal/api/v1/auth/users/(\\d+)/status$");
  private static final Pattern MEMBERSHIP_STATUS_PATH_PATTERN =
      Pattern.compile("^/internal/api/v1/auth/users/(\\d+)/memberships/(\\d+)/status$");
  private final String actorSubjectHeader;

  public ApiExceptionHandler() {
    this.actorSubjectHeader = DEFAULT_ACTOR_SUBJECT_HEADER;
  }

  @Autowired
  public ApiExceptionHandler(BootstrapHeaderAuthProperties bootstrapHeaderAuthProperties) {
    this.actorSubjectHeader = bootstrapHeaderAuthProperties.subjectHeader();
  }

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

  @ExceptionHandler(AccountAccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> handleForbidden(
      AccountAccessDeniedException ex, HttpServletRequest request) {
    return response(HttpStatus.FORBIDDEN, ex.getMessage(), request);
  }

  @ExceptionHandler({
    SnapshotNotFoundException.class,
    AuthStatusChangeAuditNotFoundException.class,
    AuthUserNotFoundException.class,
    UserAccountMembershipNotFoundException.class
  })
  ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler({
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
        "internal auth status update failed requestId={} httpStatus={} actorSubject={} targetUserId={} targetAccountId={} requestedStatus={} reason={} path={} error={}",
        RequestTraceContext.currentRequestId().orElse("-"),
        status.value(),
        context.actorSubject(),
        context.targetUserId(),
        context.targetAccountId() == null ? "-" : context.targetAccountId(),
        context.requestedStatus(),
        context.reason(),
        request.getRequestURI(),
        error);
  }

  private AuditFailureContext extractAuditFailureContext(HttpServletRequest request) {
    String path = request.getRequestURI();
    Matcher membershipMatcher = MEMBERSHIP_STATUS_PATH_PATTERN.matcher(path);
    if (membershipMatcher.matches()) {
      return new AuditFailureContext(
          request.getHeader(actorSubjectHeader),
          parseLong(membershipMatcher.group(1)),
          parseLong(membershipMatcher.group(2)),
          extractJsonField(request, "membershipStatus"),
          extractJsonField(request, "reason"));
    }

    Matcher userMatcher = USER_STATUS_PATH_PATTERN.matcher(path);
    if (userMatcher.matches()) {
      return new AuditFailureContext(
          request.getHeader(actorSubjectHeader),
          parseLong(userMatcher.group(1)),
          null,
          extractJsonField(request, "userStatus"),
          extractJsonField(request, "reason"));
    }
    return null;
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

  private record AuditFailureContext(
      String actorSubject,
      Long targetUserId,
      Long targetAccountId,
      String requestedStatus,
      String reason) {

    private AuditFailureContext {
      actorSubject = actorSubject == null || actorSubject.isBlank() ? "-" : actorSubject;
      requestedStatus =
          requestedStatus == null || requestedStatus.isBlank() ? "-" : requestedStatus;
      reason = reason == null || reason.isBlank() ? "-" : reason;
    }
  }

  /** 모든 API가 공유하는 기본 error body */
  public record ApiErrorResponse(
      Instant timestamp, int status, String error, String message, String path) {}
}
