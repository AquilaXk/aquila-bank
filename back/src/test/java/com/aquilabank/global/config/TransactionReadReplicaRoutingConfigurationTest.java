package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.JdbcProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

class TransactionReadReplicaRoutingConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TransactionReadReplicaRoutingConfiguration.class)
          .withBean("dataSource", DataSource.class, () -> mock(DataSource.class))
          .withBean(JdbcProperties.class, JdbcProperties::new);

  @Test
  void createsTransactionReadJdbcTemplateBackedByPrimaryWhenReplicaIsDisabled() {
    contextRunner
        .withPropertyValues("spring.jdbc.template.query-timeout=3s")
        .run(
            context -> {
              assertThat(context).hasBean("transactionReadDataSource");
              assertThat(context).hasBean("transactionReadJdbcTemplate");
              assertThat(context).doesNotHaveBean("transactionReadReplicaDataSource");
              NamedParameterJdbcTemplate jdbcTemplate =
                  context.getBean("transactionReadJdbcTemplate", NamedParameterJdbcTemplate.class);
              assertThat(jdbcTemplate.getJdbcTemplate().getDataSource())
                  .isInstanceOf(LazyConnectionDataSourceProxy.class);
              assertThat(jdbcTemplate.getJdbcTemplate().getQueryTimeout()).isEqualTo(3);
            });
  }

  @Test
  void createsDedicatedReplicaDataSourceWhenReplicaIsEnabled() {
    contextRunner
        .withPropertyValues(
            "transaction.read-replica.enabled=true",
            "transaction.read-replica.url=jdbc:postgresql://replica.example:5432/aquila_bank",
            "transaction.read-replica.username=replica_user",
            "transaction.read-replica.password=replica_password")
        .run(
            context -> {
              assertThat(context).hasBean("transactionReadReplicaDataSource");
              assertThat(context.getBean("transactionReadReplicaDataSource"))
                  .isNotSameAs(context.getBean("dataSource"));
            });
  }

  @Test
  void fallsBackToPrimaryWhenReplicaUrlIsMissing() {
    contextRunner
        .withPropertyValues("transaction.read-replica.enabled=true")
        .run(
            context -> {
              assertThat(context).hasBean("transactionReadJdbcTemplate");
              assertThat(context).doesNotHaveBean("transactionReadReplicaDataSource");
            });
  }
}
