package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationReplayQuery;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.global.config.NotificationSseProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

class NotificationSseBrokerTest {

  private NotificationQueryUseCase notificationQueryUseCase;
  private NotificationSseTargetResolver notificationSseTargetResolver;

  @BeforeEach
  void setUp() {
    notificationQueryUseCase = mock(NotificationQueryUseCase.class);
    notificationSseTargetResolver = mock(NotificationSseTargetResolver.class);
    when(notificationQueryUseCase.getReplayNotificationsForAccount(anyLong(), any()))
        .thenReturn(List.of());
    when(notificationQueryUseCase.getReplayNotificationsForUser(anyLong(), any()))
        .thenReturn(List.of());
    when(notificationSseTargetResolver.findActiveUserIdsByAccountId(anyLong()))
        .thenReturn(List.of());
  }

  @Test
  void rejectsSubscriptionWhenTotalSessionLimitIsReached() throws Exception {
    NotificationSseBroker notificationSseBroker = createBroker(1, 1, 2);

    SseEmitter firstEmitter = notificationSseBroker.subscribeAccount(101L, "first-session");

    assertThat(notificationSseBroker.totalSessionCount()).isEqualTo(1);
    assertThatThrownBy(() -> notificationSseBroker.subscribeUser(202L, "second-session"))
        .isInstanceOf(NotificationSseOverloadException.class)
        .hasMessage("notification SSE stream is temporarily overloaded");
    assertThat(notificationSseBroker.rejectedSubscriptionCount()).isEqualTo(1L);
    firstEmitter.complete();
  }

  @Test
  void fansOutNotificationsToAccountAndActiveUserSessionsUnderLoad() throws Exception {
    when(notificationSseTargetResolver.findActiveUserIdsByAccountId(101L))
        .thenReturn(List.of(201L, 202L));
    RecordingSseEmitterFactory emitterFactory = new RecordingSseEmitterFactory();
    NotificationSseBroker notificationSseBroker = createBroker(8, 4, 8, emitterFactory);
    notificationSseBroker.subscribeAccount(101L, "account-a");
    notificationSseBroker.subscribeAccount(101L, "account-b");
    notificationSseBroker.subscribeUser(201L, "user-a");
    notificationSseBroker.subscribeUser(202L, "user-b");
    List<NotificationSummary> items = new ArrayList<>();
    List<Long> expectedIds = new ArrayList<>();
    for (long index = 0; index < 25; index++) {
      long notificationId = 1_000L + index;
      items.add(notification(notificationId, 101L, "load-" + index));
      expectedIds.add(notificationId);
    }

    notificationSseBroker.publishInsertedItems(items);

    List<RecordingSseEmitter> emitters = emitterFactory.emitters();
    awaitCondition(
        () ->
            emitters.stream()
                .allMatch(emitter -> emitter.notificationIds().size() == expectedIds.size()),
        Duration.ofSeconds(2));
    assertThat(emitters).hasSize(4);
    assertThat(emitters)
        .allSatisfy(
            emitter ->
                assertThat(emitter.notificationIds()).containsExactlyElementsOf(expectedIds));
    assertThat(notificationSseBroker.totalSessionCount()).isEqualTo(4);
    assertThat(notificationSseBroker.backpressureDropCount()).isZero();
  }

  @Test
  void isolatesEmitterFailureDuringFanoutBatch() throws Exception {
    RecordingSseEmitterFactory emitterFactory = new RecordingSseEmitterFactory(true, false);
    NotificationSseBroker notificationSseBroker = createBroker(4, 2, 4, emitterFactory);
    notificationSseBroker.subscribeAccount(101L, "broken-account-session");
    notificationSseBroker.subscribeAccount(101L, "healthy-account-session");
    List<NotificationSummary> items =
        List.of(notification(11L, 101L, "one"), notification(12L, 101L, "two"));

    notificationSseBroker.publishInsertedItems(items);

    RecordingSseEmitter brokenEmitter = emitterFactory.emitter(0);
    RecordingSseEmitter healthyEmitter = emitterFactory.emitter(1);
    awaitCondition(() -> healthyEmitter.notificationIds().size() == 2, Duration.ofSeconds(2));
    awaitCondition(() -> notificationSseBroker.totalSessionCount() == 1, Duration.ofSeconds(2));
    assertThat(healthyEmitter.notificationIds()).containsExactly(11L, 12L);
    assertThat(brokenEmitter.completedCount()).isEqualTo(1);
    assertThat(notificationSseBroker.accountSessionCount()).isEqualTo(1);
    assertThat(notificationSseBroker.backpressureDropCount()).isZero();
  }

  @Test
  void dropsReplayingSessionWhenPendingQueueLimitIsExceeded() throws Exception {
    CountDownLatch replayEntered = new CountDownLatch(1);
    CountDownLatch releaseReplay = new CountDownLatch(1);
    when(notificationQueryUseCase.getReplayNotificationsForAccount(
            eq(101L), any(NotificationReplayQuery.class)))
        .thenAnswer(
            ignored -> {
              replayEntered.countDown();
              assertThat(releaseReplay.await(3, TimeUnit.SECONDS)).isTrue();
              return List.of();
            });
    NotificationSseBroker notificationSseBroker = createBroker(4, 2, 2);

    notificationSseBroker.subscribeAccount(101L, "replay-session", 10L);
    assertThat(replayEntered.await(3, TimeUnit.SECONDS)).isTrue();

    notificationSseBroker.publishInsertedItems(
        List.of(
            notification(11L, 101L, "one"),
            notification(12L, 101L, "two"),
            notification(13L, 101L, "three")));

    awaitCondition(() -> notificationSseBroker.totalSessionCount() == 0, Duration.ofSeconds(2));
    assertThat(notificationSseBroker.backpressureDropCount()).isEqualTo(1L);

    releaseReplay.countDown();
  }

