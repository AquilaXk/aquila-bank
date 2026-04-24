package com.aquilabank.global.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.model.VerifiedContact;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.model.VerifiedContactUpsertCommand;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcVerifiedContactRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcVerifiedContactRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void upsertsListsAndDeletesUserVerifiedContactsByChannel() {
    long userId = insertUser("alice");
    Instant verifiedAt = Instant.parse("2026-04-24T09:00:00Z");

    VerifiedContact created =
        repository.upsert(
            new VerifiedContactUpsertCommand(
                userId, VerifiedContactChannel.EMAIL, "alice@example.com", verifiedAt));

    assertThat(created.userId()).isEqualTo(userId);
    assertThat(created.channel()).isEqualTo(VerifiedContactChannel.EMAIL);
    assertThat(created.providerDestination()).isEqualTo("alice@example.com");
    assertThat(created.verifiedAt()).isEqualTo(verifiedAt);
    assertThat(repository.findByUserIdAndChannel(userId, VerifiedContactChannel.EMAIL))
        .hasValueSatisfying(
            item -> assertThat(item.providerDestination()).isEqualTo("alice@example.com"));

    Instant updatedAt = verifiedAt.plusSeconds(60);
    repository.upsert(
        new VerifiedContactUpsertCommand(
            userId, VerifiedContactChannel.EMAIL, "alice.ops@example.com", updatedAt));
    repository.upsert(
        new VerifiedContactUpsertCommand(
            userId, VerifiedContactChannel.SMS, "+821012345678", updatedAt));

    List<VerifiedContact> contacts = repository.findByUserId(userId);

    assertThat(contacts)
        .extracting(VerifiedContact::channel)
        .containsExactly(VerifiedContactChannel.EMAIL, VerifiedContactChannel.SMS);
    assertThat(contacts)
        .extracting(VerifiedContact::providerDestination)
        .containsExactly("alice.ops@example.com", "+821012345678");

    assertThat(repository.delete(userId, VerifiedContactChannel.EMAIL)).isTrue();
    assertThat(repository.findByUserIdAndChannel(userId, VerifiedContactChannel.EMAIL)).isEmpty();
    assertThat(repository.delete(userId, VerifiedContactChannel.EMAIL)).isFalse();
  }

  @Test
  void findsEmailBeforeSmsForPasswordRecoveryDestination() {
    long emailAndSmsUserId = insertUser("recoverable");
    long smsOnlyUserId = insertUser("sms-only");
    Instant verifiedAt = Instant.parse("2026-04-24T09:10:00Z");
    repository.upsert(
        new VerifiedContactUpsertCommand(
            emailAndSmsUserId, VerifiedContactChannel.SMS, "+821011112222", verifiedAt));
    repository.upsert(
        new VerifiedContactUpsertCommand(
            emailAndSmsUserId, VerifiedContactChannel.EMAIL, "recover@example.com", verifiedAt));
    repository.upsert(
        new VerifiedContactUpsertCommand(
            smsOnlyUserId, VerifiedContactChannel.SMS, "+821033334444", verifiedAt));

    assertThat(repository.findPreferredForPasswordRecovery(emailAndSmsUserId))
        .hasValueSatisfying(
            item -> {
              assertThat(item.channel()).isEqualTo(VerifiedContactChannel.EMAIL);
              assertThat(item.providerDestination()).isEqualTo("recover@example.com");
            });
    assertThat(repository.findPreferredForPasswordRecovery(smsOnlyUserId))
        .hasValueSatisfying(
            item -> {
              assertThat(item.channel()).isEqualTo(VerifiedContactChannel.SMS);
              assertThat(item.providerDestination()).isEqualTo("+821033334444");
            });
  }

  private long insertUser(String loginId) {
    final long[] id = new long[1];
    commit(
        transactionManager,
        () ->
            id[0] =
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
                        .addValue("now", Timestamp.from(Instant.parse("2026-04-24T08:00:00Z"))),
                    Long.class));
    return id[0];
  }
}
