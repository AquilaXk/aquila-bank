package com.aquilabank.global.web.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.support.PostgresContainerTestSupport;
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
}
