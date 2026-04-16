package com.aquilabank.global.security;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableConfigurationProperties({SecurityJwtProperties.class, BootstrapHeaderAuthProperties.class})
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ObjectProvider<BootstrapHeaderAuthenticationFilter> bootstrapHeaderAuthenticationFilter,
      JwtDecoder jwtDecoder)
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
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt ->
                        jwt.decoder(jwtDecoder)
                            .jwtAuthenticationConverter(new JwtAccountAuthenticationConverter())))
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
  @ConditionalOnProperty(name = "security.bootstrap-header-auth.enabled", havingValue = "true")
  BootstrapHeaderAuthenticationFilter bootstrapHeaderAuthenticationFilter(
      BootstrapHeaderAuthProperties bootstrapHeaderAuthProperties) {
    return new BootstrapHeaderAuthenticationFilter(
        bootstrapHeaderAuthProperties.accountIdHeader(),
        bootstrapHeaderAuthProperties.subjectHeader());
  }

  @Bean
  JwtDecoder jwtDecoder(SecurityJwtProperties securityJwtProperties) {
    SecretKeySpec secretKeySpec =
        new SecretKeySpec(
            securityJwtProperties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "HmacSHA256");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKeySpec).macAlgorithm(MacAlgorithm.HS256).build();

    OAuth2TokenValidator<Jwt> validator =
        securityJwtProperties.issuer() == null || securityJwtProperties.issuer().isBlank()
            ? JwtValidators.createDefault()
            : JwtValidators.createDefaultWithIssuer(securityJwtProperties.issuer());
    decoder.setJwtValidator(validator);
    return decoder;
  }
}
