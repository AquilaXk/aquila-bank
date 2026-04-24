package com.aquilabank.global.persistence.transaction;

import com.aquilabank.global.config.TransactionReadReplicaProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** replica replay lag를 짧게 cache해 고트래픽 read path의 probe 비용을 제한합니다. */
public final class TransactionReadReplicaLagProbe {

  static final String PROBE_SQL =
      """
      SELECT
          pg_is_in_recovery() AS in_recovery,
          CASE
              WHEN pg_is_in_recovery()
                  THEN FLOOR(EXTRACT(EPOCH FROM (clock_timestamp() - pg_last_xact_replay_timestamp())) * 1000)::bigint
              ELSE 0
          END AS lag_ms
      """;

  private final Supplier<DataSource> replicaDataSourceSupplier;
  private final TransactionReadReplicaProperties properties;
  private final Clock clock;
  private volatile CachedLag cachedLag;

  public TransactionReadReplicaLagProbe(
      Supplier<DataSource> replicaDataSourceSupplier,
      TransactionReadReplicaProperties properties,
      Clock clock) {
    this.replicaDataSourceSupplier =
        Objects.requireNonNull(replicaDataSourceSupplier, "replicaDataSourceSupplier");
    this.properties = Objects.requireNonNull(properties, "properties");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public ReplicaLag currentLag() {
    Instant now = clock.instant();
    CachedLag current = cachedLag;
    if (current != null && now.isBefore(current.expiresAt())) {
      return current.value();
    }
    ReplicaLag measured = measure();
    cachedLag = new CachedLag(measured, now.plusMillis(properties.probeCacheMs()));
    return measured;
  }

  private ReplicaLag measure() {
    DataSource dataSource = replicaDataSourceSupplier.get();
    if (!properties.configured() || dataSource == null) {
      return ReplicaLag.unavailable("replica_disabled");
    }
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(PROBE_SQL);
        var resultSet = statement.executeQuery()) {
      if (!resultSet.next()) {
        return ReplicaLag.unavailable("lag_unknown");
      }
      return mapLag(resultSet);
    } catch (SQLException ex) {
      return ReplicaLag.unavailable("probe_error");
    }
  }

  private ReplicaLag mapLag(ResultSet resultSet) throws SQLException {
    boolean inRecovery = resultSet.getBoolean("in_recovery");
    long lagMs = resultSet.getLong("lag_ms");
    if (resultSet.wasNull()) {
      return ReplicaLag.unavailable("lag_unknown");
    }
    if (!inRecovery) {
      return ReplicaLag.healthy(0L, "not_in_recovery");
    }
    return ReplicaLag.healthy(Math.max(lagMs, 0L), "lag_measured");
  }

  public record ReplicaLag(boolean available, long replicaLagMs, String reason) {

    public ReplicaLag {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason must not be blank");
      }
    }

    public static ReplicaLag healthy(long replicaLagMs) {
      return healthy(replicaLagMs, "lag_measured");
    }

    public static ReplicaLag healthy(long replicaLagMs, String reason) {
      return new ReplicaLag(true, Math.max(replicaLagMs, 0L), reason);
    }

    public static ReplicaLag unavailable(String reason) {
      return new ReplicaLag(false, -1L, reason);
    }

    public long lagMs() {
      return replicaLagMs;
    }
  }

  private record CachedLag(ReplicaLag value, Instant expiresAt) {}
}
