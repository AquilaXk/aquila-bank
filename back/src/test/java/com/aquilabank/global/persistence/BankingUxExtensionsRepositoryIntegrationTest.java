package com.aquilabank.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import com.aquilabank.domain.ledger.model.CreateScheduledTransferCommand;
import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import com.aquilabank.domain.ledger.model.ScheduledTransferFrequency;
import com.aquilabank.domain.ledger.model.ScheduledTransferRule;
import com.aquilabank.domain.ledger.model.ScheduledTransferStatus;
import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import com.aquilabank.domain.transaction.model.TransactionCategory;
import com.aquilabank.global.persistence.account.JdbcAccountPersonalizationRepository;
import com.aquilabank.global.persistence.ledger.JdbcSavedRecipientRepository;
import com.aquilabank.global.persistence.ledger.JdbcScheduledTransferRepository;
import com.aquilabank.global.persistence.transaction.JdbcTransactionAnnotationRepository;
import com.aquilabank.support.PostgresContainerTestSupport;
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
class BankingUxExtensionsRepositoryIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  @Autowired private JdbcSavedRecipientRepository savedRecipientRepository;
  @Autowired private JdbcScheduledTransferRepository scheduledTransferRepository;
  @Autowired private JdbcAccountPersonalizationRepository accountPersonalizationRepository;
  @Autowired private JdbcTransactionAnnotationRepository transactionAnnotationRepository;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void persistsAndQueriesRecipientsSchedulesPreferencesPocketsAndAnnotations() {
    long[] ids = new long[3];
    commit(
        transactionManager,
        () -> {
          ids[0] = insertUser("ux-ext-user");
          ids[1] = insertAccount("110-101-000001", "주거래통장");
          ids[2] = insertAccount("110-101-000002", "적금통장");
        });
    long userId = ids[0];
    long account1 = ids[1];
    long account2 = ids[2];

    // 1. Saved Recipients
    assertThat(savedRecipientRepository.existsByUserAndTarget(userId, "AQUILA", "110-101-000002"))
        .isFalse();
    SavedRecipient insertedRecipient =
        savedRecipientRepository.upsert(
            new SaveSavedRecipientCommand(
                userId, "적금 계좌", "AQUILA", "110-101-000002", "KRW", false),
            NOW);
    assertThat(savedRecipientRepository.existsByUserAndTarget(userId, "AQUILA", "110-101-000002"))
        .isTrue();
    assertThat(savedRecipientRepository.countByUserId(userId)).isEqualTo(1);
    SavedRecipient updatedRecipient =
        savedRecipientRepository.upsert(
            new SaveSavedRecipientCommand(
                userId, "비상금 적금", "AQUILA", "110-101-000002", "KRW", true),
            NOW.plusSeconds(10));
    assertThat(updatedRecipient.recipientId()).isEqualTo(insertedRecipient.recipientId());
    assertThat(updatedRecipient.favorite()).isTrue();
    assertThat(savedRecipientRepository.findByUserId(userId, 100)).hasSize(1);
    assertThat(savedRecipientRepository.deleteByUserAndId(userId, updatedRecipient.recipientId()))
        .isTrue();
    assertThat(savedRecipientRepository.deleteByUserAndId(userId, updatedRecipient.recipientId()))
        .isFalse();

    // 2. Account Preferences (atomic primary switch) & Savings Pockets
    UserAccountPreference pref1 =
        accountPersonalizationRepository.upsertPreference(userId, account1, "월급통장", true, NOW);
    assertThat(pref1.primaryAccount()).isTrue();
    UserAccountPreference pref2 =
        accountPersonalizationRepository.upsertPreference(
            userId, account2, "생활비통장", true, NOW.plusSeconds(5));
    assertThat(pref2.primaryAccount()).isTrue();
    accountPersonalizationRepository.upsertPreference(
        userId, account2, "생활비", false, NOW.plusSeconds(6));
    List<UserAccountPreference> prefs =
        accountPersonalizationRepository.findPreferencesByUserId(userId);
    assertThat(prefs).hasSize(2);
    assertThat(prefs).noneMatch(UserAccountPreference::primaryAccount);

    SavingsPocket pocket =
        accountPersonalizationRepository.insertPocket(
            new CreateSavingsPocketCommand(userId, account1, "유럽여행", 3_000_000L, 500_000L, "KRW"),
            NOW);
    assertThat(accountPersonalizationRepository.countPocketsByUserAndAccount(userId, account1))
        .isEqualTo(1);
    assertThat(accountPersonalizationRepository.findPocketsByUserAndAccount(userId, account1, 20))
        .containsExactly(pocket);
    SavingsPocket updatedPocket =
        accountPersonalizationRepository
            .updatePocket(
                new UpdateSavingsPocketCommand(
                    userId, account1, pocket.pocketId(), "유럽여행+", 3_500_000L, 1_000_000L),
                NOW.plusSeconds(10))
            .orElseThrow();
    assertThat(updatedPocket.savedAmountMinor()).isEqualTo(1_000_000L);
    assertThat(accountPersonalizationRepository.deletePocket(userId, account1, pocket.pocketId()))
        .isTrue();
    assertThat(accountPersonalizationRepository.deletePocket(userId, account1, pocket.pocketId()))
        .isFalse();

    // 3. Scheduled Transfers
    ScheduledTransferRule rule =
        scheduledTransferRepository.insert(
            new CreateScheduledTransferCommand(
                userId,
                account1,
                "110-101-000002",
                200_000L,
                "KRW",
                "매월 적금",
                ScheduledTransferFrequency.MONTHLY,
                25,
                NOW.plusSeconds(86400)),
            NOW);
    assertThat(scheduledTransferRepository.countActiveAndPausedByUserId(userId)).isEqualTo(1);
    assertThat(scheduledTransferRepository.findByUserAndId(userId, rule.scheduleId()))
        .contains(rule);
    assertThat(scheduledTransferRepository.findActiveAndPausedByUserId(userId, 50))
        .containsExactly(rule);
    ScheduledTransferRule paused =
        scheduledTransferRepository
            .updateStatus(
                userId, rule.scheduleId(), ScheduledTransferStatus.PAUSED, NOW.plusSeconds(2))
            .orElseThrow();
    assertThat(paused.ruleStatus()).isEqualTo(ScheduledTransferStatus.PAUSED);
    scheduledTransferRepository
        .updateStatus(
            userId, rule.scheduleId(), ScheduledTransferStatus.CANCELLED, NOW.plusSeconds(3))
        .orElseThrow();
    assertThat(scheduledTransferRepository.countActiveAndPausedByUserId(userId)).isZero();

    // 4. Transaction Annotations (batch IN)
    TransactionAnnotation annotation =
        transactionAnnotationRepository.upsert(
            new SaveTransactionAnnotationCommand(
                userId, account1, "TRX-100", TransactionCategory.SAVINGS, "비상금 적립"),
            NOW);
    assertThat(
            transactionAnnotationRepository.findByReferences(
                userId, account1, List.of("TRX-100", "TRX-999")))
        .containsExactly(annotation);
  }

  private long insertUser(String loginId) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_user (login_id, password_hash, display_name, user_status)
            VALUES (:loginId, 'hash', :loginId, 'ACTIVE')
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            Long.class);
    return id;
  }

  private long insertAccount(String accountNumber, String displayName) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (account_number, display_name, account_status, currency_code)
            VALUES (:accountNumber, :displayName, 'ACTIVE', 'KRW')
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountNumber", accountNumber)
                .addValue("displayName", displayName),
            Long.class);
    return id;
  }
}
