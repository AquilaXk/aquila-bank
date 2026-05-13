package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStateUpdateCommand;
import java.util.Optional;

public interface CustomerApplicationOperationPort {

  Optional<CustomerApplicationDetails> findByReferenceForUpdate(String applicationReference);

  CustomerApplicationDetails updateStatus(CustomerApplicationStateUpdateCommand command);
}
