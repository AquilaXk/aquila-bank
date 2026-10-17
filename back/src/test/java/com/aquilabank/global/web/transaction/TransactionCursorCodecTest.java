package com.aquilabank.global.web.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TransactionCursorCodecTest {

  @Test
  void roundTrip() {
    TransactionCursor cursor =
        new TransactionCursor(Instant.parse("2026-04-16T10:15:30.123456789Z"), 99L);

    String encoded = TransactionCursorCodec.encode(cursor);
    byte[] rawBytes = Base64.getUrlDecoder().decode(encoded);
    TransactionCursor decoded = TransactionCursorCodec.decode(encoded);

    assertEquals(52, rawBytes.length);
    assertEquals(cursor, decoded);
  }

  @Test
  void invalidCursorThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode("broken-cursor"));
    assertThrows(IllegalArgumentException.class, () -> TransactionCursorCodec.decode(""));
    assertThrows(IllegalArgumentException.class, () -> TransactionCursorCodec.decode(null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TransactionCursorCodec.decode(
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[20])));
  }

  @Test
  void tamperedCursorPayloadOrSignatureThrows() {
    TransactionCursor cursor =
        new TransactionCursor(Instant.parse("2026-04-16T10:15:30.123456Z"), 99L);
    byte[] raw = Base64.getUrlDecoder().decode(TransactionCursorCodec.encode(cursor));

    byte[] tamperedPayload = raw.clone();
    tamperedPayload[19] ^= 0x01;
    String tamperedPayloadCursor =
        Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayload);
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode(tamperedPayloadCursor));

    byte[] tamperedMac = raw.clone();
    tamperedMac[51] ^= 0x01;
    String tamperedMacCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedMac);
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode(tamperedMacCursor));
  }
}
