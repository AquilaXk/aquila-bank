package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.notification.model.NotificationCursor;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class NotificationCursorCodecTest {

  @Test
  void encodesAndDecodesCompactBinaryCursorPreservingNanos() {
    NotificationCursor cursor =
        new NotificationCursor(Instant.parse("2026-04-20T09:00:00.123456789Z"), 9L);

    String encoded = NotificationCursorCodec.encode(cursor);

    assertThat(encoded).hasSize(27);
    assertThat(NotificationCursorCodec.decode(encoded)).isEqualTo(cursor);
  }

  @Test
  void rejectsNullOversizedAndMalformedPayloads() {
    String wrongLength = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[19]);
    byte[] badNanoBytes =
        ByteBuffer.allocate(20).putLong(1_700_000_000L).putInt(1_000_000_000).putLong(1L).array();
    String badNano = Base64.getUrlEncoder().withoutPadding().encodeToString(badNanoBytes);
    byte[] zeroIdBytes =
        ByteBuffer.allocate(20).putLong(1_700_000_000L).putInt(0).putLong(0L).array();
    String zeroId = Base64.getUrlEncoder().withoutPadding().encodeToString(zeroIdBytes);

    assertThatThrownBy(() -> NotificationCursorCodec.encode(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NotificationCursorCodec.decode(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationCursorCodec.decode(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationCursorCodec.decode("A".repeat(33)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationCursorCodec.decode("invalid!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationCursorCodec.decode(wrongLength))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationCursorCodec.decode(badNano))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationCursorCodec.decode(zeroId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
  }
}
