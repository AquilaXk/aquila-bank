package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

/** archive transaction timeline 조회 진입점 */
public interface TransactionArchiveQueryUseCase {

  TransactionSlice getArchivedTransactions(TransactionQuery query);
}
