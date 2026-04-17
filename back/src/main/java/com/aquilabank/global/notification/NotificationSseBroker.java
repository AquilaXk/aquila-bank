package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationReplayQuery;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.global.config.NotificationSseProperties;
import com.aquilabank.global.web.notification.NotificationQueryResponse.NotificationItemResponse;
import com.aquilabank.global.web.notification.NotificationStreamConnectedResponse;
import com.aquilabank.global.web.notification.NotificationStreamHeartbeatResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE emitter registry는 app instance local 메모리에만 두고 cleanup/heartbeat 만 담당합니다. */
@Component
public class NotificationSseBroker {

  private static final Logger log = LoggerFactory.getLogger(NotificationSseBroker.class);

  private final NotificationSseProperties notificationSseProperties;
  private final NotificationSseTargetResolver notificationSseTargetResolver;
  private final NotificationQueryUseCase notificationQueryUseCase;
  private final Map<Long, Map<String, NotificationSseSession>> accountSessions =
      new ConcurrentHashMap<>();
  private final Map<Long, Map<String, NotificationSseSession>> userSessions =
      new ConcurrentHashMap<>();

  public NotificationSseBroker(
      NotificationSseProperties notificationSseProperties,
      NotificationSseTargetResolver notificationSseTargetResolver,
      NotificationQueryUseCase notificationQueryUseCase) {
    this.notificationSseProperties = notificationSseProperties;
    this.notificationSseTargetResolver = notificationSseTargetResolver;
    this.notificationQueryUseCase = notificationQueryUseCase;
  }

  public SseEmitter subscribeAccount(long accountId, String subject) {
    return subscribeAccount(accountId, subject, null);
  }

  public SseEmitter subscribeAccount(long accountId, String subject, Long lastEventId) {
    return subscribe(
        accountSessions,
        accountId,
        subject,
        "account",
        lastEventId,
        replayLastEventId ->
            notificationQueryUseCase.getReplayNotificationsForAccount(
                accountId,
                new NotificationReplayQuery(
                    replayLastEventId, notificationSseProperties.replayLimit())));
  }

  public SseEmitter subscribeUser(long userId, String subject) {
    return subscribeUser(userId, subject, null);
  }

  public SseEmitter subscribeUser(long userId, String subject, Long lastEventId) {
    return subscribe(
        userSessions,
        userId,
        subject,
        "user",
        lastEventId,
        replayLastEventId ->
            notificationQueryUseCase.getReplayNotificationsForUser(
                userId,
                new NotificationReplayQuery(
                    replayLastEventId, notificationSseProperties.replayLimit())));
  }

  @Scheduled(fixedDelayString = "${notification.sse.heartbeat-interval-ms:25000}")
  void sendHeartbeats() {
    sendHeartbeats(accountSessions, "account");
    sendHeartbeats(userSessions, "user");
  }

  @EventListener
  public void handleNotificationInboxInserted(NotificationInboxInsertedEvent event) {
    publishInsertedItems(event.items());
  }

  public void publishInsertedItems(List<NotificationSummary> items) {
    if (accountSessions.isEmpty() && userSessions.isEmpty()) {
      return;
    }
    Map<Long, List<NotificationSummary>> itemsByAccountId = groupByAccountId(items);
    for (Map.Entry<Long, List<NotificationSummary>> entry : itemsByAccountId.entrySet()) {
      long accountId = entry.getKey();
      List<NotificationSummary> accountItems = entry.getValue();
      publishToAccountSessions(accountId, accountItems);
      publishToUserSessions(accountId, accountItems);
    }
  }

