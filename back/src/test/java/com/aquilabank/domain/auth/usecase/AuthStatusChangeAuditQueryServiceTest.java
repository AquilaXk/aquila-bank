package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditItem;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchQuery;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchResult;
import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeType;
import com.aquilabank.domain.auth.port.AuthStatusChangeAuditQueryPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthStatusChangeAuditQueryServiceTest {

  @Test
  void searchesAuditWithBoundedSizeAndKeysetCursor() {
    AuthStatusChangeAuditQueryPort port = mock(AuthStatusChangeAuditQueryPort.class);
    AuthStatusChangeAuditSearchQuery query =
        new AuthStatusChangeAuditSearchQuery(
            Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-04-22T00:00:00Z"),
            21L,
            null,
            AuthStatusChangeType.USER_STATUS,
            AuthStatusChangeReasonCode.FRAUD_REVIEW,
            null,
            500);
    AuthStatusChangeAuditSearchResult result =
        new AuthStatusChangeAuditSearchResult(List.of(item()), "next-cursor");
    when(port.search(query)).thenReturn(result);
    AuthStatusChangeAuditQueryService service = new AuthStatusChangeAuditQueryService(port);

    AuthStatusChangeAuditSearchResult actual = service.search(query);

    assertThat(actual.items()).hasSize(1);
    assertThat(actual.nextCursor()).isEqualTo("next-cursor");
    assertThat(query.size()).isEqualTo(AuthStatusChangeAuditSearchQuery.MAX_SIZE);
    verify(port).search(query);
  }

  private AuthStatusChangeAuditItem item() {
    return new AuthStatusChangeAuditItem(
        101L,
        "request-001",
        "ops-admin",
        AuthStatusChangeType.USER_STATUS,
        21L,
        null,
        "ACTIVE",
        "DISABLED",
        new AuthStatusChangeReason(AuthStatusChangeReasonCode.FRAUD_REVIEW, "fraud-review"),
        AuthStatusChangeOutcome.SUCCESS,
        Instant.parse("2026-04-21T00:00:00Z"));
  }
}
