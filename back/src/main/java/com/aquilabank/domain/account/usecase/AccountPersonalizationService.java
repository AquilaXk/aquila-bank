package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.exception.SavingsPocketNotFoundException;
import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import com.aquilabank.domain.account.port.AccountPersonalizationPort;
import com.aquilabank.standard.util.DomainGuards;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 계좌 별칭·대표계좌 설정 및 목표 저축 포켓 관리 유스케이스입니다. */
public final class AccountPersonalizationService implements AccountPersonalizationUseCase {

  public static final int MAX_POCKETS_PER_ACCOUNT = 20;

  private final AccountPersonalizationPort personalizationPort;
  private final Clock clock;

  public AccountPersonalizationService(
      AccountPersonalizationPort personalizationPort, Clock clock) {
    this.personalizationPort = Objects.requireNonNull(personalizationPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public List<UserAccountPreference> listPreferences(long userId) {
    DomainGuards.requirePositive(userId, "userId");
    return personalizationPort.findPreferencesByUserId(userId);
  }

  @Override
  public UserAccountPreference savePreference(
      long userId, long accountId, String customAlias, boolean primaryAccount) {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    String normalizedAlias = DomainGuards.requireOptionalText(customAlias, 60, "customAlias");
    return personalizationPort.upsertPreference(
        userId, accountId, normalizedAlias, primaryAccount, Instant.now(clock));
  }

  @Override
  public List<SavingsPocket> listPockets(long userId, long accountId) {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    return personalizationPort.findPocketsByUserAndAccount(
        userId, accountId, MAX_POCKETS_PER_ACCOUNT);
  }

  @Override
  public SavingsPocket createPocket(CreateSavingsPocketCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (personalizationPort.countPocketsByUserAndAccount(command.userId(), command.accountId())
        >= MAX_POCKETS_PER_ACCOUNT) {
      throw new IllegalArgumentException("savings pocket limit exceeded");
    }
    return personalizationPort.insertPocket(command, Instant.now(clock));
  }

  @Override
  public SavingsPocket updatePocket(UpdateSavingsPocketCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return personalizationPort
        .updatePocket(command, Instant.now(clock))
        .orElseThrow(() -> new SavingsPocketNotFoundException("savings pocket not found"));
  }

  @Override
  public void deletePocket(long userId, long accountId, long pocketId) {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    DomainGuards.requirePositive(pocketId, "pocketId");
    if (!personalizationPort.deletePocket(userId, accountId, pocketId)) {
      throw new SavingsPocketNotFoundException("savings pocket not found");
    }
  }
}
