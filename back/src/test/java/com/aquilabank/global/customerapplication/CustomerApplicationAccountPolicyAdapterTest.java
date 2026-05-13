package com.aquilabank.global.customerapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.account.exception.AccountSummaryNotFoundException;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.domain.auth.exception.UserAccountMembershipNotFoundException;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryUseCase;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CustomerApplicationAccountPolicyAdapterTest {

  private final AccountSummaryQueryUseCase accountSummaryQueryUseCase =
      mock(AccountSummaryQueryUseCase.class);
  private final UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase =
      mock(UserAccountMembershipQueryUseCase.class);
  private final CustomerApplicationAccountPolicyAdapter adapter =
      new CustomerApplicationAccountPolicyAdapter(
          accountSummaryQueryUseCase, userAccountMembershipQueryUseCase);

  @Test
  void permitsActiveMembershipAndActiveAccount() {
    when(userAccountMembershipQueryUseCase.getByUserIdAndAccountId(7L, 101L))
        .thenReturn(membership(MembershipStatus.ACTIVE));
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(account("ACTIVE"));

    var result = adapter.checkTransferLimitChange(7L, 101L);

    assertThat(result.permitted()).isTrue();
  }

  @Test
  void rejectsInactiveAccountAtExecutionBoundary() {
    when(userAccountMembershipQueryUseCase.getByUserIdAndAccountId(7L, 101L))
        .thenReturn(membership(MembershipStatus.ACTIVE));
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(account("LOCKED"));

    var result = adapter.checkTransferLimitChange(7L, 101L);

    assertThat(result.permitted()).isFalse();
    assertThat(result.rejectionReason()).isEqualTo("TRANSFER_LIMIT_ACCOUNT_NOT_ACTIVE");
  }

  @Test
  void rejectsInactiveMembershipAtExecutionBoundary() {
    when(userAccountMembershipQueryUseCase.getByUserIdAndAccountId(7L, 101L))
        .thenReturn(membership(MembershipStatus.REVOKED));

    var result = adapter.checkTransferLimitChange(7L, 101L);

    assertThat(result.permitted()).isFalse();
    assertThat(result.rejectionReason()).isEqualTo("TRANSFER_LIMIT_MEMBERSHIP_INACTIVE");
  }

  @Test
  void rejectsMissingMembershipOrAccountAtExecutionBoundary() {
    when(userAccountMembershipQueryUseCase.getByUserIdAndAccountId(7L, 101L))
        .thenThrow(new UserAccountMembershipNotFoundException("membership is not found"));

    var membershipResult = adapter.checkTransferLimitChange(7L, 101L);

    assertThat(membershipResult.permitted()).isFalse();
    assertThat(membershipResult.rejectionReason()).isEqualTo("TRANSFER_LIMIT_MEMBERSHIP_NOT_FOUND");

    when(userAccountMembershipQueryUseCase.getByUserIdAndAccountId(8L, 102L))
        .thenReturn(membership(MembershipStatus.ACTIVE));
    when(accountSummaryQueryUseCase.getByAccountId(102L))
        .thenThrow(new AccountSummaryNotFoundException("account is not found"));

    var accountResult = adapter.checkTransferLimitChange(8L, 102L);

    assertThat(accountResult.permitted()).isFalse();
    assertThat(accountResult.rejectionReason()).isEqualTo("TRANSFER_LIMIT_ACCOUNT_NOT_FOUND");
  }

  @Test
  void rejectsInvalidAccountStatusAtExecutionBoundary() {
    when(userAccountMembershipQueryUseCase.getByUserIdAndAccountId(7L, 101L))
        .thenReturn(membership(MembershipStatus.ACTIVE));
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(account("BROKEN"));

    var result = adapter.checkTransferLimitChange(7L, 101L);

    assertThat(result.permitted()).isFalse();
    assertThat(result.rejectionReason()).isEqualTo("TRANSFER_LIMIT_ACCOUNT_STATUS_INVALID");
  }

  private static UserAccountMembershipSummary membership(MembershipStatus status) {
    Instant now = Instant.parse("2026-05-13T03:00:00Z");
    return new UserAccountMembershipSummary(7L, 101L, MembershipRole.OWNER, status, now, now);
  }

  private static AccountSummary account(String status) {
    Instant now = Instant.parse("2026-05-13T03:00:00Z");
    return new AccountSummary(101L, "10000000000101", "입출금", status, "KRW", 0L, 0L, now, now);
  }
}
