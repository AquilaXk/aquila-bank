package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

/** 거래 타임라인 조회 진입 use case */
public interface TransactionQueryUseCase {

  TransactionSlice getTransactions(TransactionQuery query);
}
