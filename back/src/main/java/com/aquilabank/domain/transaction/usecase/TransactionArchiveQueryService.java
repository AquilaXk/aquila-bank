package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.port.TransactionArchiveReadPort;

public final class TransactionArchiveQueryService implements TransactionArchiveQueryUseCase {

  private final TransactionArchiveReadPort transactionArchiveReadPort;

  public TransactionArchiveQueryService(TransactionArchiveReadPort transactionArchiveReadPort) {
    this.transactionArchiveReadPort = transactionArchiveReadPort;
  }

  @Override
  public TransactionSlice getArchivedTransactions(TransactionQuery query) {
    return transactionArchiveReadPort.fetchArchived(query);
  }
}
