package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.global.config.PasswordRecoveryDeliveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** request 시점에 확정된 verified contact snapshot만 provider webhook으로 넘깁니다. */
public final class WebhookPasswordRecoveryDeliveryAdapter implements PasswordRecoveryDeliveryPort {

  private static final Logger log =
      LoggerFactory.getLogger(WebhookPasswordRecoveryDeliveryAdapter.class);

  private final RestClient restClient;
  private final PasswordRecoveryDeliveryProperties properties;

  public WebhookPasswordRecoveryDeliveryAdapter(
      RestClient restClient, PasswordRecoveryDeliveryProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  @Override
  public void deliver(PasswordRecoveryDeliveryCommand command) {
    String url = targetUrl(command.deliveryChannel());
    if (!StringUtils.hasText(url)) {
      log.warn(
          "password recovery delivery skipped because provider URL is missing requestId={} userId={} channel={}",
          command.requestId(),
          command.userId(),
          command.deliveryChannel());
      return;
    }

    RestClient.RequestBodySpec requestSpec =
        restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON);
    if (StringUtils.hasText(properties.authHeaderValue())) {
      requestSpec.header(properties.authHeaderName(), properties.authHeaderValue());
    }
    requestSpec.header(properties.idempotencyHeaderName(), command.requestId());
    requestSpec
        .body(
            new PasswordRecoveryWebhookRequest(
                command.deliveryChannel().name(),
                command.requestId(),
                command.userId(),
                command.providerDestination(),
                command.recoveryToken(),
                command.expiresAt().toString(),
                command.issuedAt().toString()))
        .retrieve()
        .toBodilessEntity();

    log.info(
        "password recovery delivery dispatched requestId={} userId={} channel={} expiresAt={}",
        command.requestId(),
        command.userId(),
        command.deliveryChannel(),
        command.expiresAt());
  }

  private String targetUrl(VerifiedContactChannel channel) {
    return channel == VerifiedContactChannel.EMAIL
        ? properties.email().url()
        : properties.sms().url();
  }

  private record PasswordRecoveryWebhookRequest(
      String channel,
      String requestId,
      long userId,
      String destination,
      String recoveryToken,
      String expiresAt,
      String issuedAt) {}
}
