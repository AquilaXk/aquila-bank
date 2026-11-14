package com.aquilabank.domain.auth.port;

import java.util.function.Supplier;

/** CPU 집약 해시 연산을 DB 행 락/트랜잭션 밖에서 끝낸 뒤 짧은 쓰기 트랜잭션만 실행하기 위한 경계 포트입니다. */
@FunctionalInterface
public interface AuthWriteTransactionPort {

  <T> T execute(Supplier<T> action);
}