  private SseEmitter subscribe(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String subject,
      String principalType,
      Long lastEventId,
      LongFunction<List<NotificationSummary>> replayLoader) {
    String sessionId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(notificationSseProperties.connectionTimeoutMs());
    NotificationSseSession session =
        new NotificationSseSession(sessionId, subject, emitter, lastEventId);
    sessionsByPrincipalId
        .computeIfAbsent(principalId, ignored -> new ConcurrentHashMap<>())
        .put(sessionId, session);
    emitter.onCompletion(() -> removeSession(sessionsByPrincipalId, principalId, sessionId));
    emitter.onTimeout(() -> removeSession(sessionsByPrincipalId, principalId, sessionId));
    emitter.onError(error -> removeSession(sessionsByPrincipalId, principalId, sessionId));
    scheduleConnectedEvent(
        sessionsByPrincipalId, principalId, principalType, session, lastEventId, replayLoader);
    log.debug(
        "notification SSE subscribed principalType={} principalId={} sessionId={}",
        principalType,
        principalId,
        sessionId);
    return emitter;
  }

  private void sendConnectedEvent(NotificationSseSession session) throws IOException {
    synchronized (session.monitor()) {
      session
          .emitter()
          .send(
              SseEmitter.event()
                  .name("connected")
                  .reconnectTime(notificationSseProperties.reconnectDelayMs())
                  .data(new NotificationStreamConnectedResponse(Instant.now())));
    }
  }

  private void scheduleConnectedEvent(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String principalType,
      NotificationSseSession session,
      Long lastEventId,
      LongFunction<List<NotificationSummary>> replayLoader) {
    Thread.startVirtualThread(
        () -> {
          try {
            sendConnectedEvent(session);
            replayMissedNotifications(session, lastEventId, replayLoader);
          } catch (Exception ex) {
            log.debug(
                "notification SSE subscribe bootstrap failed principalType={} principalId={} sessionId={} subject={}",
                principalType,
                principalId,
                session.sessionId(),
                session.subject(),
                ex);
            removeSession(sessionsByPrincipalId, principalId, session.sessionId());
          }
        });
  }

  private void sendHeartbeats(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId, String principalType) {
    if (sessionsByPrincipalId.isEmpty()) {
      return;
    }
    for (Map.Entry<Long, Map<String, NotificationSseSession>> entry :
        sessionsByPrincipalId.entrySet()) {
      long principalId = entry.getKey();
      for (NotificationSseSession session : entry.getValue().values()) {
        try {
          synchronized (session.monitor()) {
            session
                .emitter()
                .send(
                    SseEmitter.event()
                        .name("heartbeat")
                        .data(new NotificationStreamHeartbeatResponse(Instant.now())));
          }
        } catch (IOException ex) {
          log.debug(
              "notification SSE heartbeat failed principalType={} principalId={} sessionId={}",
              principalType,
              principalId,
              session.sessionId(),
              ex);
          removeSession(sessionsByPrincipalId, principalId, session.sessionId());
        }
      }
    }
  }

  private void publishToAccountSessions(long accountId, List<NotificationSummary> items) {
    Map<String, NotificationSseSession> sessions = accountSessions.get(accountId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }
    for (NotificationSummary item : items) {
      for (NotificationSseSession session : sessions.values()) {
        try {
          queueOrSendNotification(session, item);
        } catch (IOException ex) {
          log.debug(
              "notification SSE push failed principalType=account accountId={} sessionId={} subject={}",
              accountId,
              session.sessionId(),
              session.subject(),
              ex);
          removeSession(accountSessions, accountId, session.sessionId());
        }
      }
    }
  }

  private void publishToUserSessions(long accountId, List<NotificationSummary> items) {
    if (userSessions.isEmpty()) {
      return;
    }
    List<Long> userIds = notificationSseTargetResolver.findActiveUserIdsByAccountId(accountId);
    if (userIds.isEmpty()) {
      return;
    }
    for (Long userId : userIds) {
      Map<String, NotificationSseSession> sessions = userSessions.get(userId);
      if (sessions == null || sessions.isEmpty()) {
        continue;
      }
      for (NotificationSummary item : items) {
        for (NotificationSseSession session : sessions.values()) {
          try {
            queueOrSendNotification(session, item);
          } catch (IOException ex) {
            log.debug(
                "notification SSE push failed principalType=user userId={} sessionId={} subject={}",
                userId,
                session.sessionId(),
                session.subject(),
                ex);
            removeSession(userSessions, userId, session.sessionId());
          }
        }
      }
    }
  }

