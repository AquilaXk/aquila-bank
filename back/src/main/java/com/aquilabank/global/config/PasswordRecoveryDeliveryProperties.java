package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/** password recovery token 전달 adapter 활성화 설정입니다. */
@ConfigurationProperties(prefix = "auth.password-recovery.delivery")
public record PasswordRecoveryDeliveryProperties(
    boolean enabled,
    String authHeaderName,
    String authHeaderValue,
    int connectTimeoutMs,
    int readTimeoutMs,
    ChannelProperties email,
    ChannelProperties sms) {

  public PasswordRecoveryDeliveryProperties {
    authHeaderName =
        StringUtils.hasText(authHeaderName) ? authHeaderName : HttpHeaders.AUTHORIZATION;
    authHeaderValue = StringUtils.hasText(authHeaderValue) ? authHeaderValue : "";
    connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 3000;
    readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 5000;
    email = email == null ? new ChannelProperties(null) : email;
    sms = sms == null ? new ChannelProperties(null) : sms;
  }

  public boolean hasWebhookTarget() {
    return email.configured() || sms.configured();
  }

  public record ChannelProperties(String url) {

    public boolean configured() {
      return StringUtils.hasText(url);
    }
  }
}
