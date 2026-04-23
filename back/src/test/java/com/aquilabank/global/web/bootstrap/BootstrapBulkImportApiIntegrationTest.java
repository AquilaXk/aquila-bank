package com.aquilabank.global.web.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BootstrapBulkImportApiIntegrationTest extends PostgresContainerTestSupport {

  private static final int SLO_ITEM_COUNT = 12;
  private static final Duration BULK_IMPORT_SLO = Duration.ofSeconds(8);

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void importsAccountsUsersAndMembershipsInOneInternalRequest() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/bootstrap/bulk-import")
                .header("Authorization", bootstrapImportAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accounts": [
                        {
                          "clientRef": "source",
                          "displayName": "source account",
                          "currencyCode": "KRW",
                          "initialBalanceMinor": 10000
                        },
                        {
                          "clientRef": "target",
                          "displayName": "target account",
                          "currencyCode": "KRW",
                          "initialBalanceMinor": 0
                        }
                      ],
                      "users": [
                        {
                          "clientRef": "alice",
                          "loginId": "alice",
                          "password": "password123!",
                          "displayName": "Alice"
                        }
                      ],
                      "memberships": [
                        {
                          "userRef": "alice",
                          "accountRef": "source",
                          "membershipRole": "OWNER",
                          "membershipStatus": "ACTIVE"
                        },
                        {
                          "userRef": "alice",
                          "accountRef": "target",
                          "membershipRole": "VIEWER",
                          "membershipStatus": "ACTIVE"
                        }
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.accountCount").value(2))
        .andExpect(jsonPath("$.summary.userCount").value(1))
        .andExpect(jsonPath("$.summary.membershipCount").value(2))
        .andExpect(jsonPath("$.accounts[0].clientRef").value("source"))
        .andExpect(jsonPath("$.users[0].clientRef").value("alice"))
        .andExpect(jsonPath("$.memberships[1].membershipRole").value("VIEWER"));

    assertThat(countRows("bank_account")).isEqualTo(2);
    assertThat(countRows("bank_user")).isEqualTo(1);
    assertThat(countRows("user_account_membership")).isEqualTo(2);
  }

  @Test
  void rollsBackWholeImportWhenAnyItemFails() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/bootstrap/bulk-import")
                .header("Authorization", bootstrapImportAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accounts": [
                        {
                          "clientRef": "source",
                          "displayName": "source account",
                          "currencyCode": "KRW",
                          "initialBalanceMinor": 10000
                        }
                      ],
                      "users": [
                        {
                          "clientRef": "alice-1",
                          "loginId": "alice",
                          "password": "password123!",
                          "displayName": "Alice"
                        },
                        {
                          "clientRef": "alice-2",
                          "loginId": "alice",
                          "password": "password123!",
                          "displayName": "Alice duplicate"
                        }
                      ],
                      "memberships": []
                    }
                    """))
        .andExpect(status().isConflict());

    assertThat(countRows("bank_account")).isZero();
    assertThat(countRows("bank_user")).isZero();
    assertThat(countRows("user_account_membership")).isZero();
  }

  @Test
  void importsLargeBootstrapFixtureWithinSlo() throws Exception {
    Duration elapsed =
        measure(
            () ->
                mockMvc
                    .perform(
                        post("/internal/api/v1/bootstrap/bulk-import")
                            .header("Authorization", bootstrapImportAuthorization())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bulkImportRequest(SLO_ITEM_COUNT, false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.accountCount").value(SLO_ITEM_COUNT))
                    .andExpect(jsonPath("$.summary.userCount").value(SLO_ITEM_COUNT))
                    .andExpect(jsonPath("$.summary.membershipCount").value(SLO_ITEM_COUNT)));

    assertThat(elapsed).isLessThan(BULK_IMPORT_SLO);
    assertThat(countRows("bank_account")).isEqualTo(SLO_ITEM_COUNT);
    assertThat(countRows("bank_user")).isEqualTo(SLO_ITEM_COUNT);
    assertThat(countRows("user_account_membership")).isEqualTo(SLO_ITEM_COUNT);
    assertThat(countRows("account_balance_snapshot")).isEqualTo(SLO_ITEM_COUNT);
    assertThat(countRows("ledger_entry")).isEqualTo(SLO_ITEM_COUNT);
    assertThat(countRows("transaction_read_model")).isEqualTo(SLO_ITEM_COUNT);
  }

  @Test
  void rollsBackLargeBootstrapFixtureWithinSloWhenAnyItemFails() throws Exception {
    Duration elapsed =
        measure(
            () ->
                mockMvc
                    .perform(
                        post("/internal/api/v1/bootstrap/bulk-import")
                            .header("Authorization", bootstrapImportAuthorization())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bulkImportRequest(SLO_ITEM_COUNT, true)))
                    .andExpect(status().isConflict()));

    assertThat(elapsed).isLessThan(BULK_IMPORT_SLO);
    assertThat(countRows("bank_account")).isZero();
    assertThat(countRows("bank_user")).isZero();
    assertThat(countRows("user_account_membership")).isZero();
    assertThat(countRows("account_balance_snapshot")).isZero();
    assertThat(countRows("ledger_entry")).isZero();
    assertThat(countRows("transaction_read_model")).isZero();
  }

  private String bootstrapImportAuthorization() {
    return InternalServiceTokenTestSupport.authorization(
        "bootstrap-bulk-import-test",
        InternalServiceScope.ACCOUNT_BOOTSTRAP,
        InternalServiceScope.AUTH_BOOTSTRAP);
  }

  private int countRows(String tableName) {
    Integer count =
        jdbcTemplate
            .getJdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    if (count == null) {
      throw new IllegalStateException("count query returned null");
    }
    return count;
  }

  private static Duration measure(CheckedRunnable action) throws Exception {
    long startedNanos = System.nanoTime();
    action.run();
    return Duration.ofNanos(System.nanoTime() - startedNanos);
  }

  private static String bulkImportRequest(int count, boolean duplicateLastLoginId) {
    StringBuilder accounts = new StringBuilder();
    StringBuilder users = new StringBuilder();
    StringBuilder memberships = new StringBuilder();
    for (int index = 0; index < count; index++) {
      if (index > 0) {
        accounts.append(",");
        users.append(",");
        memberships.append(",");
      }
      accounts.append(
          """
          {
            "clientRef": "account-%d",
            "displayName": "bulk account %d",
            "currencyCode": "KRW",
            "initialBalanceMinor": 1000
          }
          """
              .formatted(index, index));
      String loginId =
          duplicateLastLoginId && index == count - 1
              ? "bulk-user-0"
              : "bulk-user-%d".formatted(index);
      users.append(
          """
          {
            "clientRef": "user-%d",
            "loginId": "%s",
            "password": "password123!",
            "displayName": "Bulk User %d"
          }
          """
              .formatted(index, loginId, index));
      memberships.append(
          """
          {
            "userRef": "user-%d",
            "accountRef": "account-%d",
            "membershipRole": "OWNER",
            "membershipStatus": "ACTIVE"
          }
          """
              .formatted(index, index));
    }

    // 기존 smoke보다 큰 fixture로 BCrypt/hash, account opening ledger, rollback 비용을 함께 본다.
    return """
        {
          "accounts": [%s],
          "users": [%s],
          "memberships": [%s]
        }
        """
        .formatted(accounts, users, memberships);
  }

  @FunctionalInterface
  private interface CheckedRunnable {

    void run() throws Exception;
  }
}
