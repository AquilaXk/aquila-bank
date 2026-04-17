package com.aquilabank.global.web.account;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영/내부 도구가 계좌 bootstrap을 HTTP로 호출하는 내부 전용 adapter */
@Validated
@RestController
@RequestMapping("/internal/api/v1/accounts/bootstrap")
@ConditionalOnProperty(name = "security.account-bootstrap-api.enabled", havingValue = "true")
public class AccountBootstrapController {

  private final AccountBootstrapUseCase accountBootstrapUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public AccountBootstrapController(
      AccountBootstrapUseCase accountBootstrapUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.accountBootstrapUseCase = accountBootstrapUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @PostMapping
  public AccountBootstrapResponse bootstrap(
      HttpServletRequest httpServletRequest, @Valid @RequestBody AccountBootstrapRequest request) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.ACCOUNT_BOOTSTRAP);

    AccountBootstrapResult result =
        accountBootstrapUseCase.bootstrap(
            new AccountBootstrapCommand(
                request.displayName(), request.currencyCode(), request.initialBalanceMinor()));
    return AccountBootstrapResponse.from(result);
  }

  /** 내부 계좌 bootstrap 요청 body */
  public record AccountBootstrapRequest(
      @NotBlank(message = "displayName is required") @Size(max = 80, message = "displayName must be 80 characters or less") String displayName,
      @NotBlank(message = "currencyCode is required") @Pattern(
              regexp = "^[A-Z]{3}$",
              message = "currencyCode must be a 3-letter uppercase code")
          String currencyCode,
      @PositiveOrZero(message = "initialBalanceMinor must be zero or positive") long initialBalanceMinor) {}

  /** 내부 계좌 bootstrap 완료 응답 */
  public record AccountBootstrapResponse(
      long accountId,
      String accountNumber,
      String displayName,
      String currencyCode,
      long availableBalanceMinor,
      String accountStatus,
      java.time.Instant createdAt) {

    static AccountBootstrapResponse from(AccountBootstrapResult result) {
      return new AccountBootstrapResponse(
          result.accountId(),
          result.accountNumber(),
          result.displayName(),
          result.currencyCode(),
          result.availableBalanceMinor(),
          result.accountStatus(),
          result.createdAt());
    }
  }
}
