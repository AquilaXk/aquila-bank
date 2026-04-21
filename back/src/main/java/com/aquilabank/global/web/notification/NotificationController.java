package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationBulkActionCommand;
import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationPreferenceUpdateCommand;
import com.aquilabank.domain.notification.model.NotificationReadStatusFilter;
import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.usecase.NotificationBulkActionUseCase;
import com.aquilabank.domain.notification.usecase.NotificationPreferenceReadUseCase;
import com.aquilabank.domain.notification.usecase.NotificationPreferenceUpdateUseCase;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationReadUseCase;
import com.aquilabank.domain.notification.usecase.NotificationSearchUseCase;
import com.aquilabank.global.notification.NotificationSseBroker;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  private static final long SEARCH_WINDOW_SECONDS = 31L * 24 * 60 * 60;

  private final NotificationQueryUseCase notificationQueryUseCase;
  private final NotificationReadUseCase notificationReadUseCase;
  private final NotificationBulkActionUseCase notificationBulkActionUseCase;
  private final NotificationSearchUseCase notificationSearchUseCase;
  private final NotificationPreferenceReadUseCase notificationPreferenceReadUseCase;
  private final NotificationPreferenceUpdateUseCase notificationPreferenceUpdateUseCase;
  private final NotificationSseBroker notificationSseBroker;
  private final Clock notificationSearchClock;

  public NotificationController(
      NotificationQueryUseCase notificationQueryUseCase,
      NotificationReadUseCase notificationReadUseCase,
      NotificationBulkActionUseCase notificationBulkActionUseCase,
      NotificationSearchUseCase notificationSearchUseCase,
      NotificationPreferenceReadUseCase notificationPreferenceReadUseCase,
      NotificationPreferenceUpdateUseCase notificationPreferenceUpdateUseCase,
      NotificationSseBroker notificationSseBroker,
      @Qualifier("notificationSearchClock") Clock notificationSearchClock) {
    this.notificationQueryUseCase = notificationQueryUseCase;
    this.notificationReadUseCase = notificationReadUseCase;
    this.notificationBulkActionUseCase = notificationBulkActionUseCase;
    this.notificationSearchUseCase = notificationSearchUseCase;
    this.notificationPreferenceReadUseCase = notificationPreferenceReadUseCase;
    this.notificationPreferenceUpdateUseCase = notificationPreferenceUpdateUseCase;
    this.notificationSseBroker = notificationSseBroker;
    this.notificationSearchClock = notificationSearchClock;
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

  @GetMapping("/search")
  public NotificationSearchResponse searchNotifications(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "ALL") String readStatus,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    try {
      NotificationSearchCursor decodedCursor =
          cursor == null || cursor.isBlank() ? null : NotificationSearchCursorCodec.decode(cursor);
      NotificationSearchWindow appliedWindow = resolveSearchWindow(decodedCursor, from, to);
      NotificationSearchQuery query =
          new NotificationSearchQuery(
              limit,
              decodedCursor,
              parseReadStatus(readStatus),
              normalizeEventType(eventType),
              appliedWindow.appliedFrom(),
              appliedWindow.appliedTo());
      return NotificationSearchResponse.from(resolveSearchNotifications(principal, query));
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

  @GetMapping("/preferences")
  public NotificationPreferenceResponse getNotificationPreferences(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal) {
    try {
      return NotificationPreferenceResponse.from(
          notificationPreferenceReadUseCase.getPreferences(resolvePreferenceUserId(principal)));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @PostMapping("/preferences")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateNotificationPreferences(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
    try {
      notificationPreferenceUpdateUseCase.updatePreferences(
          resolvePreferenceUserId(principal), toPreferenceUpdateCommand(request));
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

  @PostMapping("/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markAllAsRead(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody NotificationBulkActionRequest request) {
    try {
      dispatchBulkRead(principal, new NotificationBulkActionCommand(request.notificationIds()));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @PostMapping("/archive")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archiveNotifications(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody NotificationBulkActionRequest request) {
    try {
      dispatchBulkArchive(principal, new NotificationBulkActionCommand(request.notificationIds()));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @PostMapping("/delete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteNotifications(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody NotificationBulkActionRequest request) {
    try {
      dispatchBulkDelete(principal, new NotificationBulkActionCommand(request.notificationIds()));
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

  private com.aquilabank.domain.notification.model.NotificationSearchSlice
      resolveSearchNotifications(
          AuthenticatedRequestPrincipal principal, NotificationSearchQuery query) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return notificationSearchUseCase.searchForUser(userPrincipal.userId(), query);
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      return notificationSearchUseCase.searchForAccount(accountPrincipal.accountId(), query);
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

  private void dispatchBulkRead(
      AuthenticatedRequestPrincipal principal, NotificationBulkActionCommand command) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      notificationBulkActionUseCase.markAsReadForUser(userPrincipal.userId(), command);
      return;
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      notificationBulkActionUseCase.markAsReadForAccount(accountPrincipal.accountId(), command);
      return;
    }
    throw new IllegalArgumentException("unsupported principal type");
  }

  private void dispatchBulkArchive(
      AuthenticatedRequestPrincipal principal, NotificationBulkActionCommand command) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      notificationBulkActionUseCase.archiveForUser(userPrincipal.userId(), command);
      return;
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      notificationBulkActionUseCase.archiveForAccount(accountPrincipal.accountId(), command);
      return;
    }
    throw new IllegalArgumentException("unsupported principal type");
  }

  private void dispatchBulkDelete(
      AuthenticatedRequestPrincipal principal, NotificationBulkActionCommand command) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      notificationBulkActionUseCase.deleteForUser(userPrincipal.userId(), command);
      return;
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      notificationBulkActionUseCase.deleteForAccount(accountPrincipal.accountId(), command);
      return;
    }
    throw new IllegalArgumentException("unsupported principal type");
  }

  private long resolvePreferenceUserId(AuthenticatedRequestPrincipal principal) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return userPrincipal.userId();
    }
    throw new IllegalArgumentException("notification preferences require user principal");
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

  /** cursor 재사용 시 같은 window를 유지해야 다음 page 의미가 고정됩니다. */
  private NotificationSearchWindow resolveSearchWindow(
      NotificationSearchCursor cursor, String from, String to) {
    boolean hasFrom = from != null && !from.isBlank();
    boolean hasTo = to != null && !to.isBlank();
    if (hasFrom != hasTo) {
      throw new IllegalArgumentException("from and to must be provided together");
    }
    if (!hasFrom) {
      if (cursor != null) {
        return validateSearchWindow(cursor.appliedFrom(), cursor.appliedTo());
      }
      Instant appliedTo = Instant.now(notificationSearchClock);
      return validateSearchWindow(appliedTo.minusSeconds(SEARCH_WINDOW_SECONDS), appliedTo);
    }
    Instant appliedFrom = parseInstant(from, "from");
    Instant appliedTo = parseInstant(to, "to");
    return validateSearchWindow(appliedFrom, appliedTo);
  }

  private NotificationReadStatusFilter parseReadStatus(String rawReadStatus) {
    try {
      return NotificationReadStatusFilter.valueOf(rawReadStatus.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("readStatus is invalid", ex);
    }
  }

  private Instant parseInstant(String rawValue, String fieldName) {
    try {
      return Instant.parse(rawValue);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(fieldName + " must be a valid ISO-8601 instant", ex);
    }
  }

  private NotificationSearchWindow validateSearchWindow(Instant appliedFrom, Instant appliedTo) {
    if (appliedFrom.isAfter(appliedTo)) {
      throw new IllegalArgumentException("from must be before or equal to to");
    }
    if (appliedFrom.plusSeconds(SEARCH_WINDOW_SECONDS).isBefore(appliedTo)) {
      throw new IllegalArgumentException("search window must be 31 days or less");
    }
    return new NotificationSearchWindow(appliedFrom, appliedTo);
  }

  private String normalizeEventType(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return null;
    }
    return eventType.trim();
  }

  /** bulk action body 는 작은 id 목록만 허용해 notification inbox SQL 범위를 고정합니다. */
  public record NotificationBulkActionRequest(
      @NotEmpty(message = "notificationIds must not be empty") @Size(max = 100, message = "notificationIds size must be 100 or less") List<@Positive(message = "notificationIds must contain only positive values") Long>
              notificationIds) {}

  private NotificationPreferenceUpdateCommand toPreferenceUpdateCommand(
      NotificationPreferenceUpdateRequest request) {
    return new NotificationPreferenceUpdateCommand(
        request.items().stream()
            .map(NotificationPreferenceUpdateRequest.PreferenceItem::toModel)
            .toList());
  }

  private record NotificationSearchWindow(Instant appliedFrom, Instant appliedTo) {}
}
