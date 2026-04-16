package com.aquilabank.global.web.security;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcSecurityConfiguration implements WebMvcConfigurer {

  private final CurrentAccountIdArgumentResolver currentAccountIdArgumentResolver;

  public WebMvcSecurityConfiguration(
      CurrentAccountIdArgumentResolver currentAccountIdArgumentResolver) {
    this.currentAccountIdArgumentResolver = currentAccountIdArgumentResolver;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentAccountIdArgumentResolver);
  }
}
