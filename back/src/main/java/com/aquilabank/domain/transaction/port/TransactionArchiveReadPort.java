package com.aquilabank.domain.transaction.port;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

/** cold archive transaction read model 조회를 persistence adapter 로 위임합니다. */
public interface TransactionArchiveReadPort {

  TransactionSlice fetchArchived(TransactionQuery query);
}
