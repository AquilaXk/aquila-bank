package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;

/** 거래 상세 단건 조회 진입점 */
public interface TransactionDetailQueryUseCase {

  TransactionDetail getTransactionDetail(TransactionDetailQuery query);
}
