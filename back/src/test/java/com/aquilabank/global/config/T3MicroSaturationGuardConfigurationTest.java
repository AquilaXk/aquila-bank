package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.global.ops.DbPoolSaturationProbe;
import com.aquilabank.global.ops.DbPoolSaturationSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
}
