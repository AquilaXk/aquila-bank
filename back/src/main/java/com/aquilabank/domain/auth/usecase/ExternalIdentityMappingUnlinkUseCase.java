package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.ExternalIdentityMapping;
import com.aquilabank.domain.auth.model.ExternalIdentityUnlinkCommand;

public interface ExternalIdentityMappingUnlinkUseCase {

  ExternalIdentityMapping unlink(ExternalIdentityUnlinkCommand command);
}
