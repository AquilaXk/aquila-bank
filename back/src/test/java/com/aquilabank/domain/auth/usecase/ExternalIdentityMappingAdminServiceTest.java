package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.ExternalIdentityAuditNotFoundException;
import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;
import com.aquilabank.domain.auth.model.ExternalIdentityChangeType;
import com.aquilabank.domain.auth.model.ExternalIdentityLinkCommand;
import com.aquilabank.domain.auth.model.ExternalIdentityMapping;
import com.aquilabank.domain.auth.model.ExternalIdentityUnlinkCommand;
import com.aquilabank.domain.auth.port.ExternalIdentityAuditQueryPort;
import com.aquilabank.domain.auth.port.ExternalIdentityMappingWritePort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExternalIdentityMappingAdminServiceTest {

  private static final AuthStatusChangeReason REASON =
      new AuthStatusChangeReason(AuthStatusChangeReasonCode.OPS_MANUAL, "ops-approved");

  @Test
  void linksExternalIdentityThroughWritePort() {
    ExternalIdentityMappingWritePort writePort =
        Mockito.mock(ExternalIdentityMappingWritePort.class);
    ExternalIdentityMappingAdminService service =
        new ExternalIdentityMappingAdminService(writePort);
    ExternalIdentityMapping mapping =
        new ExternalIdentityMapping(
            21L,
            "google",
            "oidc-subject-1",
            Instant.parse("2026-04-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"));
    when(writePort.link(argThat(command -> command.userId() == 21L))).thenReturn(mapping);

    ExternalIdentityMapping result =
        service.link(
            new ExternalIdentityLinkCommand(
                21L, "google", "oidc-subject-1", REASON, "ops-admin", "request-001"));

    assertThat(result).isEqualTo(mapping);
    verify(writePort)
        .link(
            argThat(
                command ->
                    command.userId() == 21L
                        && command.providerId().equals("google")
                        && command.subject().equals("oidc-subject-1")
                        && command.normalizedReason().reasonCode()
                            == AuthStatusChangeReasonCode.OPS_MANUAL
                        && command.actorSubject().equals("ops-admin")
                        && command.requestId().equals("request-001")));
  }

  @Test
  void unlinksExternalIdentityThroughWritePort() {
    ExternalIdentityMappingWritePort writePort =
        Mockito.mock(ExternalIdentityMappingWritePort.class);
    ExternalIdentityMappingAdminService service =
        new ExternalIdentityMappingAdminService(writePort);
    ExternalIdentityMapping mapping =
        new ExternalIdentityMapping(
            21L,
            "google",
            "oidc-subject-1",
            Instant.parse("2026-04-20T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"));
    when(writePort.unlink(argThat(command -> command.userId() == 21L))).thenReturn(mapping);

    ExternalIdentityMapping result =
        service.unlink(
            new ExternalIdentityUnlinkCommand(
                21L, "google", "oidc-subject-1", REASON, "ops-admin", "request-002"));

    assertThat(result).isEqualTo(mapping);
    verify(writePort)
        .unlink(
            argThat(
                command ->
                    command.userId() == 21L
                        && command.providerId().equals("google")
                        && command.subject().equals("oidc-subject-1")
                        && command.requestId().equals("request-002")));
  }

  @Test
  void getsExternalIdentityAuditByRequestId() {
    ExternalIdentityAuditQueryPort queryPort = Mockito.mock(ExternalIdentityAuditQueryPort.class);
    ExternalIdentityAuditQueryService service = new ExternalIdentityAuditQueryService(queryPort);
    ExternalIdentityAuditSummary summary =
        new ExternalIdentityAuditSummary(
            "request-001",
            "ops-admin",
            ExternalIdentityChangeType.LINK,
            21L,
            "google",
            "subject-hash",
            REASON,
            AuthStatusChangeOutcome.SUCCESS,
            Instant.parse("2026-04-21T00:00:00Z"));
    when(queryPort.findByRequestId("request-001")).thenReturn(Optional.of(summary));

    assertThat(service.getByRequestId("request-001")).isEqualTo(summary);
  }

  @Test
  void rejectsMissingExternalIdentityAudit() {
    ExternalIdentityAuditQueryPort queryPort = Mockito.mock(ExternalIdentityAuditQueryPort.class);
    ExternalIdentityAuditQueryService service = new ExternalIdentityAuditQueryService(queryPort);
    when(queryPort.findByRequestId("missing-request")).thenReturn(Optional.empty());

    assertThrows(
        ExternalIdentityAuditNotFoundException.class,
        () -> service.getByRequestId("missing-request"));
  }
}
