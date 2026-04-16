package com.aquilabank.domain.transaction.port;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

/** 거래 조회 read path 위임용 domain port */
public interface TransactionReadPort {

  TransactionSlice fetch(TransactionQuery query);
}
