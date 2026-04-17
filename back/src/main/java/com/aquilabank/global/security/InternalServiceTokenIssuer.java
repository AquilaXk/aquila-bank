package com.aquilabank.global.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/** 내부 운영 script/test가 같은 형식의 service JWT를 만들 수 있게 발급 규칙을 고정합니다. */
public class InternalServiceTokenIssuer {

  private static final String SCOPE_CLAIM = "scope";

  private final InternalServiceTokenProperties properties;

  public InternalServiceTokenIssuer(InternalServiceTokenProperties properties) {
    this.properties = properties;
  }

  public String issue(String subject, Set<InternalServiceScope> scopes) {
    return issue(subject, scopes, Instant.now(), properties.defaultTtlSeconds());
  }

  public String issue(
      String subject, Set<InternalServiceScope> scopes, Instant issuedAt, long ttlSeconds) {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    if (scopes == null || scopes.isEmpty()) {
      throw new IllegalArgumentException("scopes is required");
    }
    if (issuedAt == null) {
      throw new IllegalArgumentException("issuedAt is required");
    }
    if (ttlSeconds <= 0) {
      throw new IllegalArgumentException("ttlSeconds must be positive");
    }

    Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
    Set<String> scopeValues = new LinkedHashSet<>();
    for (InternalServiceScope scope : scopes) {
      scopeValues.add(scope.claimValue());
    }

    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(properties.issuer())
            .subject(subject)
            .audience(properties.audience())
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .claim(SCOPE_CLAIM, String.join(" ", scopeValues))
            .build();

    SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .keyID(properties.activeKeyId())
                .build(),
            claimsSet);

    try {
      signedJwt.sign(new MACSigner(properties.activeSecret().getBytes(StandardCharsets.UTF_8)));
    } catch (JOSEException ex) {
      throw new IllegalStateException("internal service token signing failed", ex);
    }
    return signedJwt.serialize();
  }

  static Set<String> parseScopes(String rawScope) {
    if (rawScope == null || rawScope.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(rawScope.trim().split("\\s+"))
        .filter(value -> !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }
}
