package com.aquilabank.global.customerapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationInvalidTransitionException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDecisionCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationWriteCommand;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExecutorPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationWritePort;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationOperationUseCase;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
@Import(CustomerApplicationOperationTransactionBoundaryIntegrationTest.ExecutorProbeConfig.class)
class CustomerApplicationOperationTransactionBoundaryIntegrationTest
    extends PostgresContainerTestSupport {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private CustomerApplicationWritePort writePort;
  @Autowired private CustomerApplicationOperationUseCase operationUseCase;
  @Autowired private ExecutorProbe probe;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
    probe.reset();
  }

  @Test
  void runsExecutorOutsideTransactionBlocksDuplicateExecuteAndCompensatesFailure() {
    long[] userIdHolder = new long[1];
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
                      'csa-tx-user',
                      '$2a$10$abcdefghijklmnopqrstuv',
                      'csa-tx-user',
                      'ACTIVE',
                      CURRENT_TIMESTAMP,
                      CURRENT_TIMESTAMP
                  )
                  RETURNING id
                  """,
                  new MapSqlParameterSource(),
                  Long.class);
          if (id == null) {
            throw new IllegalStateException("user insert failed");
          }
          userIdHolder[0] = id;

          writePort.submit(
              new CustomerApplicationWriteCommand(
                  "CSA-TX-001",
                  id,
                  null,
                  CustomerApplicationType.LOAN_APPLICATION,
                  CustomerApplicationStatus.SUBMITTED,
                  "idem-csa-tx-001",
                  "fp-csa-tx-001",
                  true,
                  Instant.now(),
                  Map.of(
                      "productCode", "LOAN-A",
                      "requestedAmountMinor", 100000L,
                      "currencyCode", "KRW",
                      "purpose", "LIVING"),
                  Instant.now()));
          writePort.submit(
              new CustomerApplicationWriteCommand(
                  "CSA-TX-002",
                  id,
                  null,
                  CustomerApplicationType.LOAN_APPLICATION,
                  CustomerApplicationStatus.SUBMITTED,
                  "idem-csa-tx-002",
                  "fp-csa-tx-002",
                  true,
                  Instant.now(),
                  Map.of(
                      "productCode", "LOAN-B",
                      "requestedAmountMinor", 100000L,
                      "currencyCode", "KRW",
                      "purpose", "LIVING"),
                  Instant.now()));
        });

    approveApplication("CSA-TX-001");
    approveApplication("CSA-TX-002");

    AtomicBoolean duplicateExecuteBlockedDuringExternalCall = new AtomicBoolean(false);
    probe.setOnExecute(
        application -> {
          String statusInDb =
              jdbcTemplate.queryForObject(
                  "SELECT application_status FROM customer_service_application WHERE application_reference = :ref",
                  new MapSqlParameterSource().addValue("ref", application.applicationReference()),
                  String.class);
          assertThat(statusInDb).isEqualTo("PENDING_EXTERNAL");

          assertThrows(
              CustomerApplicationInvalidTransitionException.class,
              () ->
                  operationUseCase.apply(
                      new CustomerApplicationDecisionCommand(
                          application.applicationReference(),
                          CustomerApplicationAction.EXECUTE,
                          "ops-second-executor",
                          "duplicate",
                          "req-dup")));
          duplicateExecuteBlockedDuringExternalCall.set(true);
        });

    CustomerApplicationDetails executed =
        operationUseCase.apply(
            new CustomerApplicationDecisionCommand(
                "CSA-TX-001",
                CustomerApplicationAction.EXECUTE,
                "ops-executor",
                "execute",
                "req-exec-1"));

    assertThat(executed.status()).isEqualTo(CustomerApplicationStatus.FAILED);
    assertThat(duplicateExecuteBlockedDuringExternalCall.get()).isTrue();
    assertThat(probe.txActiveStates()).containsExactly(false);

    probe.setThrowOnExecute(new IllegalStateException("downstream unreachable"));
    assertThrows(
        IllegalStateException.class,
        () ->
            operationUseCase.apply(
                new CustomerApplicationDecisionCommand(
                    "CSA-TX-002",
                    CustomerApplicationAction.EXECUTE,
                    "ops-executor",
                    "execute",
                    "req-exec-2")));

    String failedStatus =
        jdbcTemplate.queryForObject(
            "SELECT application_status FROM customer_service_application WHERE application_reference = 'CSA-TX-002'",
            new MapSqlParameterSource(),
            String.class);
    assertThat(failedStatus).isEqualTo("FAILED");
    assertThat(probe.txActiveStates()).containsExactly(false, false);
  }

  private void approveApplication(String reference) {
    operationUseCase.apply(
        new CustomerApplicationDecisionCommand(
            reference, CustomerApplicationAction.START_REVIEW, "ops-reviewer", "review", "r1"));
    operationUseCase.apply(
        new CustomerApplicationDecisionCommand(
            reference, CustomerApplicationAction.APPROVE, "ops-approver", "approve", "r2"));
  }

  static final class ExecutorProbe {
    private final List<Boolean> txActiveStates = new CopyOnWriteArrayList<>();
    private volatile java.util.function.Consumer<CustomerApplicationDetails> onExecute;
    private volatile RuntimeException throwOnExecute;

    void reset() {
      txActiveStates.clear();
      onExecute = null;
      throwOnExecute = null;
    }

    void setOnExecute(java.util.function.Consumer<CustomerApplicationDetails> onExecute) {
      this.onExecute = onExecute;
    }

    void setThrowOnExecute(RuntimeException throwOnExecute) {
      this.onExecute = null;
      this.throwOnExecute = throwOnExecute;
    }

    List<Boolean> txActiveStates() {
      return List.copyOf(txActiveStates);
    }
  }

  @TestConfiguration
  static class ExecutorProbeConfig {

    @Bean
    ExecutorProbe customerApplicationExecutorProbe() {
      return new ExecutorProbe();
    }

    @Bean
    BeanPostProcessor customerApplicationExecutorTxProbePostProcessor(ExecutorProbe probe) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (bean instanceof CustomerApplicationExecutorPort delegate) {
            return (CustomerApplicationExecutorPort)
                (application, actorSubject, requestId) -> {
                  probe.txActiveStates.add(
                      TransactionSynchronizationManager.isActualTransactionActive());
                  if (probe.onExecute != null) {
                    probe.onExecute.accept(application);
                  }
                  if (probe.throwOnExecute != null) {
                    throw probe.throwOnExecute;
                  }
                  return delegate.execute(application, actorSubject, requestId);
                };
          }
          return bean;
        }
      };
    }
  }
}
