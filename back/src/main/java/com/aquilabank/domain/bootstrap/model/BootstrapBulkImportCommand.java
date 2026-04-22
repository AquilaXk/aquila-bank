package com.aquilabank.domain.bootstrap.model;

import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 내부 bootstrap bulk import 입력을 작은 batch와 request-local ref로 제한합니다. */
public record BootstrapBulkImportCommand(
    List<AccountItem> accounts, List<UserItem> users, List<MembershipItem> memberships) {

  public static final int MAX_ITEMS_PER_SECTION = 50;

  public BootstrapBulkImportCommand {
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    users = users == null ? List.of() : List.copyOf(users);
    memberships = memberships == null ? List.of() : List.copyOf(memberships);
    validateSize("accounts", accounts);
    validateSize("users", users);
    validateSize("memberships", memberships);
    validateNoNull("accounts", accounts);
    validateNoNull("users", users);
    validateNoNull("memberships", memberships);
    validateRefs(accounts, users, memberships);
  }

  private static void validateSize(String name, List<?> items) {
    if (items.size() > MAX_ITEMS_PER_SECTION) {
      throw new IllegalArgumentException(name + " must contain 50 items or less");
    }
  }

  private static void validateNoNull(String name, List<?> items) {
    if (items.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(name + " must not contain null");
    }
  }

  private static void validateRefs(
      List<AccountItem> accounts, List<UserItem> users, List<MembershipItem> memberships) {
    Set<String> accountRefs =
        collectUniqueRefs(
            "account clientRef", accounts.stream().map(AccountItem::clientRef).toList());
    Set<String> userRefs =
        collectUniqueRefs("user clientRef", users.stream().map(UserItem::clientRef).toList());
    for (MembershipItem item : memberships) {
      if (!userRefs.contains(item.userRef())) {
        throw new IllegalArgumentException("membership userRef is not found: " + item.userRef());
      }
      if (!accountRefs.contains(item.accountRef())) {
        throw new IllegalArgumentException(
            "membership accountRef is not found: " + item.accountRef());
      }
    }
  }

  private static Set<String> collectUniqueRefs(String name, List<String> refs) {
    Set<String> result = new HashSet<>();
    for (String ref : refs) {
      if (!result.add(ref)) {
        throw new IllegalArgumentException(name + " must be unique: " + ref);
      }
    }
    return result;
  }

  public record AccountItem(
      String clientRef, String displayName, String currencyCode, long initialBalanceMinor) {

    public AccountItem {
      clientRef = normalizeRef(clientRef, "account clientRef");
    }
  }

  public record UserItem(String clientRef, String loginId, String password, String displayName) {

    public UserItem {
      clientRef = normalizeRef(clientRef, "user clientRef");
    }
  }

  public record MembershipItem(
      String userRef,
      String accountRef,
      MembershipRole membershipRole,
      MembershipStatus membershipStatus) {

    public MembershipItem {
      userRef = normalizeRef(userRef, "membership userRef");
      accountRef = normalizeRef(accountRef, "membership accountRef");
      if (membershipRole == null) {
        throw new IllegalArgumentException("membershipRole is required");
      }
      if (membershipStatus == null) {
        throw new IllegalArgumentException("membershipStatus is required");
      }
    }
  }

  private static String normalizeRef(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    String normalized = value.trim();
    if (normalized.length() > 64) {
      throw new IllegalArgumentException(name + " must be 64 characters or less");
    }
    return normalized;
  }
}
