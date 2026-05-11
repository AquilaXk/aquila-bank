package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;

public interface CustomerApplicationReferencePort {

  String issueReference(CustomerApplicationType applicationType);
}
