package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.exception.AuthUserNotFoundException;
import com.aquilabank.domain.auth.exception.DuplicateLoginIdException;
import com.aquilabank.domain.auth.exception.UserAccountMembershipNotFoundException;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditEntry;
import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeType;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.LoginFailureUpdateCommand;
import com.aquilabank.domain.auth.model.LoginSuccessUpdateCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenIssueCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenUseCommand;
import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRotateCommand;
import com.aquilabank.domain.auth.model.TotpCredentialActivateCommand;
import com.aquilabank.domain.auth.model.TotpCredentialTouchCommand;
import com.aquilabank.domain.auth.model.TotpCredentialUpsertCommand;
import com.aquilabank.domain.auth.model.TotpLoginChallengeUpdateCommand;
import com.aquilabank.domain.auth.model.TotpLoginChallengeUpsertCommand;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipStatusUpdateCommand;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.model.UserBootstrapWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.model.UserStatusUpdateCommand;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionCleanupPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeWritePort;
import com.aquilabank.domain.auth.port.UserAccountMembershipStatusUpdatePort;
import com.aquilabank.domain.auth.port.UserAccountMembershipUpsertPort;
import com.aquilabank.domain.auth.port.UserBootstrapPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import com.aquilabank.domain.auth.port.UserStatusUpdatePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** auth bootstrap 쓰기 경로를 JDBC로 고정해 테스트와 운영 도구가 같은 저장 경로를 탑니다. */
@Repository
public class JdbcAuthWriteRepository
    implements UserBootstrapPort,
        LoginAttemptUpdatePort,
        PasswordRecoveryTokenWritePort,
        UserCredentialUpdatePort,
        RefreshTokenSessionCleanupPort,
        RefreshTokenSessionWritePort,
        TotpCredentialWritePort,
        TotpLoginChallengeWritePort,
        UserAccountMembershipUpsertPort,
        UserStatusUpdatePort,
        UserAccountMembershipStatusUpdatePort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAuthWriteRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public UserBootstrapResult bootstrap(UserBootstrapWriteCommand command) {
    Instant now = Instant.now();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("loginId", command.loginId())
            .addValue("passwordHash", command.passwordHash())
            .addValue("displayName", command.displayName())
            .addValue("userStatus", command.status().name())
            .addValue("now", Timestamp.from(now));

    try {
      Long userId =
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
                  :passwordHash,
                  :displayName,
                  :userStatus,
                  :now,
                  :now
              )
              RETURNING id
              """,
              params,
              Long.class);

      if (userId == null) {
        throw new IllegalStateException("bank_user insert did not return an id");
      }

      return new UserBootstrapResult(
          userId, command.loginId(), command.displayName(), command.status(), now);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateLoginIdException("loginId is already used");
    }
  }

  @Override
  @Transactional
  public void recordLoginFailure(LoginFailureUpdateCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE bank_user
            SET failed_login_count = :failedLoginCount,
                last_login_failed_at = :failedAt,
                login_locked_until = :loginLockedUntil,
                updated_at = :failedAt
            WHERE id = :userId
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("failedLoginCount", command.failedLoginCount())
                .addValue("failedAt", Timestamp.from(command.failedAt()))
                .addValue("loginLockedUntil", toTimestamp(command.loginLockedUntil())));
    assertUserUpdated(updated);
  }

  @Override
  @Transactional
  public void recordLoginSuccess(LoginSuccessUpdateCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE bank_user
            SET failed_login_count = 0,
                last_login_failed_at = NULL,
                login_locked_until = NULL,
                last_login_succeeded_at = :succeededAt,
                updated_at = :succeededAt
            WHERE id = :userId
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("succeededAt", Timestamp.from(command.succeededAt())));
    assertUserUpdated(updated);
  }

  @Override
  @Transactional
  public void resetPassword(PasswordResetWriteCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE bank_user
            SET password_hash = :passwordHash,
                failed_login_count = 0,
                last_login_failed_at = NULL,
                login_locked_until = NULL,
                updated_at = :changedAt
            WHERE id = :userId
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("passwordHash", command.passwordHash())
                .addValue("changedAt", Timestamp.from(command.changedAt())));
    assertUserUpdated(updated);
  }

  @Override
  @Transactional
  public void supersedePendingTokens(long userId, Instant updatedAt) {
    jdbcTemplate.update(
        """
        UPDATE auth_password_recovery_token
        SET token_status = 'SUPERSEDED',
            updated_at = :updatedAt
        WHERE user_id = :userId
          AND token_status = 'PENDING'
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  @Override
  @Transactional
  public void issue(PasswordRecoveryTokenIssueCommand command) {
    Long tokenId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO auth_password_recovery_token (
                request_id,
                user_id,
                login_id,
                token_hash,
                token_ciphertext,
                token_nonce,
                token_status,
                expires_at,
                used_at,
                created_at,
                updated_at
            )
            VALUES (
                :requestId,
                :userId,
                :loginId,
                :tokenHash,
                :tokenCiphertext,
                :tokenNonce,
                'PENDING',
                :expiresAt,
                NULL,
                :createdAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("requestId", command.requestId())
                .addValue("userId", command.userId())
                .addValue("loginId", command.loginId())
                .addValue("tokenHash", command.tokenHash())
                .addValue("tokenCiphertext", command.tokenCiphertext())
                .addValue("tokenNonce", command.tokenNonce())
                .addValue("expiresAt", Timestamp.from(command.expiresAt()))
                .addValue("createdAt", Timestamp.from(command.createdAt())),
            Long.class);
    if (tokenId == null) {
      throw new IllegalStateException("password recovery token insert did not return an id");
    }
  }

  @Override
  @Transactional
  public void markUsed(PasswordRecoveryTokenUseCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE auth_password_recovery_token
            SET token_status = 'USED',
                used_at = :usedAt,
                updated_at = :usedAt
            WHERE id = :tokenId
              AND token_status = 'PENDING'
            """,
            new MapSqlParameterSource()
                .addValue("tokenId", command.tokenId())
                .addValue("usedAt", Timestamp.from(command.usedAt())));
    if (updated != 1) {
      throw new IllegalStateException("password recovery token is not pending");
    }
  }

  @Override
  @Transactional
  public void markExpired(long tokenId, Instant expiredAt) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE auth_password_recovery_token
            SET token_status = 'EXPIRED',
                updated_at = :expiredAt
            WHERE id = :tokenId
              AND token_status = 'PENDING'
            """,
            new MapSqlParameterSource()
                .addValue("tokenId", tokenId)
                .addValue("expiredAt", Timestamp.from(expiredAt)));
    if (updated != 1) {
      throw new IllegalStateException("password recovery token is not pending");
    }
  }

  @Override
  @Transactional
  public long create(RefreshTokenSessionCreateCommand command) {
    Long sessionId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO auth_refresh_token_session (
                user_id,
                token_hash,
                device_name,
                ip_address,
                session_status,
                expires_at,
                created_at,
                updated_at
            )
            VALUES (
                :userId,
                :tokenHash,
                :deviceName,
                :ipAddress,
                'ACTIVE',
                :expiresAt,
                :createdAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("tokenHash", command.tokenHash())
                .addValue("deviceName", command.sessionClientMetadata().deviceName())
                .addValue("ipAddress", command.sessionClientMetadata().ipAddress())
                .addValue("expiresAt", Timestamp.from(command.expiresAt()))
                .addValue("createdAt", Timestamp.from(command.createdAt())),
            Long.class);
    if (sessionId == null) {
      throw new IllegalStateException("refresh token session insert did not return an id");
    }
    return sessionId;
  }

  @Override
  @Transactional
  public void rotate(RefreshTokenSessionRotateCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE auth_refresh_token_session
            SET session_status = 'ROTATED',
                last_used_at = :rotatedAt,
                rotated_at = :rotatedAt,
                replaced_by_session_id = :replacedBySessionId,
                updated_at = :rotatedAt
            WHERE id = :sessionId
              AND session_status = 'ACTIVE'
            """,
            new MapSqlParameterSource()
                .addValue("sessionId", command.sessionId())
                .addValue("replacedBySessionId", command.replacedBySessionId())
                .addValue("rotatedAt", Timestamp.from(command.rotatedAt())));
    if (updated != 1) {
      throw new IllegalStateException("refresh token session is not active");
    }
  }

  @Override
  @Transactional
  public void revoke(RefreshTokenSessionRevokeCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE auth_refresh_token_session
            SET session_status = 'REVOKED',
                last_used_at = :revokedAt,
                updated_at = :revokedAt
            WHERE id = :sessionId
              AND session_status = 'ACTIVE'
            """,
            new MapSqlParameterSource()
                .addValue("sessionId", command.sessionId())
                .addValue("revokedAt", Timestamp.from(command.revokedAt())));
    if (updated != 1) {
      throw new IllegalStateException("refresh token session is not active");
    }
  }

  @Override
  @Transactional
  public void upsertPending(TotpCredentialUpsertCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            INSERT INTO auth_totp_credential (
                user_id,
                credential_status,
                secret_nonce,
                secret_ciphertext,
                pending_expires_at,
                verified_at,
                last_used_at,
                created_at,
                updated_at
            )
            VALUES (
                :userId,
                'PENDING',
                :secretNonce,
                :secretCiphertext,
                :pendingExpiresAt,
                NULL,
                NULL,
                :createdAt,
                :createdAt
            )
            ON CONFLICT (user_id)
            DO UPDATE
            SET credential_status = 'PENDING',
                secret_nonce = EXCLUDED.secret_nonce,
                secret_ciphertext = EXCLUDED.secret_ciphertext,
                pending_expires_at = EXCLUDED.pending_expires_at,
                verified_at = NULL,
                updated_at = EXCLUDED.updated_at
            WHERE auth_totp_credential.credential_status = 'PENDING'
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("secretNonce", command.secretNonce())
                .addValue("secretCiphertext", command.secretCiphertext())
                .addValue("pendingExpiresAt", Timestamp.from(command.pendingExpiresAt()))
                .addValue("createdAt", Timestamp.from(command.createdAt())));
    if (updated != 1) {
      throw new IllegalStateException("totp credential is already active");
    }
  }

  @Override
  @Transactional
  public void activate(TotpCredentialActivateCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE auth_totp_credential
            SET credential_status = 'ACTIVE',
                pending_expires_at = NULL,
                verified_at = :verifiedAt,
                updated_at = :verifiedAt
            WHERE user_id = :userId
              AND credential_status = 'PENDING'
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("verifiedAt", Timestamp.from(command.verifiedAt())));
    if (updated != 1) {
      throw new IllegalStateException("totp credential is not pending");
    }
  }

  @Override
  @Transactional
  public void touchLastUsed(TotpCredentialTouchCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE auth_totp_credential
            SET last_used_at = :lastUsedAt,
                updated_at = :lastUsedAt
            WHERE user_id = :userId
              AND credential_status = 'ACTIVE'
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("lastUsedAt", Timestamp.from(command.lastUsedAt())));
    if (updated != 1) {
      throw new IllegalStateException("totp credential is not active");
    }
  }

  @Override
  @Transactional
  public void deleteByUserId(long userId) {
    int updated =
        jdbcTemplate.update(
            """
            DELETE FROM auth_totp_credential
            WHERE user_id = :userId
              AND credential_status = 'ACTIVE'
            """,
            new MapSqlParameterSource().addValue("userId", userId));
    if (updated != 1) {
      throw new IllegalStateException("totp credential is not active");
    }
  }

  @Override
  @Transactional
  public void upsert(TotpLoginChallengeUpsertCommand command) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_totp_login_challenge (
            user_id,
            challenge_id,
            challenge_status,
            attempt_count,
            device_name,
            ip_address,
            expires_at,
            verified_at,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :challengeId,
            'PENDING',
            0,
            :deviceName,
            :ipAddress,
            :expiresAt,
            NULL,
            :createdAt,
            :createdAt
        )
        ON CONFLICT (user_id)
        DO UPDATE
        SET challenge_id = EXCLUDED.challenge_id,
            challenge_status = EXCLUDED.challenge_status,
            attempt_count = EXCLUDED.attempt_count,
            device_name = EXCLUDED.device_name,
            ip_address = EXCLUDED.ip_address,
            expires_at = EXCLUDED.expires_at,
            verified_at = NULL,
            created_at = EXCLUDED.created_at,
            updated_at = EXCLUDED.updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", command.userId())
            .addValue("challengeId", command.challengeId())
            .addValue("deviceName", command.deviceName())
            .addValue("ipAddress", command.ipAddress())
            .addValue("expiresAt", Timestamp.from(command.expiresAt()))
            .addValue("createdAt", Timestamp.from(command.createdAt())));
  }

  @Override
  @Transactional
  public void update(TotpLoginChallengeUpdateCommand command) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE auth_totp_login_challenge
            SET challenge_status = :challengeStatus,
                attempt_count = :attemptCount,
                verified_at = CASE
                    WHEN :challengeStatus = 'VERIFIED' THEN :updatedAt
                    ELSE verified_at
                END,
                updated_at = :updatedAt
            WHERE user_id = :userId
              AND challenge_id = :challengeId
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("challengeId", command.challengeId())
                .addValue("challengeStatus", command.challengeStatus().name())
                .addValue("attemptCount", command.attemptCount())
                .addValue("updatedAt", Timestamp.from(command.updatedAt())));
    if (updated != 1) {
      throw new IllegalStateException("totp login challenge is not found");
    }
  }

  @Override
  @Transactional
  public void revokeActiveSessionsByUserId(long userId, Instant revokedAt) {
    revokeActiveRefreshTokenSessions(userId, revokedAt);
  }

  @Override
  @Transactional
  public int deleteExpiredSessions(Instant cutoff, int batchSize) {
    // ACTIVE 만료와 비활성 세션 retention 기준이 달라 상태별 index cursor를 따로 태웁니다.
    Integer deleted =
        jdbcTemplate.queryForObject(
            """
            WITH candidates AS (
                SELECT id, cleanup_at
                FROM (
                    SELECT id, expires_at AS cleanup_at
                    FROM auth_refresh_token_session
                    WHERE session_status = 'ACTIVE'
                      AND expires_at < :cutoff
                    ORDER BY expires_at ASC, id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                ) active_candidates
                UNION ALL
                SELECT id, cleanup_at
                FROM (
                    SELECT id, updated_at AS cleanup_at
                    FROM auth_refresh_token_session
                    WHERE session_status IN ('ROTATED', 'REVOKED')
                      AND updated_at < :cutoff
                    ORDER BY updated_at ASC, id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                ) inactive_candidates
            ),
            limited AS (
                SELECT id
                FROM candidates
                ORDER BY cleanup_at ASC, id ASC
                LIMIT :batchSize
            ),
            deleted AS (
                DELETE FROM auth_refresh_token_session s
                USING limited l
                WHERE s.id = l.id
                RETURNING s.id
            )
            SELECT COUNT(*)
            FROM deleted
            """,
            new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("batchSize", batchSize),
            Integer.class);
    return deleted == null ? 0 : deleted;
  }

  @Override
  @Transactional
  public UserAccountMembership upsert(UserAccountMembershipUpsertCommand command) {
    assertUserExists(command.userId());
    assertAccountExists(command.accountId());

    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO user_account_membership (
            user_id,
            account_id,
            membership_role,
            membership_status,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :accountId,
            :membershipRole,
            :membershipStatus,
            :now,
            :now
        )
        ON CONFLICT (user_id, account_id)
        DO UPDATE
        SET membership_role = EXCLUDED.membership_role,
            membership_status = EXCLUDED.membership_status,
            updated_at = EXCLUDED.updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", command.userId())
            .addValue("accountId", command.accountId())
            .addValue("membershipRole", command.role().name())
            .addValue("membershipStatus", command.status().name())
            .addValue("now", Timestamp.from(now)));

    return new UserAccountMembership(
        command.userId(), command.accountId(), command.role(), command.status());
  }

  @Override
  @Transactional
  public AuthUserSummary updateStatus(UserStatusUpdateCommand command) {
    Instant now = Instant.now();
    UserStatus beforeStatus = loadCurrentUserStatusForUpdate(command.userId());
    AuthUserSummary summary =
        jdbcTemplate
            .query(
                """
            UPDATE bank_user
            SET user_status = :userStatus,
                updated_at = :now
            WHERE id = :userId
            RETURNING id, login_id, display_name, user_status, created_at, updated_at
            """,
                new MapSqlParameterSource()
                    .addValue("userId", command.userId())
                    .addValue("userStatus", command.status().name())
                    .addValue("now", Timestamp.from(now)),
                (rs, rowNum) -> mapUserSummary(rs))
            .stream()
            .findFirst()
            .orElseThrow(() -> new AuthUserNotFoundException("user is not found"));
    // disable 직후 옛 refresh token이 재활성화와 함께 되살아나는 경로를 막으려고 ACTIVE session만 같이 정리합니다.
    if (shouldRevokeActiveRefreshTokenSessions(beforeStatus, summary.status())) {
      revokeActiveRefreshTokenSessions(command.userId(), now);
    }
    insertStatusChangeAudit(
        new AuthStatusChangeAuditEntry(
            AuthStatusChangeType.USER_STATUS,
            command.requestId(),
            command.actorSubject(),
            command.userId(),
            null,
            beforeStatus.name(),
            summary.status().name(),
            command.normalizedReason(),
            AuthStatusChangeOutcome.SUCCESS,
            now));
    return summary;
  }

  @Override
  @Transactional
  public UserAccountMembershipSummary updateStatus(
      UserAccountMembershipStatusUpdateCommand command) {
    Instant now = Instant.now();
    com.aquilabank.domain.auth.model.MembershipStatus beforeStatus =
        loadCurrentMembershipStatusForUpdate(command.userId(), command.accountId());
    UserAccountMembershipSummary summary =
        jdbcTemplate
            .query(
                """
            UPDATE user_account_membership
            SET membership_status = :membershipStatus,
                updated_at = :now
            WHERE user_id = :userId
              AND account_id = :accountId
            RETURNING user_id, account_id, membership_role, membership_status, created_at, updated_at
            """,
                new MapSqlParameterSource()
                    .addValue("userId", command.userId())
                    .addValue("accountId", command.accountId())
                    .addValue("membershipStatus", command.status().name())
                    .addValue("now", Timestamp.from(now)),
                (rs, rowNum) -> mapMembershipSummary(rs))
            .stream()
            .findFirst()
            .orElseThrow(
                () -> new UserAccountMembershipNotFoundException("membership is not found"));
    insertStatusChangeAudit(
        new AuthStatusChangeAuditEntry(
            AuthStatusChangeType.MEMBERSHIP_STATUS,
            command.requestId(),
            command.actorSubject(),
            command.userId(),
            command.accountId(),
            beforeStatus.name(),
            summary.status().name(),
            command.normalizedReason(),
            AuthStatusChangeOutcome.SUCCESS,
            now));
    return summary;
  }

  private UserStatus loadCurrentUserStatusForUpdate(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_status
            FROM bank_user
            WHERE id = :userId
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> UserStatus.valueOf(rs.getString("user_status")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AuthUserNotFoundException("user is not found"));
  }

  private boolean shouldRevokeActiveRefreshTokenSessions(
      UserStatus beforeStatus, UserStatus afterStatus) {
    return beforeStatus != UserStatus.DISABLED && afterStatus == UserStatus.DISABLED;
  }

  private void revokeActiveRefreshTokenSessions(long userId, Instant revokedAt) {
    jdbcTemplate.update(
        """
        UPDATE auth_refresh_token_session
        SET session_status = 'REVOKED',
            last_used_at = :revokedAt,
            updated_at = :revokedAt
        WHERE user_id = :userId
          AND session_status = 'ACTIVE'
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("revokedAt", Timestamp.from(revokedAt)));
  }

  private com.aquilabank.domain.auth.model.MembershipStatus loadCurrentMembershipStatusForUpdate(
      long userId, long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT membership_status
            FROM user_account_membership
            WHERE user_id = :userId
              AND account_id = :accountId
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            (rs, rowNum) ->
                com.aquilabank.domain.auth.model.MembershipStatus.valueOf(
                    rs.getString("membership_status")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new UserAccountMembershipNotFoundException("membership is not found"));
  }

  private void insertStatusChangeAudit(AuthStatusChangeAuditEntry entry) {
    // status update와 감사 row를 같은 transaction에 묶어 운영 흔적이 빠지지 않게 합니다.
    jdbcTemplate.update(
        """
        INSERT INTO auth_status_change_audit (
            request_id,
            actor_subject,
            change_type,
            target_user_id,
            target_account_id,
            before_status,
            after_status,
            reason_code,
            reason,
            outcome,
            created_at
        )
        VALUES (
            :requestId,
            :actorSubject,
            :changeType,
            :targetUserId,
            :targetAccountId,
            :beforeStatus,
            :afterStatus,
            :reasonCode,
            :reason,
            :outcome,
            :createdAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("requestId", entry.requestId())
            .addValue("actorSubject", entry.actorSubject())
            .addValue("changeType", entry.changeType().name())
            .addValue("targetUserId", entry.targetUserId())
            .addValue("targetAccountId", entry.targetAccountId())
            .addValue("beforeStatus", entry.beforeStatus())
            .addValue("afterStatus", entry.afterStatus())
            .addValue("reasonCode", entry.reasonCode().name())
            .addValue("reason", entry.reasonDetail())
            .addValue("outcome", entry.outcome().name())
            .addValue("createdAt", Timestamp.from(entry.createdAt())));
  }

  private void assertUserUpdated(int updated) {
    if (updated == 0) {
      throw new AuthUserNotFoundException("user is not found");
    }
  }

  private Timestamp toTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private void assertUserExists(long userId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM bank_user
                WHERE id = :userId
            )
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            Boolean.class);
    if (!Boolean.TRUE.equals(exists)) {
      throw new IllegalArgumentException("userId is invalid");
    }
  }

  private void assertAccountExists(long accountId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM bank_account
                WHERE id = :accountId
            )
            """,
            new MapSqlParameterSource().addValue("accountId", accountId),
            Boolean.class);
    if (!Boolean.TRUE.equals(exists)) {
      throw new IllegalArgumentException("accountId is invalid");
    }
  }

  private AuthUserSummary mapUserSummary(ResultSet rs) throws SQLException {
    return new AuthUserSummary(
        rs.getLong("id"),
        rs.getString("login_id"),
        rs.getString("display_name"),
        UserStatus.valueOf(rs.getString("user_status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private UserAccountMembershipSummary mapMembershipSummary(ResultSet rs) throws SQLException {
    return new UserAccountMembershipSummary(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        com.aquilabank.domain.auth.model.MembershipRole.valueOf(rs.getString("membership_role")),
        com.aquilabank.domain.auth.model.MembershipStatus.valueOf(
            rs.getString("membership_status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
