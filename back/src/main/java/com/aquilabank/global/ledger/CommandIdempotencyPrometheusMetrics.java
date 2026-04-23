package com.aquilabank.global.ledger;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsQueryUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** summary gauge는 scrape마다 summary query를 반복하지 않게 짧은 cache 안에서만 재사용합니다. */
@Component
public final class CommandIdempotencyPrometheusMetrics {

  private static final Logger log =
      LoggerFactory.getLogger(CommandIdempotencyPrometheusMetrics.class);
  private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(5);
  private static final CommandIdempotencyOpsSummary EMPTY_SUMMARY =
      new CommandIdempotencyOpsSummary(Instant.EPOCH, 0, 0, 0, 0, 0);

  private final ObjectProvider<CommandIdempotencyOpsQueryUseCase> queryUseCaseProvider;
  private final Map<ConflictReason, Counter> conflictCounters;
  private final Counter recoveredCounter;
  private final Counter cleanupDeletedCounter;

  private volatile CachedSummary cachedSummary = new CachedSummary(EMPTY_SUMMARY, Instant.EPOCH);

  public CommandIdempotencyPrometheusMetrics(
      ObjectProvider<CommandIdempotencyOpsQueryUseCase> queryUseCaseProvider,
      MeterRegistry meterRegistry) {
    this.queryUseCaseProvider = queryUseCaseProvider;
    this.conflictCounters = new EnumMap<>(ConflictReason.class);
    for (ConflictReason reason : ConflictReason.values()) {
      conflictCounters.put(
          reason,
          Counter.builder("aquila.command.idempotency.conflict.count")
              .tag("reason_code", reason.name())
              .description("command idempotency conflict count")
              .register(meterRegistry));
    }
    this.recoveredCounter =
        Counter.builder("aquila.command.idempotency.recovery.recovered.count")
            .description("recovered stale command idempotency row count")
            .register(meterRegistry);
    this.cleanupDeletedCounter =
        Counter.builder("aquila.command.idempotency.cleanup.deleted.count")
            .description("deleted expired command idempotency row count")
            .register(meterRegistry);

    Gauge.builder(
            "aquila.command.idempotency.started.count",
            this,
            metrics -> metrics.currentSummary().startedCount())
        .description("current STARTED command idempotency row count")
        .register(meterRegistry);
    Gauge.builder(
            "aquila.command.idempotency.stale.started.count",
            this,
            metrics -> metrics.currentSummary().staleStartedCount())
        .description("current stale STARTED command idempotency row count")
        .register(meterRegistry);
    Gauge.builder(
            "aquila.command.idempotency.completed.count",
            this,
            metrics -> metrics.currentSummary().completedCount())
        .description("current COMPLETED command idempotency row count")
        .register(meterRegistry);
    Gauge.builder(
            "aquila.command.idempotency.failed.count",
            this,
            metrics -> metrics.currentSummary().failedCount())
        .description("current FAILED command idempotency row count")
        .register(meterRegistry);
    Gauge.builder(
            "aquila.command.idempotency.cleanup.candidate.count",
            this,
            metrics -> metrics.currentSummary().cleanupCandidateCount())
        .description("current cleanup candidate command idempotency row count")
        .register(meterRegistry);
  }

  public void recordConflict(String message) {
    conflictCounters.get(classifyConflict(message)).increment();
  }

  public void recordRecovered(long recoveredCount) {
    if (recoveredCount <= 0) {
      return;
    }
    recoveredCounter.increment(recoveredCount);
  }

  public void recordCleanupDeleted(long deletedCount) {
    if (deletedCount <= 0) {
      return;
    }
    cleanupDeletedCounter.increment(deletedCount);
  }

  private CommandIdempotencyOpsSummary currentSummary() {
    Instant now = Instant.now();
    CachedSummary current = cachedSummary;
    if (current.expiresAt().isAfter(now)) {
      return current.summary();
    }
    synchronized (this) {
      CachedSummary refreshed = cachedSummary;
      if (refreshed.expiresAt().isAfter(now)) {
        return refreshed.summary();
      }
      CommandIdempotencyOpsQueryUseCase queryUseCase = queryUseCaseProvider.getIfAvailable();
      if (queryUseCase == null) {
        return EMPTY_SUMMARY;
      }
      try {
        CommandIdempotencyOpsSummary summary = queryUseCase.getSummary();
        cachedSummary = new CachedSummary(summary, now.plus(SNAPSHOT_TTL));
        return summary;
      } catch (RuntimeException ex) {
        log.debug("command idempotency prometheus summary refresh failed", ex);
        return refreshed.summary();
      }
    }
  }

  // 응답 message는 외부 계약이라 tag는 운영 triage에 필요한 축만 남깁니다.
  private ConflictReason classifyConflict(String message) {
    if ("idempotencyKey is already used with another request".equals(message)) {
      return ConflictReason.DIFFERENT_REQUEST;
    }
    if ("same command is already in progress".equals(message)) {
      return ConflictReason.IN_PROGRESS;
    }
    if ("idempotencyKey state is missing".equals(message)) {
      return ConflictReason.STATE_MISSING;
    }
    if ("transfer is already reversed".equals(message)) {
      return ConflictReason.ALREADY_REVERSED;
    }
    if ("reversal amount exceeds remaining amount".equals(message)) {
      return ConflictReason.AMOUNT_EXCEEDED;
    }
    return ConflictReason.OTHER;
  }

  private record CachedSummary(CommandIdempotencyOpsSummary summary, Instant expiresAt) {}

  private enum ConflictReason {
    DIFFERENT_REQUEST,
    IN_PROGRESS,
    STATE_MISSING,
    ALREADY_REVERSED,
    AMOUNT_EXCEEDED,
    OTHER
  }
}
