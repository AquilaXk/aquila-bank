package com.aquilabank.domain.transaction.usecase;

/** transaction read model hot table retention batch 진입점 */
public interface TransactionReadModelRetentionCleanupUseCase {

  int archiveExpiredReadModels();
}
