package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import com.aquilabank.domain.transaction.port.TransactionAnnotationPort;
import com.aquilabank.standard.util.DomainGuards;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 거래 내역 사용자 분류 및 메모 배치 조회/저장 유스케이스입니다. */
public final class TransactionAnnotationService implements TransactionAnnotationUseCase {

  public static final int MAX_ANNOTATION_REFERENCES = 50;

  private final TransactionAnnotationPort annotationPort;
  private final Clock clock;

  public TransactionAnnotationService(TransactionAnnotationPort annotationPort, Clock clock) {
    this.annotationPort = Objects.requireNonNull(annotationPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public List<TransactionAnnotation> findByReferences(
      long userId, long accountId, List<String> transactionReferences) {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    if (transactionReferences == null || transactionReferences.isEmpty()) {
      return List.of();
    }
    if (transactionReferences.size() > MAX_ANNOTATION_REFERENCES) {
      throw new IllegalArgumentException("too many transaction references");
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>(transactionReferences.size());
    for (int i = 0; i < transactionReferences.size(); i++) {
      String ref = transactionReferences.get(i);
      normalized.add(DomainGuards.requireText(ref, 80, "transactionReference"));
    }
    return annotationPort.findByReferences(userId, accountId, new ArrayList<>(normalized));
  }

  @Override
  public TransactionAnnotation save(SaveTransactionAnnotationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return annotationPort.upsert(command, Instant.now(clock));
  }
}
