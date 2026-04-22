package com.aquilabank.global.ops;

public record T3MicroSaturationSnapshot(
    DbPoolSaturationSnapshot pool,
    ServletThreadSaturationSnapshot servletThreads,
    int recentQueryTimeoutCount,
    boolean poolSaturated,
    boolean servletThreadsSaturated,
    boolean queryTimeoutSaturated) {

  public static T3MicroSaturationSnapshot empty() {
    return new T3MicroSaturationSnapshot(
        DbPoolSaturationSnapshot.empty(),
        ServletThreadSaturationSnapshot.empty(),
        0,
        false,
        false,
        false);
  }

  public boolean saturated() {
    return poolSaturated && servletThreadsSaturated && queryTimeoutSaturated;
  }
}
