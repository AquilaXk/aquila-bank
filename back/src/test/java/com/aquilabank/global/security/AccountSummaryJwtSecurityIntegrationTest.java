package com.aquilabank.global.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.port.AccountSummaryReadPort;
import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AccountSummaryJwtSecurityIntegrationTest {

  private static final String TEST_SECRET = "test-local-jwt-secret-test-local-jwt-secret";

  @Autowired private WebApplicationContext context;

  @MockitoBean private AccountAccessUseCase accountAccessUseCase;

  @MockitoBean private AccountSummaryReadPort accountSummaryReadPort;

  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void acceptsBearerJwtAndReturnsMappedAccountSummary() throws Exception {
    when(accountAccessUseCase.verify(55L, 555L, AccountAccessScope.READ)).thenReturn(555L);
    when(accountSummaryReadPort.findByAccountId(555L))
        .thenReturn(
            Optional.of(
                new AccountSummary(
                    555L,
                    "100000000000001",
                    "salary account",
                    "ACTIVE",
                    "KRW",
                    15000L,
                    0L,
                    Instant.parse("2026-04-16T09:00:00Z"),
                    Instant.parse("2026-04-16T09:05:00Z"))));

    mockMvc
        .perform(
            get("/api/v1/accounts/555")
                .header("Authorization", "Bearer " + issueToken("user-555", 55L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(555L))
        .andExpect(jsonPath("$.availableBalanceMinor").value(15000L))
        .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
  }

  @Test
  void rejectsRequestWithoutJwtOrBootstrapHeader() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/555")).andExpect(status().isUnauthorized());
  }

  private String issueToken(String subject, long userId) throws JOSEException {
    Instant now = Instant.now();
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("user_id", userId)
            .build();

    SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claimsSet);
    signedJwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
    return signedJwt.serialize();
  }
}
