package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationWriteCommand;

public interface CustomerApplicationWritePort {

  CustomerApplicationSubmission submit(CustomerApplicationWriteCommand command);
}
