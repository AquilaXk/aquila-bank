package com.aquilabank.global.web.account;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import com.aquilabank.domain.account.usecase.AccountPersonalizationUseCase;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.AuthenticatedPrincipals;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 계좌 별칭·대표계좌 설정 및 목표 저축 포켓 API 컨트롤러입니다. */
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountPersonalizationController {

  private final AccountPersonalizationUseCase personalizationUseCase;
  private final AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;

  public AccountPersonalizationController(
      AccountPersonalizationUseCase personalizationUseCase,
      AccountSummaryQueryUseCase accountSummaryQueryUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this.personalizationUseCase = personalizationUseCase;
    this.accountSummaryQueryUseCase = accountSummaryQueryUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
  }

  @GetMapping("/preferences")
  public AccountPreferenceListResponse listPreferences(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    List<AccountPreferenceResponse> items =
        personalizationUseCase.listPreferences(userPrincipal.userId()).stream()
            .map(AccountPreferenceResponse::from)
            .toList();
    return new AccountPreferenceListResponse(items);
  }

  @PutMapping("/{accountId}/preferences")
  public AccountPreferenceResponse savePreference(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @Valid @RequestBody SaveAccountPreferenceRequest request) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedAccountId =
        requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
    UserAccountPreference saved =
        personalizationUseCase.savePreference(
            userPrincipal.userId(),
            resolvedAccountId,
            request.customAlias(),
            Boolean.TRUE.equals(request.primaryAccount()));
    return AccountPreferenceResponse.from(saved);
  }

  @GetMapping("/{accountId}/pockets")
  public SavingsPocketListResponse listPockets(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "accountId must be positive") long accountId) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedAccountId =
        requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
    List<SavingsPocketResponse> items =
        personalizationUseCase.listPockets(userPrincipal.userId(), resolvedAccountId).stream()
            .map(SavingsPocketResponse::from)
            .toList();
    return new SavingsPocketListResponse(items);
  }

  @PostMapping("/{accountId}/pockets")
  public SavingsPocketResponse createPocket(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @Valid @RequestBody SaveSavingsPocketRequest request) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedAccountId =
        requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
    AccountSummary account = accountSummaryQueryUseCase.getByAccountId(resolvedAccountId);
    SavingsPocket created =
        personalizationUseCase.createPocket(
            new CreateSavingsPocketCommand(
                userPrincipal.userId(),
                resolvedAccountId,
                request.pocketName(),
                request.targetAmountMinor(),
                request.savedAmountMinor(),
                account.currencyCode()));
    return SavingsPocketResponse.from(created);
  }

  @PutMapping("/{accountId}/pockets/{pocketId}")
  public SavingsPocketResponse updatePocket(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @PathVariable @Positive(message = "pocketId must be positive") long pocketId,
      @Valid @RequestBody SaveSavingsPocketRequest request) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedAccountId =
        requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
    SavingsPocket updated =
        personalizationUseCase.updatePocket(
            new UpdateSavingsPocketCommand(
                userPrincipal.userId(),
                resolvedAccountId,
                pocketId,
                request.pocketName(),
                request.targetAmountMinor(),
                request.savedAmountMinor()));
    return SavingsPocketResponse.from(updated);
  }

  @DeleteMapping("/{accountId}/pockets/{pocketId}")
  public DeleteSavingsPocketResponse deletePocket(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @PathVariable @Positive(message = "pocketId must be positive") long pocketId) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedAccountId =
        requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
    personalizationUseCase.deletePocket(userPrincipal.userId(), resolvedAccountId, pocketId);
    return new DeleteSavingsPocketResponse(pocketId, true);
  }

  public record SaveAccountPreferenceRequest(
      @Size(max = 60, message = "customAlias must be 60 characters or less") String customAlias,
      Boolean primaryAccount) {}

  public record SaveSavingsPocketRequest(
      @NotBlank(message = "pocketName is required") @Size(max = 60, message = "pocketName must be 60 characters or less") String pocketName,
      @Positive(message = "targetAmountMinor must be positive") long targetAmountMinor,
      @PositiveOrZero(message = "savedAmountMinor must be zero or positive") long savedAmountMinor) {}

  public record AccountPreferenceListResponse(List<AccountPreferenceResponse> items) {}

  public record AccountPreferenceResponse(
      long accountId, String customAlias, boolean primaryAccount, Instant updatedAt) {

    static AccountPreferenceResponse from(UserAccountPreference item) {
      return new AccountPreferenceResponse(
          item.accountId(), item.customAlias(), item.primaryAccount(), item.updatedAt());
    }
  }

  public record SavingsPocketListResponse(List<SavingsPocketResponse> items) {}

  public record SavingsPocketResponse(
      long pocketId,
      long accountId,
      String pocketName,
      long targetAmountMinor,
      long savedAmountMinor,
      String currencyCode,
      Instant createdAt,
      Instant updatedAt) {

    static SavingsPocketResponse from(SavingsPocket item) {
      return new SavingsPocketResponse(
          item.pocketId(),
          item.accountId(),
          item.pocketName(),
          item.targetAmountMinor(),
          item.savedAmountMinor(),
          item.currencyCode(),
          item.createdAt(),
          item.updatedAt());
    }
  }

  public record DeleteSavingsPocketResponse(long pocketId, boolean deleted) {}
}
