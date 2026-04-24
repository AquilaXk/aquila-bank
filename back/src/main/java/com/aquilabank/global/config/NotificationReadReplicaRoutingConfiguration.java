package com.aquilabank.global.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** notification list/search read path를 replica datasource로 분리하고 미구성 시 primary로 fallback 합니다. */
@Configuration
public class NotificationReadReplicaRoutingConfiguration {

  @Bean(name = "notificationReadDataSource", defaultCandidate = false)
  @ConditionalOnMissingBean(name = "notificationReadDataSource")
  DataSource notificationReadDataSource(
      @Qualifier("dataSource") DataSource primaryDataSource,
      @Qualifier("transactionReadReplicaDataSource") ObjectProvider<DataSource> replicaDataSource) {
    DataSource readOnlyDataSource = replicaDataSource.getIfAvailable();
    return readOnlyDataSource != null ? readOnlyDataSource : primaryDataSource;
  }

  @Bean(name = "notificationReadJdbcTemplate", defaultCandidate = false)
  @ConditionalOnMissingBean(name = "notificationReadJdbcTemplate")
  NamedParameterJdbcTemplate notificationReadJdbcTemplate(
      @Qualifier("notificationReadDataSource") DataSource notificationReadDataSource,
      JdbcProperties jdbcProperties) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(notificationReadDataSource);
    if (jdbcProperties.getTemplate().getQueryTimeout() != null) {
      jdbcTemplate.setQueryTimeout(
          (int) jdbcProperties.getTemplate().getQueryTimeout().getSeconds());
    }
    return new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  @Bean(name = "notificationReadTransactionManager", defaultCandidate = false)
  @ConditionalOnMissingBean(name = "notificationReadTransactionManager")
  JdbcTransactionManager notificationReadTransactionManager(
      @Qualifier("notificationReadDataSource") DataSource notificationReadDataSource) {
    return new JdbcTransactionManager(notificationReadDataSource);
  }
}
