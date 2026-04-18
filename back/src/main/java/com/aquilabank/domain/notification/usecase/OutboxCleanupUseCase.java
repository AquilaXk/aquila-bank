package com.aquilabank.domain.notification.usecase;

/** retention cutoff 밖의 PUBLISHED outbox cleanup batch 진입점 */
public interface OutboxCleanupUseCase {

  int cleanupPublishedEvents();
}
