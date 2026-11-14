package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class ApiAdmissionControl {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final ApiAdmissionControlProperties properties;
  private final EndpointState[] endpoints;

  public ApiAdmissionControl(
      ApiAdmissionControlProperties properties, MeterRegistry meterRegistry) {
    this(properties, meterRegistry, System::nanoTime);
  }

  ApiAdmissionControl(
      ApiAdmissionControlProperties properties, MeterRegistry meterRegistry, LongSupplier ticker) {
    this.properties = properties;
    this.endpoints =
        properties.endpoints().stream()
            .map(
                endpoint ->
                    new EndpointState(
                        endpoint, properties.retryAfterSeconds(), meterRegistry, ticker))
            .toArray(EndpointState[]::new);
  }

  public ApiAdmissionPermit tryAcquire(String rawPath) {
    if (!properties.enabled()) {
      return ApiAdmissionPermit.ignored();
    }
    EndpointState endpoint = findEndpoint(pathOnly(rawPath));
    if (endpoint == null) {
      return ApiAdmissionPermit.ignored();
    }
    if (!endpoint.tryAcquire()) {
      endpoint.recordRejection();
      return endpoint.rejectedPermit();
    }
    endpoint.recordAccepted();
    return ApiAdmissionPermit.acquired(endpoint.group(), endpoint);
  }

  private EndpointState findEndpoint(String path) {
    EndpointState result = null;
    int longestPrefix = -1;
    for (EndpointState endpoint : endpoints) {
      int matchedPrefix = endpoint.matchedPrefixLength(path);
      if (matchedPrefix > longestPrefix) {
        result = endpoint;
        longestPrefix = matchedPrefix;
      }
    }
    // archive처럼 겹치는 prefix는 더 구체적인 endpoint group이 admission budget을 가져간다.
    return result;
  }

  private String pathOnly(String rawPath) {
    int queryIndex = rawPath.indexOf('?');
    return queryIndex < 0 ? rawPath : rawPath.substring(0, queryIndex);
  }

  private static final class EndpointState implements ApiAdmissionPermit.ReleaseTarget {

    private final String group;
    private final String[] pathPrefixes;
    private final ApiAdmissionControlProperties.AdaptiveLimit adaptive;
    private final ApiAdmissionPermit rejectedPermit;
    private final AtomicInteger currentLimit;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger healthyCompletions = new AtomicInteger();
    private final LongSupplier ticker;
    private final int rejectionWindowSize;
    private final long rejectionWindowMask;
    // 최근 최대 64건의 거부 여부를 64비트 CAS 슬라이딩 비트마스크로 유지해 락과 배열 할당을 제거한다.
    private final AtomicLong rejectionWindowBits = new AtomicLong();
    private final AtomicInteger rejectionWindowSamples = new AtomicInteger();
    private final AtomicLong decreaseCooldownUntilNanos;
    private final Counter acceptedCounter;
    private final Counter rejectedCounter;

    private EndpointState(
        ApiAdmissionControlProperties.EndpointLimit endpoint,
        int defaultRetryAfterSeconds,
        MeterRegistry meterRegistry,
        LongSupplier ticker) {
      this.group = endpoint.group();
      int resolvedRetryAfterSeconds =
          endpoint.retryAfterSeconds() >= 0
              ? endpoint.retryAfterSeconds()
              : defaultRetryAfterSeconds;
      this.rejectedPermit = ApiAdmissionPermit.rejected(this.group, resolvedRetryAfterSeconds);
      this.pathPrefixes = endpoint.pathPrefixes().toArray(String[]::new);
      this.adaptive = endpoint.adaptive();
      this.currentLimit = new AtomicInteger(endpoint.maxConcurrency());
      this.ticker = ticker;
      this.rejectionWindowSize = adaptive.rejectionWindowSize();
      this.rejectionWindowMask = rejectionWindowSize == 64 ? -1L : (1L << rejectionWindowSize) - 1L;
      this.decreaseCooldownUntilNanos = new AtomicLong(ticker.getAsLong());
      this.acceptedCounter =
          Counter.builder("aquila.api.admission.requests")
              .tag("group", group)
              .tag("outcome", "accepted")
              .description("endpoint admission control request decisions")
              .register(meterRegistry);
      this.rejectedCounter =
          Counter.builder("aquila.api.admission.requests")
              .tag("group", group)
              .tag("outcome", "rejected")
              .description("endpoint admission control request decisions")
              .register(meterRegistry);
      Gauge.builder("aquila.api.admission.inflight", inFlight, AtomicInteger::get)
          .tag("group", group)
          .description("endpoint admission control in-flight requests")
          .register(meterRegistry);
      Gauge.builder("aquila.api.admission.limit", currentLimit, AtomicInteger::get)
          .tag("group", group)
          .description("endpoint admission control current concurrency limit")
          .register(meterRegistry);
    }

    private String group() {
      return group;
    }

    private ApiAdmissionPermit rejectedPermit() {
      return rejectedPermit;
    }

    private int matchedPrefixLength(String path) {
      int result = -1;
      for (String prefix : pathPrefixes) {
        if (path.startsWith(prefix) && prefix.length() > result) {
          result = prefix.length();
        }
      }
      return result;
    }

    private boolean tryAcquire() {
      int current;
      do {
        current = inFlight.get();
        if (current >= currentLimit.get()) {
          return false;
        }
      } while (!inFlight.compareAndSet(current, current + 1));
      return true;
    }

    private void recordAccepted() {
      acceptedCounter.increment();
      if (adaptive.enabled()) {
        recordAdmissionDecision(false);
      }
    }

    private void recordRejection() {
      rejectedCounter.increment();
      if (!adaptive.enabled()) {
        return;
      }
      healthyCompletions.set(0);
      if (!tryReserveDecreaseOnRejection()) {
        return;
      }
      // 429가 짧게 튄 경우는 window ratio로 거르고, 실제 포화가 이어질 때만 단계적으로 낮춥니다.
      currentLimit.updateAndGet(
          value -> Math.max(adaptive.minConcurrency(), value - adaptive.decreaseOnRejections()));
    }

    private long recordAdmissionDecision(boolean rejected) {
      long bit = rejected ? 1L : 0L;
      long current;
      long updatedBits;
      do {
        current = rejectionWindowBits.get();
        updatedBits = ((current << 1) | bit) & rejectionWindowMask;
      } while (!rejectionWindowBits.compareAndSet(current, updatedBits));
      if (rejectionWindowSamples.get() < rejectionWindowSize) {
        rejectionWindowSamples.updateAndGet(
            samples -> samples < rejectionWindowSize ? samples + 1 : samples);
      }
      return updatedBits;
    }

    private boolean tryReserveDecreaseOnRejection() {
      long bits = recordAdmissionDecision(true);
      if (currentLimit.get() <= adaptive.minConcurrency()) {
        return false;
      }
      int samples = rejectionWindowSamples.get();
      if (samples < rejectionWindowSize) {
        return false;
      }
      int rejectedInWindow = Long.bitCount(bits);
      double ratio = (double) rejectedInWindow / samples;
      if (ratio < adaptive.decreaseRejectionRatio()) {
        return false;
      }
      long nowNanos = ticker.getAsLong();
      long cooldownNanos = adaptive.decreaseCooldownSeconds() * NANOS_PER_SECOND;
      long cooldownUntil;
      do {
        cooldownUntil = decreaseCooldownUntilNanos.get();
        if (cooldownNanos > 0 && nowNanos - cooldownUntil < 0) {
          return false;
        }
      } while (!decreaseCooldownUntilNanos.compareAndSet(cooldownUntil, nowNanos + cooldownNanos));
      return true;
    }

    @Override
    public void release(boolean healthy) {
      inFlight.decrementAndGet();
      if (healthy) {
        recordHealthyCompletion();
      } else {
        healthyCompletions.set(0);
      }
    }

    private void recordHealthyCompletion() {
      if (!adaptive.enabled() || currentLimit.get() >= adaptive.maxConcurrency()) {
        return;
      }
      int completions = healthyCompletions.incrementAndGet();
      // in-flight가 낮으면 포화가 풀린 구간으로 보고 limit 회복을 더 빠르게 허용한다.
      boolean lowSaturation = inFlight.get() <= adaptive.lowSaturationMaxInFlight();
      int requiredCompletions =
          lowSaturation
              ? adaptive.lowSaturationIncreaseEverySuccesses()
              : adaptive.increaseEverySuccesses();
      int step = lowSaturation ? adaptive.lowSaturationRecoveryStep() : adaptive.recoveryStep();
      if (completions < requiredCompletions) {
        return;
      }
      if (healthyCompletions.compareAndSet(completions, 0)) {
        currentLimit.updateAndGet(value -> Math.min(adaptive.maxConcurrency(), value + step));
      }
    }
  }
}
