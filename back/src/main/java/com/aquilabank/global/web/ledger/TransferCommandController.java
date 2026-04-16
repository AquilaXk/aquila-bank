package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.usecase.TransferCommandUseCase;
import com.aquilabank.global.web.security.CurrentAccountId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferCommandController {

  private final TransferCommandUseCase transferCommandUseCase;

  public TransferCommandController(TransferCommandUseCase transferCommandUseCase) {
    this.transferCommandUseCase = transferCommandUseCase;
  }

  @PostMapping
  public TransferResponse transfer(
      @CurrentAccountId long sourceAccountId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody TransferRequest request) {
    TransferResult result =
        transferCommandUseCase.transfer(
            new TransferCommand(
                sourceAccountId,
                request.targetAccountId(),
                request.amountMinor(),
                request.currencyCode(),
                request.summary(),
                idempotencyKey));
    return TransferResponse.from(result);
  }

  public record TransferRequest(
      @Positive(message = "targetAccountId must be positive") long targetAccountId,
      @Positive(message = "amountMinor must be positive") long amountMinor,
      @NotBlank(message = "currencyCode is required") @Pattern(
              regexp = "^[A-Z]{3}$",
              message = "currencyCode must be a 3-letter uppercase code")
          String currencyCode,
      @NotBlank(message = "summary is required") @Size(max = 120, message = "summary must be 120 characters or less") String summary) {}

  public record TransferResponse(
      String transactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      long availableBalanceAfterMinor,
      java.time.Instant bookedAt,
      String status) {

    static TransferResponse from(TransferResult result) {
      return new TransferResponse(
          result.transactionReference(),
          result.sourceAccountId(),
          result.targetAccountId(),
          result.amountMinor(),
          result.currencyCode(),
          result.availableBalanceAfterMinor(),
          result.bookedAt(),
          result.status());
    }
  }
}
