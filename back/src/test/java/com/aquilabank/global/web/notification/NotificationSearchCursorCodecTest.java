package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationSearchCursorCodecTest {

  @Test
  void encodesAndDecodesSearchCursor() {
    NotificationSearchCursor cursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-21T09:00:00Z"),
            41L,
            Instant.parse("2026-03-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"),
            "UNREAD|TransferBooked|2026-03-21T00:00:00Z|2026-04-21T00:00:00Z");

    String encoded = NotificationSearchCursorCodec.encode(cursor);

    assertThat(NotificationSearchCursorCodec.decode(encoded)).isEqualTo(cursor);
  }

  @Test
  void rejectsBrokenCursorPayload() {
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode("broken"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
  }
}
