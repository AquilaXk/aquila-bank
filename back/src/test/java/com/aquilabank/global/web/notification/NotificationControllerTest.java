package com.aquilabank.global.web.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.notification.exception.NotificationNotFoundException;
import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationReadUseCase;
import com.aquilabank.global.notification.NotificationSseBroker;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationControllerTest {

  private NotificationQueryUseCase notificationQueryUseCase;
  private NotificationReadUseCase notificationReadUseCase;
  private NotificationSseBroker notificationSseBroker;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    notificationQueryUseCase = mock(NotificationQueryUseCase.class);
    notificationReadUseCase = mock(NotificationReadUseCase.class);
    notificationSseBroker = mock(NotificationSseBroker.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationController(
                    notificationQueryUseCase, notificationReadUseCase, notificationSseBroker))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @Test
  void returnsNotificationSlice() throws Exception {
    NotificationCursor nextCursor =
        new NotificationCursor(Instant.parse("2026-04-17T00:00:00Z"), 9L);
    when(notificationQueryUseCase.getNotificationsForAccount(anyLong(), any()))
        .thenReturn(
            new NotificationSlice(
                List.of(
                    new NotificationSummary(
                        10L,
                        101L,
                        "TransferBooked",
                        "입금 완료",
                        "급여가 입금되었습니다.",
                        Instant.parse("2026-04-17T00:10:00Z"),
                        null)),
                nextCursor,
                true,
                20));

    mockMvc
        .perform(get("/api/v1/notifications").header("X-Account-Id", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].notificationId").value(10L))
        .andExpect(jsonPath("$.items[0].accountId").value(101L))
        .andExpect(jsonPath("$.items[0].title").value("입금 완료"))
        .andExpect(jsonPath("$.items[0].read").value(false))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").isString());
  }

  @Test
  void returnsUnreadCount() throws Exception {
    when(notificationQueryUseCase.getUnreadCountForAccount(101L)).thenReturn(3L);

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("X-Account-Id", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(3L));
  }

  @Test
  void marksNotificationAsRead() throws Exception {
    mockMvc
        .perform(post("/api/v1/notifications/10/read").header("X-Account-Id", "101"))
        .andExpect(status().isNoContent());
  }

  @Test
  void returnsNotFoundWhenNotificationIsMissing() throws Exception {
    doThrow(new NotificationNotFoundException("notification is not found"))
        .when(notificationReadUseCase)
        .markAsReadForAccount(eq(101L), eq(999L));

    mockMvc
        .perform(post("/api/v1/notifications/999/read").header("X-Account-Id", "101"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("notification is not found"));
  }

  @Test
  void rejectsInvalidCursor() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications").header("X-Account-Id", "101").param("cursor", "broken"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void opensNotificationStream() throws Exception {
    when(notificationSseBroker.subscribeAccount(101L, "bootstrap", null))
        .thenReturn(new SseEmitter(60_000L));

    mockMvc
        .perform(get("/api/v1/notifications/stream").header("X-Account-Id", "101"))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andExpect(header().string("X-Accel-Buffering", "no"));
  }

  @Test
  void opensNotificationStreamWithLastEventIdReplay() throws Exception {
    when(notificationSseBroker.subscribeAccount(101L, "bootstrap", 7L))
        .thenReturn(new SseEmitter(60_000L));

    mockMvc
        .perform(
            get("/api/v1/notifications/stream")
                .header("X-Account-Id", "101")
                .header("Last-Event-ID", "7"))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andExpect(header().string("X-Accel-Buffering", "no"));

    verify(notificationSseBroker).subscribeAccount(101L, "bootstrap", 7L);
  }

  @Test
  void rejectsInvalidLastEventIdHeader() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/stream")
                .header("X-Account-Id", "101")
                .header("Last-Event-ID", "broken"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsMissingAuthenticationHeader() throws Exception {
    mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
  }
}
