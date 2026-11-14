package com.aquilabank.domain.customerapplication.port;

import java.util.function.Supplier;

/** 외부 HTTP 실행 동안 DB row 락을 쥐지 않도록 짧은 쓰기 트랜잭션을 구분 실행합니다. */
public interface CustomerApplicationWriteTransactionPort {

  <T> T execute(Supplier<T> action);
}
