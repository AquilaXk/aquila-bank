package com.aquilabank.global.web.security;

import com.aquilabank.global.security.InternalServiceTokenAuthenticationInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** security 기반 argument resolver 등록 */
@Configuration
public class WebMvcSecurityConfiguration implements WebMvcConfigurer {

  private final CurrentAuthenticatedPrincipalArgumentResolver
      currentAuthenticatedPrincipalArgumentResolver;
  private final InternalServiceTokenAuthenticationInterceptor
      internalServiceTokenAuthenticationInterceptor;

  public WebMvcSecurityConfiguration(
      CurrentAuthenticatedPrincipalArgumentResolver currentAuthenticatedPrincipalArgumentResolver,
      InternalServiceTokenAuthenticationInterceptor internalServiceTokenAuthenticationInterceptor) {
    this.currentAuthenticatedPrincipalArgumentResolver =
        currentAuthenticatedPrincipalArgumentResolver;
    this.internalServiceTokenAuthenticationInterceptor =
        internalServiceTokenAuthenticationInterceptor;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentAuthenticatedPrincipalArgumentResolver);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(internalServiceTokenAuthenticationInterceptor)
        .addPathPatterns(
            "/internal/api/v1/accounts/bootstrap",
            "/internal/api/v1/accounts/status-change-audits/**",
            "/internal/api/v1/accounts/*/status",
            "/internal/api/v1/auth/**",
            "/internal/api/v1/outbox/**");
  }
}
