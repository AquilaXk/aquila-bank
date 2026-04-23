package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import com.aquilabank.global.config.NotificationChannelProviderDeliveryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** loginId 형식 기반 routing만 허용해 잘못된 외부 발송을 fail-safe skip 합니다. */
public final class WebhookNotificationChannelProvider implements NotificationChannelProviderPort {

  private static final Logger log =
      LoggerFactory.getLogger(WebhookNotificationChannelProvider.class);

  private final RestClient restClient;
  private final NotificationChannelRecipientLookupPort recipientLookupPort;
  private final NotificationChannelDeliveryDestinationResolver destinationResolver;
  private final NotificationChannelProviderDeliveryProperties properties;
  private final ObjectMapper objectMapper;

  public WebhookNotificationChannelProvider(
      RestClient restClient,
      NotificationChannelRecipientLookupPort recipientLookupPort,
      NotificationChannelDeliveryDestinationResolver destinationResolver,
      NotificationChannelProviderDeliveryProperties properties,
      ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.recipientLookupPort = recipientLookupPort;
    this.destinationResolver = destinationResolver;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public void send(NotificationChannelOutboxItem item) {
    String loginId = recipientLookupPort.findLoginIdByUserId(item.userId()).orElse(null);
    if (!StringUtils.hasText(loginId)) {
      log.warn(
          "notification channel delivery skipped because loginId lookup is missing id={} userId={} channel={}",
          item.id(),
          item.userId(),
          item.channel());
      return;
    }

    String destination = destinationResolver.resolve(item.channel(), loginId).orElse(null);
    if (!StringUtils.hasText(destination)) {
      log.warn(
          "notification channel delivery skipped because destination does not match channel id={} userId={} channel={}",
          item.id(),
          item.userId(),
          item.channel());
      return;
    }

    String url = targetUrl(item.channel());
    if (!StringUtils.hasText(url)) {
      log.warn(
          "notification channel delivery skipped because provider URL is missing id={} userId={} channel={}",
          item.id(),
          item.userId(),
          item.channel());
      return;
    }

    RestClient.RequestBodySpec requestSpec =
        restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON);
    if (StringUtils.hasText(properties.authHeaderValue())) {
      requestSpec.header(properties.authHeaderName(), properties.authHeaderValue());
    }
    requestSpec
        .body(
            new NotificationChannelWebhookRequest(
                item.channel().name(),
                item.eventKey(),
                item.notificationId(),
                item.userId(),
                item.accountId(),
                item.category().name(),
                item.eventType(),
                destination,
                parsePayload(item.payload())))
        .retrieve()
        .toBodilessEntity();

    log.info(
        "notification channel delivery dispatched id={} userId={} channel={} eventKey={}",
        item.id(),
        item.userId(),
        item.channel(),
        item.eventKey());
  }

  private String targetUrl(NotificationPreferenceChannel channel) {
    if (channel == NotificationPreferenceChannel.EMAIL) {
      return properties.email().url();
    }
    if (channel == NotificationPreferenceChannel.SMS) {
      return properties.sms().url();
    }
    return null;
  }

  private Object parsePayload(String rawPayload) {
    try {
      return objectMapper.readValue(rawPayload, Object.class);
    } catch (IOException ex) {
      throw new IllegalArgumentException("notification channel payload must be valid JSON", ex);
    }
  }

  private record NotificationChannelWebhookRequest(
      String channel,
      String deliveryKey,
      long notificationId,
      long userId,
      long accountId,
      String category,
      String eventType,
      String destination,
      Object payload) {}
}
