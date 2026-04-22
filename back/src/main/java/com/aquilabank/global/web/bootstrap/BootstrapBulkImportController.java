package com.aquilabank.global.web.bootstrap;

import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.bootstrap.model.BootstrapBulkImportCommand;
import com.aquilabank.domain.bootstrap.model.BootstrapBulkImportResult;
import com.aquilabank.domain.bootstrap.usecase.BootstrapBulkImportUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내부 bootstrap bulk import를 작고 동기적인 운영 API로 노출합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/bootstrap/bulk-import")
@ConditionalOnProperty(
    name = {"security.account-bootstrap-api.enabled", "security.auth-bootstrap-api.enabled"},
    havingValue = "true")
public class BootstrapBulkImportController {

  private final BootstrapBulkImportUseCase bootstrapBulkImportUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public BootstrapBulkImportController(
      BootstrapBulkImportUseCase bootstrapBulkImportUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.bootstrapBulkImportUseCase = bootstrapBulkImportUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @PostMapping
  public BootstrapBulkImportResponse importItems(
      HttpServletRequest httpServletRequest,
      @Valid @RequestBody BootstrapBulkImportRequest request) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.ACCOUNT_BOOTSTRAP);
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_BOOTSTRAP);

    return BootstrapBulkImportResponse.from(
        bootstrapBulkImportUseCase.importItems(request.toCommand()));
  }

  /** bulk import 요청 body */
  public record BootstrapBulkImportRequest(
      @Size(
              max = BootstrapBulkImportCommand.MAX_ITEMS_PER_SECTION,
              message = "accounts must contain 50 items or less")
          List<@Valid AccountImportRequest> accounts,
      @Size(
              max = BootstrapBulkImportCommand.MAX_ITEMS_PER_SECTION,
              message = "users must contain 50 items or less")
          List<@Valid UserImportRequest> users,
      @Size(
              max = BootstrapBulkImportCommand.MAX_ITEMS_PER_SECTION,
              message = "memberships must contain 50 items or less")
          List<@Valid MembershipImportRequest> memberships) {

    BootstrapBulkImportCommand toCommand() {
      return new BootstrapBulkImportCommand(
          accounts == null
              ? List.of()
              : accounts.stream().map(AccountImportRequest::toCommand).toList(),
          users == null ? List.of() : users.stream().map(UserImportRequest::toCommand).toList(),
          memberships == null
              ? List.of()
              : memberships.stream().map(MembershipImportRequest::toCommand).toList());
    }
  }

  /** bulk account item */
  public record AccountImportRequest(
      @NotBlank(message = "clientRef is required") @Size(max = 64, message = "clientRef must be 64 characters or less") String clientRef,
      @NotBlank(message = "displayName is required") @Size(max = 80, message = "displayName must be 80 characters or less") String displayName,
      @NotBlank(message = "currencyCode is required") @Pattern(
              regexp = "^[A-Z]{3}$",
              message = "currencyCode must be a 3-letter uppercase code")
          String currencyCode,
      @PositiveOrZero(message = "initialBalanceMinor must be zero or positive") long initialBalanceMinor) {

    BootstrapBulkImportCommand.AccountItem toCommand() {
      return new BootstrapBulkImportCommand.AccountItem(
          clientRef, displayName, currencyCode, initialBalanceMinor);
    }
  }

  /** bulk user item */
  public record UserImportRequest(
      @NotBlank(message = "clientRef is required") @Size(max = 64, message = "clientRef must be 64 characters or less") String clientRef,
      @NotBlank(message = "loginId is required") @Size(max = 80, message = "loginId must be 80 characters or less") String loginId,
      @NotBlank(message = "password is required") @Size(max = 120, message = "password must be 120 characters or less") String password,
      @NotBlank(message = "displayName is required") @Size(max = 80, message = "displayName must be 80 characters or less") String displayName) {

    BootstrapBulkImportCommand.UserItem toCommand() {
      return new BootstrapBulkImportCommand.UserItem(clientRef, loginId, password, displayName);
    }
  }

  /** bulk membership item */
  public record MembershipImportRequest(
      @NotBlank(message = "userRef is required") @Size(max = 64, message = "userRef must be 64 characters or less") String userRef,
      @NotBlank(message = "accountRef is required") @Size(max = 64, message = "accountRef must be 64 characters or less") String accountRef,
      @NotNull(message = "membershipRole is required") MembershipRole membershipRole,
      @NotNull(message = "membershipStatus is required") MembershipStatus membershipStatus) {

    BootstrapBulkImportCommand.MembershipItem toCommand() {
      return new BootstrapBulkImportCommand.MembershipItem(
          userRef, accountRef, membershipRole, membershipStatus);
    }
  }

  /** bulk import 응답 */
  public record BootstrapBulkImportResponse(
      List<AccountImportResponse> accounts,
      List<UserImportResponse> users,
      List<MembershipImportResponse> memberships,
      SummaryResponse summary) {

    static BootstrapBulkImportResponse from(BootstrapBulkImportResult result) {
      return new BootstrapBulkImportResponse(
          result.accounts().stream().map(AccountImportResponse::from).toList(),
          result.users().stream().map(UserImportResponse::from).toList(),
          result.memberships().stream().map(MembershipImportResponse::from).toList(),
          SummaryResponse.from(result.summary()));
    }
  }

  public record AccountImportResponse(
      String clientRef,
      long accountId,
      String accountNumber,
      String displayName,
      String currencyCode,
      long availableBalanceMinor,
      String accountStatus,
      Instant createdAt) {

    static AccountImportResponse from(BootstrapBulkImportResult.AccountItem item) {
      AccountBootstrapResult account = item.account();
      return new AccountImportResponse(
          item.clientRef(),
          account.accountId(),
          account.accountNumber(),
          account.displayName(),
          account.currencyCode(),
          account.availableBalanceMinor(),
          account.accountStatus(),
          account.createdAt());
    }
  }

  public record UserImportResponse(
      String clientRef,
      long userId,
      String loginId,
      String displayName,
      String userStatus,
      Instant createdAt) {

    static UserImportResponse from(BootstrapBulkImportResult.UserItem item) {
      UserBootstrapResult user = item.user();
      return new UserImportResponse(
          item.clientRef(),
          user.userId(),
          user.loginId(),
          user.displayName(),
          user.status().name(),
          user.createdAt());
    }
  }

  public record MembershipImportResponse(
      String userRef,
      String accountRef,
      long userId,
      long accountId,
      String membershipRole,
      String membershipStatus) {

    static MembershipImportResponse from(BootstrapBulkImportResult.MembershipItem item) {
      UserAccountMembership membership = item.membership();
      return new MembershipImportResponse(
          item.userRef(),
          item.accountRef(),
          membership.userId(),
          membership.accountId(),
          membership.role().name(),
          membership.status().name());
    }
  }

  public record SummaryResponse(int accountCount, int userCount, int membershipCount) {

    static SummaryResponse from(BootstrapBulkImportResult.Summary summary) {
      return new SummaryResponse(
          summary.accountCount(), summary.userCount(), summary.membershipCount());
    }
  }
}
