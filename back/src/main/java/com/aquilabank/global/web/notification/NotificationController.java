package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationReadUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** notification inbox 목록, unread count, read 처리 API */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationQueryUseCase notificationQueryUseCase;
  private final NotificationReadUseCase notificationReadUseCase;

  public NotificationController(
      NotificationQueryUseCase notificationQueryUseCase,
      NotificationReadUseCase notificationReadUseCase) {
    this.notificationQueryUseCase = notificationQueryUseCase;
    this.notificationReadUseCase = notificationReadUseCase;
  }

  @GetMapping
  public NotificationQueryResponse getNotifications(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    try {
      NotificationCursor decodedCursor =
          cursor == null || cursor.isBlank() ? null : NotificationCursorCodec.decode(cursor);
      NotificationListQuery query = new NotificationListQuery(limit, decodedCursor);
      return NotificationQueryResponse.from(resolveNotifications(principal, query));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @GetMapping("/unread-count")
  public NotificationUnreadCountResponse getUnreadCount(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal) {
    try {
      return new NotificationUnreadCountResponse(resolveUnreadCount(principal));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @PostMapping("/{notificationId}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markAsRead(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "notificationId must be positive") long notificationId) {
    try {
      dispatchMarkAsRead(principal, notificationId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  private com.aquilabank.domain.notification.model.NotificationSlice resolveNotifications(
      AuthenticatedRequestPrincipal principal, NotificationListQuery query) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return notificationQueryUseCase.getNotificationsForUser(userPrincipal.userId(), query);
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      return notificationQueryUseCase.getNotificationsForAccount(
          accountPrincipal.accountId(), query);
    }
    throw new IllegalArgumentException("unsupported principal type");
  }

  private long resolveUnreadCount(AuthenticatedRequestPrincipal principal) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return notificationQueryUseCase.getUnreadCountForUser(userPrincipal.userId());
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      return notificationQueryUseCase.getUnreadCountForAccount(accountPrincipal.accountId());
    }
    throw new IllegalArgumentException("unsupported principal type");
  }

  private void dispatchMarkAsRead(AuthenticatedRequestPrincipal principal, long notificationId) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      notificationReadUseCase.markAsReadForUser(userPrincipal.userId(), notificationId);
      return;
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      notificationReadUseCase.markAsReadForAccount(accountPrincipal.accountId(), notificationId);
      return;
    }
    throw new IllegalArgumentException("unsupported principal type");
  }
}
