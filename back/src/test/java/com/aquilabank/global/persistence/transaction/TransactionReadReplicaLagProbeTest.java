package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.global.config.TransactionReadReplicaProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class TransactionReadReplicaLagProbeTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-24T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void returnsZeroLagWhenConfiguredDataSourceIsPrimaryForLocalTests() throws Exception {
    DataSource dataSource = dataSource(false, 0L, false);
    TransactionReadReplicaLagProbe probe =
        new TransactionReadReplicaLagProbe(() -> dataSource, properties(), CLOCK);

    TransactionReadReplicaLagProbe.ReplicaLag lag = probe.currentLag();

    assertThat(lag.available()).isTrue();
    assertThat(lag.lagMs()).isEqualTo(0);
    assertThat(lag.reason()).isEqualTo("not_in_recovery");
  }

  @Test
  void returnsHealthyLagWhenReplicaReplayTimestampIsAvailable() throws Exception {
    DataSource dataSource = dataSource(true, 120L, false);
    TransactionReadReplicaLagProbe probe =
        new TransactionReadReplicaLagProbe(() -> dataSource, properties(), CLOCK);

    TransactionReadReplicaLagProbe.ReplicaLag lag = probe.currentLag();

    assertThat(lag.available()).isTrue();
    assertThat(lag.lagMs()).isEqualTo(120);
    assertThat(lag.reason()).isEqualTo("lag_measured");
  }

  @Test
  void returnsUnknownWhenReplicaReplayTimestampIsMissing() throws Exception {
    DataSource dataSource = dataSource(true, 0L, true);
    TransactionReadReplicaLagProbe probe =
        new TransactionReadReplicaLagProbe(() -> dataSource, properties(), CLOCK);

    TransactionReadReplicaLagProbe.ReplicaLag lag = probe.currentLag();

    assertThat(lag.available()).isFalse();
    assertThat(lag.reason()).isEqualTo("lag_unknown");
  }

  @Test
  void cachesProbeResultWithinTtl() throws Exception {
    DataSource dataSource = dataSource(true, 120L, false);
    TransactionReadReplicaLagProbe probe =
        new TransactionReadReplicaLagProbe(() -> dataSource, properties(), CLOCK);

    probe.currentLag();
    probe.currentLag();

    verify(dataSource).getConnection();
  }

  @Test
  void returnsUnavailableWhenProbeQueryFails() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("replica down"));
    TransactionReadReplicaLagProbe probe =
        new TransactionReadReplicaLagProbe(() -> dataSource, properties(), CLOCK);

    TransactionReadReplicaLagProbe.ReplicaLag lag = probe.currentLag();

    assertThat(lag.available()).isFalse();
    assertThat(lag.reason()).isEqualTo("probe_error");
  }

  private static TransactionReadReplicaProperties properties() {
    return new TransactionReadReplicaProperties(
        true,
        "jdbc:postgresql://replica.example:5432/aquila_bank",
        "replica_user",
        "replica_password",
        null,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        3000,
        1000,
        30000);
  }

  private static DataSource dataSource(boolean inRecovery, long lagMs, boolean lagNull)
      throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(TransactionReadReplicaLagProbe.PROBE_SQL))
        .thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getBoolean("in_recovery")).thenReturn(inRecovery);
    when(resultSet.getLong("lag_ms")).thenReturn(lagMs);
    when(resultSet.wasNull()).thenReturn(lagNull);
    return dataSource;
  }
}
