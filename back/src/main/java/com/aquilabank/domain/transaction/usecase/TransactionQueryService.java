package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.port.TransactionReadPort;

public final class TransactionQueryService implements TransactionQueryUseCase {

  private final TransactionReadPort transactionReadPort;

  public TransactionQueryService(TransactionReadPort transactionReadPort) {
    this.transactionReadPort = transactionReadPort;
  }

  @Override
  public TransactionSlice getTransactions(TransactionQuery query) {
    return transactionReadPort.fetch(query);
  }
}
