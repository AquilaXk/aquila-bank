package com.aquilabank.global.ops;

public record ServletThreadSaturationSnapshot(int busyThreads, int maxThreads) {

  public static ServletThreadSaturationSnapshot empty() {
    return new ServletThreadSaturationSnapshot(0, 0);
  }

  public boolean saturated(T3MicroSaturationGuardProperties.ServletThreads properties) {
    return maxThreads > 0
        && busyThreads
            >= Math.max(
                1, (int) Math.ceil(maxThreads * (properties.busyThresholdPercent() / 100.0)));
  }
}
