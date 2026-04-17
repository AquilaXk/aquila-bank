package com.aquilabank.global.security;

import com.aquilabank.global.config.OutboxOpsProperties;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/** 내부 운영 API는 public JWT와 분리된 service JWT로만 접근시키는 검증기입니다. */
public class InternalServiceTokenVerifier {

  private static final String BEARER_PREFIX = "Bearer ";

  private final InternalServiceTokenProperties properties;

  public InternalServiceTokenVerifier(
      InternalServiceTokenProperties properties,
      AccountBootstrapApiProperties accountBootstrapApiProperties,
      AuthBootstrapApiProperties authBootstrapApiProperties,
      OutboxOpsProperties outboxOpsProperties) {
    this.properties = properties;
    properties.validateWhenEnabled(
        accountBootstrapApiProperties.enabled()
            || authBootstrapApiProperties.enabled()
            || outboxOpsProperties.enabled());
  }

  public InternalServiceTokenClaims verify(HttpServletRequest request) {
    try {
      String token = extractBearerToken(request);
      SignedJWT signedJwt = SignedJWT.parse(token);
      validateHeader(signedJwt);
      String keyId = signedJwt.getHeader().getKeyID();
      if (!signedJwt.verify(
          new MACVerifier(properties.secretOf(keyId).getBytes(StandardCharsets.UTF_8)))) {
        throw invalid();
      }
      JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();
      validateClaims(claimsSet);
      return new InternalServiceTokenClaims(
          keyId,
          claimsSet.getSubject(),
          claimsSet.getIssuer(),
          resolveAudience(claimsSet),
          InternalServiceTokenIssuer.parseScopes((String) claimsSet.getClaim("scope")),
          toInstant(claimsSet.getIssueTime()),
          toInstant(claimsSet.getExpirationTime()));
    } catch (ParseException | com.nimbusds.jose.JOSEException ex) {
      throw invalid();
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw invalid();
    }
  }

  private String extractBearerToken(HttpServletRequest request) {
    String authorization = request.getHeader(properties.authorizationHeader());
    if (authorization == null
        || authorization.isBlank()
        || !authorization.startsWith(BEARER_PREFIX)
        || authorization.length() <= BEARER_PREFIX.length()) {
      throw invalid();
    }
    return authorization.substring(BEARER_PREFIX.length()).trim();
  }

  private void validateHeader(SignedJWT signedJwt) {
    if (!JWSAlgorithm.HS256.equals(signedJwt.getHeader().getAlgorithm())) {
      throw invalid();
    }
    if (signedJwt.getHeader().getType() != null
        && !JOSEObjectType.JWT.equals(signedJwt.getHeader().getType())) {
      throw invalid();
    }
    if (signedJwt.getHeader().getKeyID() == null || signedJwt.getHeader().getKeyID().isBlank()) {
      throw invalid();
    }
  }

  private void validateClaims(JWTClaimsSet claimsSet) {
    Instant now = Instant.now();
    Instant issuedAt = toInstant(claimsSet.getIssueTime());
    Instant expiresAt = toInstant(claimsSet.getExpirationTime());
    if (claimsSet.getIssuer() == null
        || claimsSet.getIssuer().isBlank()
        || !claimsSet.getIssuer().equals(properties.issuer())) {
      throw invalid();
    }
    if (claimsSet.getSubject() == null || claimsSet.getSubject().isBlank()) {
      throw invalid();
    }
    List<String> audiences = claimsSet.getAudience();
    if (audiences == null || audiences.isEmpty() || !audiences.contains(properties.audience())) {
      throw invalid();
    }
    if (issuedAt == null || issuedAt.isAfter(now.plusSeconds(properties.clockSkewSeconds()))) {
      throw invalid();
    }
    if (expiresAt == null || expiresAt.isBefore(now.minusSeconds(properties.clockSkewSeconds()))) {
      throw invalid();
    }
    Set<String> scopes =
        InternalServiceTokenIssuer.parseScopes((String) claimsSet.getClaim("scope"));
    if (scopes.isEmpty()) {
      throw invalid();
    }
  }

  private String resolveAudience(JWTClaimsSet claimsSet) {
    List<String> audiences = claimsSet.getAudience();
    if (audiences == null || audiences.isEmpty()) {
      throw invalid();
    }
    return audiences.get(0);
  }

  private Instant toInstant(Date value) {
    return value == null ? null : value.toInstant();
  }

  private BootstrapApiAccessDeniedException invalid() {
    return new BootstrapApiAccessDeniedException("internal service token is invalid");
  }
}
