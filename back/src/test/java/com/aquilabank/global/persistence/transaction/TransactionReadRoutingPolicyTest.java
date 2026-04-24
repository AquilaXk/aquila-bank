package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.global.config.TransactionReadReplicaProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TransactionReadRoutingPolicyTest {

  private static final Instant NOW = Instant.parse("2026-04-24T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final long ACCOUNT_ID = 101L;

  @Test
  void routesDetailExactLookupToPrimary() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(10));

    TransactionReadRouteDecision decision = policy.routeDetail();

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.PRIMARY);
    assertThat(decision.reason()).isEqualTo("detail_exact");
  }

  @Test
  void routesReferenceExactTimelineLookupToPrimary() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(10));

    TransactionReadRouteDecision decision = policy.routeTimeline(referenceQuery());

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.PRIMARY);
    assertThat(decision.reason()).isEqualTo("reference_exact");
  }

  @Test
  void routesHotFirstPageToPrimary() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(10));

    TransactionReadRouteDecision decision = policy.routeTimeline(firstPageEndingAt(NOW));

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.PRIMARY);
    assertThat(decision.reason()).isEqualTo("hot_first_page");
  }

  @Test
  void routesOldFirstPageToReplicaWhenLagIsHealthy() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(10));

    TransactionReadRouteDecision decision =
        policy.routeTimeline(firstPageEndingAt(NOW.minusSeconds(120)));

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.REPLICA);
    assertThat(decision.reason()).isEqualTo("replica_healthy");
  }

  @Test
  void routesCursorPageToReplicaWhenLagIsHealthy() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(10));

    TransactionReadRouteDecision decision = policy.routeTimeline(cursorPageEndingAt(NOW));

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.REPLICA);
    assertThat(decision.reason()).isEqualTo("replica_healthy");
  }

  @Test
  void routesArchiveToReplicaWhenLagIsHealthy() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(10));

    TransactionReadRouteDecision decision = policy.routeArchive(firstPageEndingAt(NOW));

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.REPLICA);
    assertThat(decision.reason()).isEqualTo("replica_healthy");
  }

  @Test
  void fallsBackToPrimaryWhenLagIsUnknown() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.unavailable("lag_unknown"));

    TransactionReadRouteDecision decision =
        policy.routeArchive(firstPageEndingAt(NOW.minusSeconds(120)));

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.PRIMARY);
    assertThat(decision.reason()).isEqualTo("lag_unknown");
  }

  @Test
  void fallsBackToPrimaryWhenLagExceedsThreshold() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(3001));

    TransactionReadRouteDecision decision =
        policy.routeArchive(firstPageEndingAt(NOW.minusSeconds(120)));

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.PRIMARY);
    assertThat(decision.reason()).isEqualTo("lag_exceeded");
  }

  @Test
  void treatsPrimaryCompatibleReplicaProbeAsHealthyLagZero() {
    TransactionReadRoutingPolicy policy =
        policy(TransactionReadReplicaLagProbe.ReplicaLag.healthy(0, "not_in_recovery"));

    TransactionReadRouteDecision decision =
        policy.routeArchive(firstPageEndingAt(NOW.minusSeconds(120)));

    assertThat(decision.route()).isEqualTo(TransactionReadRoute.REPLICA);
    assertThat(decision.reason()).isEqualTo("replica_healthy");
  }

  private static TransactionReadRoutingPolicy policy(
      TransactionReadReplicaLagProbe.ReplicaLag lag) {
    return new TransactionReadRoutingPolicy(enabledProperties(), () -> lag, CLOCK);
  }

  private static TransactionReadReplicaProperties enabledProperties() {
    return new TransactionReadReplicaProperties(
        true,
        "jdbc:postgresql://replica.example:5432/aquila_bank",
        "replica_user",
        "replica_password",
        null,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        3000,
        1000,
        30000);
  }

  private static TransactionQuery firstPageEndingAt(Instant to) {
    return new TransactionQuery(
        ACCOUNT_ID, to.minusSeconds(30), to, 20, null, null, null, null, null, null);
  }

  private static TransactionQuery cursorPageEndingAt(Instant to) {
    return new TransactionQuery(
        ACCOUNT_ID,
        to.minusSeconds(30),
        to,
        20,
        new TransactionCursor(to.minusSeconds(10), 200L),
        null,
        null,
        null,
        null,
        null);
  }

  private static TransactionQuery referenceQuery() {
    return new TransactionQuery(
        ACCOUNT_ID, NOW.minusSeconds(30), NOW, 20, null, null, null, null, null, "trx-reference-1");
  }
}
