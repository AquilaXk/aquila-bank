package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.global.auth.PasswordRecoveryDestinationResolver.PasswordRecoveryChannel;
import com.aquilabank.global.auth.PasswordRecoveryDestinationResolver.ResolvedDestination;
import com.aquilabank.global.config.PasswordRecoveryDeliveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** contact schema 없이 잘못된 외부 전달을 막기 위해 email/E.164 phone 형식만 webhook으로 넘깁니다. */
public final class WebhookPasswordRecoveryDeliveryAdapter implements PasswordRecoveryDeliveryPort {

  private static final Logger log =
      LoggerFactory.getLogger(WebhookPasswordRecoveryDeliveryAdapter.class);

  private final RestClient restClient;
  private final PasswordRecoveryDestinationResolver destinationResolver;
  private final PasswordRecoveryDeliveryProperties properties;

  public WebhookPasswordRecoveryDeliveryAdapter(
      RestClient restClient,
      PasswordRecoveryDestinationResolver destinationResolver,
      PasswordRecoveryDeliveryProperties properties) {
    this.restClient = restClient;
    this.destinationResolver = destinationResolver;
    this.properties = properties;
  }

  @Override
  public void deliver(PasswordRecoveryDeliveryCommand command) {
    ResolvedDestination destination = destinationResolver.resolve(command.loginId()).orElse(null);
    if (destination == null) {
      log.warn(
          "password recovery delivery skipped due to unsupported loginId format requestId={} userId={}",
          command.requestId(),
          command.userId());
      return;
    }

    String url = targetUrl(destination.channel());
    if (!StringUtils.hasText(url)) {
      log.warn(
          "password recovery delivery skipped because provider URL is missing requestId={} userId={} channel={}",
          command.requestId(),
          command.userId(),
          destination.channel());
      return;
    }

    RestClient.RequestBodySpec requestSpec =
        restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON);
    if (StringUtils.hasText(properties.authHeaderValue())) {
      requestSpec.header(properties.authHeaderName(), properties.authHeaderValue());
    }
    requestSpec
        .body(
            new PasswordRecoveryWebhookRequest(
                destination.channel().name(),
                command.requestId(),
                command.userId(),
                command.loginId(),
                destination.value(),
                command.recoveryToken(),
                command.expiresAt().toString(),
                command.issuedAt().toString()))
        .retrieve()
        .toBodilessEntity();

    log.info(
        "password recovery delivery dispatched requestId={} userId={} channel={} expiresAt={}",
        command.requestId(),
        command.userId(),
        destination.channel(),
        command.expiresAt());
  }

  private String targetUrl(PasswordRecoveryChannel channel) {
    return channel == PasswordRecoveryChannel.EMAIL
        ? properties.email().url()
        : properties.sms().url();
  }

  private record PasswordRecoveryWebhookRequest(
      String channel,
      String requestId,
      long userId,
      String loginId,
      String destination,
      String recoveryToken,
      String expiresAt,
      String issuedAt) {}
}
