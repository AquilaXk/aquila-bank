package com.aquilabank.domain.transaction.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.port.TransactionArchiveReadPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionArchiveQueryServiceTest {

  @Test
  void delegatesArchiveQueryToArchiveReadPort() {
    TransactionArchiveReadPort readPort = mock(TransactionArchiveReadPort.class);
    TransactionArchiveQueryService service = new TransactionArchiveQueryService(readPort);
    TransactionQuery query =
        new TransactionQuery(
            101L,
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-01-31T00:00:00Z"),
            20,
            null,
            null,
            null,
            null,
            null,
            null);
    TransactionSlice expected = new TransactionSlice(List.of(), null, false, 20);
    when(readPort.fetchArchived(query)).thenReturn(expected);

    TransactionSlice result = service.getArchivedTransactions(query);

    assertThat(result).isSameAs(expected);
    verify(readPort).fetchArchived(query);
  }
}
