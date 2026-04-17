package com.aquilabank.global.web.account;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.global.security.AccountBootstrapApiProperties;
import com.aquilabank.global.security.BootstrapApiAccessDeniedException;
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
  private final AccountBootstrapApiProperties accountBootstrapApiProperties;

  public AccountBootstrapController(
      AccountBootstrapUseCase accountBootstrapUseCase,
      AccountBootstrapApiProperties accountBootstrapApiProperties) {
    this.accountBootstrapUseCase = accountBootstrapUseCase;
    this.accountBootstrapApiProperties = accountBootstrapApiProperties;
  }

  @PostMapping
  public AccountBootstrapResponse bootstrap(
      HttpServletRequest httpServletRequest, @Valid @RequestBody AccountBootstrapRequest request) {
    validateBootstrapToken(httpServletRequest);

    AccountBootstrapResult result =
        accountBootstrapUseCase.bootstrap(
            new AccountBootstrapCommand(
                request.displayName(), request.currencyCode(), request.initialBalanceMinor()));
    return AccountBootstrapResponse.from(result);
  }

  private void validateBootstrapToken(HttpServletRequest httpServletRequest) {
    String bootstrapToken =
        httpServletRequest.getHeader(accountBootstrapApiProperties.tokenHeader());

    // permitAll endpoint라도 전용 shared token이 없으면 내부 bootstrap 경로가 그대로 노출됩니다.
    if (bootstrapToken == null
        || bootstrapToken.isBlank()
        || !accountBootstrapApiProperties.token().equals(bootstrapToken)) {
      throw new BootstrapApiAccessDeniedException("bootstrap token is invalid");
    }
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
