package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.global.ops.DbPoolSaturationProbe;
import com.aquilabank.global.ops.DbPoolSaturationSnapshot;
import com.aquilabank.global.ops.JvmPressureSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class T3MicroSaturationGuardConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(T3MicroSaturationGuardConfiguration.class)
          .withBean("dataSource", DataSource.class, () -> mock(DataSource.class))
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

  @Test
  void createsEmptyReadReplicaPoolProbeWhenReplicaDataSourceIsMissing() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("transactionReadReplicaDbPoolSaturationProbe");
          DbPoolSaturationProbe probe =
              context.getBean(
                  "transactionReadReplicaDbPoolSaturationProbe", DbPoolSaturationProbe.class);

          assertThat(probe.snapshot()).isEqualTo(DbPoolSaturationSnapshot.empty());
        });
  }

  @Test
  void accumulatesGcCountsWithinWindowBeforeResettingAnchorAfterWindowExpires() {
    MemoryMXBean memoryMXBean = mock(MemoryMXBean.class);
    when(memoryMXBean.getHeapMemoryUsage()).thenReturn(new MemoryUsage(100L, 450L, 800L, 1_000L));
    GarbageCollectorMXBean gcBean = mock(GarbageCollectorMXBean.class);
    when(gcBean.getCollectionCount()).thenReturn(10L, 11L, 13L, 14L);
    when(gcBean.getCollectionTime()).thenReturn(100L, 180L, 360L, 400L);
    AtomicReference<Instant> currentInstant =
        new AtomicReference<>(Instant.parse("2026-04-22T00:00:00Z"));
    Clock clock =
        new Clock() {
          @Override
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return currentInstant.get();
          }
        };

    T3MicroSaturationGuardConfiguration.JmxJvmPressureProbe probe =
        new T3MicroSaturationGuardConfiguration.JmxJvmPressureProbe(
            memoryMXBean, List.of(gcBean), clock);
    Duration window = Duration.ofSeconds(10);

    JvmPressureSnapshot first = probe.snapshot(window);
    assertThat(first).isEqualTo(new JvmPressureSnapshot(450L, 1_000L, 0L, 0L));

    currentInstant.set(Instant.parse("2026-04-22T00:00:03Z"));
    JvmPressureSnapshot second = probe.snapshot(window);
    assertThat(second).isEqualTo(new JvmPressureSnapshot(450L, 1_000L, 1L, 80L));

    currentInstant.set(Instant.parse("2026-04-22T00:00:07Z"));
    JvmPressureSnapshot third = probe.snapshot(window);
    assertThat(third).isEqualTo(new JvmPressureSnapshot(450L, 1_000L, 3L, 260L));

    currentInstant.set(Instant.parse("2026-04-22T00:00:11Z"));
    JvmPressureSnapshot resetAfterWindow = probe.snapshot(window);
    assertThat(resetAfterWindow).isEqualTo(new JvmPressureSnapshot(450L, 1_000L, 0L, 0L));
  }
}
