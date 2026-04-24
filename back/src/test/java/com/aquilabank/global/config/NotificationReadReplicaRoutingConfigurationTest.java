package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.JdbcProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class NotificationReadReplicaRoutingConfigurationTest {

  private final DataSource primaryDataSource = mock(DataSource.class);
  private final DataSource replicaDataSource = mock(DataSource.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(NotificationReadReplicaRoutingConfiguration.class)
          .withBean("dataSource", DataSource.class, () -> primaryDataSource)
          .withBean(JdbcProperties.class, JdbcProperties::new);

  @Test
  void fallsBackToPrimaryWhenTransactionReadReplicaDataSourceIsMissing() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("notificationReadJdbcTemplate");
          assertThat(context).hasBean("notificationReadTransactionManager");

          NamedParameterJdbcTemplate jdbcTemplate =
              context.getBean("notificationReadJdbcTemplate", NamedParameterJdbcTemplate.class);
          assertThat(jdbcTemplate.getJdbcTemplate().getDataSource()).isSameAs(primaryDataSource);
        });
  }

  @Test
  void usesTransactionReadReplicaDataSourceWhenPresent() {
    contextRunner
        .withBean("transactionReadReplicaDataSource", DataSource.class, () -> replicaDataSource)
        .run(
            context -> {
              NamedParameterJdbcTemplate jdbcTemplate =
                  context.getBean("notificationReadJdbcTemplate", NamedParameterJdbcTemplate.class);

              assertThat(jdbcTemplate.getJdbcTemplate().getDataSource())
                  .isSameAs(replicaDataSource);
            });
  }
}
