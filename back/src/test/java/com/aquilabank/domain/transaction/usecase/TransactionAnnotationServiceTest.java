package com.aquilabank.domain.transaction.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import com.aquilabank.domain.transaction.model.TransactionCategory;
import com.aquilabank.domain.transaction.port.TransactionAnnotationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionAnnotationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  @Test
  void findsDeduplicatedAndSavesAnnotations() {
    TransactionAnnotationPort port = mock(TransactionAnnotationPort.class);
    TransactionAnnotationService service =
        new TransactionAnnotationService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    TransactionAnnotation item =
        new TransactionAnnotation(7L, 101L, "TRX-1", TransactionCategory.FOOD, "점심", NOW);
    SaveTransactionAnnotationCommand command =
        new SaveTransactionAnnotationCommand(7L, 101L, "TRX-1", TransactionCategory.FOOD, "점심");

    when(port.findByReferences(7L, 101L, List.of("TRX-1", "TRX-2"))).thenReturn(List.of(item));
    when(port.upsert(command, NOW)).thenReturn(item);

    assertThat(service.findByReferences(7L, 101L, null)).isEmpty();
    assertThat(service.findByReferences(7L, 101L, List.of())).isEmpty();
    assertThat(service.findByReferences(7L, 101L, List.of(" TRX-1 ", "TRX-1", "TRX-2")))
        .containsExactly(item);
    assertThat(service.save(command)).isEqualTo(item);

    verify(port).findByReferences(7L, 101L, List.of("TRX-1", "TRX-2"));
    verify(port).upsert(command, NOW);
    verifyNoMoreInteractions(port);
  }

  @Test
  void rejectsReferenceListAboveBudget() {
    TransactionAnnotationPort port = mock(TransactionAnnotationPort.class);
    TransactionAnnotationService service =
        new TransactionAnnotationService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    List<String> tooMany =
        Collections.nCopies(TransactionAnnotationService.MAX_ANNOTATION_REFERENCES + 1, "TRX-1");

    assertThatThrownBy(() -> service.findByReferences(7L, 101L, tooMany))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("too many transaction references");
  }
}
