package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationReadUseCase;
import com.aquilabank.global.notification.NotificationSseBroker;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** notification inbox 목록, unread count, read 처리 API */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationQueryUseCase notificationQueryUseCase;
  private final NotificationReadUseCase notificationReadUseCase;
  private final NotificationSseBroker notificationSseBroker;

  public NotificationController(
      NotificationQueryUseCase notificationQueryUseCase,
      NotificationReadUseCase notificationReadUseCase,
      NotificationSseBroker notificationSseBroker) {
    this.notificationQueryUseCase = notificationQueryUseCase;
    this.notificationReadUseCase = notificationReadUseCase;
    this.notificationSseBroker = notificationSseBroker;
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

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> streamNotifications(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestHeader(name = "Last-Event-ID", required = false) String lastEventIdHeader) {
    try {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .header("X-Accel-Buffering", "no")
          .body(openStream(principal, parseLastEventId(lastEventIdHeader)));
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

  private SseEmitter openStream(AuthenticatedRequestPrincipal principal, Long lastEventId) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return notificationSseBroker.subscribeUser(
          userPrincipal.userId(), userPrincipal.subject(), lastEventId);
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      return notificationSseBroker.subscribeAccount(
          accountPrincipal.accountId(), accountPrincipal.subject(), lastEventId);
    }
    throw new IllegalArgumentException("unsupported principal type");
  }

  private Long parseLastEventId(String lastEventIdHeader) {
    if (lastEventIdHeader == null || lastEventIdHeader.isBlank()) {
      return null;
    }
    try {
      long lastEventId = Long.parseLong(lastEventIdHeader.trim());
      if (lastEventId <= 0) {
        throw new IllegalArgumentException("Last-Event-ID must be positive");
      }
      return lastEventId;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Last-Event-ID must be numeric", ex);
    }
  }
}
