package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.port.TransactionReadPort;

/** 거래 조회를 read port로 위임하는 기본 use case 구현 */
public final class TransactionQueryService implements TransactionQueryUseCase {

  private final TransactionReadPort transactionReadPort;

  public TransactionQueryService(TransactionReadPort transactionReadPort) {
    this.transactionReadPort = transactionReadPort;
  }

  @Override
  public TransactionSlice getTransactions(TransactionQuery query) {
    // 실제 read model 조회 책임은 read port 구현으로 위임
    return transactionReadPort.fetch(query);
  }
}
