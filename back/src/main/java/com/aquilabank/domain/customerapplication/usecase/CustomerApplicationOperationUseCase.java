package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDecisionCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;

public interface CustomerApplicationOperationUseCase {

  CustomerApplicationDetails apply(CustomerApplicationDecisionCommand command);
}
