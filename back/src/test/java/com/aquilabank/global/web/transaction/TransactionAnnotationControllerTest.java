package com.aquilabank.global.web.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import com.aquilabank.domain.transaction.model.TransactionCategory;
import com.aquilabank.domain.transaction.usecase.TransactionAnnotationUseCase;
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

class TransactionAnnotationControllerTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  private TransactionAnnotationUseCase annotationUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    annotationUseCase = mock(TransactionAnnotationUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new TransactionAnnotationController(
                    annotationUseCase, requestAccountAuthorizationService))
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
  void batchLoadsAndSavesTransactionAnnotations() throws Exception {
    authenticateUser(7L);
    TransactionAnnotation annotation =
        new TransactionAnnotation(7L, 101L, "TRX-1", TransactionCategory.LIVING, "장보기", NOW);

    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenReturn(101L);
    when(annotationUseCase.findByReferences(7L, 101L, List.of("TRX-1", "TRX-2")))
        .thenReturn(List.of(annotation));
    when(annotationUseCase.save(any(SaveTransactionAnnotationCommand.class)))
        .thenReturn(annotation);

    mockMvc
        .perform(
            get("/api/v1/transactions/annotations")
                .queryParam("accountId", "101")
                .queryParam("references", "TRX-1", "TRX-2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].transactionReference").value("TRX-1"))
        .andExpect(jsonPath("$.items[0].category").value("LIVING"))
        .andExpect(jsonPath("$.items[0].memo").value("장보기"));

    mockMvc
        .perform(
            put("/api/v1/transactions/TRX-1/annotation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":101,\"category\":\"LIVING\",\"memo\":\"장보기\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("LIVING"));
  }

  private void authenticateUser(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, "tester"), null, List.of()));
  }
}
