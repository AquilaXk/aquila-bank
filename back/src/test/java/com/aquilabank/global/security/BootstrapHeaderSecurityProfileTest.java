package com.aquilabank.global.security;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("prod")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=false",
      "management.health.db.enabled=false",
      "spring.datasource.url=jdbc:postgresql://localhost:5432/aquila_bank_test",
      "spring.datasource.username=postgres",
      "spring.datasource.password=postgres",
      "spring.datasource.hikari.initialization-fail-timeout=0",
      "security.bootstrap-header-auth.enabled=true",
      "security.jwt.secret=test-local-jwt-secret-test-local-jwt-secret",
      "security.jwt.access-token-ttl-seconds=300"
    })
class BootstrapHeaderSecurityProfileTest {

  @Autowired private ObjectProvider<BootstrapHeaderAuthenticationFilter> filterProvider;

  @Test
  void doesNotRegisterBootstrapHeaderFilterOutsideDevAndTest() {
    assertNull(filterProvider.getIfAvailable());
  }
}
