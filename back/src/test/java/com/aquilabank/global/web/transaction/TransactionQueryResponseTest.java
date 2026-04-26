package com.aquilabank.global.web.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionQueryResponseTest {

  @Test
  void preservesCursorPageContract() {
    TransactionCursor nextCursor = new TransactionCursor(Instant.parse("2026-04-16T09:00:00Z"), 2L);
    TransactionSlice slice =
        new TransactionSlice(
            List.of(
                summary(1L, "TX-1", TransactionDirection.DEBIT),
                summary(2L, "TX-2", TransactionDirection.CREDIT)),
            nextCursor,
            true,
            20);

    TransactionQueryResponse response = TransactionQueryResponse.from(slice);

    assertEquals(2, response.items().size());
    assertEquals("TX-1", response.items().get(0).transactionReference());
    assertEquals("DEBIT", response.items().get(0).direction());
    assertEquals("TX-2", response.items().get(1).transactionReference());
    assertEquals("CREDIT", response.items().get(1).direction());
    assertEquals(TransactionCursorCodec.encode(nextCursor), response.nextCursor());
    assertEquals(true, response.hasNext());
    assertEquals(20, response.limit());
    assertThrows(
        UnsupportedOperationException.class, () -> response.items().add(response.items().get(0)));
  }

  @Test
  void avoidsStreamPipelineInHotPathMapping() throws Exception {
    Path source =
        Path.of(
            "src/main/java/com/aquilabank/global/web/transaction/TransactionQueryResponse.java");

    String code = Files.readString(source);

    assertFalse(
        code.contains("slice.items().stream()"),
        "JFR에서 response item mapping이 allocation site로 확인된 경로는 Stream pipeline을 피한다.");
    assertFalse(
        code.contains("Collections.unmodifiableList"),
        "JFR에서 ArrayList iterator allocation이 확인된 경로는 mutable list wrapper를 피한다.");
    assertFalse(
        code.contains("for (TransactionSummary item : items)"),
        "JFR에서 response item mapping 경로는 iterator 대신 index loop를 사용한다.");
  }

  private static TransactionSummary summary(
      long id, String transactionReference, TransactionDirection direction) {
    return new TransactionSummary(
        id,
        101L,
        transactionReference,
        direction,
        TransactionStatus.BOOKED,
        1200L,
        8800L,
        "KRW",
        "card payment",
        "STORE",
        Instant.parse("2026-04-16T09:00:00Z"));
  }
}
