package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDecisionCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationAccountPolicyPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExecutorPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExternalExecutionPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationAuditPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReadPort;
import com.aquilabank.domain.customerapplication.port.CustomerTransferLimitPolicyPort;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationOperationUseCase;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSelfServiceUseCase;
import com.aquilabank.global.persistence.customerapplication.TransactionTemplateCustomerApplicationWriteAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class CustomerApplicationConfigurationTest {

  private final CustomerApplicationConfiguration configuration =
      new CustomerApplicationConfiguration();
  private final CustomerApplicationReadPort readPort = mock(CustomerApplicationReadPort.class);
  private final CustomerApplicationOperationPort operationPort =
      mock(CustomerApplicationOperationPort.class);
  private final CustomerApplicationOperationAuditPort operationAuditPort =
      mock(CustomerApplicationOperationAuditPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-13T03:00:00Z"), ZoneOffset.UTC);

  @Test
  void createsSelfServiceUseCaseForReadAndCancel() {
    CustomerApplicationSelfServiceUseCase useCase =
        configuration.customerApplicationSelfServiceUseCase(
            readPort, operationPort, operationAuditPort, clock, new NoopTransactionManager());
    when(readPort.findByUserId(7L, 20))
        .thenReturn(List.of(details(CustomerApplicationStatus.SUBMITTED)));
    when(readPort.findByUserIdAndReference(7L, "CSA-001"))
        .thenReturn(java.util.Optional.of(details(CustomerApplicationStatus.SUBMITTED)));
    when(operationPort.findByReferenceForUpdate("CSA-001"))
        .thenReturn(java.util.Optional.of(details(CustomerApplicationStatus.SUBMITTED)));
    when(operationPort.updateStatus(org.mockito.ArgumentMatchers.any()))
        .thenReturn(details(CustomerApplicationStatus.CANCELLED));

    assertThat(useCase.findByUserId(7L, 20)).hasSize(1);
    assertThat(useCase.getByUserIdAndReference(7L, "CSA-001").applicationReference())
        .isEqualTo("CSA-001");
    assertThat(useCase.cancel(7L, "CSA-001", "req").status())
        .isEqualTo(CustomerApplicationStatus.CANCELLED);
  }

  @Test
  void rejectsNullSelfServiceTransactionResult() {
    CustomerApplicationSelfServiceUseCase useCase =
        configuration.customerApplicationSelfServiceUseCase(
            readPort, operationPort, operationAuditPort, clock, new NoopTransactionManager());
    when(operationPort.findByReferenceForUpdate("CSA-001"))
        .thenReturn(java.util.Optional.of(details(CustomerApplicationStatus.SUBMITTED)));

    assertThrows(IllegalStateException.class, () -> useCase.cancel(7L, "CSA-001", "req"));
  }

  @Test
  void executesExternalApplicationOutsideActiveTransaction() {
    AtomicBoolean txActive = new AtomicBoolean(false);
    AtomicInteger committedTxCount = new AtomicInteger(0);
    PlatformTransactionManager trackingTxManager =
        new PlatformTransactionManager() {
          @Override
          public TransactionStatus getTransaction(TransactionDefinition definition)
              throws TransactionException {
            txActive.set(true);
            return new SimpleTransactionStatus();
          }

          @Override
          public void commit(TransactionStatus status) throws TransactionException {
            txActive.set(false);
            committedTxCount.incrementAndGet();
          }

          @Override
          public void rollback(TransactionStatus status) throws TransactionException {
            txActive.set(false);
          }
        };

    AtomicBoolean txActiveDuringExecute = new AtomicBoolean(true);
    CustomerApplicationExecutorPort executorPort =
        (application, actorSubject, requestId) -> {
          txActiveDuringExecute.set(txActive.get());
          return CustomerApplicationExecutionResult.pendingExternal(
              "EXTERNAL_EXECUTION_DISPATCHED", Map.of("providerType", "WEBHOOK"));
        };

    when(operationPort.findByReferenceForUpdate("CSA-001"))
        .thenReturn(
            java.util.Optional.of(details(CustomerApplicationStatus.APPROVED)),
            java.util.Optional.of(details(CustomerApplicationStatus.PENDING_EXTERNAL)));
    when(operationPort.updateStatus(org.mockito.ArgumentMatchers.any()))
        .thenReturn(details(CustomerApplicationStatus.PENDING_EXTERNAL));

    CustomerApplicationOperationUseCase useCase =
        configuration.customerApplicationOperationUseCase(
            operationPort, operationAuditPort, executorPort, clock, trackingTxManager);

    CustomerApplicationDetails result =
        useCase.apply(
            new CustomerApplicationDecisionCommand(
                "CSA-001",
                CustomerApplicationAction.EXECUTE,
                "ops-executor",
                "dispatch",
                "req-exec-1"));

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.PENDING_EXTERNAL);
    assertThat(txActiveDuringExecute.get()).isFalse();
    assertThat(committedTxCount.get()).isEqualTo(2);

    TransactionTemplateCustomerApplicationWriteAdapter adapter =
        new TransactionTemplateCustomerApplicationWriteAdapter(
            new TransactionTemplate(trackingTxManager), "null tx result");
    assertThrows(IllegalStateException.class, () -> adapter.execute(() -> null));

    CustomerApplicationExecutorPort wiredExecutor =
        configuration.customerApplicationExecutorPort(
            mock(CustomerTransferLimitPolicyPort.class),
            mock(CustomerApplicationAccountPolicyPort.class),
            mock(CustomerApplicationExternalExecutionPort.class),
            new CustomerApplicationProperties(
                new CustomerApplicationProperties.TransferLimitChange(10L, 20L)),
            clock);
    assertThat(wiredExecutor).isNotNull();
  }

  private static CustomerApplicationDetails details(CustomerApplicationStatus status) {
    Instant now = Instant.parse("2026-05-13T02:00:00Z");
    return new CustomerApplicationDetails(
        "CSA-001",
        7L,
        101L,
        CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
        status,
        true,
        now,
        Map.of("requestedSingleTransferLimitMinor", 100_000L),
        now,
        now,
        null,
        null,
        null,
        Map.of());
  }

  private static final class NoopTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition)
        throws TransactionException {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {}

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {}
  }
}
