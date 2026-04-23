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

import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.notification.exception.NotificationNotFoundException;
import com.aquilabank.domain.notification.model.NotificationBulkActionCommand;
import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.model.NotificationPreferenceUpdateCommand;
import com.aquilabank.domain.notification.model.NotificationReadStatusFilter;
import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.usecase.NotificationBulkActionUseCase;
import com.aquilabank.domain.notification.usecase.NotificationPreferenceReadUseCase;
import com.aquilabank.domain.notification.usecase.NotificationPreferenceUpdateUseCase;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationReadUseCase;
import com.aquilabank.domain.notification.usecase.NotificationSearchUseCase;
import com.aquilabank.global.notification.NotificationSseBroker;
import com.aquilabank.global.notification.NotificationSseOverloadException;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationControllerTest {

  private NotificationQueryUseCase notificationQueryUseCase;
  private NotificationReadUseCase notificationReadUseCase;
  private NotificationBulkActionUseCase notificationBulkActionUseCase;
  private NotificationSearchUseCase notificationSearchUseCase;
  private NotificationPreferenceReadUseCase notificationPreferenceReadUseCase;
  private NotificationPreferenceUpdateUseCase notificationPreferenceUpdateUseCase;
  private NotificationSseBroker notificationSseBroker;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    notificationQueryUseCase = mock(NotificationQueryUseCase.class);
    notificationReadUseCase = mock(NotificationReadUseCase.class);
    notificationBulkActionUseCase = mock(NotificationBulkActionUseCase.class);
    notificationSearchUseCase = mock(NotificationSearchUseCase.class);
    notificationPreferenceReadUseCase = mock(NotificationPreferenceReadUseCase.class);
    notificationPreferenceUpdateUseCase = mock(NotificationPreferenceUpdateUseCase.class);
    notificationSseBroker = mock(NotificationSseBroker.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), anyLong()))
        .thenAnswer(invocation -> invocation.getArgument(1, Long.class));
    Clock notificationSearchClock =
        Clock.fixed(Instant.parse("2026-04-21T12:00:00Z"), ZoneOffset.UTC);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationController(
                    notificationQueryUseCase,
                    notificationReadUseCase,
                    notificationBulkActionUseCase,
                    notificationSearchUseCase,
                    notificationPreferenceReadUseCase,
                    notificationPreferenceUpdateUseCase,
                    notificationSseBroker,
                    requestAccountAuthorizationService,
                    notificationSearchClock))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.clearContext();
  }

  @Test
  void getsNotificationPreferencesForJwtUser() throws Exception {
    authenticateUser(321L);
    when(notificationPreferenceReadUseCase.getPreferences(321L))
        .thenReturn(
            List.of(
                new NotificationPreference(
                    NotificationPreferenceCategory.TRANSACTIONAL,
                    NotificationPreferenceChannel.IN_APP,
                    true),
                new NotificationPreference(
                    NotificationPreferenceCategory.MARKETING,
                    NotificationPreferenceChannel.EMAIL,
                    false)));

    mockMvc
        .perform(get("/api/v1/notifications/preferences"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].category").value("TRANSACTIONAL"))
        .andExpect(jsonPath("$.items[0].channel").value("IN_APP"))
        .andExpect(jsonPath("$.items[0].enabled").value(true))
        .andExpect(jsonPath("$.items[1].category").value("MARKETING"));
  }

  @Test
  void updatesNotificationPreferencesForJwtUser() throws Exception {
    authenticateUser(321L);

    mockMvc
        .perform(
            post("/api/v1/notifications/preferences")
                .contentType("application/json")
                .content(
                    """
                    {
                      "items": [
                        {"category": "MARKETING", "channel": "EMAIL", "enabled": true},
                        {"category": "SECURITY", "channel": "SMS", "enabled": false}
                      ]
                    }
                    """))
        .andExpect(status().isNoContent());

    verify(notificationPreferenceUpdateUseCase)
        .updatePreferences(
            eq(321L),
            eq(
                new NotificationPreferenceUpdateCommand(
                    List.of(
                        new NotificationPreference(
                            NotificationPreferenceCategory.MARKETING,
                            NotificationPreferenceChannel.EMAIL,
                            true),
                        new NotificationPreference(
                            NotificationPreferenceCategory.SECURITY,
                            NotificationPreferenceChannel.SMS,
                            false)))));
  }

  @Test
  void rejectsPreferenceApiForAccountPrincipal() throws Exception {
    mockMvc
        .perform(get("/api/v1/notifications/preferences").header("X-Account-Id", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("notification preferences require user principal"));
  }

  @Test
  void rejectsDuplicatePreferenceItems() throws Exception {
    authenticateUser(321L);

    mockMvc
        .perform(
            post("/api/v1/notifications/preferences")
                .contentType("application/json")
                .content(
                    """
                    {
                      "items": [
                        {"category": "MARKETING", "channel": "EMAIL", "enabled": true},
                        {"category": "MARKETING", "channel": "EMAIL", "enabled": false}
                      ]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("duplicate preference item"));
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
  void rejectsAccountPrincipalWhenReadableAccountAccessIsDenied() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenThrow(new AccountAccessDeniedException("account access is denied"));

    mockMvc
        .perform(get("/api/v1/notifications").header("X-Account-Id", "101"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
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
  void marksNotificationsAsReadInBulk() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/notifications/read")
                .header("X-Account-Id", "101")
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[10,11]}
                    """))
        .andExpect(status().isNoContent());

    verify(notificationBulkActionUseCase)
        .markAsReadForAccount(eq(101L), eq(new NotificationBulkActionCommand(List.of(10L, 11L))));
  }

  @Test
  void archivesNotificationsInBulk() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/notifications/archive")
                .header("X-Account-Id", "101")
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[10,12]}
                    """))
        .andExpect(status().isNoContent());

    verify(notificationBulkActionUseCase)
        .archiveForAccount(eq(101L), eq(new NotificationBulkActionCommand(List.of(10L, 12L))));
  }

  @Test
  void deletesNotificationsInBulk() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/notifications/delete")
                .header("X-Account-Id", "101")
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[10,13]}
                    """))
        .andExpect(status().isNoContent());

    verify(notificationBulkActionUseCase)
        .deleteForAccount(eq(101L), eq(new NotificationBulkActionCommand(List.of(10L, 13L))));
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
  void searchesNotificationsForAccountWithDefaultWindow() throws Exception {
    Instant appliedTo = Instant.parse("2026-04-21T12:00:00Z");
    Instant appliedFrom = appliedTo.minusSeconds(31L * 24 * 60 * 60);
    when(notificationSearchUseCase.searchForAccount(eq(101L), any(NotificationSearchQuery.class)))
        .thenReturn(
            new NotificationSearchSlice(
                List.of(
                    new NotificationSummary(
                        20L,
                        101L,
                        "TransferBooked",
                        "검색 알림",
                        "검색 본문",
                        Instant.parse("2026-04-20T10:00:00Z"),
                        null)),
                null,
                false,
                20,
                appliedFrom,
                appliedTo));

    mockMvc
        .perform(get("/api/v1/notifications/search").header("X-Account-Id", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].notificationId").value(20L))
        .andExpect(jsonPath("$.appliedFrom").value(appliedFrom.toString()))
        .andExpect(jsonPath("$.appliedTo").value(appliedTo.toString()))
        .andExpect(jsonPath("$.hasNext").value(false));

    verify(notificationSearchUseCase)
        .searchForAccount(
            eq(101L),
            eq(
                new NotificationSearchQuery(
                    20, null, NotificationReadStatusFilter.ALL, null, appliedFrom, appliedTo)));
  }

  @Test
  void rejectsSearchWhenOnlyFromIsProvided() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("from", "2026-04-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("from and to must be provided together"));
  }

  @Test
  void rejectsSearchWhenReadStatusIsInvalid() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("readStatus", "BROKEN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("readStatus is invalid"));
  }

  @Test
  void rejectsMalformedSearchCursor() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("cursor", "broken"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("cursor format is invalid"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/search"));
  }

  @Test
  void rejectsSearchWhenFromIsInvalidIsoInstant() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("from", "broken")
                .param("to", "2026-04-21T12:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("from must be a valid ISO-8601 instant"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/search"));
  }

  @Test
  void rejectsSearchWhenToIsInvalidIsoInstant() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "broken"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("to must be a valid ISO-8601 instant"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/search"));
  }

  @Test
  void rejectsSearchWhenFromIsAfterTo() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("from", "2026-04-21T12:00:01Z")
                .param("to", "2026-04-21T12:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("from must be before or equal to to"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/search"));
  }

  @Test
  void rejectsSearchWhenExplicitRangeExceedsThirtyOneDays() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("from", "2026-03-01T00:00:00Z")
                .param("to", "2026-04-21T12:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("search window must be 31 days or less"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/search"));
  }

  @Test
  void reusesCursorWindowWhenOnlyCursorIsProvided() throws Exception {
    NotificationSearchCursor cursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-20T10:00:00Z"),
            20L,
            Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-04-21T12:00:00Z"),
            "ALL|TransferBooked|2026-04-01T00:00:00Z|2026-04-21T12:00:00Z");
    when(notificationSearchUseCase.searchForAccount(eq(101L), any(NotificationSearchQuery.class)))
        .thenReturn(
            new NotificationSearchSlice(
                List.of(), null, false, 20, cursor.appliedFrom(), cursor.appliedTo()));

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("cursor", NotificationSearchCursorCodec.encode(cursor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appliedFrom").value(cursor.appliedFrom().toString()))
        .andExpect(jsonPath("$.appliedTo").value(cursor.appliedTo().toString()));

    verify(notificationSearchUseCase)
        .searchForAccount(
            eq(101L),
            eq(
                new NotificationSearchQuery(
                    20,
                    cursor,
                    NotificationReadStatusFilter.ALL,
                    null,
                    cursor.appliedFrom(),
                    cursor.appliedTo())));
  }

  @Test
  void trimsReadStatusAndEventTypeBeforeSearchQueryResolution() throws Exception {
    Instant appliedFrom = Instant.parse("2026-04-01T00:00:00Z");
    Instant appliedTo = Instant.parse("2026-04-21T12:00:00Z");
    when(notificationSearchUseCase.searchForAccount(eq(101L), any(NotificationSearchQuery.class)))
        .thenReturn(
            new NotificationSearchSlice(List.of(), null, false, 20, appliedFrom, appliedTo));

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("readStatus", " UNREAD ")
                .param("eventType", " TransferBooked ")
                .param("from", appliedFrom.toString())
                .param("to", appliedTo.toString()))
        .andExpect(status().isOk());

    verify(notificationSearchUseCase)
        .searchForAccount(
            eq(101L),
            eq(
                new NotificationSearchQuery(
                    20,
                    null,
                    NotificationReadStatusFilter.UNREAD,
                    "TransferBooked",
                    appliedFrom,
                    appliedTo)));
  }

  @Test
  void rejectsSearchWhenReusedCursorWindowExceedsThirtyOneDays() throws Exception {
    NotificationSearchCursor cursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-21T10:00:00Z"),
            41L,
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-04-21T12:00:00Z"),
            "ALL||2026-03-01T00:00:00Z|2026-04-21T12:00:00Z");

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", "101")
                .param("cursor", NotificationSearchCursorCodec.encode(cursor)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("search window must be 31 days or less"));
  }

  @Test
  void keepsExistingNotificationListPathUnchanged() throws Exception {
    when(notificationQueryUseCase.getNotificationsForAccount(anyLong(), any()))
        .thenReturn(new NotificationSlice(List.of(), null, false, 20));

    mockMvc
        .perform(get("/api/v1/notifications").header("X-Account-Id", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.limit").value(20))
        .andExpect(jsonPath("$.nextCursor").isEmpty());

    verify(notificationQueryUseCase)
        .getNotificationsForAccount(eq(101L), eq(new NotificationListQuery(20, null)));
  }

  @Test
  void rejectsEmptyBulkRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/notifications/archive")
                .header("X-Account-Id", "101")
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[]}
                    """))
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
  void rejectsNotificationStreamWhenBrokerIsOverloaded() throws Exception {
    when(notificationSseBroker.subscribeAccount(101L, "bootstrap", null))
        .thenThrow(
            new NotificationSseOverloadException(
                "notification SSE stream is temporarily overloaded"));

    mockMvc
        .perform(get("/api/v1/notifications/stream").header("X-Account-Id", "101"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.message").value("notification SSE stream is temporarily overloaded"));
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

  private void authenticateUser(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, "tester"), null, List.of()));
  }
}
