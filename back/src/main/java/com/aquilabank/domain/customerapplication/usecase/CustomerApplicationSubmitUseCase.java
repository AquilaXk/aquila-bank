package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmitCommand;

public interface CustomerApplicationSubmitUseCase {

  CustomerApplicationSubmission submit(CustomerApplicationSubmitCommand command);
}
