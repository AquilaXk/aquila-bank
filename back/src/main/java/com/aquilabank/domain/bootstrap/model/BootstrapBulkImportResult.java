package com.aquilabank.domain.bootstrap.model;

import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import java.util.List;

/** bulk import 결과는 request-local ref와 생성된 식별자를 함께 반환합니다. */
public record BootstrapBulkImportResult(
    List<AccountItem> accounts,
    List<UserItem> users,
    List<MembershipItem> memberships,
    Summary summary) {

  public BootstrapBulkImportResult {
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    users = users == null ? List.of() : List.copyOf(users);
    memberships = memberships == null ? List.of() : List.copyOf(memberships);
    summary =
        summary == null ? new Summary(accounts.size(), users.size(), memberships.size()) : summary;
  }

  public record AccountItem(String clientRef, AccountBootstrapResult account) {

    public AccountItem {
      if (clientRef == null || clientRef.isBlank()) {
        throw new IllegalArgumentException("clientRef is required");
      }
      if (account == null) {
        throw new IllegalArgumentException("account is required");
      }
    }
  }

  public record UserItem(String clientRef, UserBootstrapResult user) {

    public UserItem {
      if (clientRef == null || clientRef.isBlank()) {
        throw new IllegalArgumentException("clientRef is required");
      }
      if (user == null) {
        throw new IllegalArgumentException("user is required");
      }
    }
  }

  public record MembershipItem(
      String userRef, String accountRef, UserAccountMembership membership) {

    public MembershipItem {
      if (userRef == null || userRef.isBlank()) {
        throw new IllegalArgumentException("userRef is required");
      }
      if (accountRef == null || accountRef.isBlank()) {
        throw new IllegalArgumentException("accountRef is required");
      }
      if (membership == null) {
        throw new IllegalArgumentException("membership is required");
      }
    }
  }

  public record Summary(int accountCount, int userCount, int membershipCount) {

    public Summary {
      if (accountCount < 0 || userCount < 0 || membershipCount < 0) {
        throw new IllegalArgumentException("summary counts must not be negative");
      }
    }
  }
}
