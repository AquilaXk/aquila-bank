package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** transaction read replica 연결/timeout/pool 기준을 고정합니다. */
@ConfigurationProperties(prefix = "transaction.read-replica")
public record TransactionReadReplicaProperties(
    boolean enabled,
    String url,
    String username,
    String password,
    String driverClassName,
    int minimumIdle,
    int maximumPoolSize,
    long connectionTimeoutMs,
    long validationTimeoutMs,
    long idleTimeoutMs,
    long maxLifetimeMs,
    long statementTimeoutMs,
    long lockTimeoutMs,
    long idleInTransactionTimeoutMs) {

  public TransactionReadReplicaProperties {
    url = StringUtils.hasText(url) ? url : null;
    username = StringUtils.hasText(username) ? username : null;
    password = password == null ? "" : password;
    driverClassName =
        StringUtils.hasText(driverClassName) ? driverClassName : "org.postgresql.Driver";
    minimumIdle = Math.max(minimumIdle, 0);
    maximumPoolSize = maximumPoolSize > 0 ? maximumPoolSize : 2;
    if (minimumIdle > maximumPoolSize) {
      minimumIdle = maximumPoolSize;
    }
    connectionTimeoutMs = positiveOrDefault(connectionTimeoutMs, 3000L);
    validationTimeoutMs = positiveOrDefault(validationTimeoutMs, 1000L);
    idleTimeoutMs = positiveOrDefault(idleTimeoutMs, 300000L);
    maxLifetimeMs = positiveOrDefault(maxLifetimeMs, 1700000L);
    statementTimeoutMs = positiveOrDefault(statementTimeoutMs, 3000L);
    lockTimeoutMs = positiveOrDefault(lockTimeoutMs, 1000L);
    idleInTransactionTimeoutMs = positiveOrDefault(idleInTransactionTimeoutMs, 5000L);
  }

  public boolean configured() {
    return enabled && StringUtils.hasText(url);
  }

  public String connectionInitSql() {
    return """
        SET statement_timeout TO '%dms';
        SET lock_timeout TO '%dms';
        SET idle_in_transaction_session_timeout TO '%dms'
        """
        .formatted(statementTimeoutMs, lockTimeoutMs, idleInTransactionTimeoutMs)
        .replace('\n', ' ')
        .trim();
  }

  private static long positiveOrDefault(long value, long defaultValue) {
    return value > 0 ? value : defaultValue;
  }
}
