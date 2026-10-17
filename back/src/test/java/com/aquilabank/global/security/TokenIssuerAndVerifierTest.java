package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.global.config.OutboxOpsProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TokenIssuerAndVerifierTest {

  private static final String VALID_SECRET = "test-secret-with-at-least-32-bytes-length";

  @Test
  void hmacAccessTokenIssuerIssuesValidTokenAndRejectsInvalidInputs() throws Exception {
    SecretKeySpec keySpec =
        new SecretKeySpec(VALID_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    HmacAccessTokenIssuer issuerWithIssuer = new HmacAccessTokenIssuer(keySpec, "aquila-bank", 300);
    HmacAccessTokenIssuer issuerWithoutIssuer = new HmacAccessTokenIssuer(keySpec, " ", 300);
    HmacAccessTokenIssuer issuerWithNullIssuer = new HmacAccessTokenIssuer(keySpec, null, 300);

    Instant now = Instant.parse("2026-04-20T10:00:00Z");
    IssuedAccessToken token1 = issuerWithIssuer.issue(7L, "alice", 11L, now);
    IssuedAccessToken token2 = issuerWithoutIssuer.issue(7L, "alice", 11L, now);
    IssuedAccessToken token3 = issuerWithNullIssuer.issue(7L, "alice", 11L, now);

    com.nimbusds.jose.crypto.MACVerifier macVerifier =
        new com.nimbusds.jose.crypto.MACVerifier(keySpec);
    com.nimbusds.jwt.SignedJWT parsed1 = com.nimbusds.jwt.SignedJWT.parse(token1.accessToken());
    com.nimbusds.jwt.SignedJWT parsed2 = com.nimbusds.jwt.SignedJWT.parse(token2.accessToken());
    com.nimbusds.jwt.SignedJWT parsed3 = com.nimbusds.jwt.SignedJWT.parse(token3.accessToken());

    assertThat(parsed1.verify(macVerifier)).isTrue();
    assertThat(parsed2.verify(macVerifier)).isTrue();
    assertThat(parsed3.verify(macVerifier)).isTrue();
    assertThat(parsed1.getJWTClaimsSet().getSubject()).isEqualTo("alice");
    assertThat(parsed1.getJWTClaimsSet().getLongClaim("user_id")).isEqualTo(7L);
    assertThat(parsed1.getJWTClaimsSet().getLongClaim("session_id")).isEqualTo(11L);
    assertThat(parsed1.getJWTClaimsSet().getIssuer()).isEqualTo("aquila-bank");
    assertThat(parsed1.getJWTClaimsSet().getExpirationTime().toInstant())
        .isEqualTo(now.plusSeconds(300));
    assertThat(parsed2.getJWTClaimsSet().getIssuer()).isNull();
    assertThat(parsed3.getJWTClaimsSet().getIssuer()).isNull();
    assertThrows(
        IllegalArgumentException.class, () -> issuerWithIssuer.issue(7L, "alice", 0L, now));
    assertThrows(
        IllegalStateException.class,
        () ->
            new HmacAccessTokenIssuer(
                new SecretKeySpec("short".getBytes(StandardCharsets.UTF_8), "HmacSHA256"),
                "aquila",
                300));
  }

  @Test
  void internalServiceTokenIssuerAndVerifierReuseCachedMacAndValidateClaims() {
    InternalServiceTokenProperties properties =
        new InternalServiceTokenProperties(
            "test-internal-service",
            "aquila-internal-api",
            "Authorization",
            30,
            300,
            "ops-202604",
            Map.of(
                "ops-202604", "test-internal-service-secret-ops-202604",
                "ops-202603", "test-internal-service-secret-ops-202603"));

    InternalServiceTokenIssuer issuer = new InternalServiceTokenIssuer(properties);
    InternalServiceTokenVerifier verifier =
        new InternalServiceTokenVerifier(
            properties,
            new AccountBootstrapApiProperties(true, "X-Bootstrap-Token", "token"),
            new AuthBootstrapApiProperties(false, "X-Auth-Bootstrap-Token", ""),
            new OutboxOpsProperties(
                false, "X-Outbox-Ops-Token", "", 20, new OutboxOpsProperties.Health(120, 10, 0)));

    String jwt = issuer.issue("ops-admin", Set.of(InternalServiceScope.AUTH_ADMIN));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + jwt);

    InternalServiceTokenClaims claims = verifier.verify(request);
    assertThat(claims.subject()).isEqualTo("ops-admin");
    assertThat(claims.keyId()).isEqualTo("ops-202604");
    assertThat(claims.scopes()).containsExactly("internal:auth-admin");

    assertThrows(
        IllegalArgumentException.class,
        () -> issuer.issue(" ", Set.of(InternalServiceScope.AUTH_ADMIN)));
    assertThrows(IllegalArgumentException.class, () -> issuer.issue("ops", Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> issuer.issue("ops", Set.of(InternalServiceScope.AUTH_ADMIN), null, 300));
    assertThrows(
        IllegalArgumentException.class,
        () -> issuer.issue("ops", Set.of(InternalServiceScope.AUTH_ADMIN), Instant.now(), 0));

    // Unconfigured properties when internal APIs are disabled must construct cleanly
    InternalServiceTokenProperties unconfigured =
        new InternalServiceTokenProperties(" ", " ", "Authorization", 30, 300, " ", Map.of());
    InternalServiceTokenIssuer unconfiguredIssuer = new InternalServiceTokenIssuer(unconfigured);
    InternalServiceTokenVerifier unconfiguredVerifier =
        new InternalServiceTokenVerifier(
            unconfigured,
            new AccountBootstrapApiProperties(false, "X-Bootstrap-Token", ""),
            new AuthBootstrapApiProperties(false, "X-Auth-Bootstrap-Token", ""),
            new OutboxOpsProperties(
                false, "X-Outbox-Ops-Token", "", 20, new OutboxOpsProperties.Health(120, 10, 0)));
    assertThrows(
        IllegalStateException.class,
        () -> unconfiguredIssuer.issue("ops", Set.of(InternalServiceScope.AUTH_ADMIN)));
    assertThrows(
        BootstrapApiAccessDeniedException.class, () -> unconfiguredVerifier.verify(request));

    // Too-short secret fails fast
    InternalServiceTokenProperties shortSecretProps =
        new InternalServiceTokenProperties(
            "issuer", "aud", "Authorization", 30, 300, "k1", Map.of("k1", "short"));
    assertThrows(
        IllegalStateException.class, () -> new InternalServiceTokenIssuer(shortSecretProps));
    assertThrows(
        IllegalStateException.class,
        () ->
            new InternalServiceTokenVerifier(
                shortSecretProps,
                new AccountBootstrapApiProperties(false, "X-Bootstrap-Token", ""),
                new AuthBootstrapApiProperties(false, "X-Auth-Bootstrap-Token", ""),
                new OutboxOpsProperties(
                    false,
                    "X-Outbox-Ops-Token",
                    "",
                    20,
                    new OutboxOpsProperties.Health(120, 10, 0))));
  }
}
