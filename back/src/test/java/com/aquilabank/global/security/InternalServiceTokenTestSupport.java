package com.aquilabank.global.security;

import com.aquilabank.global.config.OutboxOpsProperties;
import java.util.Map;
import java.util.Set;

/** standalone test가 운영과 같은 내부 service JWT 계약을 그대로 재사용하게 맞춥니다. */
public final class InternalServiceTokenTestSupport {

  private static final InternalServiceTokenProperties PROPERTIES =
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

  private static final InternalServiceTokenIssuer ISSUER =
      new InternalServiceTokenIssuer(PROPERTIES);

  private InternalServiceTokenTestSupport() {}

  public static InternalServiceRequestAuthorizer authorizer() {
    return new InternalServiceRequestAuthorizer(
        new InternalServiceTokenVerifier(
            PROPERTIES,
            new AccountBootstrapApiProperties(
                true, "X-Bootstrap-Token", "legacy-account-bootstrap"),
            new AuthBootstrapApiProperties(true, "X-Auth-Bootstrap-Token", "legacy-auth-bootstrap"),
            new OutboxOpsProperties(
                true,
                "X-Outbox-Ops-Token",
                "legacy-outbox-ops",
                20,
                new OutboxOpsProperties.Health(120, 10, 0))));
  }

  public static String authorization(String subject, InternalServiceScope... scopes) {
    return "Bearer " + ISSUER.issue(subject, Set.of(scopes));
  }
}
