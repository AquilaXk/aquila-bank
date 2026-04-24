package com.aquilabank.global.persistence.transaction;

import java.util.function.Supplier;

/** transaction 시작 전 route를 고정해 connection acquisition 시점에만 DataSource를 분기합니다. */
final class TransactionReadRouteContext {

  private static final ThreadLocal<TransactionReadRoute> CURRENT = new ThreadLocal<>();

  private TransactionReadRouteContext() {}

  static TransactionReadRoute current() {
    return CURRENT.get();
  }

  static <T> T call(TransactionReadRoute route, Supplier<T> action) {
    TransactionReadRoute previous = CURRENT.get();
    CURRENT.set(route);
    try {
      return action.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }
}
