package com.aquilabank.global.web.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TransactionCursorCodecTest {

  @Test
  void roundTrip() {
    TransactionCursor cursor =
        new TransactionCursor(Instant.parse("2026-04-16T10:15:30.123456789Z"), 99L);

    String encoded = TransactionCursorCodec.encode(101L, cursor);
    byte[] rawBytes = Base64.getUrlDecoder().decode(encoded);
    TransactionCursor decoded = TransactionCursorCodec.decode(101L, encoded);

    assertEquals(60, rawBytes.length);
    assertEquals(cursor, decoded);
  }

  @Test
  void invalidOrForeignAccountCursorThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode(101L, "broken-cursor"));
    assertThrows(IllegalArgumentException.class, () -> TransactionCursorCodec.decode(101L, ""));
    assertThrows(IllegalArgumentException.class, () -> TransactionCursorCodec.decode(101L, null));
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode(101L, "A".repeat(129)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TransactionCursorCodec.decode(
                101L, Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[28])));
    assertThrows(IllegalArgumentException.class, () -> TransactionCursorCodec.encode(101L, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TransactionCursorCodec.encode(
                0L, new TransactionCursor(Instant.parse("2026-04-16T10:15:30Z"), 99L)));

    String encoded101 =
        TransactionCursorCodec.encode(
            101L, new TransactionCursor(Instant.parse("2026-04-16T10:15:30Z"), 99L));
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode(202L, encoded101));
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode(0L, encoded101));
  }

  @Test
  void tamperedCursorPayloadOrSignatureThrows() {
    TransactionCursor cursor =
        new TransactionCursor(Instant.parse("2026-04-16T10:15:30.123456Z"), 99L);
    byte[] raw = Base64.getUrlDecoder().decode(TransactionCursorCodec.encode(101L, cursor));

    byte[] tamperedPayload = raw.clone();
    tamperedPayload[27] ^= 0x01;
    String tamperedPayloadCursor =
        Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayload);
    assertThrows(
        IllegalArgumentException.class,
        () -> TransactionCursorCodec.decode(101L, tamperedPayloadCursor));

    byte[] tamperedMac = raw.clone();
    tamperedMac[59] ^= 0x01;
    String tamperedMacCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedMac);
    assertThrows(
        IllegalArgumentException.class,
        () -> TransactionCursorCodec.decode(101L, tamperedMacCursor));
  }

  @Test
  void resolvesHmacKeyAndFailsClosedOutsideDevOrTest() {
    assertEquals(
        "prop-secret",
        new String(
            TransactionCursorCodec.resolveHmacKey("prop-secret", "env-secret", "prod", null)
                .getEncoded(),
            StandardCharsets.UTF_8));
    assertEquals(
        "env-secret",
        new String(
            TransactionCursorCodec.resolveHmacKey(" ", "env-secret", null, "prod").getEncoded(),
            StandardCharsets.UTF_8));
    assertEquals(
        "aquila-bank-transaction-cursor-hmac-v1",
        new String(
            TransactionCursorCodec.resolveHmacKey(null, " ", "test", null).getEncoded(),
            StandardCharsets.UTF_8));
    assertEquals(
        "aquila-bank-transaction-cursor-hmac-v1",
        new String(
            TransactionCursorCodec.resolveHmacKey(null, null, null, "local,dev").getEncoded(),
            StandardCharsets.UTF_8));

    assertThrows(
        IllegalStateException.class,
        () -> TransactionCursorCodec.resolveHmacKey(null, null, "prod", null));
    assertThrows(
        IllegalStateException.class,
        () -> TransactionCursorCodec.resolveHmacKey(" ", " ", " ", "staging"));
    assertThrows(
        IllegalStateException.class,
        () -> TransactionCursorCodec.resolveHmacKey(null, null, null, null));
  }
}
