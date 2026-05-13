package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/** notification channel provider webhook delivery 설정입니다. */
@ConfigurationProperties(prefix = "notification.channel-provider.delivery")
public record NotificationChannelProviderDeliveryProperties(
    boolean enabled,
    String authHeaderName,
    String authHeaderValue,
    String idempotencyHeaderName,
    int connectTimeoutMs,
    int readTimeoutMs,
    ChannelProperties email,
    ChannelProperties sms) {

  public NotificationChannelProviderDeliveryProperties {
    authHeaderName =
        StringUtils.hasText(authHeaderName) ? authHeaderName : HttpHeaders.AUTHORIZATION;
    authHeaderValue = StringUtils.hasText(authHeaderValue) ? authHeaderValue : "";
    idempotencyHeaderName =
        StringUtils.hasText(idempotencyHeaderName) ? idempotencyHeaderName : "Idempotency-Key";
    connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 3000;
    readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 5000;
    email = email == null ? new ChannelProperties(null) : email;
    sms = sms == null ? new ChannelProperties(null) : sms;
  }

  public record ChannelProperties(String url) {

    public boolean configured() {
      return StringUtils.hasText(url);
    }
  }
}