  @Test
  void rejectsSubscriptionWhenUserSessionLimitIsReached() throws Exception {
    NotificationSseBroker notificationSseBroker = createBroker(4, 2, 2);

    SseEmitter firstEmitter = notificationSseBroker.subscribeUser(202L, "first-user-session");
    SseEmitter secondEmitter = notificationSseBroker.subscribeUser(202L, "second-user-session");

    assertThatThrownBy(() -> notificationSseBroker.subscribeUser(202L, "third-user-session"))
        .isInstanceOf(NotificationSseOverloadException.class)
        .hasMessage("notification SSE stream is temporarily overloaded");
    assertThat(notificationSseBroker.rejectedSubscriptionCount()).isEqualTo(1L);

    SseEmitter otherUserEmitter = notificationSseBroker.subscribeUser(303L, "other-user-session");
    assertThat(otherUserEmitter).isNotNull();

    firstEmitter.complete();
    secondEmitter.complete();
    otherUserEmitter.complete();
  }

  @Test
  void rejectsConfigurationWhenUserSessionLimitExceedsTotalLimit() {
    assertThatThrownBy(
            () ->
                new NotificationSseProperties(
                    60_000L,
                    10_000L,
                    3_000L,
                    100,
                    4,
                    5,
                    32,
                    "notification_sse_fanout",
                    1_000L,
                    2_000L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "notification.sse.max-user-sessions must not exceed notification.sse.max-total-sessions");
  }

  private NotificationSseBroker createBroker(
      int maxTotalSessions, int maxUserSessions, int maxPendingEventsPerSession) {
    return createBroker(
        maxTotalSessions, maxUserSessions, maxPendingEventsPerSession, SseEmitter::new);
  }

  private NotificationSseBroker createBroker(
      int maxTotalSessions,
      int maxUserSessions,
      int maxPendingEventsPerSession,
      LongFunction<SseEmitter> emitterFactory) {
    return new NotificationSseBroker(
        new NotificationSseProperties(
            60_000L,
            10_000L,
            3_000L,
            100,
            maxTotalSessions,
            maxUserSessions,
            maxPendingEventsPerSession,
            "notification_sse_fanout",
            1_000L,
            2_000L),
        notificationSseTargetResolver,
        notificationQueryUseCase,
        emitterFactory);
  }

  private NotificationSummary notification(long id, long accountId, String suffix) {
    return new NotificationSummary(
        id,
        accountId,
        "TransferBooked",
        "이체 완료",
        "message-" + suffix,
        Instant.parse("2026-04-18T06:30:00Z"),
        null);
  }

  private void awaitCondition(BooleanSupplier condition, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(20L);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  private static final class RecordingSseEmitterFactory implements LongFunction<SseEmitter> {

    private final Queue<Boolean> failurePlan = new ArrayDeque<>();
    private final List<RecordingSseEmitter> emitters = new CopyOnWriteArrayList<>();

    private RecordingSseEmitterFactory(boolean... failNotificationEvents) {
      for (boolean failNotificationEvent : failNotificationEvents) {
        failurePlan.add(failNotificationEvent);
      }
    }

    @Override
    public SseEmitter apply(long timeoutMs) {
      Boolean failNotificationEvent = failurePlan.poll();
      RecordingSseEmitter emitter =
          new RecordingSseEmitter(timeoutMs, Boolean.TRUE.equals(failNotificationEvent));
      emitters.add(emitter);
      return emitter;
    }

    private List<RecordingSseEmitter> emitters() {
      return List.copyOf(emitters);
    }

    private RecordingSseEmitter emitter(int index) {
      return emitters.get(index);
    }
  }

  private static final class RecordingSseEmitter extends SseEmitter {

    private final boolean failNotificationEvents;
    private final List<RecordedSseEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger completedCount = new AtomicInteger();

    private RecordingSseEmitter(long timeoutMs, boolean failNotificationEvents) {
      super(timeoutMs);
      this.failNotificationEvents = failNotificationEvents;
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
      RecordedSseEvent event = RecordedSseEvent.from(builder);
      if (failNotificationEvents && "notification".equals(event.name())) {
        throw new IOException("injected SSE send failure");
      }
      events.add(event);
    }

    @Override
    public void complete() {
      completedCount.incrementAndGet();
      super.complete();
    }

    private List<Long> notificationIds() {
      return events.stream()
          .filter(event -> "notification".equals(event.name()))
          .map(event -> Long.parseLong(event.id()))
          .toList();
    }

    private int completedCount() {
      return completedCount.get();
    }
  }

  private record RecordedSseEvent(String name, String id, Object data) {

    private static RecordedSseEvent from(SseEventBuilder builder) {
      String name = null;
      String id = null;
      Object data = null;
      for (DataWithMediaType item : builder.build()) {
        Object value = item.getData();
        if (value instanceof String text) {
          for (String line : text.split("\\n")) {
            if (line.startsWith("event:")) {
              name = line.substring("event:".length());
            } else if (line.startsWith("id:")) {
              id = line.substring("id:".length());
            }
          }
        } else {
          data = value;
        }
      }
      return new RecordedSseEvent(name, id, data);
    }
  }
}
