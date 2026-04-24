package com.aquilabank.global.persistence.transaction;

public record TransactionReadRouteDecision(
    TransactionReadRoute route, String reason, long replicaLagMs) {

  public TransactionReadRouteDecision {
    if (route == null) {
      throw new IllegalArgumentException("route must not be null");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
  }

  public static TransactionReadRouteDecision primary(String reason, long replicaLagMs) {
    return new TransactionReadRouteDecision(TransactionReadRoute.PRIMARY, reason, replicaLagMs);
  }

  public static TransactionReadRouteDecision replica(String reason, long replicaLagMs) {
    return new TransactionReadRouteDecision(TransactionReadRoute.REPLICA, reason, replicaLagMs);
  }
}
