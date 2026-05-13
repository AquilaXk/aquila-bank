package com.aquilabank.global.config;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/** customer application 외부 provider webhook 실행 설정입니다. */
@ConfigurationProperties(prefix = "customer-application.external-execution")
public record CustomerApplicationExternalExecutionProperties(
    boolean enabled,
    String authHeaderName,
    String authHeaderValue,
    String idempotencyHeaderName,
    int connectTimeoutMs,
    int readTimeoutMs,
    Map<CustomerApplicationType, EndpointProperties> endpoints) {

  public CustomerApplicationExternalExecutionProperties {
    authHeaderName =
        StringUtils.hasText(authHeaderName) ? authHeaderName : HttpHeaders.AUTHORIZATION;
    authHeaderValue = StringUtils.hasText(authHeaderValue) ? authHeaderValue : "";
    idempotencyHeaderName =
        StringUtils.hasText(idempotencyHeaderName) ? idempotencyHeaderName : "Idempotency-Key";
    connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 3000;
    readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 5000;
    endpoints = endpoints == null ? Map.of() : Map.copyOf(endpoints);
  }

  public String endpointUrl(CustomerApplicationType type) {
    EndpointProperties endpoint = endpoints.get(type);
    return endpoint == null ? null : endpoint.url();
  }

  public boolean hasEndpoint(CustomerApplicationType type) {
    return StringUtils.hasText(endpointUrl(type));
  }

  public record EndpointProperties(String url) {}
}
