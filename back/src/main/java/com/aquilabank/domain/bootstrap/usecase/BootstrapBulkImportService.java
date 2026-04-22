package com.aquilabank.domain.bootstrap.usecase;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;
import com.aquilabank.domain.auth.model.UserBootstrapCommand;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertUseCase;
import com.aquilabank.domain.auth.usecase.UserBootstrapUseCase;
import com.aquilabank.domain.bootstrap.model.BootstrapBulkImportCommand;
import com.aquilabank.domain.bootstrap.model.BootstrapBulkImportResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 단건 bootstrap use case를 순서대로 묶어 같은 정합성 규칙을 재사용합니다. */
public final class BootstrapBulkImportService implements BootstrapBulkImportUseCase {

  private final AccountBootstrapUseCase accountBootstrapUseCase;
  private final UserBootstrapUseCase userBootstrapUseCase;
  private final UserAccountMembershipUpsertUseCase userAccountMembershipUpsertUseCase;

  public BootstrapBulkImportService(
      AccountBootstrapUseCase accountBootstrapUseCase,
      UserBootstrapUseCase userBootstrapUseCase,
      UserAccountMembershipUpsertUseCase userAccountMembershipUpsertUseCase) {
    this.accountBootstrapUseCase =
        Objects.requireNonNull(accountBootstrapUseCase, "accountBootstrapUseCase");
    this.userBootstrapUseCase =
        Objects.requireNonNull(userBootstrapUseCase, "userBootstrapUseCase");
    this.userAccountMembershipUpsertUseCase =
        Objects.requireNonNull(
            userAccountMembershipUpsertUseCase, "userAccountMembershipUpsertUseCase");
  }

  @Override
  public BootstrapBulkImportResult importItems(BootstrapBulkImportCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }

    Map<String, AccountBootstrapResult> accountsByRef = new LinkedHashMap<>();
    Map<String, UserBootstrapResult> usersByRef = new LinkedHashMap<>();
    List<BootstrapBulkImportResult.AccountItem> accountResults = new ArrayList<>();
    List<BootstrapBulkImportResult.UserItem> userResults = new ArrayList<>();
    List<BootstrapBulkImportResult.MembershipItem> membershipResults = new ArrayList<>();

    for (BootstrapBulkImportCommand.AccountItem item : command.accounts()) {
      AccountBootstrapResult account =
          accountBootstrapUseCase.bootstrap(
              new AccountBootstrapCommand(
                  item.displayName(), item.currencyCode(), item.initialBalanceMinor()));
      accountsByRef.put(item.clientRef(), account);
      accountResults.add(new BootstrapBulkImportResult.AccountItem(item.clientRef(), account));
    }

    for (BootstrapBulkImportCommand.UserItem item : command.users()) {
      UserBootstrapResult user =
          userBootstrapUseCase.bootstrap(
              new UserBootstrapCommand(item.loginId(), item.password(), item.displayName()));
      usersByRef.put(item.clientRef(), user);
      userResults.add(new BootstrapBulkImportResult.UserItem(item.clientRef(), user));
    }

    for (BootstrapBulkImportCommand.MembershipItem item : command.memberships()) {
      UserBootstrapResult user = usersByRef.get(item.userRef());
      AccountBootstrapResult account = accountsByRef.get(item.accountRef());
      UserAccountMembership membership =
          userAccountMembershipUpsertUseCase.upsert(
              new UserAccountMembershipUpsertCommand(
                  user.userId(),
                  account.accountId(),
                  item.membershipRole(),
                  item.membershipStatus()));
      membershipResults.add(
          new BootstrapBulkImportResult.MembershipItem(
              item.userRef(), item.accountRef(), membership));
    }

    return new BootstrapBulkImportResult(accountResults, userResults, membershipResults, null);
  }
}
