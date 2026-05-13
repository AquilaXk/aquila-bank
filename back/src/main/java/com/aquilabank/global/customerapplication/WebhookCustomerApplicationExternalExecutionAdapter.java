package com.aquilabank.global.customerapplication;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExternalExecutionPort;
import com.aquilabank.global.config.CustomerApplicationExternalExecutionProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 외부 provider가 명시 설정된 신청만 webhook으로 dispatch합니다. */
public final class WebhookCustomerApplicationExternalExecutionAdapter
    implements CustomerApplicationExternalExecutionPort {

  private static final Logger log =
      LoggerFactory.getLogger(WebhookCustomerApplicationExternalExecutionAdapter.class);

  private final RestClient restClient;
  private final CustomerApplicationExternalExecutionProperties properties;

  public WebhookCustomerApplicationExternalExecutionAdapter(
      RestClient restClient, CustomerApplicationExternalExecutionProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  @Override
  public CustomerApplicationExecutionResult execute(
      CustomerApplicationDetails application, String actorSubject, String requestId) {
    if (!properties.enabled()) {
      return CustomerApplicationExecutionResult.failed(
          "EXTERNAL_EXECUTION_NOT_CONFIGURED",
          Map.of(
              "applicationType", application.applicationType().name(), "providerType", "WEBHOOK"));
    }
    String url = properties.endpointUrl(application.applicationType());
    if (!StringUtils.hasText(url)) {
      return CustomerApplicationExecutionResult.failed(
          "EXTERNAL_EXECUTION_PROVIDER_URL_MISSING",
          Map.of(
              "applicationType", application.applicationType().name(), "providerType", "WEBHOOK"));
    }

    try {
      RestClient.RequestBodySpec requestSpec =
          restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON);
      if (StringUtils.hasText(properties.authHeaderValue())) {
        requestSpec.header(properties.authHeaderName(), properties.authHeaderValue());
      }
      requestSpec.header(
          properties.idempotencyHeaderName(), application.applicationReference() + ":" + requestId);
      requestSpec
          .body(
              new ExternalExecutionWebhookRequest(
                  application.applicationReference(),
                  application.applicationType().name(),
                  application.userId(),
                  application.accountId(),
                  actorSubject,
                  requestId,
                  application.payload()))
          .retrieve()
          .toBodilessEntity();
      log.info(
          "customer application external execution dispatched reference={} type={} requestId={}",
          application.applicationReference(),
          application.applicationType(),
          requestId);
      return CustomerApplicationExecutionResult.pendingExternal(
          "EXTERNAL_EXECUTION_DISPATCHED",
          Map.of(
              "applicationType",
              application.applicationType().name(),
              "providerType",
              "WEBHOOK",
              "requestId",
              requestId));
    } catch (RestClientException exception) {
      log.warn(
          "customer application external execution dispatch failed reference={} type={} requestId={}",
          application.applicationReference(),
          application.applicationType(),
          requestId,
          exception);
      return CustomerApplicationExecutionResult.failed(
          "EXTERNAL_EXECUTION_PROVIDER_DISPATCH_FAILED",
          Map.of(
              "applicationType",
              application.applicationType().name(),
              "providerType",
              "WEBHOOK",
              "error",
              exception.getClass().getSimpleName()));
    }
  }

  private record ExternalExecutionWebhookRequest(
      String applicationReference,
      String applicationType,
      long userId,
      Long accountId,
      String actorSubject,
      String requestId,
      Map<String, Object> payload) {}
}
