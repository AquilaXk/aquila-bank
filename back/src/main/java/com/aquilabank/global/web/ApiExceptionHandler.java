package com.aquilabank.global.web;

import com.aquilabank.domain.ledger.exception.CommandConflictException;
import com.aquilabank.domain.ledger.exception.CurrencyMismatchException;
import com.aquilabank.domain.ledger.exception.InsufficientBalanceException;
import com.aquilabank.domain.ledger.exception.SnapshotNotFoundException;
import com.aquilabank.global.security.BootstrapApiAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** domain/web 예외를 공통 API error response로 바꾸는 handler */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("validation failed");
    return response(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiErrorResponse> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(BootstrapApiAccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> handleBootstrapUnauthorized(
      BootstrapApiAccessDeniedException ex, HttpServletRequest request) {
    return response(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(SnapshotNotFoundException.class)
  ResponseEntity<ApiErrorResponse> handleNotFound(
      SnapshotNotFoundException ex, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler({
    CommandConflictException.class,
    CurrencyMismatchException.class,
    InsufficientBalanceException.class
  })
  ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> handleIllegalState(
      IllegalStateException ex, HttpServletRequest request) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
  }

  private ResponseEntity<ApiErrorResponse> response(
      HttpStatus status, String message, String path) {
    return ResponseEntity.status(status)
        .body(
            new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, path));
  }

  /** 모든 API가 공유하는 기본 error body */
  public record ApiErrorResponse(
      Instant timestamp, int status, String error, String message, String path) {}
}
