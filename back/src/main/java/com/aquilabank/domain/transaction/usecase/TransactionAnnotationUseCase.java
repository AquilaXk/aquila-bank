package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import java.util.List;

public interface TransactionAnnotationUseCase {

  List<TransactionAnnotation> findByReferences(
      long userId, long accountId, List<String> transactionReferences);

  TransactionAnnotation save(SaveTransactionAnnotationCommand command);
}
