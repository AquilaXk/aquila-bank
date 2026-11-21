package com.aquilabank.domain.account.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.account.exception.SavingsPocketNotFoundException;
import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import com.aquilabank.domain.account.port.AccountPersonalizationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountPersonalizationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  @Test
  void managesPreferencesAndSavingsPockets() {
    AccountPersonalizationPort port = mock(AccountPersonalizationPort.class);
    AccountPersonalizationService service =
        new AccountPersonalizationService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    UserAccountPreference pref = new UserAccountPreference(7L, 101L, "생활비", true, NOW);
    CreateSavingsPocketCommand createCmd =
        new CreateSavingsPocketCommand(7L, 101L, "비상금", 5_000_000L, 1_200_000L, "KRW");
    UpdateSavingsPocketCommand updateCmd =
        new UpdateSavingsPocketCommand(7L, 101L, 11L, "비상금 목표", 6_000_000L, 2_000_000L);
    SavingsPocket pocket =
        new SavingsPocket(11L, 7L, 101L, "비상금", 5_000_000L, 1_200_000L, "KRW", NOW, NOW);

    when(port.findPreferencesByUserId(7L)).thenReturn(List.of(pref));
    when(port.upsertPreference(7L, 101L, "생활비", true, NOW)).thenReturn(pref);
    when(port.findPocketsByUserAndAccount(
            7L, 101L, AccountPersonalizationService.MAX_POCKETS_PER_ACCOUNT))
        .thenReturn(List.of(pocket));
    when(port.countPocketsByUserAndAccount(7L, 101L)).thenReturn(0);
    when(port.insertPocket(createCmd, NOW)).thenReturn(pocket);
    when(port.updatePocket(updateCmd, NOW)).thenReturn(Optional.of(pocket));
    when(port.deletePocket(7L, 101L, 11L)).thenReturn(true);

    assertThat(service.listPreferences(7L)).containsExactly(pref);
    assertThat(service.savePreference(7L, 101L, " 생활비 ", true)).isEqualTo(pref);
    assertThat(service.listPockets(7L, 101L)).containsExactly(pocket);
    assertThat(service.createPocket(createCmd)).isEqualTo(pocket);
    assertThat(service.updatePocket(updateCmd)).isEqualTo(pocket);
    service.deletePocket(7L, 101L, 11L);
    verify(port).deletePocket(7L, 101L, 11L);
  }

  @Test
  void enforcesPocketLimitAndNotFound() {
    AccountPersonalizationPort port = mock(AccountPersonalizationPort.class);
    AccountPersonalizationService service =
        new AccountPersonalizationService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    CreateSavingsPocketCommand createCmd =
        new CreateSavingsPocketCommand(7L, 101L, "여행", 2_000_000L, 0L, "KRW");
    UpdateSavingsPocketCommand updateCmd =
        new UpdateSavingsPocketCommand(7L, 101L, 99L, "여행", 2_000_000L, 100L);

    when(port.countPocketsByUserAndAccount(7L, 101L))
        .thenReturn(AccountPersonalizationService.MAX_POCKETS_PER_ACCOUNT);
    assertThatThrownBy(() -> service.createPocket(createCmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("savings pocket limit exceeded");

    when(port.updatePocket(updateCmd, NOW)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.updatePocket(updateCmd))
        .isInstanceOf(SavingsPocketNotFoundException.class);

    when(port.deletePocket(7L, 101L, 99L)).thenReturn(false);
    assertThatThrownBy(() -> service.deletePocket(7L, 101L, 99L))
        .isInstanceOf(SavingsPocketNotFoundException.class);
  }
}
