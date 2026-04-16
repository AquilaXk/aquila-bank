package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.usecase.TransactionQueryUseCase;
import com.aquilabank.global.web.security.CurrentAccountId;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionQueryController {

  private final TransactionQueryUseCase transactionQueryUseCase;

  public TransactionQueryController(TransactionQueryUseCase transactionQueryUseCase) {
    this.transactionQueryUseCase = transactionQueryUseCase;
  }

  @GetMapping
  public TransactionQueryResponse getTransactions(
      @CurrentAccountId long accountId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) TransactionStatus status) {
    try {
      TransactionCursor decodedCursor =
          cursor == null || cursor.isBlank() ? null : TransactionCursorCodec.decode(cursor);
      TransactionQuery query =
          new TransactionQuery(
              accountId, from.toInstant(), to.toInstant(), limit, decodedCursor, status);
      return TransactionQueryResponse.from(transactionQueryUseCase.getTransactions(query));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }
}
