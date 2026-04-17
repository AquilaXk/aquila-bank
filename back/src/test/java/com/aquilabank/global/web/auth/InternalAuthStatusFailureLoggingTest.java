package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aquilabank.domain.auth.usecase.AuthUserQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipStatusUpdateUseCase;
import com.aquilabank.domain.auth.usecase.UserStatusUpdateUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenAuthenticationInterceptor;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.InternalAuthStatusAuditRequestCachingFilter;
import com.aquilabank.global.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalAuthStatusFailureLoggingTest {

  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private ListAppender<ILoggingEvent> listAppender;
  private Logger apiExceptionHandlerLogger;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AuthUserQueryUseCase authUserQueryUseCase = Mockito.mock(AuthUserQueryUseCase.class);
    UserStatusUpdateUseCase userStatusUpdateUseCase = Mockito.mock(UserStatusUpdateUseCase.class);
    UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase =
        Mockito.mock(UserAccountMembershipQueryUseCase.class);
    UserAccountMembershipStatusUpdateUseCase userAccountMembershipStatusUpdateUseCase =
        Mockito.mock(UserAccountMembershipStatusUpdateUseCase.class);
    var authorizer = InternalServiceTokenTestSupport.authorizer();

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalAuthAdminController(
                    authUserQueryUseCase,
                    userStatusUpdateUseCase,
                    userAccountMembershipQueryUseCase,
                    userAccountMembershipStatusUpdateUseCase,
                    authorizer))
            .addInterceptors(new InternalServiceTokenAuthenticationInterceptor(authorizer))
            .addFilters(new RequestIdFilter(), new InternalAuthStatusAuditRequestCachingFilter())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    apiExceptionHandlerLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    apiExceptionHandlerLogger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    apiExceptionHandlerLogger.detachAppender(listAppender);
  }

  @Test
  void logsBadRequestWithHttpStatusAndRequestId() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "ops-admin", InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "missing-subject-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userStatus": "DISABLED"
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(lastMessage())
        .contains("internal auth status update failed")
        .contains("requestId=missing-subject-request")
        .contains("httpStatus=400")
        .contains("actorSubject=ops-admin")
        .contains("requestedStatus=DISABLED")
        .contains("reasonCode=-")
        .contains("reasonDetail=-")
        .contains("error=reasonCode is required");
  }

  @Test
  void logsUnauthorizedWithHttpStatusAndRequestId() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/status")
                .header(REQUEST_ID_HEADER, "invalid-token-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userStatus": "DISABLED",
                      "reasonCode": "FRAUD_REVIEW",
                      "reasonDetail": "fraud-review"
                    }
                    """))
        .andExpect(status().isUnauthorized());

    assertThat(lastMessage())
        .contains("internal auth status update failed")
        .contains("requestId=invalid-token-request")
        .contains("httpStatus=401")
        .contains("actorSubject=-")
        .contains("requestedStatus=-")
        .contains("reasonCode=-")
        .contains("reasonDetail=-")
        .contains("error=internal service token is invalid");
  }

  private String lastMessage() {
    assertThat(listAppender.list).isNotEmpty();
    return listAppender.list.get(listAppender.list.size() - 1).getFormattedMessage();
  }
}
