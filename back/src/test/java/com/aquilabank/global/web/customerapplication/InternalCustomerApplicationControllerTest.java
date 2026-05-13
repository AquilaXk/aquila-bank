package com.aquilabank.global.web.customerapplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationOperationUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalCustomerApplicationControllerTest {

  private static final String SUBJECT = "customer-ops-test";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private CustomerApplicationOperationUseCase operationUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    operationUseCase = mock(CustomerApplicationOperationUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalCustomerApplicationController(
                    operationUseCase, InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void approvesApplicationWithInternalCustomerOpsScope() throws Exception {
    when(operationUseCase.apply(
            argThat(
                command ->
                    command != null && command.action() == CustomerApplicationAction.APPROVE)))
        .thenReturn(details(CustomerApplicationStatus.APPROVED));

    mockMvc
        .perform(
            post("/internal/api/v1/customer-service/applications/CSA-001/approve")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.CUSTOMER_APPLICATION_OPS))
                .header(REQUEST_ID_HEADER, "customer-application-approve-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "documents checked"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationReference").value("CSA-001"))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.processedBy").value(SUBJECT));

    verify(operationUseCase)
        .apply(
            argThat(
                command ->
                    command.applicationReference().equals("CSA-001")
                        && command.action() == CustomerApplicationAction.APPROVE
                        && command.actorSubject().equals(SUBJECT)
                        && command.reason().equals("documents checked")
                        && command.requestId().equals("customer-application-approve-request")));
  }

  @Test
  void handlesReviewRejectCancelAndExecuteActions() throws Exception {
    when(operationUseCase.apply(any()))
        .thenAnswer(
            invocation -> {
              var command =
                  (com.aquilabank.domain.customerapplication.model
                          .CustomerApplicationDecisionCommand)
                      invocation.getArgument(0);
              CustomerApplicationStatus status =
                  switch (command.action()) {
                    case START_REVIEW -> CustomerApplicationStatus.REVIEWING;
                    case REJECT -> CustomerApplicationStatus.REJECTED;
                    case CANCEL -> CustomerApplicationStatus.CANCELLED;
                    case EXECUTE -> CustomerApplicationStatus.FAILED;
                    case APPROVE -> CustomerApplicationStatus.APPROVED;
                  };
              return details(status);
            });

    performOperation("review").andExpect(jsonPath("$.status").value("REVIEWING"));
    performOperation("reject").andExpect(jsonPath("$.status").value("REJECTED"));
    performOperation("cancel").andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc
        .perform(
            post("/internal/api/v1/customer-service/applications/CSA-001/execute")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.CUSTOMER_APPLICATION_OPS))
                .header(REQUEST_ID_HEADER, "customer-application-execute-request")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"));
  }

  @Test
  void rejectsMissingRequestId() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/customer-service/applications/CSA-001/approve")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.CUSTOMER_APPLICATION_OPS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "documents checked"
                    }
                    """))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("requestId is not initialized"));
  }

  @Test
  void rejectsMissingOrWrongInternalServiceToken() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/customer-service/applications/CSA-001/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "documents checked"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));

    mockMvc
        .perform(
            post("/internal/api/v1/customer-service/applications/CSA-001/approve")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.ACCOUNT_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "documents checked"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  private static CustomerApplicationDetails details(CustomerApplicationStatus status) {
    Instant now = Instant.parse("2026-05-13T03:00:00Z");
    return new CustomerApplicationDetails(
        "CSA-001",
        7L,
        101L,
        CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
        status,
        true,
        now,
        Map.of("requestedSingleTransferLimitMinor", 100_000L),
        now,
        now,
        "documents checked",
        SUBJECT,
        now,
        Map.of());
  }

  private org.springframework.test.web.servlet.ResultActions performOperation(String action)
      throws Exception {
    return mockMvc
        .perform(
            post("/internal/api/v1/customer-service/applications/CSA-001/" + action)
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.CUSTOMER_APPLICATION_OPS))
                .header(REQUEST_ID_HEADER, "customer-application-" + action + "-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "documents checked"
                    }
                    """))
        .andExpect(status().isOk());
  }
}
