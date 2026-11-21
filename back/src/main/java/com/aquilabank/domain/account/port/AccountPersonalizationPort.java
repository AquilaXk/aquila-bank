package com.aquilabank.domain.account.port;

import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountPersonalizationPort {

  List<UserAccountPreference> findPreferencesByUserId(long userId);

  UserAccountPreference upsertPreference(
      long userId, long accountId, String customAlias, boolean primaryAccount, Instant now);

  List<SavingsPocket> findPocketsByUserAndAccount(long userId, long accountId, int limit);

  int countPocketsByUserAndAccount(long userId, long accountId);

  SavingsPocket insertPocket(CreateSavingsPocketCommand command, Instant now);

  Optional<SavingsPocket> updatePocket(UpdateSavingsPocketCommand command, Instant now);

  boolean deletePocket(long userId, long accountId, long pocketId);
}
