package com.aquilabank.global.ops;

public record JvmPressureSnapshot(
    long heapUsedBytes, long heapMaxBytes, long recentGcCollectionCount, long recentGcTimeMs) {

  public JvmPressureSnapshot {
    heapUsedBytes = Math.max(heapUsedBytes, 0L);
    heapMaxBytes = Math.max(heapMaxBytes, 0L);
    recentGcCollectionCount = Math.max(recentGcCollectionCount, 0L);
    recentGcTimeMs = Math.max(recentGcTimeMs, 0L);
  }

  public static JvmPressureSnapshot empty() {
    return new JvmPressureSnapshot(0L, 0L, 0L, 0L);
  }

  public boolean saturated(T3MicroSaturationGuardProperties.JvmPressure properties) {
    if (!properties.enabled()) {
      return false;
    }
    boolean heapSaturated =
        heapMaxBytes > 0
            && heapUsedBytes
                >= Math.max(
                    1L,
                    (long)
                        Math.ceil(heapMaxBytes * (properties.heapUsedThresholdPercent() / 100.0)));
    boolean gcCountSaturated =
        properties.gcCollectionThreshold() > 0
            && recentGcCollectionCount >= properties.gcCollectionThreshold();
    boolean gcTimeSaturated =
        properties.gcTimeThresholdMs() > 0 && recentGcTimeMs >= properties.gcTimeThresholdMs();
    return heapSaturated || gcCountSaturated || gcTimeSaturated;
  }
}
