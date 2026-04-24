package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.global.config.TransactionReadReplicaProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** query shape와 replica lag를 함께 봐서 강한 read-after-write 경로를 primary로 보냅니다. */
public final class TransactionReadRoutingPolicy {

  private final TransactionReadReplicaProperties properties;
  private final Supplier<TransactionReadReplicaLagProbe.ReplicaLag> lagSupplier;
  private final Clock clock;
  private final MeterRegistry meterRegistry;
  private final AtomicLong latestReplicaLagMs = new AtomicLong(-1L);

  public TransactionReadRoutingPolicy(
      TransactionReadReplicaProperties properties,
      Supplier<TransactionReadReplicaLagProbe.ReplicaLag> lagSupplier,
      Clock clock) {
    this(properties, lagSupplier, clock, null);
  }

  public TransactionReadRoutingPolicy(
      TransactionReadReplicaProperties properties,
      Supplier<TransactionReadReplicaLagProbe.ReplicaLag> lagSupplier,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.lagSupplier = Objects.requireNonNull(lagSupplier, "lagSupplier");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.meterRegistry = meterRegistry;
    if (meterRegistry != null) {
      Gauge.builder("aquila.transaction.read_replica.lag.ms", latestReplicaLagMs, AtomicLong::get)
          .description("Latest measured transaction read replica replay lag in milliseconds")
          .register(meterRegistry);
    }
  }

  public TransactionReadRouteDecision routeDetail() {
    return recordDecision("detail", TransactionReadRouteDecision.primary("detail_exact", -1L));
  }

  public TransactionReadRouteDecision routeTimeline(TransactionQuery query) {
    if (query.transactionReference() != null) {
      return recordDecision(
          "timeline", TransactionReadRouteDecision.primary("reference_exact", -1L));
    }
    if (isHotFirstPage(query)) {
      return recordDecision(
          "timeline", TransactionReadRouteDecision.primary("hot_first_page", -1L));
    }
    return recordDecision("timeline", routeReplicaCandidate());
  }

  public TransactionReadRouteDecision routeArchive(TransactionQuery query) {
    return recordDecision("archive", routeReplicaCandidate());
  }

  private boolean isHotFirstPage(TransactionQuery query) {
    if (query.cursor() != null) {
      return false;
    }
    Instant hotCutoff = clock.instant().minusMillis(properties.hotReadWindowMs());
    return !query.to().isBefore(hotCutoff);
  }

  private TransactionReadRouteDecision routeReplicaCandidate() {
    if (!properties.configured()) {
      return TransactionReadRouteDecision.primary("replica_disabled", -1L);
    }
    TransactionReadReplicaLagProbe.ReplicaLag lag = lagSupplier.get();
    if (!lag.available()) {
      return TransactionReadRouteDecision.primary(lag.reason(), lag.replicaLagMs());
    }
    if (lag.replicaLagMs() > properties.lagThresholdMs()) {
      return TransactionReadRouteDecision.primary("lag_exceeded", lag.replicaLagMs());
    }
    return TransactionReadRouteDecision.replica("replica_healthy", lag.replicaLagMs());
  }

  private TransactionReadRouteDecision recordDecision(
      String queryShape, TransactionReadRouteDecision decision) {
    if (decision.replicaLagMs() >= 0) {
      latestReplicaLagMs.set(decision.replicaLagMs());
    }
    if (meterRegistry != null) {
      meterRegistry
          .counter(
              "aquila.transaction.read_replica.route.decisions",
              "query_shape",
              queryShape,
              "route",
              decision.route().name().toLowerCase(Locale.ROOT),
              "reason",
              decision.reason())
          .increment();
    }
    return decision;
  }
}
