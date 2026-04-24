package com.aquilabank.global.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxEntry;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxItem;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryStatus;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcPasswordRecoveryDeliveryOutboxRepositoryIntegrationTest
    extends PostgresContainerTestSupport {

  @Autowired private JdbcPasswordRecoveryDeliveryOutboxRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void claimsDueRowsInAvailableOrderAndMovesThemToSending() {
    long userId = insertUser("alice@example.com");
    Instant base = Instant.parse("2026-04-23T14:30:00Z");
    repository.append(
        new PasswordRecoveryDeliveryOutboxEntry(
            "request-1",
            userId,
            "alice@example.com",
            VerifiedContactChannel.EMAIL,
            "alice.recovery@example.com",
            base.minusSeconds(10),
            base));
    repository.append(
        new PasswordRecoveryDeliveryOutboxEntry(
            "request-2",
            userId,
            "alice@example.com",
            VerifiedContactChannel.SMS,
            "+821012345678",
            base.minusSeconds(5),
            base.plusSeconds(1)));

    List<PasswordRecoveryDeliveryOutboxItem> claimed = repository.claimPending(1, base);

    assertThat(claimed).hasSize(1);
    assertThat(claimed.getFirst().requestId()).isEqualTo("request-1");
    assertThat(claimed.getFirst().deliveryChannel()).isEqualTo(VerifiedContactChannel.EMAIL);
    assertThat(claimed.getFirst().providerDestination()).isEqualTo("alice.recovery@example.com");
    assertThat(claimed.getFirst().deliveryStatus())
        .isEqualTo(PasswordRecoveryDeliveryStatus.SENDING);
    assertThat(repository.claimPending(10, base))
        .extracting(PasswordRecoveryDeliveryOutboxItem::requestId)
        .containsExactly("request-2");
  }

  @Test
  void marksRowsFailedThenSent() {
    long userId = insertUser("bob@example.com");
    Instant base = Instant.parse("2026-04-23T14:35:00Z");
    repository.append(
        new PasswordRecoveryDeliveryOutboxEntry(
            "request-3",
            userId,
            "bob@example.com",
            VerifiedContactChannel.EMAIL,
            "bob.recovery@example.com",
            base.minusSeconds(5),
            base));
    PasswordRecoveryDeliveryOutboxItem claimed = repository.claimPending(1, base).getFirst();

    repository.markFailed(
        claimed.id(), base.plusSeconds(15), base.plusSeconds(1), "provider timeout");

    DeliveryRow failedRow = findRow(claimed.id());
    assertThat(failedRow.deliveryStatus()).isEqualTo(PasswordRecoveryDeliveryStatus.FAILED);
    assertThat(failedRow.retryCount()).isEqualTo(1);
    assertThat(failedRow.availableAt()).isEqualTo(base.plusSeconds(15));
    assertThat(repository.claimPending(1, base.plusSeconds(10))).isEmpty();

    PasswordRecoveryDeliveryOutboxItem retryItem =
        repository.claimPending(1, base.plusSeconds(15)).getFirst();
    repository.markSent(retryItem.id(), base.plusSeconds(16));

    DeliveryRow sentRow = findRow(retryItem.id());
    assertThat(sentRow.deliveryStatus()).isEqualTo(PasswordRecoveryDeliveryStatus.SENT);
    assertThat(sentRow.sentAt()).isEqualTo(base.plusSeconds(16));
    assertThat(sentRow.lastError()).isNull();
  }

  @Test
  void quarantinesRowsAfterClaim() {
    long userId = insertUser("carol@example.com");
    Instant base = Instant.parse("2026-04-23T14:40:00Z");
    repository.append(
        new PasswordRecoveryDeliveryOutboxEntry(
            "request-4",
            userId,
            "carol@example.com",
            VerifiedContactChannel.EMAIL,
            "carol.recovery@example.com",
            base.minusSeconds(5),
            base));
    PasswordRecoveryDeliveryOutboxItem claimed = repository.claimPending(1, base).getFirst();

    repository.markQuarantined(claimed.id(), base.plusSeconds(1), "provider rejected");

    DeliveryRow row = findRow(claimed.id());
    assertThat(row.deliveryStatus()).isEqualTo(PasswordRecoveryDeliveryStatus.QUARANTINED);
    assertThat(row.retryCount()).isEqualTo(1);
    assertThat(repository.claimPending(10, base.plusSeconds(3600))).isEmpty();
  }

  @Test
  void rejectsDuplicateRequestIdByUniqueConstraint() {
    long userId = insertUser("duplicate@example.com");
    Instant base = Instant.parse("2026-04-23T14:45:00Z");
    repository.append(
        new PasswordRecoveryDeliveryOutboxEntry(
            "request-dup",
            userId,
            "duplicate@example.com",
            VerifiedContactChannel.EMAIL,
            "duplicate.recovery@example.com",
            base,
            base));

    assertThatThrownBy(
            () ->
                repository.append(
                    new PasswordRecoveryDeliveryOutboxEntry(
                        "request-dup",
                        userId,
                        "duplicate@example.com",
                        VerifiedContactChannel.EMAIL,
                        "duplicate.recovery@example.com",
                        base.plusSeconds(1),
                        base.plusSeconds(1))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private long insertUser(String loginId) {
    return commitAndReturn(
        () ->
            jdbcTemplate.queryForObject(
                """
                INSERT INTO bank_user (
                    login_id,
                    password_hash,
                    display_name,
                    user_status,
                    created_at,
                    updated_at
                ) VALUES (
                    :loginId,
                    :passwordHash,
                    :displayName,
                    'ACTIVE',
                    :now,
                    :now
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                    .addValue("loginId", loginId)
                    .addValue("passwordHash", "encoded-password")
                    .addValue("displayName", "Display " + loginId)
                    .addValue("now", Timestamp.from(Instant.parse("2026-04-23T14:00:00Z"))),
                Long.class));
  }

  private <T> T commitAndReturn(java.util.concurrent.Callable<T> callable) {
    final Object[] box = new Object[1];
    commit(
        transactionManager,
        () -> {
          try {
            box[0] = callable.call();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });
    @SuppressWarnings("unchecked")
    T value = (T) box[0];
    return value;
  }

  private DeliveryRow findRow(long id) {
    return jdbcTemplate.queryForObject(
        """
        SELECT id,
               delivery_status,
               available_at,
               sent_at,
               retry_count,
               last_error,
               updated_at
        FROM auth_password_recovery_delivery_outbox
        WHERE id = :id
        """,
        new MapSqlParameterSource().addValue("id", id),
        (rs, rowNum) ->
            new DeliveryRow(
                rs.getLong("id"),
                PasswordRecoveryDeliveryStatus.valueOf(rs.getString("delivery_status")),
                rs.getTimestamp("available_at").toInstant(),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                rs.getInt("retry_count"),
                rs.getString("last_error"),
                rs.getTimestamp("updated_at").toInstant()));
  }

  private record DeliveryRow(
      long id,
      PasswordRecoveryDeliveryStatus deliveryStatus,
      Instant availableAt,
      Instant sentAt,
      int retryCount,
      String lastError,
      Instant updatedAt) {}
}
