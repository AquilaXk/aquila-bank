package com.aquilabank.global.persistence.customerapplication;

import com.aquilabank.domain.customerapplication.port.CustomerApplicationWriteTransactionPort;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** 고객 업무 단위 작업마다 짧은 쓰기 트랜잭션을 열어 외부 호출과 DB 락 경계를 분리합니다. */
public final class TransactionTemplateCustomerApplicationWriteAdapter
    implements CustomerApplicationWriteTransactionPort {

  private final TransactionTemplate transactionTemplate;
  private final String nullResultMessage;

  public TransactionTemplateCustomerApplicationWriteAdapter(
      TransactionTemplate transactionTemplate, String nullResultMessage) {
    this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
    this.nullResultMessage = Objects.requireNonNull(nullResultMessage);
  }

  @Override
  public <T> T execute(Supplier<T> action) {
    T result = transactionTemplate.execute(status -> action.get());
    if (result == null) {
      throw new IllegalStateException(nullResultMessage);
    }
    return result;
  }
}
