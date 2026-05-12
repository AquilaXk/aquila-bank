package com.aquilabank.global.web.notification;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.global.persistence.notification.JdbcNotificationInboxRepository;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class NotificationApiIntegrationTest extends PostgresContainerTestSupport {

  private static final String TEST_SECRET = "test-local-jwt-secret-test-local-jwt-secret";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private JdbcNotificationInboxRepository notificationInboxRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void listsUnreadCountAndMarkAsReadForJwtUser() throws Exception {
    long[] userId = new long[1];
    long[] accountIds = new long[3];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("api-user");
          accountIds[0] = insertAccount("daily account");
          accountIds[1] = insertAccount("salary account");
          accountIds[2] = insertAccount("hidden account");
          insertMembership(userId[0], accountIds[0], "OWNER", "ACTIVE");
          insertMembership(userId[0], accountIds[1], "VIEWER", "ACTIVE");
          insertMembership(userId[0], accountIds[2], "VIEWER", "REVOKED");
          notificationIds[0] =
              insertNotification(
                  accountIds[0], "evt-api-1", "TransferBooked", "첫 알림", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountIds[1],
                  "evt-api-2",
                  "TransferBooked",
                  "둘째 알림",
                  "B",
                  null,
                  base.plusSeconds(10));
          notificationIds[2] =
              insertNotification(
                  accountIds[2],
                  "evt-api-3",
                  "TransferBooked",
                  "숨김 알림",
                  "C",
                  null,
                  base.plusSeconds(20));
        });

    String token = issueToken("user-55", userId[0]);

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].title").value("둘째 알림"))
        .andExpect(jsonPath("$.items[1].title").value("첫 알림"));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(2));

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationIds[0] + "/read")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(1));

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationIds[2] + "/read")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("notification is not found"));
  }

  @Test
  void keepsReadStateSeparatedBetweenSharedAccountUsers() throws Exception {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    long[] notificationId = new long[1];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("shared-user-a");
          userIds[1] = insertUser("shared-user-b");
          accountId[0] = insertAccount("shared account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          notificationId[0] =
              insertNotification(
                  accountId[0], "evt-shared-1", "TransferBooked", "공동 알림", "shared", null, base);
        });

    String tokenA = issueToken("shared-user-a-subject", userIds[0]);
    String tokenB = issueToken("shared-user-b-subject", userIds[1]);

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(1));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(1));

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationId[0] + "/read")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].notificationId").value(notificationId[0]))
        .andExpect(jsonPath("$.items[0].read").value(true))
        .andExpect(jsonPath("$.items[0].readAt").isNotEmpty());

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].notificationId").value(notificationId[0]))
        .andExpect(jsonPath("$.items[0].read").value(false))
        .andExpect(jsonPath("$.items[0].readAt").isEmpty());

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(0));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(1));
  }

  @Test
  void getsAndUpdatesNotificationPreferencesForJwtUser() throws Exception {
    long[] userIds = new long[2];
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("pref-user-a");
          userIds[1] = insertUser("pref-user-b");
        });

    String tokenA = issueToken("pref-user-a-subject", userIds[0]);
    String tokenB = issueToken("pref-user-b-subject", userIds[1]);

    mockMvc
        .perform(
            get("/api/v1/notifications/preferences").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(9))
        .andExpect(jsonPath("$.items[0].category").isString())
        .andExpect(jsonPath("$.items[0].channel").isString());

    mockMvc
        .perform(
            post("/api/v1/notifications/preferences")
                .header("Authorization", "Bearer " + tokenA)
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

    mockMvc
        .perform(
            get("/api/v1/notifications/preferences").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[?(@.category=='MARKETING' && @.channel=='EMAIL')].enabled")
                .value(org.hamcrest.Matchers.contains(true)))
        .andExpect(
            jsonPath("$.items[?(@.category=='SECURITY' && @.channel=='SMS')].enabled")
                .value(org.hamcrest.Matchers.contains(false)));

    mockMvc
        .perform(
            get("/api/v1/notifications/preferences").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[?(@.category=='MARKETING' && @.channel=='EMAIL')].enabled")
                .value(org.hamcrest.Matchers.contains(false)))
        .andExpect(
            jsonPath("$.items[?(@.category=='SECURITY' && @.channel=='SMS')].enabled")
                .value(org.hamcrest.Matchers.contains(true)));
  }

  @Test
  void hidesInAppNotificationsForJwtUserWithDisabledTransactionalPreference() throws Exception {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    Instant createdAt = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("api-pref-enabled-user");
          userIds[1] = insertUser("api-pref-disabled-user");
          accountId[0] = insertAccount("api preference account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          insertPreference(userIds[1], "TRANSACTIONAL", "IN_APP", false);
          notificationInboxRepository.appendAllIfAbsent(
              java.util.List.of(
                  new NotificationInboxEntry(
                      accountId[0],
                      "evt-api-preference-disabled",
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 입금 · preference",
                      createdAt)));
        });

    String enabledToken = issueToken("api-pref-enabled-subject", userIds[0]);
    String disabledToken = issueToken("api-pref-disabled-subject", userIds[1]);

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + enabledToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("이체 완료"));

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + disabledToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count")
                .header("Authorization", "Bearer " + enabledToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(1));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count")
                .header("Authorization", "Bearer " + disabledToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(0));
  }

  @Test
  void rejectsNotificationPreferencesForAccountPrincipal() throws Exception {
    long[] accountId = new long[1];
    commit(transactionManager, () -> accountId[0] = insertAccount("pref-account"));

    mockMvc
        .perform(get("/api/v1/notifications/preferences").header("X-Account-Id", accountId[0]))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("notification preferences require user principal"));
  }

  @Test
  void supportsBulkReadArchiveAndDeleteForJwtUser() throws Exception {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("bulk-user-a");
          userIds[1] = insertUser("bulk-user-b");
          accountId[0] = insertAccount("bulk user account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          notificationIds[0] =
              insertNotification(
                  accountId[0], "evt-user-bulk-1", "TransferBooked", "첫 알림", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-user-bulk-2",
                  "TransferBooked",
                  "둘째 알림",
                  "B",
                  null,
                  base.plusSeconds(5));
          notificationIds[2] =
              insertNotification(
                  accountId[0],
                  "evt-user-bulk-3",
                  "TransferBooked",
                  "셋째 알림",
                  "C",
                  null,
                  base.plusSeconds(10));
        });

    String tokenA = issueToken("bulk-user-a-subject", userIds[0]);
    String tokenB = issueToken("bulk-user-b-subject", userIds[1]);

    mockMvc
        .perform(
            post("/api/v1/notifications/read")
                .header("Authorization", "Bearer " + tokenA)
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d,%d]}
                    """
                        .formatted(notificationIds[0], notificationIds[1])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[1].notificationId").value(notificationIds[1]))
        .andExpect(jsonPath("$.items[1].read").value(true))
        .andExpect(jsonPath("$.items[2].notificationId").value(notificationIds[0]))
        .andExpect(jsonPath("$.items[2].read").value(true));

    mockMvc
        .perform(
            post("/api/v1/notifications/archive")
                .header("Authorization", "Bearer " + tokenA)
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[0])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/notifications/delete")
                .header("Authorization", "Bearer " + tokenA)
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[1])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].notificationId").value(notificationIds[2]))
        .andExpect(jsonPath("$.items[0].read").value(false));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(1));

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].read").value(false))
        .andExpect(jsonPath("$.items[1].read").value(false))
        .andExpect(jsonPath("$.items[2].read").value(false));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(3));
  }

  @Test
  void supportsBulkReadArchiveAndDeleteForAccountPrincipal() throws Exception {
    long[] accountId = new long[1];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount("bulk account principal");
          notificationIds[0] =
              insertNotification(
                  accountId[0], "evt-account-bulk-1", "TransferBooked", "첫 알림", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-account-bulk-2",
                  "TransferBooked",
                  "둘째 알림",
                  "B",
                  null,
                  base.plusSeconds(5));
          notificationIds[2] =
              insertNotification(
                  accountId[0],
                  "evt-account-bulk-3",
                  "TransferBooked",
                  "셋째 알림",
                  "C",
                  null,
                  base.plusSeconds(10));
        });

    mockMvc
        .perform(
            post("/api/v1/notifications/read")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d,%d]}
                    """
                        .formatted(notificationIds[0], notificationIds[1])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("X-Account-Id", accountId[0]))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(1));

    mockMvc
        .perform(
            post("/api/v1/notifications/archive")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[0])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/notifications").header("X-Account-Id", accountId[0]))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].notificationId").value(notificationIds[2]))
        .andExpect(jsonPath("$.items[1].notificationId").value(notificationIds[1]))
        .andExpect(jsonPath("$.items[1].read").value(true));

    mockMvc
        .perform(
            post("/api/v1/notifications/delete")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[2])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/notifications").header("X-Account-Id", accountId[0]))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].notificationId").value(notificationIds[1]))
        .andExpect(jsonPath("$.items[0].read").value(true));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("X-Account-Id", accountId[0]))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(0));
  }

  @Test
  void jwtNotificationApisHideClosedAccountsButKeepLockedAccounts() throws Exception {
    long[] userId = new long[1];
    long[] accountIds = new long[2];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    Instant from = base.minusSeconds(60);
    Instant to = base.plusSeconds(300);
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("status-filter-user");
          accountIds[0] = insertAccount("locked notification account");
          accountIds[1] = insertAccount("closed notification account");
          insertMembership(userId[0], accountIds[0], "OWNER", "ACTIVE");
          insertMembership(userId[0], accountIds[1], "VIEWER", "ACTIVE");
          notificationIds[0] =
              insertNotification(
                  accountIds[0],
                  "evt-status-filter-1",
                  "TransferBooked",
                  "첫 LOCKED 알림",
                  "A",
                  null,
                  base);
          notificationIds[1] =
              insertNotification(
                  accountIds[1],
                  "evt-status-filter-2",
                  "TransferBooked",
                  "CLOSED 알림",
                  "B",
                  null,
                  base.plusSeconds(10));
          notificationIds[2] =
              insertNotification(
                  accountIds[0],
                  "evt-status-filter-3",
                  "TransferBooked",
                  "둘째 LOCKED 알림",
                  "C",
                  null,
                  base.plusSeconds(20));
        });

    updateAccountStatus(accountIds[0], "LOCKED");
    updateAccountStatus(accountIds[1], "CLOSED");
    String token = issueToken("status-filter-user-subject", userId[0]);

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].accountId").value(accountIds[0]))
        .andExpect(jsonPath("$.items[1].accountId").value(accountIds[0]));

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("Authorization", "Bearer " + token)
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].accountId").value(accountIds[0]))
        .andExpect(jsonPath("$.items[1].accountId").value(accountIds[0]));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(2));

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationIds[1] + "/read")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("notification is not found"));

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationIds[0] + "/read")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/notifications/archive")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d,%d]}
                    """
                        .formatted(notificationIds[1], notificationIds[2])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/notifications/delete")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d,%d]}
                    """
                        .formatted(notificationIds[1], notificationIds[0])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(0));
  }

  @Test
  void accountPrincipalNotificationApisAllowLockedAccountButRejectClosedAccount() throws Exception {
    long[] accountId = new long[1];
    long[] notificationIds = new long[2];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    Instant from = base.minusSeconds(60);
    Instant to = base.plusSeconds(300);
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount("account-principal status account");
          notificationIds[0] =
              insertNotification(
                  accountId[0], "evt-account-status-1", "TransferBooked", "첫 알림", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-account-status-2",
                  "TransferBooked",
                  "둘째 알림",
                  "B",
                  null,
                  base.plusSeconds(10));
        });

    updateAccountStatus(accountId[0], "LOCKED");

    mockMvc
        .perform(get("/api/v1/notifications").header("X-Account-Id", accountId[0]))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", accountId[0])
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("X-Account-Id", accountId[0]))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(2));

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationIds[0] + "/read")
                .header("X-Account-Id", Long.toString(accountId[0])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/notifications/read")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[1])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/notifications/archive")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[0])))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/notifications/delete")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[1])))
        .andExpect(status().isNoContent());

    updateAccountStatus(accountId[0], "CLOSED");

    mockMvc
        .perform(get("/api/v1/notifications").header("X-Account-Id", accountId[0]))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("X-Account-Id", accountId[0])
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("X-Account-Id", accountId[0]))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationIds[0] + "/read")
                .header("X-Account-Id", Long.toString(accountId[0])))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/notifications/read")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[1])))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/notifications/archive")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[0])))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/notifications/delete")
                .header("X-Account-Id", Long.toString(accountId[0]))
                .contentType("application/json")
                .content(
                    """
                    {"notificationIds":[%d]}
                    """
                        .formatted(notificationIds[1])))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(get("/api/v1/notifications/stream").header("X-Account-Id", accountId[0]))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void searchesNotificationsForJwtUserWithExplicitFilters() throws Exception {
    long[] userId = new long[1];
    long[] accountIds = new long[2];
    long[] notificationIds = new long[4];
    Instant from = Instant.parse("2026-04-01T00:00:00Z");
    Instant to = Instant.parse("2026-04-30T23:59:59Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("search-user");
          accountIds[0] = insertAccount("search account");
          accountIds[1] = insertAccount("search hidden account");
          insertMembership(userId[0], accountIds[0], "OWNER", "ACTIVE");
          insertMembership(userId[0], accountIds[1], "OWNER", "REVOKED");
          notificationIds[0] =
              insertNotification(
                  accountIds[0],
                  "evt-search-1",
                  "TransferBooked",
                  "읽지 않은 검색 알림",
                  "A",
                  null,
                  Instant.parse("2026-04-18T10:00:00Z"));
          notificationIds[1] =
              insertNotification(
                  accountIds[0],
                  "evt-search-2",
                  "TransferBooked",
                  "읽은 검색 알림",
                  "B",
                  null,
                  Instant.parse("2026-04-19T10:00:00Z"));
          notificationIds[2] =
              insertNotification(
                  accountIds[0],
                  "evt-search-3",
                  "CardApproved",
                  "다른 eventType",
                  "C",
                  null,
                  Instant.parse("2026-04-20T10:00:00Z"));
          notificationIds[3] =
              insertNotification(
                  accountIds[1],
                  "evt-search-4",
                  "TransferBooked",
                  "권한 없는 알림",
                  "D",
                  null,
                  Instant.parse("2026-04-21T10:00:00Z"));
        });

    String token = issueToken("search-user-subject", userId[0]);

    mockMvc
        .perform(
            post("/api/v1/notifications/" + notificationIds[1] + "/read")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("Authorization", "Bearer " + token)
                .param("readStatus", "UNREAD")
                .param("eventType", "TransferBooked")
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("읽지 않은 검색 알림"))
        .andExpect(jsonPath("$.items[0].eventType").value("TransferBooked"))
        .andExpect(jsonPath("$.items[0].read").value(false))
        .andExpect(jsonPath("$.appliedFrom").value(from.toString()))
        .andExpect(jsonPath("$.appliedTo").value(to.toString()));
  }

  @Test
  void rejectsSearchCursorWhenRequestedFiltersDoNotMatch() throws Exception {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant from = Instant.parse("2026-04-01T00:00:00Z");
    Instant to = Instant.parse("2026-04-30T23:59:59Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("cursor-user");
          accountId[0] = insertAccount("cursor account");
          insertMembership(userId[0], accountId[0], "OWNER", "ACTIVE");
          insertNotification(
              accountId[0],
              "evt-cursor-1",
              "TransferBooked",
              "첫 페이지 알림 A",
              "A",
              null,
              Instant.parse("2026-04-21T10:00:00Z"));
          insertNotification(
              accountId[0],
              "evt-cursor-2",
              "TransferBooked",
              "첫 페이지 알림 B",
              "B",
              null,
              Instant.parse("2026-04-21T09:00:00Z"));
          insertNotification(
              accountId[0],
              "evt-cursor-3",
              "TransferBooked",
              "둘째 페이지 알림",
              "C",
              null,
              Instant.parse("2026-04-21T08:00:00Z"));
        });

    String token = issueToken("cursor-user-subject", userId[0]);

    MvcResult pageOne =
        mockMvc
            .perform(
                get("/api/v1/notifications/search")
                    .header("Authorization", "Bearer " + token)
                    .param("limit", "2")
                    .param("readStatus", "ALL")
                    .param("eventType", "TransferBooked")
                    .param("from", from.toString())
                    .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andReturn();

    String cursor =
        OBJECT_MAPPER
            .readTree(pageOne.getResponse().getContentAsString())
            .get("nextCursor")
            .asText();

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("Authorization", "Bearer " + token)
                .param("limit", "2")
                .param("cursor", cursor)
                .param("readStatus", "UNREAD")
                .param("eventType", "TransferBooked")
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsSearchWhenReadStatusIsInvalid() throws Exception {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant from = Instant.parse("2026-04-01T00:00:00Z");
    Instant to = Instant.parse("2026-04-30T23:59:59Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("invalid-status-user");
          accountId[0] = insertAccount("invalid status account");
          insertMembership(userId[0], accountId[0], "OWNER", "ACTIVE");
        });

    String token = issueToken("invalid-status-user-subject", userId[0]);

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("Authorization", "Bearer " + token)
                .param("readStatus", "BROKEN")
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("readStatus is invalid"));
  }

  @Test
  void continuesSearchNextPageWithCursorOnlyUsingSameWindow() throws Exception {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant from = Instant.parse("2026-04-01T00:00:00Z");
    Instant to = Instant.parse("2026-04-30T23:59:59Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("cursor-window-user");
          accountId[0] = insertAccount("cursor window account");
          insertMembership(userId[0], accountId[0], "OWNER", "ACTIVE");
          insertNotification(
              accountId[0],
              "evt-window-1",
              "TransferBooked",
              "첫 페이지 A",
              "A",
              null,
              Instant.parse("2026-04-21T10:00:00Z"));
          insertNotification(
              accountId[0],
              "evt-window-2",
              "TransferBooked",
              "첫 페이지 B",
              "B",
              null,
              Instant.parse("2026-04-21T09:00:00Z"));
          insertNotification(
              accountId[0],
              "evt-window-3",
              "TransferBooked",
              "둘째 페이지",
              "C",
              null,
              Instant.parse("2026-04-21T08:00:00Z"));
        });

    String token = issueToken("cursor-window-subject", userId[0]);

    MvcResult pageOne =
        mockMvc
            .perform(
                get("/api/v1/notifications/search")
                    .header("Authorization", "Bearer " + token)
                    .param("limit", "2")
                    .param("from", from.toString())
                    .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].title").value("첫 페이지 A"))
            .andExpect(jsonPath("$.items[1].title").value("첫 페이지 B"))
            .andExpect(jsonPath("$.appliedFrom").value(from.toString()))
            .andExpect(jsonPath("$.appliedTo").value(to.toString()))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andReturn();

    String cursor =
        OBJECT_MAPPER
            .readTree(pageOne.getResponse().getContentAsString())
            .get("nextCursor")
            .asText();

    mockMvc
        .perform(
            get("/api/v1/notifications/search")
                .header("Authorization", "Bearer " + token)
                .param("limit", "2")
                .param("cursor", cursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("둘째 페이지"))
        .andExpect(jsonPath("$.appliedFrom").value(from.toString()))
        .andExpect(jsonPath("$.appliedTo").value(to.toString()))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  private long insertUser(String loginId) {
    Long userId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_user (
                login_id,
                password_hash,
                display_name,
                user_status,
                created_at,
                updated_at
            )
            VALUES (
                :loginId,
                '$2a$10$abcdefghijklmnopqrstuv',
                :loginId,
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            Long.class);
    if (userId == null) {
      throw new IllegalStateException("bank_user insert did not return id");
    }
    return userId;
  }

  private long insertAccount(String displayName) {
    Long accountId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            VALUES (
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("displayName", displayName),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return accountId;
  }

  private void insertMembership(long userId, long accountId, String role, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO user_account_membership (
            user_id,
            account_id,
            membership_role,
            membership_status,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :accountId,
            :role,
            :status,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("role", role)
            .addValue("status", status));
  }

  private void updateAccountStatus(long accountId, String accountStatus) {
    commit(
        transactionManager,
        () -> {
          int updated =
              jdbcTemplate.update(
                  """
                  UPDATE bank_account
                  SET account_status = :accountStatus,
                      updated_at = CURRENT_TIMESTAMP
                  WHERE id = :accountId
                  """,
                  new MapSqlParameterSource()
                      .addValue("accountId", accountId)
                      .addValue("accountStatus", accountStatus));
          if (updated != 1) {
            throw new IllegalStateException("bank_account update did not affect exactly one row");
          }
        });
  }

  private long insertNotification(
      long accountId,
      String eventKey,
      String eventType,
      String title,
      String message,
      Instant readAt,
      Instant createdAt) {
    Long notificationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO notification_inbox (
                account_id,
                event_key,
                event_type,
                title,
                message,
                read_at,
                created_at
            )
            VALUES (
                :accountId,
                :eventKey,
                :eventType,
                :title,
                :message,
                :readAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("eventKey", eventKey)
                .addValue("eventType", eventType)
                .addValue("title", title)
                .addValue("message", message)
                .addValue("readAt", readAt == null ? null : Timestamp.from(readAt))
                .addValue("createdAt", Timestamp.from(createdAt)),
            Long.class);
    if (notificationId == null) {
      throw new IllegalStateException("notification_inbox insert did not return id");
    }
    seedUnreadProjectionForInsertedNotification(accountId, readAt);
    return notificationId;
  }

  private void seedUnreadProjectionForInsertedNotification(long accountId, Instant readAt) {
    if (readAt == null) {
      incrementTestProjection("ACCOUNT", accountId, 1L);
    }
    jdbcTemplate.update(
        """
        INSERT INTO notification_unread_count_projection (
            scope_type,
            scope_id,
            unread_count,
            updated_at
        )
        SELECT 'USER',
               m.user_id,
               1,
               CURRENT_TIMESTAMP
        FROM user_account_membership m
        JOIN bank_user u
          ON u.id = m.user_id
        LEFT JOIN notification_preference p
          ON p.user_id = m.user_id
         AND p.category = 'TRANSACTIONAL'
         AND p.channel = 'IN_APP'
        WHERE m.account_id = :accountId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND COALESCE(p.enabled, TRUE) = TRUE
        ON CONFLICT (scope_type, scope_id)
        DO UPDATE
        SET unread_count = notification_unread_count_projection.unread_count + EXCLUDED.unread_count,
            updated_at = CURRENT_TIMESTAMP
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  private void incrementTestProjection(String scopeType, long scopeId, long delta) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_unread_count_projection (
            scope_type,
            scope_id,
            unread_count,
            updated_at
        )
        VALUES (
            :scopeType,
            :scopeId,
            :delta,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (scope_type, scope_id)
        DO UPDATE
        SET unread_count = notification_unread_count_projection.unread_count + EXCLUDED.unread_count,
            updated_at = CURRENT_TIMESTAMP
        """,
        new MapSqlParameterSource()
            .addValue("scopeType", scopeType)
            .addValue("scopeId", scopeId)
            .addValue("delta", delta));
  }

  private void insertPreference(long userId, String category, String channel, boolean enabled) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_preference (
            user_id,
            category,
            channel,
            enabled,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :category,
            :channel,
            :enabled,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("category", category)
            .addValue("channel", channel)
            .addValue("enabled", enabled));
  }

  private String issueToken(String subject, long userId) throws JOSEException {
    Instant now = Instant.now();
    long sessionId = insertActiveRefreshTokenSession(userId, now);
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("user_id", userId)
            .claim("session_id", sessionId)
            .build();

    SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claimsSet);
    signedJwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
    return signedJwt.serialize();
  }

  private long insertActiveRefreshTokenSession(long userId, Instant now) {
    long[] sessionId = new long[1];
    commit(
        transactionManager,
        () -> {
          Long insertedId =
              jdbcTemplate.queryForObject(
                  """
                  INSERT INTO auth_refresh_token_session (
                      user_id,
                      token_hash,
                      session_status,
                      expires_at,
                      created_at,
                      updated_at
                  )
                  VALUES (
                      :userId,
                      :tokenHash,
                      'ACTIVE',
                      :expiresAt,
                      :now,
                      :now
                  )
                  RETURNING id
                  """,
                  new MapSqlParameterSource()
                      .addValue("userId", userId)
                      .addValue("tokenHash", UUID.randomUUID().toString().replace("-", ""))
                      .addValue("expiresAt", Timestamp.from(now.plusSeconds(600)))
                      .addValue("now", Timestamp.from(now)),
                  Long.class);
          if (insertedId == null) {
            throw new IllegalStateException("auth_refresh_token_session insert did not return id");
          }
          sessionId[0] = insertedId;
        });
    if (sessionId[0] <= 0) {
      throw new IllegalStateException("auth_refresh_token_session insert did not return id");
    }
    return sessionId[0];
  }
}
