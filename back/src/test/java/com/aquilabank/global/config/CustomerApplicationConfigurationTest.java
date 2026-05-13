package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationAuditPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReadPort;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSelfServiceUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
