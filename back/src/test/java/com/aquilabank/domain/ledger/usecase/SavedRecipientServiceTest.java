package com.aquilabank.domain.ledger.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.exception.SavedRecipientNotFoundException;
import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import com.aquilabank.domain.ledger.port.SavedRecipientPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SavedRecipientServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  @Test
  void listsSavesAndDeletesRecipientsWithinLimit() {
    SavedRecipientPort port = mock(SavedRecipientPort.class);
    SavedRecipientService service =
        new SavedRecipientService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    SavedRecipient recipient =
        new SavedRecipient(10L, 7L, "생활비", "AQUILA", "110-123-456789", "KRW", true, NOW, NOW);
    SaveSavedRecipientCommand command =
        new SaveSavedRecipientCommand(7L, "생활비", "AQUILA", "110-123-456789", "KRW", true);

    when(port.findByUserId(7L, SavedRecipientService.MAX_RECIPIENTS_PER_USER))
        .thenReturn(List.of(recipient));
    when(port.existsByUserAndTarget(7L, "AQUILA", "110-123-456789")).thenReturn(false);
    when(port.countByUserId(7L)).thenReturn(0);
    when(port.upsert(command, NOW)).thenReturn(recipient);
    when(port.deleteByUserAndId(7L, 10L)).thenReturn(true);

    assertThat(service.list(7L)).containsExactly(recipient);
    assertThat(service.save(command)).isEqualTo(recipient);
    service.delete(7L, 10L);
    verify(port).deleteByUserAndId(7L, 10L);
  }

  @Test
  void allowsUpsertAtLimitWhenTargetExistsAndRejectsNewWhenFullOrMissing() {
    SavedRecipientPort port = mock(SavedRecipientPort.class);
    SavedRecipientService service =
        new SavedRecipientService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    SaveSavedRecipientCommand command =
        new SaveSavedRecipientCommand(7L, "월세", "AQUILA", "110-999-888777", "KRW", false);
    SavedRecipient updated =
        new SavedRecipient(11L, 7L, "월세", "AQUILA", "110-999-888777", "KRW", false, NOW, NOW);

    when(port.existsByUserAndTarget(7L, "AQUILA", "110-999-888777")).thenReturn(true);
    when(port.upsert(command, NOW)).thenReturn(updated);
    assertThat(service.save(command)).isEqualTo(updated);

    when(port.existsByUserAndTarget(7L, "AQUILA", "110-999-888777")).thenReturn(false);
    when(port.countByUserId(7L)).thenReturn(SavedRecipientService.MAX_RECIPIENTS_PER_USER);
    assertThatThrownBy(() -> service.save(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("saved recipient limit exceeded");

    when(port.deleteByUserAndId(7L, 999L)).thenReturn(false);
    assertThatThrownBy(() -> service.delete(7L, 999L))
        .isInstanceOf(SavedRecipientNotFoundException.class);
  }
}
