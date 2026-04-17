package com.aquilabank.global.security;

import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.global.web.InternalAuthStatusAuditRequestCachingFilter;
import com.aquilabank.global.web.RequestIdFilter;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/** bootstrap auth와 JWT resource server를 함께 조립하는 security 설정 */
@Configuration
@EnableConfigurationProperties({
  SecurityJwtProperties.class,
  LoginProtectionProperties.class,
  BootstrapHeaderAuthProperties.class,
  AccountBootstrapApiProperties.class,
  AuthBootstrapApiProperties.class
})
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RequestIdFilter requestIdFilter,
      InternalAuthStatusAuditRequestCachingFilter internalAuthStatusAuditRequestCachingFilter,
      ObjectProvider<BootstrapHeaderAuthenticationFilter> bootstrapHeaderAuthenticationFilter,
      JwtDecoder jwtDecoder)
      throws Exception {
    // stateless API 기본선
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
                    .requestMatchers("/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/refresh")
                    .permitAll()
                    // 내부 bootstrap API는 JWT 대신 별도 shared token으로 보호합니다.
                    .requestMatchers("/internal/api/v1/accounts/bootstrap")
                    .permitAll()
                    .requestMatchers("/internal/api/v1/outbox/**")
                    .permitAll()
                    .requestMatchers("/internal/api/v1/auth/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt ->
                        jwt.decoder(jwtDecoder)
                            .jwtAuthenticationConverter(new JwtUserAuthenticationConverter())))
        .exceptionHandling(
            exception ->
                exception.authenticationEntryPoint(
                    new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)));

    http.addFilterBefore(requestIdFilter, AnonymousAuthenticationFilter.class);
    http.addFilterAfter(internalAuthStatusAuditRequestCachingFilter, RequestIdFilter.class);

    BootstrapHeaderAuthenticationFilter filter =
        bootstrapHeaderAuthenticationFilter.getIfAvailable();
    if (filter != null) {
      // request-id를 먼저 심고 그 다음에 bootstrap auth를 해석합니다.
      http.addFilterAfter(filter, RequestIdFilter.class);
    }
    return http.build();
  }

  @Bean
  RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }

  @Bean
  InternalAuthStatusAuditRequestCachingFilter internalAuthStatusAuditRequestCachingFilter() {
    return new InternalAuthStatusAuditRequestCachingFilter();
  }

  @Bean
  @Profile({"dev", "test"})
  @ConditionalOnProperty(name = "security.bootstrap-header-auth.enabled", havingValue = "true")
  BootstrapHeaderAuthenticationFilter bootstrapHeaderAuthenticationFilter(
      BootstrapHeaderAuthProperties bootstrapHeaderAuthProperties) {
    return new BootstrapHeaderAuthenticationFilter(
        bootstrapHeaderAuthProperties.accountIdHeader(),
        bootstrapHeaderAuthProperties.subjectHeader());
  }

  @Bean
  JwtDecoder jwtDecoder(SecurityJwtProperties securityJwtProperties) {
    SecretKeySpec secretKeySpec = secretKeySpec(securityJwtProperties);
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKeySpec).macAlgorithm(MacAlgorithm.HS256).build();

    OAuth2TokenValidator<Jwt> validator =
        // issuer 값 존재 시 issuer 검증 자동 활성화
        securityJwtProperties.issuer() == null || securityJwtProperties.issuer().isBlank()
            ? JwtValidators.createDefault()
            : JwtValidators.createDefaultWithIssuer(securityJwtProperties.issuer());
    decoder.setJwtValidator(validator);
    return decoder;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  PasswordHashPort passwordHashPort(PasswordEncoder passwordEncoder) {
    return new PasswordHashPort() {
      @Override
      public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
      }

      @Override
      public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
      }
    };
  }

  @Bean
  RefreshTokenSecretPort refreshTokenSecretPort() {
    return new Sha256RefreshTokenManager();
  }

  @Bean
  AuthTokenIssuePort authTokenIssuePort(SecurityJwtProperties securityJwtProperties) {
    return new HmacAccessTokenIssuer(
        secretKeySpec(securityJwtProperties),
        securityJwtProperties.issuer(),
        securityJwtProperties.accessTokenTtlSeconds());
  }

  private SecretKeySpec secretKeySpec(SecurityJwtProperties securityJwtProperties) {
    return new SecretKeySpec(
        securityJwtProperties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "HmacSHA256");
  }
}
