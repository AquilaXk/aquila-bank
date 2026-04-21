package com.aquilabank.global.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.security.oauth2.client.registration.google.provider=google",
      "spring.security.oauth2.client.registration.google.client-id=test-client",
      "spring.security.oauth2.client.registration.google.client-secret=test-secret",
      "spring.security.oauth2.client.registration.google.authorization-grant-type=authorization_code",
      "spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
      "spring.security.oauth2.client.registration.google.scope=openid",
      "spring.security.oauth2.client.provider.google.authorization-uri=https://idp.example/oauth2/v1/authorize",
      "spring.security.oauth2.client.provider.google.token-uri=https://idp.example/oauth2/v1/token",
      "spring.security.oauth2.client.provider.google.jwk-set-uri=https://idp.example/oauth2/v1/keys",
      "spring.security.oauth2.client.provider.google.user-info-uri=https://idp.example/oauth2/v1/userinfo",
      "spring.security.oauth2.client.provider.google.user-name-attribute=sub"
    })
class OidcLoginSecurityIntegrationTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void redirectsAuthorizationRequestToConfiguredOidcProvider() throws Exception {
    mockMvc
        .perform(get("/oauth2/authorization/google"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            header().string("Location", containsString("https://idp.example/oauth2/v1/authorize")));
  }
}
