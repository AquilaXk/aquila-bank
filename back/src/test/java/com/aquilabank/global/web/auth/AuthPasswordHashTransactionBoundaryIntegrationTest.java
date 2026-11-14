package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResultStatus;
import com.aquilabank.domain.auth.model.PasswordRecoveryConfirmCommand;
import com.aquilabank.domain.auth.model.PasswordResetCommand;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryConfirmUseCase;
import com.aquilabank.domain.auth.usecase.PasswordResetUseCase;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
@Import(AuthPasswordHashTransactionBoundaryIntegrationTest.TxProbeConfig.class)
class AuthPasswordHashTransactionBoundaryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private PasswordHashPort passwordHashPort;
  @Autowired private PasswordRecoverySecretPort passwordRecoverySecretPort;
  @Autowired private LoginUseCase loginUseCase;
  @Autowired private PasswordResetUseCase passwordResetUseCase;
  @Autowired private PasswordRecoveryConfirmUseCase passwordRecoveryConfirmUseCase;
  @Autowired private HashCallTxRecorder recorder;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
    recorder.clear();
  }

  @Test
  void executesBcryptOperationsOutsideDatabaseTransactionsWhilePersistingFailuresAndUpdates() {
    long[] userIdHolder = new long[1];
    String initialHash = passwordHashPort.encode("InitPass123!");
    String recoveryToken = "rec-token-tx-boundary-1";
    String tokenHash = passwordRecoverySecretPort.hash(recoveryToken);
    recorder.clear();

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
                      'tx-boundary-user',
                      :passwordHash,
                      'tx-boundary-user',
                      'ACTIVE',
                      CURRENT_TIMESTAMP,
                      CURRENT_TIMESTAMP
                  )
                  RETURNING id
                  """,
                  new MapSqlParameterSource().addValue("passwordHash", initialHash),
                  Long.class);
          if (id == null) {
            throw new IllegalStateException("user insert failed");
          }
          userIdHolder[0] = id;

          Instant futureExpiry = Instant.now().plusSeconds(600);
          jdbcTemplate.update(
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
                  'req-tx-boundary',
                  :userId,
                  'tx-boundary-user',
                  :tokenHash,
                  'cipher',
                  'nonce',
                  'PENDING',
                  :expiresAt,
                  NULL,
                  CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP
              )
              """,
              new MapSqlParameterSource()
                  .addValue("userId", id)
                  .addValue("tokenHash", tokenHash)
                  .addValue("expiresAt", Timestamp.from(futureExpiry)));
        });

    long userId = userIdHolder[0];
    AuthSessionClientMetadata client = new AuthSessionClientMetadata("Chrome", "198.51.100.10");

    assertThrows(
        InvalidCredentialsException.class,
        () -> loginUseCase.login(new LoginCommand("tx-boundary-user", "WrongPass!", client)));
    Integer failedCountAfterBadLogin =
        jdbcTemplate.queryForObject(
            "SELECT failed_login_count FROM bank_user WHERE id = :id",
            new MapSqlParameterSource().addValue("id", userId),
            Integer.class);
    assertThat(failedCountAfterBadLogin).isEqualTo(1);

    var loginResult =
        loginUseCase.login(new LoginCommand("tx-boundary-user", "InitPass123!", client));
    assertThat(loginResult.status()).isEqualTo(LoginResultStatus.SUCCESS);

    passwordResetUseCase.reset(new PasswordResetCommand(userId, "InitPass123!", "ResetPass456!"));
    assertThat(
            loginUseCase
                .login(new LoginCommand("tx-boundary-user", "ResetPass456!", client))
                .status())
        .isEqualTo(LoginResultStatus.SUCCESS);

    passwordRecoveryConfirmUseCase.confirm(
        new PasswordRecoveryConfirmCommand(recoveryToken, "RecoveredPass789!"));
    assertThat(
            loginUseCase
                .login(new LoginCommand("tx-boundary-user", "RecoveredPass789!", client))
                .status())
        .isEqualTo(LoginResultStatus.SUCCESS);

    assertThat(recorder.events()).isNotEmpty();
    assertThat(recorder.events()).allSatisfy(active -> assertThat(active).isFalse());
  }

  static final class HashCallTxRecorder {
    private final List<Boolean> txActiveDuringHashCall = new CopyOnWriteArrayList<>();

    void record() {
      txActiveDuringHashCall.add(TransactionSynchronizationManager.isActualTransactionActive());
    }

    List<Boolean> events() {
      return List.copyOf(txActiveDuringHashCall);
    }

    void clear() {
      txActiveDuringHashCall.clear();
    }
  }

  @TestConfiguration
  static class TxProbeConfig {

    @Bean
    HashCallTxRecorder hashCallTxRecorder() {
      return new HashCallTxRecorder();
    }

    @Bean
    BeanPostProcessor passwordHashPortTxProbePostProcessor(HashCallTxRecorder recorder) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (bean instanceof PasswordHashPort delegate) {
            return new PasswordHashPort() {
              @Override
              public String encode(String rawPassword) {
                recorder.record();
                return delegate.encode(rawPassword);
              }

              @Override
              public boolean matches(String rawPassword, String passwordHash) {
                recorder.record();
                return delegate.matches(rawPassword, passwordHash);
              }
            };
          }
          return bean;
        }
      };
    }
  }
}
