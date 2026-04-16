package com.aquilabank.global.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ObjectProvider<BootstrapHeaderAuthenticationFilter> bootstrapHeaderAuthenticationFilter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exception ->
                exception.authenticationEntryPoint(
                    new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)));

    BootstrapHeaderAuthenticationFilter filter =
        bootstrapHeaderAuthenticationFilter.getIfAvailable();
    if (filter != null) {
      http.addFilterBefore(filter, AnonymousAuthenticationFilter.class);
    }
    return http.build();
  }

  @Bean
  @Profile({"dev", "test"})
  BootstrapHeaderAuthenticationFilter bootstrapHeaderAuthenticationFilter(
      @Value("${security.bootstrap-header-auth.account-id-header:X-Account-Id}")
          String accountIdHeader,
      @Value("${security.bootstrap-header-auth.subject-header:X-Subject}") String subjectHeader) {
    return new BootstrapHeaderAuthenticationFilter(accountIdHeader, subjectHeader);
  }
}
