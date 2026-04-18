package com.aquilabank.global.web.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class InternalAccountAdminApiIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private AccountBootstrapUseCase accountBootstrapUseCase;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void updatesAccountStatusAndPersistsUpdatedRow() throws Exception {
    AccountBootstrapResult account =
        accountBootstrapUseCase.bootstrap(
            new AccountBootstrapCommand("main account", "KRW", 10000L));

    mockMvc
        .perform(
            put("/internal/api/v1/accounts/%d/status".formatted(account.accountId()))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "CLOSED"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(account.accountId()))
        .andExpect(jsonPath("$.accountStatus").value("CLOSED"))
        .andExpect(jsonPath("$.availableBalanceMinor").value(10000L));

    assertEquals("CLOSED", loadAccountStatus(account.accountId()));
  }

  @Test
  void returnsNotFoundWhenAccountDoesNotExist() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/accounts/999/status")
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "LOCKED"
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("account summary is not found"));
  }

  private String internalServiceAuthorization(String subject, InternalServiceScope scope) {
    return "Bearer " + internalServiceTokenIssuer.issue(subject, java.util.Set.of(scope));
  }

  private String loadAccountStatus(long accountId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT account_status
        FROM bank_account
        WHERE id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId),
        String.class);
  }
}
