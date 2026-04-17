package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.global.config.NotificationSseProperties;
import com.aquilabank.global.web.notification.NotificationQueryResponse.NotificationItemResponse;
import com.aquilabank.global.web.notification.NotificationStreamConnectedResponse;
import com.aquilabank.global.web.notification.NotificationStreamHeartbeatResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
  private final Map<Long, Map<String, NotificationSseSession>> accountSessions =
      new ConcurrentHashMap<>();
  private final Map<Long, Map<String, NotificationSseSession>> userSessions =
      new ConcurrentHashMap<>();

  public NotificationSseBroker(
      NotificationSseProperties notificationSseProperties,
      NotificationSseTargetResolver notificationSseTargetResolver) {
    this.notificationSseProperties = notificationSseProperties;
    this.notificationSseTargetResolver = notificationSseTargetResolver;
  }

  public SseEmitter subscribeAccount(long accountId, String subject) {
    return subscribe(accountSessions, accountId, subject, "account");
  }

  public SseEmitter subscribeUser(long userId, String subject) {
    return subscribe(userSessions, userId, subject, "user");
  }

  @Scheduled(fixedDelayString = "${notification.sse.heartbeat-interval-ms:25000}")
  void sendHeartbeats() {
    sendHeartbeats(accountSessions, "account");
    sendHeartbeats(userSessions, "user");
  }

  @EventListener
  public void handleNotificationInboxInserted(NotificationInboxInsertedEvent event) {
    if (accountSessions.isEmpty() && userSessions.isEmpty()) {
      return;
    }
    Map<Long, List<NotificationSummary>> itemsByAccountId = groupByAccountId(event.items());
    for (Map.Entry<Long, List<NotificationSummary>> entry : itemsByAccountId.entrySet()) {
      long accountId = entry.getKey();
      List<NotificationSummary> items = entry.getValue();
      publishToAccountSessions(accountId, items);
      publishToUserSessions(accountId, items);
    }
  }

  private SseEmitter subscribe(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String subject,
      String principalType) {
    String sessionId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(notificationSseProperties.connectionTimeoutMs());
    NotificationSseSession session = new NotificationSseSession(sessionId, subject, emitter);
    sessionsByPrincipalId
        .computeIfAbsent(principalId, ignored -> new ConcurrentHashMap<>())
        .put(sessionId, session);
    emitter.onCompletion(() -> removeSession(sessionsByPrincipalId, principalId, sessionId));
    emitter.onTimeout(() -> removeSession(sessionsByPrincipalId, principalId, sessionId));
    emitter.onError(error -> removeSession(sessionsByPrincipalId, principalId, sessionId));
    scheduleConnectedEvent(sessionsByPrincipalId, principalId, principalType, session);
    log.debug(
        "notification SSE subscribed principalType={} principalId={} sessionId={}",
        principalType,
        principalId,
        sessionId);
    return emitter;
  }

  private void sendConnectedEvent(NotificationSseSession session) throws IOException {
    session
        .emitter()
        .send(
            SseEmitter.event()
                .name("connected")
                .reconnectTime(notificationSseProperties.reconnectDelayMs())
                .data(new NotificationStreamConnectedResponse(Instant.now())));
  }

  private void scheduleConnectedEvent(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String principalType,
      NotificationSseSession session) {
    Thread.startVirtualThread(
        () -> {
          try {
            sendConnectedEvent(session);
          } catch (IOException ex) {
            log.debug(
                "notification SSE connected event failed principalType={} principalId={} sessionId={} subject={}",
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
          session
              .emitter()
              .send(
                  SseEmitter.event()
                      .name("heartbeat")
                      .data(new NotificationStreamHeartbeatResponse(Instant.now())));
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
      NotificationItemResponse payload = NotificationItemResponse.from(item);
      for (NotificationSseSession session : sessions.values()) {
        try {
          session
              .emitter()
              .send(
                  SseEmitter.event()
                      .id(Long.toString(item.id()))
                      .name("notification")
                      .data(payload));
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
        NotificationItemResponse payload = NotificationItemResponse.from(item);
        for (NotificationSseSession session : sessions.values()) {
          try {
            session
                .emitter()
                .send(
                    SseEmitter.event()
                        .id(Long.toString(item.id()))
                        .name("notification")
                        .data(payload));
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

  private record NotificationSseSession(String sessionId, String subject, SseEmitter emitter) {}
}
