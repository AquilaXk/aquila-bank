package com.aquilabank.global.security;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.port.TransactionDetailReadPort;
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
class TransactionDetailJwtSecurityIntegrationTest {

  private static final String TEST_SECRET = "test-local-jwt-secret-test-local-jwt-secret";

  @Autowired private WebApplicationContext context;

  @MockitoBean private AccountAccessUseCase accountAccessUseCase;

  @MockitoBean private TransactionReadPort transactionReadPort;

  @MockitoBean private TransactionDetailReadPort transactionDetailReadPort;

  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void acceptsBearerJwtAndReturnsMappedTransactionDetail() throws Exception {
    when(accountAccessUseCase.verify(55L, 555L, AccountAccessScope.READ)).thenReturn(555L);
    when(transactionDetailReadPort.find(
            argThat(
                query -> query.accountId() == 555L && "TX-1".equals(query.transactionReference()))))
        .thenReturn(
            Optional.of(
                new TransactionDetail(
                    555L,
                    "TX-1",
                    TransactionDirection.DEBIT,
                    TransactionStatus.BOOKED,
                    5000L,
                    10000L,
                    "KRW",
                    "salary",
                    "COMPANY",
                    Instant.parse("2026-04-16T09:00:00Z"),
                    "ENT-1",
                    TransactionStatus.BOOKED,
                    Instant.parse("2026-04-16T09:00:01Z"),
                    "salary")));

    mockMvc
        .perform(
            get("/api/v1/transactions/TX-1")
                .header("Authorization", "Bearer " + issueToken("user-555", 55L))
                .param("accountId", "555"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(555L))
        .andExpect(jsonPath("$.transactionReference").value("TX-1"))
        .andExpect(jsonPath("$.entryReference").value("ENT-1"));
  }

  @Test
  void rejectsRequestWithoutJwtOrBootstrapHeader() throws Exception {
    mockMvc
        .perform(get("/api/v1/transactions/TX-1").param("accountId", "555"))
        .andExpect(status().isUnauthorized());
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
