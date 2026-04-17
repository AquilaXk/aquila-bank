package com.aquilabank.global.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.model.AccountSummaryList;
import com.aquilabank.domain.account.port.AccountListReadPort;
import com.aquilabank.domain.account.port.AccountSummaryReadPort;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AccountListJwtSecurityIntegrationTest {

  private static final String TEST_SECRET = "test-local-jwt-secret-test-local-jwt-secret";

  @Autowired private WebApplicationContext context;

  @MockitoBean private AccountListReadPort accountListReadPort;

  @MockitoBean private AccountSummaryReadPort accountSummaryReadPort;

  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void acceptsBearerJwtAndReturnsMappedAccountList() throws Exception {
    when(accountListReadPort.findByUserId(55L))
        .thenReturn(
            new AccountSummaryList(
                List.of(
                    new AccountSummary(
                        555L,
                        "100000000000001",
                        "salary account",
                        "ACTIVE",
                        "KRW",
                        15_000L,
                        0L,
                        Instant.parse("2026-04-16T09:00:00Z"),
                        Instant.parse("2026-04-16T09:05:00Z")),
                    new AccountSummary(
                        777L,
                        "100000000000002",
                        "saving account",
                        "ACTIVE",
                        "KRW",
                        20_000L,
                        0L,
                        Instant.parse("2026-04-16T10:00:00Z"),
                        Instant.parse("2026-04-16T10:05:00Z")))));

    mockMvc
        .perform(
            get("/api/v1/accounts").header("Authorization", "Bearer " + issueToken("user-55", 55L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].accountId").value(555L))
        .andExpect(jsonPath("$.items[1].accountId").value(777L))
        .andExpect(jsonPath("$.items[0].availableBalanceMinor").value(15_000L));
  }

  @Test
  void rejectsRequestWithoutJwtOrBootstrapHeader() throws Exception {
    mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
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
