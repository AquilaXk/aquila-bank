package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.port.AuthWriteTransactionPort;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** 인증 실패 예외(InvalidCredentialsException) 커밋 여부를 선택해 짧은 쓰기 트랜잭션을 실행합니다. */
public final class TransactionTemplateAuthWriteAdapter implements AuthWriteTransactionPort {

  private final TransactionTemplate transactionTemplate;
  private final boolean commitOnInvalidCredentials;
  private final String nullResultMessage;

  public TransactionTemplateAuthWriteAdapter(
      TransactionTemplate transactionTemplate,
      boolean commitOnInvalidCredentials,
      String nullResultMessage) {
    this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
    this.commitOnInvalidCredentials = commitOnInvalidCredentials;
    this.nullResultMessage = nullResultMessage;
  }

  @Override
  public <T> T execute(Supplier<T> action) {
    if (!commitOnInvalidCredentials) {
      return transactionTemplate.execute(status -> action.get());
    }
    InvalidCredentialsException[] failureHolder = new InvalidCredentialsException[1];
    T result =
        transactionTemplate.execute(
            status -> {
              try {
                return action.get();
              } catch (InvalidCredentialsException ex) {
                failureHolder[0] = ex;
                return null;
              }
            });
    if (failureHolder[0] != null) {
      throw failureHolder[0];
    }
    if (nullResultMessage != null && result == null) {
      throw new IllegalStateException(nullResultMessage);
    }
    return result;
  }
}
