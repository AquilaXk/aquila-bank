package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.account.model.AccountStatus;
import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.model.AccountAccessMembership;
import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AccountStatusAccessPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountAccessServiceTest {

  @Test
  void allowsReadForLockedAccount() {
    AccountAccessPort accountAccessPort = mock(AccountAccessPort.class);
    AccountStatusAccessPort accountStatusAccessPort = mock(AccountStatusAccessPort.class);
    when(accountAccessPort.findAccessMembership(7L, 70L))
        .thenReturn(Optional.of(accessMembership(MembershipRole.VIEWER, AccountStatus.LOCKED)));

    AccountAccessService accountAccessService =
        new AccountAccessService(accountAccessPort, accountStatusAccessPort);

    assertEquals(70L, accountAccessService.verify(7L, 70L, AccountAccessScope.READ));
  }

  @Test
  void rejectsTransferForLockedAccount() {
    AccountAccessPort accountAccessPort = mock(AccountAccessPort.class);
    AccountStatusAccessPort accountStatusAccessPort = mock(AccountStatusAccessPort.class);
    when(accountAccessPort.findAccessMembership(7L, 70L))
        .thenReturn(Optional.of(accessMembership(MembershipRole.OWNER, AccountStatus.LOCKED)));

    AccountAccessService accountAccessService =
        new AccountAccessService(accountAccessPort, accountStatusAccessPort);

    assertThrows(
        AccountAccessDeniedException.class,
        () -> accountAccessService.verify(7L, 70L, AccountAccessScope.TRANSFER));
  }

  @Test
  void rejectsReadForClosedAccount() {
    AccountAccessPort accountAccessPort = mock(AccountAccessPort.class);
    AccountStatusAccessPort accountStatusAccessPort = mock(AccountStatusAccessPort.class);
    when(accountAccessPort.findAccessMembership(7L, 70L))
        .thenReturn(Optional.of(accessMembership(MembershipRole.OWNER, AccountStatus.CLOSED)));

    AccountAccessService accountAccessService =
        new AccountAccessService(accountAccessPort, accountStatusAccessPort);

    assertThrows(
        AccountAccessDeniedException.class,
        () -> accountAccessService.verify(7L, 70L, AccountAccessScope.READ));
  }

  @Test
  void allowsBootstrapReadForLockedAccount() {
    AccountAccessPort accountAccessPort = mock(AccountAccessPort.class);
    AccountStatusAccessPort accountStatusAccessPort = mock(AccountStatusAccessPort.class);
    when(accountStatusAccessPort.findAccountStatus(70L))
        .thenReturn(Optional.of(AccountStatus.LOCKED));

    AccountAccessService accountAccessService =
        new AccountAccessService(accountAccessPort, accountStatusAccessPort);

    assertEquals(70L, accountAccessService.verifyBootstrapAccount(70L, AccountAccessScope.READ));
  }

  @Test
  void rejectsBootstrapTransferForLockedAccount() {
    AccountAccessPort accountAccessPort = mock(AccountAccessPort.class);
    AccountStatusAccessPort accountStatusAccessPort = mock(AccountStatusAccessPort.class);
    when(accountStatusAccessPort.findAccountStatus(70L))
        .thenReturn(Optional.of(AccountStatus.LOCKED));

    AccountAccessService accountAccessService =
        new AccountAccessService(accountAccessPort, accountStatusAccessPort);

    assertThrows(
        AccountAccessDeniedException.class,
        () -> accountAccessService.verifyBootstrapAccount(70L, AccountAccessScope.TRANSFER));
  }

  @Test
  void keepsMissingBootstrapAccountForDownstreamNotFoundHandling() {
    AccountAccessPort accountAccessPort = mock(AccountAccessPort.class);
    AccountStatusAccessPort accountStatusAccessPort = mock(AccountStatusAccessPort.class);
    when(accountStatusAccessPort.findAccountStatus(70L)).thenReturn(Optional.empty());

    AccountAccessService accountAccessService =
        new AccountAccessService(accountAccessPort, accountStatusAccessPort);

    assertEquals(70L, accountAccessService.verifyBootstrapAccount(70L, AccountAccessScope.READ));
  }

  private AccountAccessMembership accessMembership(
      MembershipRole membershipRole, AccountStatus accountStatus) {
    return new AccountAccessMembership(
        7L, 70L, membershipRole, MembershipStatus.ACTIVE, UserStatus.ACTIVE, accountStatus);
  }
}
