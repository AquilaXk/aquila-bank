package com.aquilabank.global.web.customerapplication;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSubmitUseCase;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerApplicationControllerTest {

  private CustomerApplicationSubmitUseCase customerApplicationSubmitUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    customerApplicationSubmitUseCase = mock(CustomerApplicationSubmitUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new CustomerApplicationController(
                    customerApplicationSubmitUseCase, requestAccountAuthorizationService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void submitsApplicationForAuthenticatedUserWithAccountAuthorization() throws Exception {
    authenticateUser(7L);
    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenReturn(101L);
    when(customerApplicationSubmitUseCase.submit(
            argThat(command -> command.applicationType() == CustomerApplicationType.BILL_PAYMENT)))
        .thenReturn(submission());

    mockMvc
        .perform(
            post("/api/v1/customer-service/applications")
                .header("Idempotency-Key", "bill-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "applicationType": "BILL_PAYMENT",
                      "accountId": 101,
                      "totpCode": "123456",
                      "payload": {
                        "billerCode": "GIRO",
                        "paymentNumber": "1234567890"
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationReference").value("CSA-20260511-001"))
        .andExpect(jsonPath("$.applicationType").value("BILL_PAYMENT"))
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.mfaVerified").value(true));

    verify(customerApplicationSubmitUseCase)
        .submit(
            argThat(
                command ->
                    command.userId() == 7L
                        && Long.valueOf(101L).equals(command.accountId())
                        && "bill-001".equals(command.idempotencyKey())
                        && "123456".equals(command.totpCode())));
  }

  @Test
  void rejectsSensitivePayloadKeysBeforeSubmit() throws Exception {
    authenticateUser(7L);

    mockMvc
        .perform(
            post("/api/v1/customer-service/applications")
                .header("Idempotency-Key", "cert-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "applicationType": "CERTIFICATE_ISSUANCE",
                      "totpCode": "123456",
                      "payload": {
                        "certificatePrivateKey": "must-not-store"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(customerApplicationSubmitUseCase);
  }

  @Test
  void rejectsMissingAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customer-service/applications")
                .header("Idempotency-Key", "bill-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "applicationType": "BILL_PAYMENT",
                      "totpCode": "123456",
                      "payload": {}
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsBootstrapPrincipalForCustomerApplication() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customer-service/applications")
                .header("X-Account-Id", "101")
                .header("Idempotency-Key", "bill-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "applicationType": "BILL_PAYMENT",
                      "totpCode": "123456",
                      "payload": {}
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidPayloadShapeDirectly() {
    CustomerApplicationController controller =
        new CustomerApplicationController(
            customerApplicationSubmitUseCase, requestAccountAuthorizationService);
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(7L, "tester");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            controller.submit(
                principal,
                "payload-null",
                new CustomerApplicationController.CustomerApplicationRequest(
                    CustomerApplicationType.BILL_PAYMENT, null, "123456", null)));

    LinkedHashMap<String, Object> tooManyFields = new LinkedHashMap<>();
    for (int index = 0; index < 31; index++) {
      tooManyFields.put("field" + index, "value");
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            controller.submit(
                principal,
                "payload-large",
                new CustomerApplicationController.CustomerApplicationRequest(
                    CustomerApplicationType.BILL_PAYMENT, null, "123456", tooManyFields)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            controller.submit(
                principal,
                "payload-blank",
                new CustomerApplicationController.CustomerApplicationRequest(
                    CustomerApplicationType.BILL_PAYMENT, null, "123456", Map.of("", "value"))));
  }

  private void authenticateUser(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, "tester"), null, List.of()));
  }

  private static CustomerApplicationSubmission submission() {
    Instant now = Instant.parse("2026-05-11T03:00:00Z");
    return new CustomerApplicationSubmission(
        "CSA-20260511-001",
        7L,
        101L,
        CustomerApplicationType.BILL_PAYMENT,
        CustomerApplicationStatus.SUBMITTED,
        true,
        now,
        now,
        now);
  }
}
