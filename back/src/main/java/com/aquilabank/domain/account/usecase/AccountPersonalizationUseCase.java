package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import java.util.List;

public interface AccountPersonalizationUseCase {

  List<UserAccountPreference> listPreferences(long userId);

  UserAccountPreference savePreference(
      long userId, long accountId, String customAlias, boolean primaryAccount);

  List<SavingsPocket> listPockets(long userId, long accountId);

  SavingsPocket createPocket(CreateSavingsPocketCommand command);

  SavingsPocket updatePocket(UpdateSavingsPocketCommand command);

  void deletePocket(long userId, long accountId, long pocketId);
}
