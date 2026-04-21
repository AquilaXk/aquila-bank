package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveAuditEntry;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveAuditItem;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveOutcome;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcNotificationDlqRedriveAuditRepositoryIntegrationTest
    extends PostgresContainerTestSupport {

  private static final String SOURCE_TOPIC = "bank.transfer.booked.dlq.v1";
  private static final String TARGET_TOPIC = "bank.transfer.booked.v1";

  @Autowired private JdbcNotificationDlqRedriveAuditRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void appendsAuditRowsForRepeatedSourceOffsetAttempts() {
    Instant base = Instant.parse("2026-04-22T03:00:00Z");
    repository.append(
        new NotificationDlqRedriveAuditEntry(
            "outbox-ops",
            "redrive-request-1",
            SOURCE_TOPIC,
            0,
            42L,
            "transfer-booked:redrive-audit",
            TARGET_TOPIC,
            0,
            12L,
            NotificationDlqRedriveOutcome.SUCCESS,
            null,
            base));
    repository.append(
        new NotificationDlqRedriveAuditEntry(
            "outbox-ops",
            "redrive-request-2",
            SOURCE_TOPIC,
            0,
            42L,
            null,
            null,
            null,
            null,
            NotificationDlqRedriveOutcome.NOT_FOUND,
            "notification DLQ record is not found: partition=0 offset=42",
            base.plusSeconds(1)));

    List<NotificationDlqRedriveAuditItem> items = repository.findBySource(SOURCE_TOPIC, 0, 42L);

    assertThat(items).hasSize(2);
    assertThat(items)
        .extracting(NotificationDlqRedriveAuditItem::outcome)
        .containsExactly(
            NotificationDlqRedriveOutcome.SUCCESS, NotificationDlqRedriveOutcome.NOT_FOUND);
    assertThat(items.getFirst().actor()).isEqualTo("outbox-ops");
    assertThat(items.getFirst().requestId()).isEqualTo("redrive-request-1");
    assertThat(items.getFirst().eventKey()).isEqualTo("transfer-booked:redrive-audit");
    assertThat(items.getFirst().targetTopic()).isEqualTo(TARGET_TOPIC);
    assertThat(items.getFirst().targetPartition()).isEqualTo(0);
    assertThat(items.getFirst().targetOffset()).isEqualTo(12L);
    assertThat(items.getLast().targetTopic()).isNull();
    assertThat(items.getLast().targetPartition()).isNull();
    assertThat(items.getLast().targetOffset()).isNull();
    assertThat(items.getLast().errorMessage()).contains("not found");
  }
}
