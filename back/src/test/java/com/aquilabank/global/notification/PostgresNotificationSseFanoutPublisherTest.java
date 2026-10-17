package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.global.config.NotificationSseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresNotificationSseFanoutPublisherTest {

  @Test
  void publishesPgNotifyAndRestoresPreviousAutoCommitState() throws Exception {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    when(connection.getAutoCommit()).thenReturn(false);
    when(connection.prepareStatement("SELECT pg_notify(?, ?)")).thenReturn(statement);
    when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Void>>any()))
        .thenAnswer(
            invocation -> {
              ConnectionCallback<?> callback = invocation.getArgument(0);
              return callback.doInConnection(connection);
            });

    NotificationSseProperties properties =
        new NotificationSseProperties(
            60_000L, 10_000L, 3_000L, 100, 100, 4, 32, "notification_sse_fanout", 1_000L, 2_000L);
    NotificationSseFanoutInstanceId instanceId = new NotificationSseFanoutInstanceId("instance-a");
    NotificationSseFanoutSignalCodec codec =
        new NotificationSseFanoutSignalCodec(new ObjectMapper());
    PostgresNotificationSseFanoutPublisher publisher =
        new PostgresNotificationSseFanoutPublisher(jdbcTemplate, properties, instanceId, codec);

    NotificationSummary summary =
        new NotificationSummary(
            15L,
            101L,
            "TransferBooked",
            "이체 완료",
            "1500 KRW 출금",
            Instant.parse("2026-04-17T00:00:00Z"),
            null);

    publisher.handleNotificationInboxInserted(new NotificationInboxInsertedEvent(List.of(summary)));

    InOrder inOrder = inOrder(connection, statement);
    inOrder.verify(connection).getAutoCommit();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(statement).setString(1, "notification_sse_fanout");
    inOrder.verify(statement).execute();
    inOrder.verify(connection).setAutoCommit(false);
    verify(statement).close();
    assertThat(instanceId.value()).isEqualTo("instance-a");
  }
}
