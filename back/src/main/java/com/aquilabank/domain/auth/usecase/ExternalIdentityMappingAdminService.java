package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.ExternalIdentityLinkCommand;
import com.aquilabank.domain.auth.model.ExternalIdentityMapping;
import com.aquilabank.domain.auth.model.ExternalIdentityUnlinkCommand;
import com.aquilabank.domain.auth.port.ExternalIdentityMappingWritePort;

public final class ExternalIdentityMappingAdminService
    implements ExternalIdentityMappingLinkUseCase, ExternalIdentityMappingUnlinkUseCase {

  private final ExternalIdentityMappingWritePort externalIdentityMappingWritePort;

  public ExternalIdentityMappingAdminService(
      ExternalIdentityMappingWritePort externalIdentityMappingWritePort) {
    this.externalIdentityMappingWritePort = externalIdentityMappingWritePort;
  }

  @Override
  public ExternalIdentityMapping link(ExternalIdentityLinkCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return externalIdentityMappingWritePort.link(command);
  }

  @Override
  public ExternalIdentityMapping unlink(ExternalIdentityUnlinkCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return externalIdentityMappingWritePort.unlink(command);
  }
}
