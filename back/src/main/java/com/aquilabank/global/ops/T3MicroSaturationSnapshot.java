package com.aquilabank.global.ops;

public record T3MicroSaturationSnapshot(
    DbPoolSaturationSnapshot pool,
    DbPoolSaturationSnapshot readReplicaPool,
    ServletThreadSaturationSnapshot servletThreads,
    JvmPressureSnapshot jvmPressure,
    int recentQueryTimeoutCount,
    boolean poolSaturated,
    boolean readReplicaPoolSaturated,
    boolean servletThreadsSaturated,
    boolean queryTimeoutSaturated,
    boolean jvmPressureSaturated) {

  public static T3MicroSaturationSnapshot empty() {
    return new T3MicroSaturationSnapshot(
        DbPoolSaturationSnapshot.empty(),
        DbPoolSaturationSnapshot.empty(),
        ServletThreadSaturationSnapshot.empty(),
        JvmPressureSnapshot.empty(),
        0,
        false,
        false,
        false,
        false,
        false);
  }

  public boolean saturated() {
    return (poolSaturated && servletThreadsSaturated && queryTimeoutSaturated)
        || jvmPressureSaturated;
  }
}
