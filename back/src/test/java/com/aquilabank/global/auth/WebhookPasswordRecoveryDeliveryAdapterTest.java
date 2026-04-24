package com.aquilabank.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryResult;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliverySkipReason;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.global.config.PasswordRecoveryDeliveryProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebhookPasswordRecoveryDeliveryAdapterTest {

  @Test
  void sendsEmailWebhookToSnapshotDestination() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://email-provider.example/recovery"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer delivery-secret"))
        .andExpect(header("Idempotency-Key", "request-1"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.channel").value("EMAIL"))
        .andExpect(jsonPath("$.requestId").value("request-1"))
        .andExpect(jsonPath("$.destination").value("alice@example.com"))
        .andExpect(jsonPath("$.recoveryToken").value("plain-recovery-token"))
        .andRespond(withAccepted());

    WebhookPasswordRecoveryDeliveryAdapter adapter =
        new WebhookPasswordRecoveryDeliveryAdapter(builder.build(), properties());

    PasswordRecoveryDeliveryResult result = adapter.deliver(emailCommand());

    assertThat(result.sent()).isTrue();
    server.verify();
  }

  @Test
  void sendsSmsWebhookToSnapshotDestination() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://sms-provider.example/recovery"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "request-2"))
        .andExpect(jsonPath("$.channel").value("SMS"))
        .andExpect(jsonPath("$.destination").value("+821012345678"))
        .andRespond(withAccepted());

    WebhookPasswordRecoveryDeliveryAdapter adapter =
        new WebhookPasswordRecoveryDeliveryAdapter(builder.build(), properties());

    PasswordRecoveryDeliveryResult result = adapter.deliver(phoneCommand());

    assertThat(result.sent()).isTrue();
    server.verify();
  }

  @Test
  void skipsWhenChannelUrlIsMissing() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WebhookPasswordRecoveryDeliveryAdapter adapter =
        new WebhookPasswordRecoveryDeliveryAdapter(
            builder.build(),
            new PasswordRecoveryDeliveryProperties(
                true,
                "Authorization",
                "Bearer delivery-secret",
                "Idempotency-Key",
                3000,
                5000,
                new PasswordRecoveryDeliveryProperties.ChannelProperties(null),
                new PasswordRecoveryDeliveryProperties.ChannelProperties(
                    "https://sms-provider.example/recovery")));

    PasswordRecoveryDeliveryResult result =
        adapter.deliver(
            new PasswordRecoveryDeliveryCommand(
                "request-3",
                9L,
                VerifiedContactChannel.EMAIL,
                "alice@example.com",
                "plain-recovery-token",
                Instant.parse("2026-04-22T00:15:00Z"),
                Instant.parse("2026-04-22T00:00:00Z")));

    assertThat(result.sent()).isFalse();
    assertThat(result.skipReason())
        .isEqualTo(PasswordRecoveryDeliverySkipReason.PROVIDER_URL_MISSING);
    server.verify();
  }

  private PasswordRecoveryDeliveryProperties properties() {
    return new PasswordRecoveryDeliveryProperties(
        true,
        "Authorization",
        "Bearer delivery-secret",
        "Idempotency-Key",
        3000,
        5000,
        new PasswordRecoveryDeliveryProperties.ChannelProperties(
            "https://email-provider.example/recovery"),
        new PasswordRecoveryDeliveryProperties.ChannelProperties(
            "https://sms-provider.example/recovery"));
  }

  private PasswordRecoveryDeliveryCommand emailCommand() {
    return new PasswordRecoveryDeliveryCommand(
        "request-1",
        7L,
        VerifiedContactChannel.EMAIL,
        "alice@example.com",
        "plain-recovery-token",
        Instant.parse("2026-04-22T00:15:00Z"),
        Instant.parse("2026-04-22T00:00:00Z"));
  }

  private PasswordRecoveryDeliveryCommand phoneCommand() {
    return new PasswordRecoveryDeliveryCommand(
        "request-2",
        8L,
        VerifiedContactChannel.SMS,
        "+821012345678",
        "plain-recovery-token",
        Instant.parse("2026-04-22T00:15:00Z"),
        Instant.parse("2026-04-22T00:00:00Z"));
  }
}
