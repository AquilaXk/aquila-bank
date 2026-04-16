package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;

/** resource server 검증과 같은 shared secret으로 access token을 발급합니다. */
public class HmacAccessTokenIssuer implements AuthTokenIssuePort {

  private final SecretKeySpec secretKeySpec;
  private final String issuer;
  private final long accessTokenTtlSeconds;

  public HmacAccessTokenIssuer(
      SecretKeySpec secretKeySpec, String issuer, long accessTokenTtlSeconds) {
    this.secretKeySpec = secretKeySpec;
    this.issuer = issuer;
    this.accessTokenTtlSeconds = accessTokenTtlSeconds;
  }

  @Override
  public LoginResult issue(long userId, String subject) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .claim("user_id", userId);
    if (issuer != null && !issuer.isBlank()) {
      claims.issuer(issuer);
    }

    SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
            claims.build());
    try {
      signedJwt.sign(new MACSigner(secretKeySpec.getEncoded()));
    } catch (JOSEException ex) {
      throw new IllegalStateException("access token signing failed", ex);
    }
    return new LoginResult(signedJwt.serialize(), "Bearer", expiresAt, userId);
  }
}
