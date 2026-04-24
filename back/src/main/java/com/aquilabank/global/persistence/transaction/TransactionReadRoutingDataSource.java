package com.aquilabank.global.persistence.transaction;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/** wrapper port가 지정한 route를 transaction-read connection 선택에 반영합니다. */
public final class TransactionReadRoutingDataSource extends AbstractRoutingDataSource {

  @Override
  protected Object determineCurrentLookupKey() {
    return TransactionReadRouteContext.current();
  }
}
