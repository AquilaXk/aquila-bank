package com.aquilabank.global.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.auth.exception.DuplicateExternalIdentityMappingException;
import com.aquilabank.domain.auth.exception.ExternalIdentityMappingNotFoundException;
import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;
import com.aquilabank.domain.auth.model.ExternalIdentityChangeType;
import com.aquilabank.domain.auth.model.ExternalIdentityLinkCommand;
import com.aquilabank.domain.auth.model.ExternalIdentityMapping;
import com.aquilabank.domain.auth.model.ExternalIdentityUnlinkCommand;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.util.Optional;
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
class JdbcAuthExternalIdentityAdminRepositoryIntegrationTest extends PostgresContainerTestSupport {

  private static final AuthStatusChangeReason REASON =
      new AuthStatusChangeReason(AuthStatusChangeReasonCode.OPS_MANUAL, "ops-approved");

  @Autowired private JdbcAuthWriteRepository writeRepository;

  @Autowired private JdbcExternalIdentityAuditRepository queryRepository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void linksExternalIdentityAndWritesAuditWithSubjectHash() {
    long userId = insertUser("alice");

    ExternalIdentityMapping mapping =
        writeRepository.link(
            new ExternalIdentityLinkCommand(
                userId, "google", "oidc-subject-1", REASON, "ops-admin", "request-link-001"));

    assertThat(mapping.userId()).isEqualTo(userId);
    assertThat(mapping.providerId()).isEqualTo("google");
    assertThat(mapping.subject()).isEqualTo("oidc-subject-1");
    assertThat(mapping.createdAt()).isNotNull();
    assertThat(countMappings()).isEqualTo(1);

    Optional<ExternalIdentityAuditSummary> audit =
        queryRepository.findByRequestId("request-link-001");
    assertThat(audit).isPresent();
    assertThat(audit.get().changeType()).isEqualTo(ExternalIdentityChangeType.LINK);
    assertThat(audit.get().providerId()).isEqualTo("google");
    assertThat(audit.get().subjectHash()).hasSize(64);
    assertThat(audit.get().subjectHash()).doesNotContain("oidc-subject-1");
    assertThat(audit.get().outcome()).isEqualTo(AuthStatusChangeOutcome.SUCCESS);
    assertThat(audit.get().reasonCode()).isEqualTo(AuthStatusChangeReasonCode.OPS_MANUAL);
  }

  @Test
  void rejectsDuplicateProviderSubjectOrProviderUserMapping() {
    long userId = insertUser("alice");
    long otherUserId = insertUser("bob");
    writeRepository.link(
        new ExternalIdentityLinkCommand(
            userId, "google", "oidc-subject-1", REASON, "ops-admin", "request-link-001"));

    assertThatThrownBy(
            () ->
                writeRepository.link(
                    new ExternalIdentityLinkCommand(
                        otherUserId,
                        "google",
                        "oidc-subject-1",
                        REASON,
                        "ops-admin",
                        "request-link-duplicate-subject")))
        .isInstanceOf(DuplicateExternalIdentityMappingException.class);

    assertThatThrownBy(
            () ->
                writeRepository.link(
                    new ExternalIdentityLinkCommand(
                        userId,
                        "google",
                        "oidc-subject-2",
                        REASON,
                        "ops-admin",
                        "request-link-duplicate-user")))
        .isInstanceOf(DuplicateExternalIdentityMappingException.class);
  }

  @Test
  void unlinksExternalIdentityAndWritesAudit() {
    long userId = insertUser("alice");
    writeRepository.link(
        new ExternalIdentityLinkCommand(
            userId, "google", "oidc-subject-1", REASON, "ops-admin", "request-link-001"));

    ExternalIdentityMapping mapping =
        writeRepository.unlink(
            new ExternalIdentityUnlinkCommand(
                userId, "google", "oidc-subject-1", REASON, "ops-admin", "request-unlink-001"));

    assertThat(mapping.userId()).isEqualTo(userId);
    assertThat(mapping.subject()).isEqualTo("oidc-subject-1");
    assertThat(countMappings()).isZero();

    Optional<ExternalIdentityAuditSummary> audit =
        queryRepository.findByRequestId("request-unlink-001");
    assertThat(audit).isPresent();
    assertThat(audit.get().changeType()).isEqualTo(ExternalIdentityChangeType.UNLINK);
    assertThat(audit.get().userId()).isEqualTo(userId);
  }

  @Test
  void rejectsUnlinkWhenMappingIsMissing() {
    long userId = insertUser("alice");

    assertThatThrownBy(
            () ->
                writeRepository.unlink(
                    new ExternalIdentityUnlinkCommand(
                        userId,
                        "google",
                        "missing-subject",
                        REASON,
                        "ops-admin",
                        "request-unlink-missing")))
        .isInstanceOf(ExternalIdentityMappingNotFoundException.class);
  }

  private long insertUser(String loginId) {
    long[] userId = new long[1];
    commit(
        transactionManager,
        () -> {
          Long id =
              jdbcTemplate.queryForObject(
                  """
                  INSERT INTO bank_user (
                      login_id,
                      password_hash,
                      display_name,
                      user_status,
                      created_at,
                      updated_at
                  )
                  VALUES (
                      :loginId,
                      '$2a$10$abcdefghijklmnopqrstuv',
                      :loginId,
                      'ACTIVE',
                      CURRENT_TIMESTAMP,
                      CURRENT_TIMESTAMP
                  )
                  RETURNING id
                  """,
                  new MapSqlParameterSource().addValue("loginId", loginId),
                  Long.class);
          if (id == null) {
            throw new IllegalStateException("bank_user insert did not return id");
          }
          userId[0] = id;
        });
    return userId[0];
  }

  private int countMappings() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_external_identity",
            new MapSqlParameterSource(),
            Integer.class);
    return count == null ? 0 : count;
  }
}
