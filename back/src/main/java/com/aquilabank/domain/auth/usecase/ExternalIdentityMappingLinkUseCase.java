package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.ExternalIdentityLinkCommand;
import com.aquilabank.domain.auth.model.ExternalIdentityMapping;

public interface ExternalIdentityMappingLinkUseCase {

  ExternalIdentityMapping link(ExternalIdentityLinkCommand command);
}
