package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.account.model.AccountStatus;
import com.aquilabank.domain.auth.model.AccountAccessMembership;
import com.aquilabank.domain.auth.model.AuthSessionSummary;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;
import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeType;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.BackupCodeRecord;
import com.aquilabank.domain.auth.model.BackupCodeStatus;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.model.RememberDevice;
import com.aquilabank.domain.auth.model.RememberDeviceStatus;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpLoginChallenge;
import com.aquilabank.domain.auth.model.TotpLoginChallengeStatus;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AccountStatusAccessPort;
import com.aquilabank.domain.auth.port.AuthSessionQueryPort;
import com.aquilabank.domain.auth.port.AuthStatusChangeAuditQueryPort;
import com.aquilabank.domain.auth.port.BackupCodeLoadPort;
import com.aquilabank.domain.auth.port.ExternalIdentityUserLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RememberDeviceLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeLoadPort;
import com.aquilabank.domain.auth.port.UserAccountMembershipQueryPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** login/access exact lookup을 한 adapter로 묶어 auth read 경로를 단순화합니다. */
@Repository
public class JdbcAuthRepository
    implements UserCredentialLoadPort,
        BackupCodeLoadPort,
        RememberDeviceLoadPort,
        RefreshTokenSessionLoadPort,
        TotpCredentialLoadPort,
        TotpLoginChallengeLoadPort,
        AuthSessionQueryPort,
        AccountAccessPort,
        AccountStatusAccessPort,
        ExternalIdentityUserLoadPort,
        UserQueryPort,
        UserAccountMembershipQueryPort,
        AuthStatusChangeAuditQueryPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAuthRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<LoginUser> findByLoginId(String loginId) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   login_id,
                   password_hash,
                   user_status,
                   failed_login_count,
                   last_login_failed_at,
                   login_locked_until,
                   last_login_succeeded_at
            FROM bank_user
            WHERE login_id = :loginId
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            (rs, rowNum) -> mapLoginUser(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<LoginUser> findByLoginIdForUpdate(String loginId) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   login_id,
                   password_hash,
                   user_status,
                   failed_login_count,
                   last_login_failed_at,
                   login_locked_until,
                   last_login_succeeded_at
            FROM bank_user
            WHERE login_id = :loginId
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            (rs, rowNum) -> mapLoginUser(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<LoginUser> findByUserIdForUpdate(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   login_id,
                   password_hash,
                   user_status,
                   failed_login_count,
                   last_login_failed_at,
                   login_locked_until,
                   last_login_succeeded_at
            FROM bank_user
            WHERE id = :userId
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> mapLoginUser(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<LoginUser> findByProviderIdAndSubjectForUpdate(
      String providerId, String subject) {
    return jdbcTemplate
        .query(
            """
            SELECT u.id,
                   u.login_id,
                   u.password_hash,
                   u.user_status,
                   u.failed_login_count,
                   u.last_login_failed_at,
                   u.login_locked_until,
                   u.last_login_succeeded_at
            FROM auth_external_identity e
            JOIN bank_user u
              ON u.id = e.user_id
            WHERE e.provider_id = :providerId
              AND e.subject = :subject
            FOR UPDATE OF u
            """,
            new MapSqlParameterSource()
                .addValue("providerId", providerId)
                .addValue("subject", subject),
            (rs, rowNum) -> mapLoginUser(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<TotpCredential> findCredentialByUserId(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_id,
                   credential_status,
                   secret_ciphertext,
                   secret_nonce,
                   pending_expires_at,
                   verified_at,
                   last_used_at
            FROM auth_totp_credential
            WHERE user_id = :userId
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> mapTotpCredential(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<TotpCredential> findCredentialByUserIdForUpdate(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_id,
                   credential_status,
                   secret_ciphertext,
                   secret_nonce,
                   pending_expires_at,
                   verified_at,
                   last_used_at
            FROM auth_totp_credential
            WHERE user_id = :userId
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> mapTotpCredential(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<TotpLoginChallenge> findByChallengeIdForUpdate(String challengeId) {
    return jdbcTemplate
        .query(
            """
            SELECT c.user_id,
                   u.login_id,
                   u.user_status,
                   c.challenge_id,
                   c.challenge_status,
                   c.attempt_count,
                   c.expires_at,
                   c.device_name,
                   c.ip_address
            FROM auth_totp_login_challenge c
            JOIN bank_user u
              ON u.id = c.user_id
            WHERE c.challenge_id = :challengeId
            FOR UPDATE OF c, u
            """,
            new MapSqlParameterSource().addValue("challengeId", challengeId),
            (rs, rowNum) -> mapTotpLoginChallenge(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<BackupCodeRecord> findActiveByUserIdAndCodeHashForUpdate(
      long userId, String codeHash) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   user_id,
                   code_hash,
                   code_status,
                   used_at,
                   created_at
            FROM auth_mfa_backup_code
            WHERE user_id = :userId
              AND code_hash = :codeHash
              AND code_status = 'ACTIVE'
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("codeHash", codeHash),
            (rs, rowNum) -> mapBackupCodeRecord(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RememberDevice> findActiveByUserIdAndTokenHashForUpdate(
      long userId, String tokenHash) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   user_id,
                   token_hash,
                   device_status,
                   device_name,
                   last_used_at,
                   expires_at,
                   created_at
            FROM auth_mfa_remember_device
            WHERE user_id = :userId
              AND token_hash = :tokenHash
              AND device_status = 'ACTIVE'
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("tokenHash", tokenHash),
            (rs, rowNum) -> mapRememberDevice(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RefreshTokenSession> findByTokenHashForUpdate(String tokenHash) {
    return jdbcTemplate
        .query(
            """
            SELECT s.id,
                   s.user_id,
                   u.login_id,
                   u.user_status,
                   s.token_hash,
                   s.device_binding_hash,
                   s.session_status,
                   s.expires_at,
                   s.last_used_at,
                   s.rotated_at,
                   s.replaced_by_session_id
            FROM auth_refresh_token_session s
            JOIN bank_user u
              ON u.id = s.user_id
            WHERE s.token_hash = :tokenHash
            FOR UPDATE OF s, u
            """,
            new MapSqlParameterSource().addValue("tokenHash", tokenHash),
            (rs, rowNum) -> mapRefreshTokenSession(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RefreshTokenSession> findBySessionIdForUpdate(long sessionId) {
    return jdbcTemplate
        .query(
            """
            SELECT s.id,
                   s.user_id,
                   u.login_id,
                   u.user_status,
                   s.token_hash,
                   s.device_binding_hash,
                   s.session_status,
                   s.expires_at,
                   s.last_used_at,
                   s.rotated_at,
                   s.replaced_by_session_id
            FROM auth_refresh_token_session s
            JOIN bank_user u
              ON u.id = s.user_id
            WHERE s.id = :sessionId
            FOR UPDATE OF s, u
            """,
            new MapSqlParameterSource().addValue("sessionId", sessionId),
            (rs, rowNum) -> mapRefreshTokenSession(rs))
        .stream()
        .findFirst();
  }

  @Override
  public List<AuthSessionSummary> findActiveSessionsByUserId(long userId, Instant now, int size) {
    // user_id + session_status + expires_at DESC index 경로를 그대로 쓰려고 조건과 정렬을 고정합니다.
    return jdbcTemplate.query(
        """
        SELECT id,
               session_status,
               expires_at,
               last_used_at,
               created_at,
               device_name,
               ip_address
        FROM auth_refresh_token_session
        WHERE user_id = :userId
          AND session_status = 'ACTIVE'
          AND expires_at > :now
        ORDER BY expires_at DESC, id DESC
        LIMIT :size
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("now", Timestamp.from(now))
            .addValue("size", size),
        (rs, rowNum) -> mapAuthSessionSummary(rs));
  }

  @Override
  public Optional<AuthUserSummary> findSummaryByUserId(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, login_id, display_name, user_status, created_at, updated_at
            FROM bank_user
            WHERE id = :userId
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> mapUserSummary(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AuthUserSummary> findSummaryByLoginId(String loginId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, login_id, display_name, user_status, created_at, updated_at
            FROM bank_user
            WHERE login_id = :loginId
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            (rs, rowNum) -> mapUserSummary(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<UserAccountMembership> findMembership(long userId, long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_id, account_id, membership_role, membership_status
            FROM user_account_membership
            WHERE user_id = :userId
              AND account_id = :accountId
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            (rs, rowNum) -> mapMembership(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AccountAccessMembership> findAccessMembership(long userId, long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT m.user_id,
                   m.account_id,
                   m.membership_role,
                   m.membership_status,
                   u.user_status,
                   a.account_status
            FROM user_account_membership m
            JOIN bank_user u
              ON u.id = m.user_id
            JOIN bank_account a
              ON a.id = m.account_id
            WHERE m.user_id = :userId
              AND m.account_id = :accountId
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            (rs, rowNum) -> mapAccessMembership(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AccountStatus> findAccountStatus(long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT account_status
            FROM bank_account
            WHERE id = :accountId
            """,
            new MapSqlParameterSource().addValue("accountId", accountId),
            (rs, rowNum) -> AccountStatus.valueOf(rs.getString("account_status")))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<UserAccountMembershipSummary> findByUserIdAndAccountId(
      long userId, long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_id, account_id, membership_role, membership_status, created_at, updated_at
            FROM user_account_membership
            WHERE user_id = :userId
              AND account_id = :accountId
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            (rs, rowNum) -> mapMembershipSummary(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AuthStatusChangeAuditSummary> findByRequestId(String requestId) {
    // requestId exact match만 허용해 idx_auth_status_change_audit_request_id 경로를 그대로 사용합니다.
    return jdbcTemplate
        .query(
            """
            SELECT request_id,
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
            FROM auth_status_change_audit
            WHERE request_id = :requestId
            ORDER BY id DESC
            LIMIT 1
            """,
            new MapSqlParameterSource().addValue("requestId", requestId),
            (rs, rowNum) -> mapStatusChangeAuditSummary(rs))
        .stream()
        .findFirst();
  }

  private LoginUser mapLoginUser(ResultSet rs) throws SQLException {
    return new LoginUser(
        rs.getLong("id"),
        rs.getString("login_id"),
        rs.getString("password_hash"),
        UserStatus.valueOf(rs.getString("user_status")),
        rs.getInt("failed_login_count"),
        toNullableInstant(rs.getTimestamp("last_login_failed_at")),
        toNullableInstant(rs.getTimestamp("login_locked_until")),
        toNullableInstant(rs.getTimestamp("last_login_succeeded_at")));
  }

  private TotpCredential mapTotpCredential(ResultSet rs) throws SQLException {
    return new TotpCredential(
        rs.getLong("user_id"),
        TotpCredentialStatus.valueOf(rs.getString("credential_status")),
        rs.getString("secret_ciphertext"),
        rs.getString("secret_nonce"),
        toNullableInstant(rs.getTimestamp("pending_expires_at")),
        toNullableInstant(rs.getTimestamp("verified_at")),
        toNullableInstant(rs.getTimestamp("last_used_at")));
  }

  private TotpLoginChallenge mapTotpLoginChallenge(ResultSet rs) throws SQLException {
    return new TotpLoginChallenge(
        rs.getLong("user_id"),
        rs.getString("login_id"),
        UserStatus.valueOf(rs.getString("user_status")),
        rs.getString("challenge_id"),
        TotpLoginChallengeStatus.valueOf(rs.getString("challenge_status")),
        rs.getInt("attempt_count"),
        toInstant(rs.getTimestamp("expires_at")),
        rs.getString("device_name"),
        rs.getString("ip_address"));
  }

  private BackupCodeRecord mapBackupCodeRecord(ResultSet rs) throws SQLException {
    return new BackupCodeRecord(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("code_hash"),
        BackupCodeStatus.valueOf(rs.getString("code_status")),
        toNullableInstant(rs.getTimestamp("used_at")),
        toInstant(rs.getTimestamp("created_at")));
  }

  private RefreshTokenSession mapRefreshTokenSession(ResultSet rs) throws SQLException {
    return new RefreshTokenSession(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("login_id"),
        UserStatus.valueOf(rs.getString("user_status")),
        rs.getString("token_hash"),
        rs.getString("device_binding_hash"),
        RefreshTokenSessionStatus.valueOf(rs.getString("session_status")),
        toInstant(rs.getTimestamp("expires_at")),
        toNullableInstant(rs.getTimestamp("last_used_at")),
        toNullableInstant(rs.getTimestamp("rotated_at")),
        rs.getObject("replaced_by_session_id", Long.class));
  }

  private RememberDevice mapRememberDevice(ResultSet rs) throws SQLException {
    return new RememberDevice(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("token_hash"),
        RememberDeviceStatus.valueOf(rs.getString("device_status")),
        rs.getString("device_name"),
        toNullableInstant(rs.getTimestamp("last_used_at")),
        toInstant(rs.getTimestamp("expires_at")),
        toInstant(rs.getTimestamp("created_at")));
  }

  private AuthSessionSummary mapAuthSessionSummary(ResultSet rs) throws SQLException {
    return new AuthSessionSummary(
        rs.getLong("id"),
        RefreshTokenSessionStatus.valueOf(rs.getString("session_status")),
        toInstant(rs.getTimestamp("expires_at")),
        toNullableInstant(rs.getTimestamp("last_used_at")),
        toInstant(rs.getTimestamp("created_at")),
        rs.getString("device_name"),
        rs.getString("ip_address"));
  }

  private UserAccountMembership mapMembership(ResultSet rs) throws SQLException {
    return new UserAccountMembership(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        MembershipRole.valueOf(rs.getString("membership_role")),
        MembershipStatus.valueOf(rs.getString("membership_status")));
  }

  private AccountAccessMembership mapAccessMembership(ResultSet rs) throws SQLException {
    return new AccountAccessMembership(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        MembershipRole.valueOf(rs.getString("membership_role")),
        MembershipStatus.valueOf(rs.getString("membership_status")),
        UserStatus.valueOf(rs.getString("user_status")),
        AccountStatus.valueOf(rs.getString("account_status")));
  }

  private AuthUserSummary mapUserSummary(ResultSet rs) throws SQLException {
    return new AuthUserSummary(
        rs.getLong("id"),
        rs.getString("login_id"),
        rs.getString("display_name"),
        UserStatus.valueOf(rs.getString("user_status")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private UserAccountMembershipSummary mapMembershipSummary(ResultSet rs) throws SQLException {
    return new UserAccountMembershipSummary(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        MembershipRole.valueOf(rs.getString("membership_role")),
        MembershipStatus.valueOf(rs.getString("membership_status")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private AuthStatusChangeAuditSummary mapStatusChangeAuditSummary(ResultSet rs)
      throws SQLException {
    Long targetAccountId = rs.getObject("target_account_id", Long.class);
    return new AuthStatusChangeAuditSummary(
        rs.getString("request_id"),
        rs.getString("actor_subject"),
        AuthStatusChangeType.valueOf(rs.getString("change_type")),
        rs.getLong("target_user_id"),
        targetAccountId,
        rs.getString("before_status"),
        rs.getString("after_status"),
        new AuthStatusChangeReason(
            AuthStatusChangeReasonCode.valueOf(rs.getString("reason_code")),
            rs.getString("reason")),
        AuthStatusChangeOutcome.valueOf(rs.getString("outcome")),
        toInstant(rs.getTimestamp("created_at")));
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      throw new IllegalStateException("timestamp must not be null");
    }
    return timestamp.toInstant();
  }

  private Instant toNullableInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
