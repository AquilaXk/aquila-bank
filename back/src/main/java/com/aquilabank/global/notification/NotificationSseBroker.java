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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE emitter registry는 app instance local 메모리에만 두고 cleanup/heartbeat 만 담당합니다. */
@Component
public class NotificationSseBroker {

  private static final Logger log = LoggerFactory.getLogger(NotificationSseBroker.class);
  private static final String OVERLOAD_MESSAGE =
      "notification SSE stream is temporarily overloaded";
  private static final String TOTAL_SESSION_LIMIT_REASON = "total_session_limit";
  private static final String USER_SESSION_LIMIT_REASON = "user_session_limit";

  private final NotificationSseProperties notificationSseProperties;
  private final NotificationSseTargetResolver notificationSseTargetResolver;
  private final NotificationQueryUseCase notificationQueryUseCase;
  private final LongFunction<SseEmitter> sseEmitterFactory;
  private final Map<Long, Map<String, NotificationSseSession>> accountSessions =
      new ConcurrentHashMap<>();
  private final Map<Long, Map<String, NotificationSseSession>> userSessions =
      new ConcurrentHashMap<>();
  private final AtomicInteger activeSessionCount = new AtomicInteger();
  private final AtomicLong rejectedSubscriptionCount = new AtomicLong();
  private final AtomicLong backpressureDropCount = new AtomicLong();

  @Autowired
  public NotificationSseBroker(
      NotificationSseProperties notificationSseProperties,
      NotificationSseTargetResolver notificationSseTargetResolver,
      NotificationQueryUseCase notificationQueryUseCase) {
    this(
        notificationSseProperties,
        notificationSseTargetResolver,
        notificationQueryUseCase,
        SseEmitter::new);
  }

