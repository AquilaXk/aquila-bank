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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    NotificationSseBroker notificationSseBroker = createBroker(1, 2);

    SseEmitter firstEmitter = notificationSseBroker.subscribeAccount(101L, "first-session");

    assertThat(notificationSseBroker.totalSessionCount()).isEqualTo(1);
    assertThatThrownBy(() -> notificationSseBroker.subscribeUser(202L, "second-session"))
        .isInstanceOf(NotificationSseOverloadException.class)
        .hasMessage("notification SSE stream is temporarily overloaded");
    assertThat(notificationSseBroker.rejectedSubscriptionCount()).isEqualTo(1L);
    firstEmitter.complete();
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
    NotificationSseBroker notificationSseBroker = createBroker(4, 2);

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

  private NotificationSseBroker createBroker(int maxTotalSessions, int maxPendingEventsPerSession) {
    return new NotificationSseBroker(
        new NotificationSseProperties(
            60_000L,
            10_000L,
            3_000L,
            100,
            maxTotalSessions,
            maxPendingEventsPerSession,
            "notification_sse_fanout",
            1_000L,
            2_000L),
        notificationSseTargetResolver,
        notificationQueryUseCase);
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
}
