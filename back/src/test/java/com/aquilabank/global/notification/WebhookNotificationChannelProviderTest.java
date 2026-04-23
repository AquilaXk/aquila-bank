package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import com.aquilabank.global.config.NotificationChannelProviderDeliveryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebhookNotificationChannelProviderTest {

  @Test
  void sendsEmailWebhookWhenLoginIdMatchesEmailChannel() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://email-provider.example/notifications"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer notification-secret"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.channel").value("EMAIL"))
        .andExpect(jsonPath("$.deliveryKey").value("evt-provider-1"))
        .andExpect(jsonPath("$.destination").value("alice@example.com"))
        .andExpect(jsonPath("$.payload.title").value("Transfer complete"))
        .andRespond(withAccepted());

    NotificationChannelRecipientLookupPort recipientLookupPort =
        userId -> Optional.of("alice@example.com");
    WebhookNotificationChannelProvider provider =
        new WebhookNotificationChannelProvider(
            builder.build(),
            recipientLookupPort,
            new NotificationChannelDeliveryDestinationResolver(),
            properties(),
            new ObjectMapper().findAndRegisterModules());

    provider.send(emailItem());

    server.verify();
  }

  @Test
  void sendsSmsWebhookWhenLoginIdMatchesSmsChannel() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://sms-provider.example/notifications"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.channel").value("SMS"))
        .andExpect(jsonPath("$.destination").value("+821012345678"))
        .andRespond(withAccepted());

    NotificationChannelRecipientLookupPort recipientLookupPort =
        userId -> Optional.of("+821012345678");
    WebhookNotificationChannelProvider provider =
        new WebhookNotificationChannelProvider(
            builder.build(),
            recipientLookupPort,
            new NotificationChannelDeliveryDestinationResolver(),
            properties(),
            new ObjectMapper().findAndRegisterModules());

    provider.send(smsItem());

    server.verify();
  }

  @Test
  void skipsWhenLoginIdDoesNotMatchRequestedChannel() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    NotificationChannelRecipientLookupPort recipientLookupPort =
        userId -> Optional.of("+821012345678");
    WebhookNotificationChannelProvider provider =
        new WebhookNotificationChannelProvider(
            builder.build(),
            recipientLookupPort,
            new NotificationChannelDeliveryDestinationResolver(),
            properties(),
            new ObjectMapper().findAndRegisterModules());

    assertThatCode(() -> provider.send(emailItem())).doesNotThrowAnyException();

    server.verify();
  }

  @Test
  void skipsWhenChannelUrlIsMissing() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    NotificationChannelRecipientLookupPort recipientLookupPort =
        userId -> Optional.of("alice@example.com");
    WebhookNotificationChannelProvider provider =
        new WebhookNotificationChannelProvider(
            builder.build(),
            recipientLookupPort,
            new NotificationChannelDeliveryDestinationResolver(),
            new NotificationChannelProviderDeliveryProperties(
                true,
                "Authorization",
                "Bearer notification-secret",
                3000,
                5000,
                new NotificationChannelProviderDeliveryProperties.ChannelProperties(null),
                new NotificationChannelProviderDeliveryProperties.ChannelProperties(
                    "https://sms-provider.example/notifications")),
            new ObjectMapper().findAndRegisterModules());

    assertThatCode(() -> provider.send(emailItem())).doesNotThrowAnyException();

    server.verify();
  }

  @Test
  void propagatesProviderFailureForRetryBackoff() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://email-provider.example/notifications"))
        .andRespond(withServerError());

    NotificationChannelRecipientLookupPort recipientLookupPort =
        userId -> Optional.of("alice@example.com");
    WebhookNotificationChannelProvider provider =
        new WebhookNotificationChannelProvider(
            builder.build(),
            recipientLookupPort,
            new NotificationChannelDeliveryDestinationResolver(),
            properties(),
            new ObjectMapper().findAndRegisterModules());

    assertThatThrownBy(() -> provider.send(emailItem())).isInstanceOf(RuntimeException.class);

    server.verify();
  }

  private NotificationChannelProviderDeliveryProperties properties() {
    return new NotificationChannelProviderDeliveryProperties(
        true,
        "Authorization",
        "Bearer notification-secret",
        3000,
        5000,
        new NotificationChannelProviderDeliveryProperties.ChannelProperties(
            "https://email-provider.example/notifications"),
        new NotificationChannelProviderDeliveryProperties.ChannelProperties(
            "https://sms-provider.example/notifications"));
  }

  private NotificationChannelOutboxItem emailItem() {
    return item(
        1L,
        NotificationPreferenceChannel.EMAIL,
        """
        {"title":"Transfer complete","message":"10,000 won sent","createdAt":"2026-04-23T09:00:00Z"}
        """);
  }

  private NotificationChannelOutboxItem smsItem() {
    return item(
        2L,
        NotificationPreferenceChannel.SMS,
        """
        {"title":"Security alert","message":"New login detected","createdAt":"2026-04-23T09:00:00Z"}
        """);
  }

  private NotificationChannelOutboxItem item(
      long id, NotificationPreferenceChannel channel, String payload) {
    Instant now = Instant.parse("2026-04-23T09:00:00Z");
    return new NotificationChannelOutboxItem(
        id,
        100L + id,
        7L,
        33L,
        NotificationPreferenceCategory.TRANSACTIONAL,
        channel,
        "TransferBooked",
        "evt-provider-" + id,
        payload,
        NotificationChannelDeliveryStatus.SENDING,
        now.minusSeconds(1),
        null,
        0,
        null,
        now,
        now);
  }
}
