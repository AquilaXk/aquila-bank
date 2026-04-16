package com.aquilabank.global.web.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionCursorCodecTest {

  @Test
  void roundTrip() {
    TransactionCursor cursor =
        new TransactionCursor(Instant.parse("2026-04-16T10:15:30.123456Z"), 99L);

    String encoded = TransactionCursorCodec.encode(cursor);
    TransactionCursor decoded = TransactionCursorCodec.decode(encoded);

    assertEquals(cursor, decoded);
  }

  @Test
  void invalidCursorThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> TransactionCursorCodec.decode("broken-cursor"));
  }
}
