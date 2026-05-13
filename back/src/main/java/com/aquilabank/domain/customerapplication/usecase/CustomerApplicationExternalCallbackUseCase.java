package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExternalCallbackCommand;

public interface CustomerApplicationExternalCallbackUseCase {

  CustomerApplicationDetails apply(CustomerApplicationExternalCallbackCommand command);
}
