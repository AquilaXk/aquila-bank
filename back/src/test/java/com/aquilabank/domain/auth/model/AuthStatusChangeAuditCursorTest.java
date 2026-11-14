package com.aquilabank.domain.auth.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AuthStatusChangeAuditCursorTest {

  @Test
  void encodesAndDecodesCompactBinaryCursorPreservingNanos() {
    AuthStatusChangeAuditCursor cursor =
        new AuthStatusChangeAuditCursor(Instant.parse("2026-04-20T09:30:00.987654321Z"), 42L);

    String encoded = cursor.encode();

    assertThat(encoded).hasSize(27);
    assertThat(AuthStatusChangeAuditCursor.decode(encoded)).isEqualTo(cursor);
  }

  @Test
  void rejectsOversizedAndMalformedBinaryPayloads() {
    String zeroAuditId = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[20]);
    byte[] badNanoBytes =
        ByteBuffer.allocate(20).putLong(1_700_000_000L).putInt(-1).putLong(1L).array();
    String badNano = Base64.getUrlEncoder().withoutPadding().encodeToString(badNanoBytes);

    assertThat(AuthStatusChangeAuditCursor.decode(null)).isNull();
    assertThat(AuthStatusChangeAuditCursor.decode(" ")).isNull();
    assertThatThrownBy(() -> AuthStatusChangeAuditCursor.decode("A".repeat(33)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor is invalid");
    assertThatThrownBy(() -> AuthStatusChangeAuditCursor.decode(zeroAuditId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor is invalid");
    assertThatThrownBy(() -> AuthStatusChangeAuditCursor.decode(badNano))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor is invalid");
  }
}
