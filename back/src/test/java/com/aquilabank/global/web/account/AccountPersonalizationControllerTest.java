package com.aquilabank.global.web.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import com.aquilabank.domain.account.usecase.AccountPersonalizationUseCase;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountPersonalizationControllerTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  private AccountPersonalizationUseCase personalizationUseCase;
  private AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    personalizationUseCase = mock(AccountPersonalizationUseCase.class);
    accountSummaryQueryUseCase = mock(AccountSummaryQueryUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AccountPersonalizationController(
                    personalizationUseCase,
                    accountSummaryQueryUseCase,
                    requestAccountAuthorizationService))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void managesAccountPreferencesAndSavingsPockets() throws Exception {
    authenticateUser(7L);
    UserAccountPreference pref = new UserAccountPreference(7L, 101L, "월급 통장", true, NOW);
    SavingsPocket pocket =
        new SavingsPocket(9L, 7L, 101L, "여행 자금", 3_000_000L, 500_000L, "KRW", NOW, NOW);

    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L))
        .thenReturn(
            new AccountSummary(
                101L, "110-123-456789", "주거래", "ACTIVE", "KRW", 5_000_000L, 0L, NOW, NOW));
    when(personalizationUseCase.listPreferences(7L)).thenReturn(List.of(pref));
    when(personalizationUseCase.savePreference(7L, 101L, "월급 통장", true)).thenReturn(pref);
    when(personalizationUseCase.listPockets(7L, 101L)).thenReturn(List.of(pocket));
    when(personalizationUseCase.createPocket(any(CreateSavingsPocketCommand.class)))
        .thenReturn(pocket);
    when(personalizationUseCase.updatePocket(any(UpdateSavingsPocketCommand.class)))
        .thenReturn(pocket);

    mockMvc
        .perform(get("/api/v1/accounts/preferences"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].customAlias").value("월급 통장"));

    mockMvc
        .perform(
            put("/api/v1/accounts/101/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customAlias\":\"월급 통장\",\"primaryAccount\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryAccount").value(true));

    mockMvc
        .perform(get("/api/v1/accounts/101/pockets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].pocketName").value("여행 자금"));

    mockMvc
        .perform(
            post("/api/v1/accounts/101/pockets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"pocketName\":\"여행 자금\",\"targetAmountMinor\":3000000,\"savedAmountMinor\":500000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pocketId").value(9L));

    mockMvc
        .perform(
            put("/api/v1/accounts/101/pockets/9")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"pocketName\":\"여행 자금\",\"targetAmountMinor\":3000000,\"savedAmountMinor\":500000}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/v1/accounts/101/pockets/9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(true));

    verify(personalizationUseCase).deletePocket(7L, 101L, 9L);
  }

  private void authenticateUser(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, "tester"), null, List.of()));
  }
}
