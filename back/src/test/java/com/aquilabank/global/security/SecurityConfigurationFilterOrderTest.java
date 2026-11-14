package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.global.web.InternalAuthStatusAuditRequestCachingFilter;
import com.aquilabank.global.web.RequestIdFilter;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
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
class SecurityConfigurationFilterOrderTest {

  @Autowired private FilterChainProxy springSecurityFilterChain;
  @Autowired private WebApplicationContext webApplicationContext;

  @Test
  void runsRequestIdFilterBeforeAllAuthenticationFilters() throws Exception {
    List<Filter> filters = springSecurityFilterChain.getFilters("/api/v1/accounts");

    int requestIdIdx = indexOf(filters, RequestIdFilter.class);
    int cachingIdx = indexOf(filters, InternalAuthStatusAuditRequestCachingFilter.class);
    int securityContextIdx = indexOf(filters, SecurityContextHolderFilter.class);
    int bearerIdx = indexOf(filters, BearerTokenAuthenticationFilter.class);
    int bootstrapIdx = indexOf(filters, BootstrapHeaderAuthenticationFilter.class);
    int anonymousIdx = indexOf(filters, AnonymousAuthenticationFilter.class);

    assertThat(requestIdIdx).isGreaterThanOrEqualTo(0);
    assertThat(cachingIdx).isGreaterThan(requestIdIdx);
    assertThat(requestIdIdx).isLessThan(securityContextIdx);
    assertThat(securityContextIdx).isLessThan(bearerIdx);
    assertThat(bearerIdx).isLessThan(bootstrapIdx);
    assertThat(bootstrapIdx).isLessThan(anonymousIdx);

    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(springSecurityFilterChain)
            .build();

    mockMvc
        .perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer invalid-token")
                .header("X-Request-Id", "req-auth-fail-001"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Request-Id", "req-auth-fail-001"));
  }

  private static int indexOf(List<Filter> filters, Class<? extends Filter> targetType) {
    for (int index = 0; index < filters.size(); index++) {
      if (targetType.isInstance(filters.get(index))) {
        return index;
      }
    }
    return -1;
  }
}
