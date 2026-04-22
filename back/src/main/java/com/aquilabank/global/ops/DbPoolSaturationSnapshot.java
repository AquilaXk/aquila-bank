package com.aquilabank.global.ops;

public record DbPoolSaturationSnapshot(
    int activeConnections, int totalConnections, int threadsAwaitingConnection) {

  public static DbPoolSaturationSnapshot empty() {
    return new DbPoolSaturationSnapshot(0, 0, 0);
  }

  public boolean saturated(T3MicroSaturationGuardProperties.Pool properties) {
    boolean activeSaturated =
        totalConnections > 0
            && activeConnections
                >= thresholdCount(totalConnections, properties.activeThresholdPercent());
    boolean awaitingSaturated =
        properties.awaitingThreadsThreshold() > 0
            && threadsAwaitingConnection >= properties.awaitingThreadsThreshold();
    return activeSaturated || awaitingSaturated;
  }

  private int thresholdCount(int total, int percent) {
    return Math.max(1, (int) Math.ceil(total * (percent / 100.0)));
  }
}
