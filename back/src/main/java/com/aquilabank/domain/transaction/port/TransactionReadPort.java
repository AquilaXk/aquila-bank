package com.aquilabank.domain.transaction.port;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;

public interface TransactionReadPort {

  TransactionSlice fetch(TransactionQuery query);
}
