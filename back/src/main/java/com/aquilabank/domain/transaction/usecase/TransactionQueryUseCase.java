package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

public interface TransactionQueryUseCase {

  TransactionSlice getTransactions(TransactionQuery query);
}
