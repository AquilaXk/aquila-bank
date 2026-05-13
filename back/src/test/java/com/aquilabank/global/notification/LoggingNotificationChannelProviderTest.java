package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationChannelDeliverySkipReason;
import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoggingNotificationChannelProviderTest {

  @Test
  void skipsDeliveryBecauseFallbackProviderDoesNotSendExternally() {
    LoggingNotificationChannelProvider provider = new LoggingNotificationChannelProvider();

    var result = provider.send(item());

    assertThat(result.sent()).isFalse();
    assertThat(result.skipReason())
        .isEqualTo(NotificationChannelDeliverySkipReason.PROVIDER_DISABLED);
  }

  private NotificationChannelOutboxItem item() {
    Instant now = Instant.parse("2026-05-13T03:00:00Z");
    return new NotificationChannelOutboxItem(
        1L,
        10L,
        20L,
        30L,
        NotificationPreferenceCategory.TRANSACTIONAL,
        NotificationPreferenceChannel.EMAIL,
        "TransferBooked",
        "evt-logging-provider",
        "{\"kind\":\"transfer\"}",
        NotificationChannelDeliveryStatus.SENDING,
        now.minusSeconds(1),
        null,
        0,
        null,
        now.minusSeconds(10),
        now);
  }
}
