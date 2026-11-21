package com.aquilabank.global.web.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.ledger.exception.SavedRecipientNotFoundException;
import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import com.aquilabank.domain.ledger.usecase.SavedRecipientUseCase;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
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

class SavedRecipientControllerTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  private SavedRecipientUseCase savedRecipientUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    savedRecipientUseCase = mock(SavedRecipientUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SavedRecipientController(savedRecipientUseCase))
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
  void listsSavesAndDeletesRecipients() throws Exception {
    authenticateUser(7L);
    SavedRecipient saved =
        new SavedRecipient(15L, 7L, "생활비 계좌", "AQUILA", "110-123-456789", "KRW", true, NOW, NOW);
    when(savedRecipientUseCase.list(7L)).thenReturn(List.of(saved));
    when(savedRecipientUseCase.save(any(SaveSavedRecipientCommand.class))).thenReturn(saved);

    mockMvc
        .perform(get("/api/v1/transfers/recipients"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].recipientId").value(15L))
        .andExpect(jsonPath("$.items[0].nickname").value("생활비 계좌"))
        .andExpect(jsonPath("$.items[0].favorite").value(true));

    mockMvc
        .perform(
            post("/api/v1/transfers/recipients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nickname": "생활비 계좌",
                      "accountNumber": "110-123-456789",
                      "currencyCode": "KRW",
                      "favorite": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.institutionCode").value("AQUILA"));

    mockMvc
        .perform(
            post("/api/v1/transfers/recipients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nickname": "외부 계좌",
                      "institutionCode": "AQUILA",
                      "accountNumber": "110-123-456789",
                      "currencyCode": "KRW"
                    }
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/transfers/recipients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nickname": "빈 기관코드",
                      "institutionCode": " ",
                      "accountNumber": "110-123-456789",
                      "currencyCode": "KRW"
                    }
                    """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(delete("/api/v1/transfers/recipients/15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(true));

    verify(savedRecipientUseCase).delete(7L, 15L);

    org.mockito.Mockito.doThrow(new SavedRecipientNotFoundException("saved recipient not found"))
        .when(savedRecipientUseCase)
        .delete(7L, 999L);
    mockMvc.perform(delete("/api/v1/transfers/recipients/999")).andExpect(status().isNotFound());
  }

  private void authenticateUser(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, "tester"), null, List.of()));
  }
}
