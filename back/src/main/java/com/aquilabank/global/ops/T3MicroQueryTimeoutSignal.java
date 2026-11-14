package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class T3MicroQueryTimeoutSignal {

  private static final int BUCKET_COUNT = 64;
  private static final int BUCKET_MASK = BUCKET_COUNT - 1;
  private static final long MAX_BUCKET_COUNT = 0xFFFFFFFFL;

  private final Clock clock;
  private final Counter timeoutCounter;
  // 64개 초 단위 슬롯에 (epochSecond << 32) | count 를 비트 패킹해 락 없이 CAS로 기록한다.
  private final AtomicLongArray buckets = new AtomicLongArray(BUCKET_COUNT);
  private final AtomicLong version = new AtomicLong();

  public T3MicroQueryTimeoutSignal(Clock clock, MeterRegistry meterRegistry) {
    this.clock = clock;
    this.timeoutCounter =
        Counter.builder("aquila.t3micro.saturation.guard.query.timeouts")
            .description("recent query timeout events recorded for t3.micro saturation guard")
            .register(meterRegistry);
  }

  public void record() {
    long epochSecond = Math.floorDiv(clock.millis(), 1000L);
    int slot = (int) (epochSecond & BUCKET_MASK);
    long current;
    long updated;
    do {
      current = buckets.get(slot);
      long nextCount =
          (current >> 32) == epochSecond
              ? Math.min((current & MAX_BUCKET_COUNT) + 1L, MAX_BUCKET_COUNT)
              : 1L;
      updated = packBucket(epochSecond, nextCount);
    } while (!buckets.compareAndSet(slot, current, updated));
    version.incrementAndGet();
    timeoutCounter.increment();
  }

  public int recentCount(Duration window) {
    long windowSeconds = Math.min(window.toSeconds(), BUCKET_MASK);
    if (windowSeconds < 0L) {
      return 0;
    }
    long currentSecond = Math.floorDiv(clock.millis(), 1000L);
    long total = 0L;
    for (long age = 0L; age <= windowSeconds; age++) {
      long targetSecond = currentSecond - age;
      long packed = buckets.get((int) (targetSecond & BUCKET_MASK));
      if ((packed >> 32) == targetSecond) {
        total += packed & MAX_BUCKET_COUNT;
      }
    }
    return Math.clamp(total, 0, Integer.MAX_VALUE);
  }

  public long version() {
    return version.get();
  }

  private static long packBucket(long epochSecond, long count) {
    return (epochSecond << 32) | (count & MAX_BUCKET_COUNT);
  }
}
