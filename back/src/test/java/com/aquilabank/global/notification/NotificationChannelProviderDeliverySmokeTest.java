package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.aquilabank.domain.notification.model.NotificationChannelDeliverySkipReason;
import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerService;
import com.aquilabank.global.config.NotificationChannelProviderDeliveryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NotificationChannelProviderDeliverySmokeTest {

  private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");

  @Test
  void dispatchesImmediateEmailAndMarksProviderRejectedSmsForRetryBackoff() throws Exception {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://email-provider.example/notifications"))
        .andRespond(withAccepted());
    // 실제 socket 대신 mock provider 5xx로 retry 경로를 고정해 CI runner 부하 영향을 제거합니다.
    server
        .expect(requestTo("https://sms-provider.example/notifications"))
        .andRespond(withServerError());

    NotificationChannelProviderDeliveryProperties properties =
        new NotificationChannelProviderDeliveryProperties(
            true,
            "Authorization",
            "Bearer smoke-secret",
            500,
            50,
            new NotificationChannelProviderDeliveryProperties.ChannelProperties(
                "https://email-provider.example/notifications"),
            new NotificationChannelProviderDeliveryProperties.ChannelProperties(
                "https://sms-provider.example/notifications"));
    NotificationChannelRecipientLookupPort recipientLookupPort =
        (userId, channel) ->
            switch ((int) userId) {
              case 101 -> Optional.of("alice@example.com");
              case 202 -> Optional.of("+821012345678");
              default -> Optional.empty();
            };
    WebhookNotificationChannelProvider provider =
        new WebhookNotificationChannelProvider(
            builder.build(),
            recipientLookupPort,
            properties,
            new ObjectMapper().findAndRegisterModules());
    RecordingDispatchPort dispatchPort =
        new RecordingDispatchPort(List.of(emailItem(), smsItem()), NOW);
    NotificationChannelProviderWorkerService service =
        new NotificationChannelProviderWorkerService(
            dispatchPort,
            provider,
            Clock.fixed(NOW, ZoneOffset.UTC),
            2,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            10);

    int claimed = service.dispatchDueDeliveries();

    assertThat(claimed).isEqualTo(2);
    server.verify();
    assertThat(dispatchPort.sentIds()).containsExactly(1L);
    assertThat(dispatchPort.failedItems()).containsOnlyKeys(2L);
    assertThat(dispatchPort.failedItems().get(2L).nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(dispatchPort.failedItems().get(2L).errorMessage()).isNotBlank();
  }

  private NotificationChannelOutboxItem emailItem() {
    return item(1L, 101L, NotificationPreferenceChannel.EMAIL, "evt-provider-email");
  }

  private NotificationChannelOutboxItem smsItem() {
    return item(2L, 202L, NotificationPreferenceChannel.SMS, "evt-provider-sms");
  }

  private NotificationChannelOutboxItem item(
      long id, long userId, NotificationPreferenceChannel channel, String eventKey) {
    return new NotificationChannelOutboxItem(
        id,
        10L + id,
        userId,
        30L + id,
        NotificationPreferenceCategory.TRANSACTIONAL,
        channel,
        "TransferBooked",
        eventKey,
        "{\"title\":\"Transfer complete\",\"message\":\"10,000 won sent\"}",
        NotificationChannelDeliveryStatus.SENDING,
        NOW.minusSeconds(1),
        null,
        0,
        null,
        NOW.minusSeconds(10),
        NOW);
  }

  private record FailedItem(Instant nextAttemptAt, String errorMessage) {}

  private static final class RecordingDispatchPort
      implements NotificationChannelOutboxDispatchPort {

    private final List<NotificationChannelOutboxItem> items;
    private final Instant expectedNow;
    private final java.util.List<Long> sentIds = new java.util.ArrayList<>();
    private final Map<Long, FailedItem> failedItems = new ConcurrentHashMap<>();

    private RecordingDispatchPort(List<NotificationChannelOutboxItem> items, Instant expectedNow) {
      this.items = items;
      this.expectedNow = expectedNow;
    }

    @Override
    public List<NotificationChannelOutboxItem> claimPending(int limit, Instant now) {
      assertThat(limit).isEqualTo(2);
      assertThat(now).isEqualTo(expectedNow);
      return items;
    }

    @Override
    public void markSent(long id, Instant sentAt) {
      assertThat(sentAt).isEqualTo(expectedNow);
      sentIds.add(id);
    }

    @Override
    public void markSkipped(
        long id, Instant skippedAt, NotificationChannelDeliverySkipReason skipReason) {
      throw new AssertionError("skip is not expected in smoke");
    }

    @Override
    public void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage) {
      assertThat(failedAt).isEqualTo(expectedNow);
      failedItems.put(id, new FailedItem(nextAttemptAt, errorMessage));
    }

    @Override
    public void markQuarantined(long id, Instant quarantinedAt, String errorMessage) {
      throw new AssertionError("quarantine is not expected in smoke");
    }

    private java.util.List<Long> sentIds() {
      return sentIds;
    }

    private Map<Long, FailedItem> failedItems() {
      return failedItems;
    }
  }
}
