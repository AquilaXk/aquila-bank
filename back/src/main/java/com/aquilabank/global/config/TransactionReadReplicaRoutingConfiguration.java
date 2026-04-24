package com.aquilabank.global.config;

import com.aquilabank.global.persistence.transaction.TransactionReadReplicaLagProbe;
import com.aquilabank.global.persistence.transaction.TransactionReadRoute;
import com.aquilabank.global.persistence.transaction.TransactionReadRoutingDataSource;
import com.aquilabank.global.persistence.transaction.TransactionReadRoutingPolicy;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.JdbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** transaction read scope에 query-shape/lag 기준 primary fallback을 적용합니다. */
@Configuration
@EnableConfigurationProperties(TransactionReadReplicaProperties.class)
public class TransactionReadReplicaRoutingConfiguration {

  @Bean(name = "transactionReadJdbcTemplate", defaultCandidate = false)
  @ConditionalOnMissingBean(name = "transactionReadJdbcTemplate")
  NamedParameterJdbcTemplate transactionReadJdbcTemplate(
      @Qualifier("transactionReadDataSource") DataSource transactionReadDataSource,
      JdbcProperties jdbcProperties) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(transactionReadDataSource);
    if (jdbcProperties.getTemplate().getQueryTimeout() != null) {
      jdbcTemplate.setQueryTimeout(
          (int) jdbcProperties.getTemplate().getQueryTimeout().getSeconds());
    }
    return new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  @Bean(name = "transactionReadDataSource", defaultCandidate = false)
  @ConditionalOnMissingBean(name = "transactionReadDataSource")
  DataSource transactionReadDataSource(
      @Qualifier("dataSource") DataSource primaryDataSource,
      @Qualifier("transactionReadReplicaDataSource") ObjectProvider<DataSource> replicaDataSource) {
    TransactionReadRoutingDataSource dataSource = new TransactionReadRoutingDataSource();
    DataSource readOnlyDataSource = replicaDataSource.getIfAvailable();
    Map<Object, Object> targets = new HashMap<>();
    targets.put(TransactionReadRoute.PRIMARY, primaryDataSource);
    targets.put(
        TransactionReadRoute.REPLICA,
        readOnlyDataSource != null ? readOnlyDataSource : primaryDataSource);
    dataSource.setTargetDataSources(targets);
    dataSource.setDefaultTargetDataSource(primaryDataSource);
    dataSource.afterPropertiesSet();
    return dataSource;
  }

  @Bean(name = "transactionReadTransactionManager", defaultCandidate = false)
  @ConditionalOnMissingBean(name = "transactionReadTransactionManager")
  JdbcTransactionManager transactionReadTransactionManager(
      @Qualifier("transactionReadDataSource") DataSource transactionReadDataSource) {
    return new JdbcTransactionManager(transactionReadDataSource);
  }

  @Bean
  @ConditionalOnMissingBean
  TransactionReadReplicaLagProbe transactionReadReplicaLagProbe(
      @Qualifier("transactionReadReplicaDataSource") ObjectProvider<DataSource> replicaDataSource,
      TransactionReadReplicaProperties transactionReadReplicaProperties) {
    return new TransactionReadReplicaLagProbe(
        replicaDataSource::getIfAvailable, transactionReadReplicaProperties, Clock.systemUTC());
  }

  @Bean
  @ConditionalOnMissingBean
  TransactionReadRoutingPolicy transactionReadRoutingPolicy(
      TransactionReadReplicaProperties transactionReadReplicaProperties,
      TransactionReadReplicaLagProbe transactionReadReplicaLagProbe,
      MeterRegistry meterRegistry) {
    return new TransactionReadRoutingPolicy(
        transactionReadReplicaProperties,
        transactionReadReplicaLagProbe::currentLag,
        Clock.systemUTC(),
        meterRegistry);
  }

  @Bean(name = "transactionReadReplicaDataSource", defaultCandidate = false)
  @Conditional(TransactionReadReplicaEnabledCondition.class)
  HikariDataSource transactionReadReplicaDataSource(
      TransactionReadReplicaProperties transactionReadReplicaProperties) {
    HikariDataSource dataSource =
        DataSourceBuilder.create(getClass().getClassLoader())
            .type(HikariDataSource.class)
            .driverClassName(transactionReadReplicaProperties.driverClassName())
            .url(transactionReadReplicaProperties.url())
            .username(transactionReadReplicaProperties.username())
            .password(transactionReadReplicaProperties.password())
            .build();
    dataSource.setPoolName("aquila-bank-transaction-read-replica");
    dataSource.setMinimumIdle(transactionReadReplicaProperties.minimumIdle());
    dataSource.setMaximumPoolSize(transactionReadReplicaProperties.maximumPoolSize());
    dataSource.setConnectionTimeout(transactionReadReplicaProperties.connectionTimeoutMs());
    dataSource.setValidationTimeout(transactionReadReplicaProperties.validationTimeoutMs());
    dataSource.setIdleTimeout(transactionReadReplicaProperties.idleTimeoutMs());
    dataSource.setMaxLifetime(transactionReadReplicaProperties.maxLifetimeMs());
    dataSource.setAutoCommit(false);
    dataSource.setReadOnly(true);
    // primary와 timeout/transaction 기본값을 맞춰 read path만 옮겨도 거동 차이를 줄입니다.
    dataSource.setConnectionInitSql(transactionReadReplicaProperties.connectionInitSql());
    dataSource.addDataSourceProperty("ApplicationName", "aquila-bank-transaction-read-replica");
    return dataSource;
  }

  static final class TransactionReadReplicaEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return context
              .getEnvironment()
              .getProperty("transaction.read-replica.enabled", Boolean.class, false)
          && context.getEnvironment().containsProperty("transaction.read-replica.url")
          && context.getEnvironment().getProperty("transaction.read-replica.url") != null
          && !context.getEnvironment().getProperty("transaction.read-replica.url", "").isBlank();
    }
  }
}
