package com.aquilabank.domain.transaction.port;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

/** Outbound port for the transaction read path. */
public interface TransactionReadPort {

  TransactionSlice fetch(TransactionQuery query);
}
