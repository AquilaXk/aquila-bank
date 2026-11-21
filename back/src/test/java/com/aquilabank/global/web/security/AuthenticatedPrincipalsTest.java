package com.aquilabank.global.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthenticatedPrincipalsTest {

  @Test
  void returnsUserPrincipalAndRejectsBootstrapPrincipal() {
    AuthenticatedUserPrincipal user = new AuthenticatedUserPrincipal(7L, "tester");
    assertThat(AuthenticatedPrincipals.requireUserPrincipal(user)).isSameAs(user);

    AuthenticatedAccountPrincipal bootstrap = new AuthenticatedAccountPrincipal(101L, "bootstrap");
    assertThatThrownBy(() -> AuthenticatedPrincipals.requireUserPrincipal(bootstrap))
        .isInstanceOf(ResponseStatusException.class);
  }
}
