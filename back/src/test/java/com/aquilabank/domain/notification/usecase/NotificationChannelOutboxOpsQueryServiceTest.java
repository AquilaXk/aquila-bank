package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxQuarantinedItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsReadPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationChannelOutboxOpsQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-23T09:00:00Z");

  private NotificationChannelOutboxOpsReadPort readPort;
  private NotificationChannelOutboxOpsQueryService service;

  @BeforeEach
  void setUp() {
    readPort = mock(NotificationChannelOutboxOpsReadPort.class);
    service = new NotificationChannelOutboxOpsQueryService(readPort);
  }

  @Test
  void returnsQuarantinedItemsFromPort() {
    List<NotificationChannelOutboxQuarantinedItem> items = List.of(item(11L));
    when(readPort.findQuarantinedItems(20)).thenReturn(items);

    List<NotificationChannelOutboxQuarantinedItem> result = service.getQuarantinedItems(20);

    assertThat(result).containsExactlyElementsOf(items);
    verify(readPort).findQuarantinedItems(20);
  }

  @Test
  void rejectsNonPositiveLimit() {
    assertThatThrownBy(() -> service.getQuarantinedItems(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be positive");
  }

  private NotificationChannelOutboxQuarantinedItem item(long id) {
    return new NotificationChannelOutboxQuarantinedItem(
        id,
        100L + id,
        200L + id,
        300L + id,
        NotificationPreferenceCategory.TRANSACTIONAL,
        NotificationPreferenceChannel.EMAIL,
        "TransferBooked",
        "evt-channel-" + id,
        10,
        "provider rejected",
        NOW.minusSeconds(60),
        NOW);
  }
}
