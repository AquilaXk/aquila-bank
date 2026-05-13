package com.aquilabank.global.customerapplication;

import com.aquilabank.domain.account.exception.AccountSummaryNotFoundException;
import com.aquilabank.domain.account.model.AccountStatus;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.domain.auth.exception.UserAccountMembershipNotFoundException;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryUseCase;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAccountPolicyCheck;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationAccountPolicyPort;
import org.springframework.stereotype.Component;

/** 신청 승인 이후 실행 직전에도 계좌/멤버십 상태를 다시 확인하는 adapter입니다. */
@Component
public class CustomerApplicationAccountPolicyAdapter
    implements CustomerApplicationAccountPolicyPort {

  private final AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private final UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase;

  public CustomerApplicationAccountPolicyAdapter(
      AccountSummaryQueryUseCase accountSummaryQueryUseCase,
      UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase) {
    this.accountSummaryQueryUseCase = accountSummaryQueryUseCase;
    this.userAccountMembershipQueryUseCase = userAccountMembershipQueryUseCase;
  }

  @Override
  public CustomerApplicationAccountPolicyCheck checkTransferLimitChange(
      long userId, long accountId) {
    try {
      var membership = userAccountMembershipQueryUseCase.getByUserIdAndAccountId(userId, accountId);
      if (membership.status() != MembershipStatus.ACTIVE) {
        return CustomerApplicationAccountPolicyCheck.rejected("TRANSFER_LIMIT_MEMBERSHIP_INACTIVE");
      }
    } catch (UserAccountMembershipNotFoundException exception) {
      return CustomerApplicationAccountPolicyCheck.rejected("TRANSFER_LIMIT_MEMBERSHIP_NOT_FOUND");
    }

    try {
      var account = accountSummaryQueryUseCase.getByAccountId(accountId);
      AccountStatus status = AccountStatus.valueOf(account.accountStatus());
      if (status != AccountStatus.ACTIVE) {
        return CustomerApplicationAccountPolicyCheck.rejected("TRANSFER_LIMIT_ACCOUNT_NOT_ACTIVE");
      }
      return CustomerApplicationAccountPolicyCheck.allowed();
    } catch (AccountSummaryNotFoundException exception) {
      return CustomerApplicationAccountPolicyCheck.rejected("TRANSFER_LIMIT_ACCOUNT_NOT_FOUND");
    } catch (IllegalArgumentException exception) {
      return CustomerApplicationAccountPolicyCheck.rejected(
          "TRANSFER_LIMIT_ACCOUNT_STATUS_INVALID");
    }
  }
}
