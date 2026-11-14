package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationReplayQuery;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.global.config.NotificationSseProperties;
import com.aquilabank.global.web.notification.NotificationQueryResponse.NotificationItemResponse;
import com.aquilabank.global.web.notification.NotificationStreamConnectedResponse;
import com.aquilabank.global.web.notification.NotificationStreamHeartbeatResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
  private final ObjectMapper objectMapper;
  private final Map<Long, Map<String, NotificationSseSession>> accountSessions =
      new ConcurrentHashMap<>();
  private final Map<Long, Map<String, NotificationSseSession>> userSessions =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, List<Long>> userSubscribedAccounts =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Set<Long>> subscribedUsersByAccountId =
      new ConcurrentHashMap<>();
  private final Object userAccountIndexLock = new Object();
  private final AtomicInteger activeSessionCount = new AtomicInteger();
  private final AtomicLong rejectedSubscriptionCount = new AtomicLong();
  private final AtomicLong backpressureDropCount = new AtomicLong();

  @Autowired
  public NotificationSseBroker(
      NotificationSseProperties notificationSseProperties,
      NotificationSseTargetResolver notificationSseTargetResolver,
      NotificationQueryUseCase notificationQueryUseCase,
      ObjectMapper objectMapper) {
    this(
        notificationSseProperties,
        notificationSseTargetResolver,
        notificationQueryUseCase,
        SseEmitter::new,
        objectMapper);
  }

  NotificationSseBroker(
      NotificationSseProperties notificationSseProperties,
      NotificationSseTargetResolver notificationSseTargetResolver,
      NotificationQueryUseCase notificationQueryUseCase,
      LongFunction<SseEmitter> sseEmitterFactory) {
    this(
        notificationSseProperties,
        notificationSseTargetResolver,
        notificationQueryUseCase,
        sseEmitterFactory,
        defaultObjectMapper());
  }

  NotificationSseBroker(
      NotificationSseProperties notificationSseProperties,
      NotificationSseTargetResolver notificationSseTargetResolver,
      NotificationQueryUseCase notificationQueryUseCase,
      LongFunction<SseEmitter> sseEmitterFactory,
      ObjectMapper objectMapper) {
    this.notificationSseProperties = notificationSseProperties;
    this.notificationSseTargetResolver = notificationSseTargetResolver;
    this.notificationQueryUseCase = notificationQueryUseCase;
    this.sseEmitterFactory = sseEmitterFactory;
    this.objectMapper = objectMapper;
  }

  private static ObjectMapper defaultObjectMapper() {
    return JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .findAndAddModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
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
        notificationSseProperties.maxUserSessions(),
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
      Map<String, NotificationSseSession> directAccountSessions = accountSessions.get(accountId);
      List<UserSessionGroup> targetUserGroups = resolveTargetUserSessions(accountId);
      boolean hasAccountTargets = directAccountSessions != null && !directAccountSessions.isEmpty();
      if (!hasAccountTargets && targetUserGroups.isEmpty()) {
        continue;
      }
      // 로컬 구독 대상이 확인된 계좌에만 항목별 JSON 직렬화를 1회 수행해 팬아웃 전체에서 재사용합니다.
      List<PreparedNotification> preparedItems = prepareNotifications(accountItems);
      if (hasAccountTargets) {
        publishPreparedToAccountSessions(accountId, directAccountSessions, preparedItems);
      }
      if (!targetUserGroups.isEmpty()) {
        publishPreparedToUserSessions(targetUserGroups, preparedItems);
      }
    }
  }

  @Scheduled(fixedDelayString = "${notification.sse.heartbeat-interval-ms:25000}")
  void sendHeartbeats() {
    NotificationStreamHeartbeatResponse heartbeat =
        new NotificationStreamHeartbeatResponse(Instant.now());
    sendHeartbeats(accountSessions, "account", heartbeat);
    sendHeartbeats(userSessions, "user", heartbeat);
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
    List<Long> userAccountIds =
        sessionsByPrincipalId == userSessions
            ? resolveUserAccountIdsForSubscription(principalId)
            : List.of();
    String sessionId = UUID.randomUUID().toString();
    boolean registered = false;
    try {
      SseEmitter emitter = sseEmitterFactory.apply(notificationSseProperties.connectionTimeoutMs());
      NotificationSseSession session =
          new NotificationSseSession(sessionId, subject, emitter, lastEventId);
      session.sendLock().lock();
      try {
        if (!registerSession(
            sessionsByPrincipalId, principalId, session, maxPrincipalSessions, userAccountIds)) {
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
        sendConnectedEvent(session);
      } catch (IOException ex) {
        log.debug(
            "notification SSE subscribe bootstrap failed principalType={} principalId={} sessionId={} subject={}",
            principalType,
            principalId,
            session.sessionId(),
            session.subject(),
            ex);
        closeSession(sessionsByPrincipalId, principalId, session);
        return emitter;
      } finally {
        session.sendLock().unlock();
      }
      if (lastEventId != null) {
        scheduleReplay(
            sessionsByPrincipalId, principalId, principalType, session, lastEventId, replayLoader);
      }
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
    session.sendLock().lock();
    try {
      session
          .emitter()
          .send(
              SseEmitter.event()
                  .name("connected")
                  .reconnectTime(notificationSseProperties.reconnectDelayMs())
                  .data(new NotificationStreamConnectedResponse(Instant.now())));
    } finally {
      session.sendLock().unlock();
    }
  }

  private void scheduleReplay(
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      long principalId,
      String principalType,
      NotificationSseSession session,
      Long lastEventId,
      LongFunction<List<NotificationSummary>> replayLoader) {
    Thread.startVirtualThread(
        () -> {
          try {
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
      Map<Long, Map<String, NotificationSseSession>> sessionsByPrincipalId,
      String principalType,
      NotificationStreamHeartbeatResponse heartbeat) {
    if (sessionsByPrincipalId.isEmpty()) {
      return;
    }
    for (Map.Entry<Long, Map<String, NotificationSseSession>> entry :
        sessionsByPrincipalId.entrySet()) {
      long principalId = entry.getKey();
      for (NotificationSseSession session : entry.getValue().values()) {
        try {
          session.sendLock().lock();
          try {
            session.emitter().send(SseEmitter.event().name("heartbeat").data(heartbeat));
          } finally {
            session.sendLock().unlock();
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

  private List<Long> resolveUserAccountIdsForSubscription(long userId) {
    List<Long> cached = userSubscribedAccounts.get(userId);
    if (cached != null) {
      return cached;
    }
    return List.copyOf(notificationSseTargetResolver.findActiveAccountIdsByUserId(userId));
  }

  private List<UserSessionGroup> resolveTargetUserSessions(long accountId) {
    if (userSessions.isEmpty() || !subscribedUsersByAccountId.containsKey(accountId)) {
      return List.of();
    }
    List<Long> userIds = notificationSseTargetResolver.findActiveUserIdsByAccountId(accountId);
    if (userIds.isEmpty()) {
      return List.of();
    }
    List<UserSessionGroup> groups = new ArrayList<>(userIds.size());
    for (Long userId : userIds) {
      Map<String, NotificationSseSession> sessions = userSessions.get(userId);
      if (sessions != null && !sessions.isEmpty()) {
        groups.add(new UserSessionGroup(userId, sessions));
      }
    }
    return groups;
  }

  private List<PreparedNotification> prepareNotifications(List<NotificationSummary> items) {
    List<PreparedNotification> prepared = new ArrayList<>(items.size());
    for (NotificationSummary item : items) {
      prepared.add(new PreparedNotification(item, serializeNotification(item)));
    }
    return prepared;
  }

  private String serializeNotification(NotificationSummary item) {
    try {
      return objectMapper.writeValueAsString(NotificationItemResponse.from(item));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize SSE notification payload", ex);
    }
  }

  private void publishPreparedToAccountSessions(
      long accountId,
      Map<String, NotificationSseSession> sessions,
      List<PreparedNotification> items) {
    for (PreparedNotification item : items) {
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

  private void publishPreparedToUserSessions(
      List<UserSessionGroup> userGroups, List<PreparedNotification> items) {
    for (UserSessionGroup group : userGroups) {
      long userId = group.userId();
      Map<String, NotificationSseSession> sessions = group.sessions();
      for (PreparedNotification item : items) {
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
    session.sendLock().lock();
    try {
      if (session.isClosed()) {
        return;
      }
      // replay 중 새 live event는 pendingItems 에 모았다가 같은 세션 lock 안에서 이어 보냅니다.
      for (NotificationSummary item : replayItems) {
        sendNotification(session, item, null);
      }
      flushPendingNotifications(session);
      session.finishReplay();
    } finally {
      session.sendLock().unlock();
    }
  }

  private void queueOrSendNotification(
      NotificationSseSession session, PreparedNotification prepared) throws IOException {
    NotificationSummary item = prepared.item();
    session.sendLock().lock();
    try {
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
      sendNotification(session, item, prepared.serializedPayload());
    } finally {
      session.sendLock().unlock();
    }
  }

  private void flushPendingNotifications(NotificationSseSession session) throws IOException {
    session.pendingItems().sort(Comparator.comparingLong(NotificationSummary::id));
    for (NotificationSummary pendingItem : session.pendingItems()) {
      sendNotification(session, pendingItem, null);
    }
    session.pendingItems().clear();
  }

  private void sendNotification(
      NotificationSseSession session, NotificationSummary item, String preSerializedPayload)
      throws IOException {
    if (session.isClosed()) {
      return;
    }
    if (item.id() <= session.lastDeliveredEventId()) {
      return;
    }
    String payload =
        preSerializedPayload != null ? preSerializedPayload : serializeNotification(item);
    session
        .emitter()
        .send(
            SseEmitter.event()
                .id(Long.toString(item.id()))
                .name("notification")
                .data(payload, MediaType.TEXT_PLAIN));
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
      int maxPrincipalSessions,
      List<Long> userAccountIds) {
    if (sessionsByPrincipalId == userSessions) {
      synchronized (userAccountIndexLock) {
        if (!putSession(sessionsByPrincipalId, principalId, session, maxPrincipalSessions)) {
          return false;
        }
        indexUserAccounts(principalId, userAccountIds);
        return true;
      }
    }
    return putSession(sessionsByPrincipalId, principalId, session, maxPrincipalSessions);
  }

  private boolean putSession(
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

  private void indexUserAccounts(long userId, List<Long> accountIds) {
    if (userSubscribedAccounts.putIfAbsent(userId, accountIds) == null) {
      for (Long accountId : accountIds) {
        subscribedUsersByAccountId
            .computeIfAbsent(accountId, ignored -> ConcurrentHashMap.newKeySet())
            .add(userId);
      }
    }
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
    AtomicReference<NotificationSseSession> removedRef = new AtomicReference<>();
    sessionsByPrincipalId.computeIfPresent(
        principalId,
        (ignored, currentSessions) -> {
          NotificationSseSession removed = currentSessions.remove(sessionId);
          if (removed != null) {
            removedRef.set(removed);
          }
          return currentSessions.isEmpty() ? null : currentSessions;
        });
    NotificationSseSession removedSession = removedRef.get();
    if (removedSession == null) {
      return false;
    }
    removedSession.markClosed();
    releaseSessionSlot();
    if (sessionsByPrincipalId == userSessions) {
      cleanupUserAccountIndexIfIdle(principalId);
    }
    return true;
  }

  private void cleanupUserAccountIndexIfIdle(long userId) {
    synchronized (userAccountIndexLock) {
      if (userSessions.containsKey(userId)) {
        return;
      }
      List<Long> accountIds = userSubscribedAccounts.remove(userId);
      if (accountIds == null) {
        return;
      }
      for (Long accountId : accountIds) {
        subscribedUsersByAccountId.computeIfPresent(
            accountId,
            (ignored, users) -> {
              users.remove(userId);
              return users.isEmpty() ? null : users;
            });
      }
    }
  }

  private record PreparedNotification(NotificationSummary item, String serializedPayload) {}

  private record UserSessionGroup(long userId, Map<String, NotificationSseSession> sessions) {}

  private static final class NotificationSseSession {

    private final String sessionId;
    private final String subject;
    private final SseEmitter emitter;
    private final ReentrantLock sendLock = new ReentrantLock();
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

    private ReentrantLock sendLock() {
      return sendLock;
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

  private static final class NotificationSseSessionBackpressureException extends RuntimeException {

    private NotificationSseSessionBackpressureException() {
      super("notification SSE session pending queue overflow", null, false, false);
    }
  }
}
