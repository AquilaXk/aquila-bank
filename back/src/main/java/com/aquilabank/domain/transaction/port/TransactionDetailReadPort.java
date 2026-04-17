package com.aquilabank.domain.transaction.port;

import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import java.util.Optional;

/** 거래 상세 exact lookup 조회를 저장소 계층으로 위임합니다. */
public interface TransactionDetailReadPort {

  Optional<TransactionDetail> find(TransactionDetailQuery query);
}