  NotificationSseBroker(
      NotificationSseProperties notificationSseProperties,
      NotificationSseTargetResolver notificationSseTargetResolver,
      NotificationQueryUseCase notificationQueryUseCase,
      LongFunction<SseEmitter> sseEmitterFactory) {
    this.notificationSseProperties = notificationSseProperties;
    this.notificationSseTargetResolver = notificationSseTargetResolver;
    this.notificationQueryUseCase = notificationQueryUseCase;
    this.sseEmitterFactory = sseEmitterFactory;
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
        0,
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
        notificationSseProperties.maxUserSessions(),
        replayLastEventId ->
            notificationQueryUseCase.getReplayNotificationsForUser(
                userId,
                new NotificationReplayQuery(
                    replayLastEventId, notificationSseProperties.replayLimit())));
  }

  public boolean hasActiveSessions() {
    return activeSessionCount.get() > 0;
  }

  public int accountSessionCount() {
    return sessionCount(accountSessions);
  }

  public int userSessionCount() {
    return sessionCount(userSessions);
  }

  public int totalSessionCount() {
    return activeSessionCount.get();
  }

  long rejectedSubscriptionCount() {
    return rejectedSubscriptionCount.get();
  }

  long backpressureDropCount() {
    return backpressureDropCount.get();
  }

  public void publishInsertedItems(List<NotificationSummary> items) {
    if (items.isEmpty() || !hasActiveSessions()) {
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

  @Scheduled(fixedDelayString = "${notification.sse.heartbeat-interval-ms:25000}")
  void sendHeartbeats() {
    sendHeartbeats(accountSessions, "account");
    sendHeartbeats(userSessions, "user");
  }

  private SseEmitter subscribe(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String subject,
      String principalType,
      Long lastEventId,
      int maxPrincipalSessions,
      LongFunction<List<NotificationSummary>> replayLoader) {
    if (!reserveSessionSlot()) {
      rejectSubscription(
          sessionsByPrincipalId,
          principalId,
          principalType,
          TOTAL_SESSION_LIMIT_REASON,
          notificationSseProperties.maxTotalSessions());
      throw new NotificationSseOverloadException(OVERLOAD_MESSAGE);
    }
    String sessionId = UUID.randomUUID().toString();
    boolean registered = false;
    try {
      SseEmitter emitter = sseEmitterFactory.apply(notificationSseProperties.connectionTimeoutMs());
      NotificationSseSession session =
          new NotificationSseSession(sessionId, subject, emitter, lastEventId);
      if (!registerSession(sessionsByPrincipalId, principalId, session, maxPrincipalSessions)) {
        releaseSessionSlot();
        rejectSubscription(
            sessionsByPrincipalId,
            principalId,
            principalType,
            USER_SESSION_LIMIT_REASON,
            maxPrincipalSessions);
        throw new NotificationSseOverloadException(OVERLOAD_MESSAGE);
      }
      registered = true;
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
    } catch (RuntimeException ex) {
      if (registered) {
        removeSession(sessionsByPrincipalId, principalId, sessionId);
      } else {
        releaseSessionSlot();
      }
      throw ex;
    }
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
            closeSession(sessionsByPrincipalId, principalId, session);
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
          closeSession(sessionsByPrincipalId, principalId, session);
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
        } catch (NotificationSseSessionBackpressureException ex) {
          dropSessionDueToBackpressure(
              accountSessions,
              accountId,
              "account",
              session,
              notificationSseProperties.maxPendingEventsPerSession());
        } catch (IOException ex) {
          log.debug(
              "notification SSE push failed principalType=account accountId={} sessionId={} subject={}",
              accountId,
              session.sessionId(),
              session.subject(),
              ex);
          closeSession(accountSessions, accountId, session);
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
          } catch (NotificationSseSessionBackpressureException ex) {
            dropSessionDueToBackpressure(
                userSessions,
                userId,
                "user",
                session,
                notificationSseProperties.maxPendingEventsPerSession());
          } catch (IOException ex) {
            log.debug(
                "notification SSE push failed principalType=user userId={} sessionId={} subject={}",
                userId,
                session.sessionId(),
                session.subject(),
                ex);
            closeSession(userSessions, userId, session);
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
      if (session.isClosed()) {
        return;
      }
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
      if (session.isClosed()) {
        return;
      }
      if (item.id() <= session.lastDeliveredEventId()) {
        return;
      }
      if (session.isReplaying()) {
        // replay gap 동안 live event를 무제한 적재하면 heap이 커지므로 session 단위로 끊습니다.
        if (session.pendingItemCount() >= notificationSseProperties.maxPendingEventsPerSession()) {
          throw new NotificationSseSessionBackpressureException();
        }
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
    if (session.isClosed()) {
      return;
    }
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

  private int sessionCount(Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId) {
    int count = 0;
    for (Map<String, NotificationSseSession> sessions : sessionsByPrincipalId.values()) {
      count += sessions.size();
    }
    return count;
  }

  private int sessionCount(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId, long principalId) {
    Map<String, NotificationSseSession> sessions = sessionsByPrincipalId.get(principalId);
    return sessions == null ? 0 : sessions.size();
  }

  private void rejectSubscription(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String principalType,
      String reason,
      int limit) {
    long rejectedCount = rejectedSubscriptionCount.incrementAndGet();
    log.warn(
        "notification SSE subscribe rejected principalType={} principalId={} reason={} activeSessions={} principalSessions={} limit={} rejectedCount={}",
        principalType,
        principalId,
        reason,
        activeSessionCount.get(),
        sessionCount(sessionsByPrincipalId, principalId),
        limit,
        rejectedCount);
  }

  private boolean registerSession(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      NotificationSseSession session,
      int maxPrincipalSessions) {
    AtomicBoolean registered = new AtomicBoolean(false);
    sessionsByPrincipalId.compute(
        principalId,
        (ignored, currentSessions) -> {
          if (maxPrincipalSessions > 0
              && currentSessions != null
              && currentSessions.size() >= maxPrincipalSessions) {
            return currentSessions;
          }
          // 동일 user reconnect burst가 전체 cap을 잠식하지 않게 user registry 안에서 등록을 직렬화합니다.
          Map<String, NotificationSseSession> nextSessions =
              currentSessions == null ? new ConcurrentHashMap<>() : currentSessions;
          nextSessions.put(session.sessionId(), session);
          registered.set(true);
          return nextSessions;
        });
    return registered.get();
  }

  private boolean reserveSessionSlot() {
    int limit = notificationSseProperties.maxTotalSessions();
    while (true) {
      int current = activeSessionCount.get();
      if (current >= limit) {
        return false;
      }
      if (activeSessionCount.compareAndSet(current, current + 1)) {
        return true;
      }
    }
  }

  private void releaseSessionSlot() {
    activeSessionCount.updateAndGet(current -> current > 0 ? current - 1 : 0);
  }

  private void dropSessionDueToBackpressure(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String principalType,
      NotificationSseSession session,
      int pendingLimit) {
    if (!closeSession(sessionsByPrincipalId, principalId, session)) {
      return;
    }
    long droppedCount = backpressureDropCount.incrementAndGet();
    log.warn(
        "notification SSE session dropped due to pending overflow principalType={} principalId={} sessionId={} subject={} pendingLimit={} droppedCount={}",
        principalType,
        principalId,
        session.sessionId(),
        session.subject(),
        pendingLimit,
        droppedCount);
  }

  private boolean closeSession(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      NotificationSseSession session) {
    boolean removed = removeSession(sessionsByPrincipalId, principalId, session.sessionId());
    if (removed) {
      session.complete();
    }
    return removed;
  }

  private boolean removeSession(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String sessionId) {
    Map<String, NotificationSseSession> sessions = sessionsByPrincipalId.get(principalId);
    if (sessions == null) {
      return false;
    }
    NotificationSseSession removedSession = sessions.remove(sessionId);
    if (removedSession == null) {
      return false;
    }
    removedSession.markClosed();
    releaseSessionSlot();
    if (sessions.isEmpty()) {
      sessionsByPrincipalId.remove(principalId, sessions);
    }
    return true;
  }

  private static final class NotificationSseSession {

    private final String sessionId;
    private final String subject;
    private final SseEmitter emitter;
    private final Object monitor = new Object();
    private final List<NotificationSummary> pendingItems = new ArrayList<>();
    private long lastDeliveredEventId;
    private boolean replaying;
    private volatile boolean closed;

    private NotificationSseSession(
        String sessionId, String subject, SseEmitter emitter, Long lastEventId) {
      this.sessionId = sessionId;
      this.subject = subject;
      this.emitter = emitter;
      this.lastDeliveredEventId = lastEventId == null ? 0L : lastEventId;
      this.replaying = lastEventId != null;
      this.closed = false;
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

    private boolean isClosed() {
      return closed;
    }

    private void addPendingItem(NotificationSummary item) {
      pendingItems.add(item);
    }

    private int pendingItemCount() {
      return pendingItems.size();
    }

    private void markDelivered(long notificationId) {
      lastDeliveredEventId = notificationId;
    }

    private void finishReplay() {
      replaying = false;
    }

    private void markClosed() {
      closed = true;
    }

    private void complete() {
      emitter.complete();
    }
  }

  private static final class NotificationSseSessionBackpressureException extends RuntimeException {}
}