  private void replayMissedNotifications(
      NotificationSseSession session,
      Long lastEventId,
      LongFunction<List<NotificationSummary>> replayLoader)
      throws IOException {
    if (lastEventId == null) {
      return;
    }
    List<NotificationSummary> replayItems = replayLoader.apply(lastEventId);
    synchronized (session.monitor()) {
      // replay 중 새 live event는 pendingItems 에 모았다가 같은 세션 lock 안에서 이어 보냅니다.
      for (NotificationSummary item : replayItems) {
        sendNotification(session, item);
      }
      flushPendingNotifications(session);
      session.finishReplay();
    }
  }

  private void queueOrSendNotification(NotificationSseSession session, NotificationSummary item)
      throws IOException {
    synchronized (session.monitor()) {
      if (item.id() <= session.lastDeliveredEventId()) {
        return;
      }
      if (session.isReplaying()) {
        session.addPendingItem(item);
        return;
      }
      sendNotification(session, item);
    }
  }

  private void flushPendingNotifications(NotificationSseSession session) throws IOException {
    session.pendingItems().sort(Comparator.comparingLong(NotificationSummary::id));
    for (NotificationSummary pendingItem : session.pendingItems()) {
      sendNotification(session, pendingItem);
    }
    session.pendingItems().clear();
  }

  private void sendNotification(NotificationSseSession session, NotificationSummary item)
      throws IOException {
    if (item.id() <= session.lastDeliveredEventId()) {
      return;
    }
    session
        .emitter()
        .send(
            SseEmitter.event()
                .id(Long.toString(item.id()))
                .name("notification")
                .data(NotificationItemResponse.from(item)));
    session.markDelivered(item.id());
  }

  private Map<Long, List<NotificationSummary>> groupByAccountId(List<NotificationSummary> items) {
    Map<Long, List<NotificationSummary>> itemsByAccountId = new LinkedHashMap<>();
    for (NotificationSummary item : items) {
      itemsByAccountId
          .computeIfAbsent(item.accountId(), ignored -> new java.util.ArrayList<>())
          .add(item);
    }
    return itemsByAccountId;
  }

  private void removeSession(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String sessionId) {
    Map<String, NotificationSseSession> sessions = sessionsByPrincipalId.get(principalId);
    if (sessions == null) {
      return;
    }
    sessions.remove(sessionId);
    if (sessions.isEmpty()) {
      sessionsByPrincipalId.remove(principalId, sessions);
    }
  }

  private static final class NotificationSseSession {

    private final String sessionId;
    private final String subject;
    private final SseEmitter emitter;
    private final Object monitor = new Object();
    private final List<NotificationSummary> pendingItems = new ArrayList<>();
    private long lastDeliveredEventId;
    private boolean replaying;

    private NotificationSseSession(
        String sessionId, String subject, SseEmitter emitter, Long lastEventId) {
      this.sessionId = sessionId;
      this.subject = subject;
      this.emitter = emitter;
      this.lastDeliveredEventId = lastEventId == null ? 0L : lastEventId;
      this.replaying = lastEventId != null;
    }

    private String sessionId() {
      return sessionId;
    }

    private String subject() {
      return subject;
    }

    private SseEmitter emitter() {
      return emitter;
    }

    private Object monitor() {
      return monitor;
    }

    private List<NotificationSummary> pendingItems() {
      return pendingItems;
    }

    private long lastDeliveredEventId() {
      return lastDeliveredEventId;
    }

    private boolean isReplaying() {
      return replaying;
    }

    private void addPendingItem(NotificationSummary item) {
      pendingItems.add(item);
    }

    private void markDelivered(long notificationId) {
      lastDeliveredEventId = notificationId;
    }

    private void finishReplay() {
      replaying = false;
    }
  }
}
