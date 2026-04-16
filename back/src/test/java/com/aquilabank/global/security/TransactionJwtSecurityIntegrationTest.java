package com.aquilabank.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.port.TransactionReadPort;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class TransactionJwtSecurityIntegrationTest {

  private static final String TEST_SECRET = "test-local-jwt-secret-test-local-jwt-secret";

  @Autowired private WebApplicationContext context;

  @Autowired private JwtDecoder jwtDecoder;

  @MockitoBean private AccountAccessUseCase accountAccessUseCase;

  @MockitoBean private TransactionReadPort transactionReadPort;

  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void acceptsBearerJwtAndResolvesUserAccountAccess() throws Exception {
    when(transactionReadPort.fetch(argThat(query -> query.accountId() == 555L)))
        .thenReturn(
            new TransactionSlice(
                List.of(
                    new TransactionSummary(
                        1L,
                        555L,
                        "TX-1",
                        TransactionDirection.CREDIT,
                        TransactionStatus.BOOKED,
                        5000L,
                        15000L,
                        "KRW",
                        "salary",
                        "COMPANY",
                        Instant.parse("2026-04-16T09:00:00Z"))),
                null,
                false,
                20));
    when(accountAccessUseCase.verify(55L, 555L, AccountAccessScope.READ)).thenReturn(555L);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + issueToken("user-555", 55L))
                .param("accountId", "555")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].accountId").value(555));
  }

  @Test
  void rejectsRequestWithoutJwtOrBootstrapHeader() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/transactions")
                .param("accountId", "555")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void decoderReadsSignedToken() throws Exception {
    String token = issueToken("user-777", 777L);
    AuthenticatedUserPrincipal principal =
        (AuthenticatedUserPrincipal)
            new JwtUserAuthenticationConverter().convert(jwtDecoder.decode(token)).getPrincipal();
    assertEquals(777L, principal.userId());
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
