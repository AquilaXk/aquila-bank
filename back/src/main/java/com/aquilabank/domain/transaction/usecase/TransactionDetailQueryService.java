package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.exception.TransactionDetailNotFoundException;
import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.domain.transaction.port.TransactionDetailReadPort;

/** 거래 상세 exact lookup을 not found 예외와 함께 묶습니다. */
public final class TransactionDetailQueryService implements TransactionDetailQueryUseCase {

  private final TransactionDetailReadPort transactionDetailReadPort;

  public TransactionDetailQueryService(TransactionDetailReadPort transactionDetailReadPort) {
    this.transactionDetailReadPort = transactionDetailReadPort;
  }

  @Override
  public TransactionDetail getTransactionDetail(TransactionDetailQuery query) {
    return transactionDetailReadPort
        .find(query)
        .orElseThrow(
            () -> new TransactionDetailNotFoundException("transaction detail is not found"));
  }
}
