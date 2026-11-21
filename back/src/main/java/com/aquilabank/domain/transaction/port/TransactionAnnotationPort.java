package com.aquilabank.domain.transaction.port;

import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import java.time.Instant;
import java.util.List;

public interface TransactionAnnotationPort {

  List<TransactionAnnotation> findByReferences(
      long userId, long accountId, List<String> transactionReferences);

  TransactionAnnotation upsert(SaveTransactionAnnotationCommand command, Instant now);
}
