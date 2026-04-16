package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

/** Use case exposed to inbound adapters for transaction timeline lookup. */
public interface TransactionQueryUseCase {

  TransactionSlice getTransactions(TransactionQuery query);
}
