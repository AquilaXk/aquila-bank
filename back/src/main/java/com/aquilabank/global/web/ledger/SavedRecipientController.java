package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import com.aquilabank.domain.ledger.usecase.SavedRecipientUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.AuthenticatedPrincipals;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 자주 쓰는 입금계좌(수취인 주소록) API 컨트롤러입니다. */
@Validated
@RestController
@RequestMapping("/api/v1/transfers/recipients")
public class SavedRecipientController {

  private final SavedRecipientUseCase savedRecipientUseCase;

  public SavedRecipientController(SavedRecipientUseCase savedRecipientUseCase) {
    this.savedRecipientUseCase = savedRecipientUseCase;
  }

  @GetMapping
  public SavedRecipientListResponse list(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    List<SavedRecipientResponse> items =
        savedRecipientUseCase.list(userPrincipal.userId()).stream()
            .map(SavedRecipientResponse::from)
            .toList();
    return new SavedRecipientListResponse(items);
  }

  @PostMapping
  public SavedRecipientResponse save(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody SaveRecipientRequest request) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    String institutionCode =
        request.institutionCode() == null || request.institutionCode().isBlank()
            ? "AQUILA"
            : request.institutionCode().trim();
    SavedRecipient saved =
        savedRecipientUseCase.save(
            new SaveSavedRecipientCommand(
                userPrincipal.userId(),
                request.nickname(),
                institutionCode,
                request.accountNumber(),
                request.currencyCode(),
                Boolean.TRUE.equals(request.favorite())));
    return SavedRecipientResponse.from(saved);
  }

  @DeleteMapping("/{recipientId}")
  public DeleteRecipientResponse delete(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "recipientId must be positive") long recipientId) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    savedRecipientUseCase.delete(userPrincipal.userId(), recipientId);
    return new DeleteRecipientResponse(recipientId, true);
  }

  public record SaveRecipientRequest(
      @NotBlank(message = "nickname is required") @Size(max = 60, message = "nickname must be 60 characters or less") String nickname,
      @Size(max = 20, message = "institutionCode must be 20 characters or less") @Pattern(
              regexp = "^[A-Z0-9_-]*$",
              message = "institutionCode must be uppercase alphanumeric")
          String institutionCode,
      @NotBlank(message = "accountNumber is required") @Pattern(regexp = "^[0-9-]{6,40}$", message = "accountNumber format is invalid") String accountNumber,
      @NotBlank(message = "currencyCode is required") @Pattern(
              regexp = "^[A-Z]{3}$",
              message = "currencyCode must be a 3-letter uppercase code")
          String currencyCode,
      Boolean favorite) {}

  public record SavedRecipientListResponse(List<SavedRecipientResponse> items) {}

  public record SavedRecipientResponse(
      long recipientId,
      String nickname,
      String institutionCode,
      String accountNumber,
      String currencyCode,
      boolean favorite,
      Instant createdAt,
      Instant updatedAt) {

    static SavedRecipientResponse from(SavedRecipient item) {
      return new SavedRecipientResponse(
          item.recipientId(),
          item.nickname(),
          item.institutionCode(),
          item.accountNumber(),
          item.currencyCode(),
          item.favorite(),
          item.createdAt(),
          item.updatedAt());
    }
  }

  public record DeleteRecipientResponse(long recipientId, boolean deleted) {}
}
