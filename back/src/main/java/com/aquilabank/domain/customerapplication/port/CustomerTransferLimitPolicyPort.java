package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitPolicyCommand;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;

public interface CustomerTransferLimitPolicyPort {

  TransferLimitPolicy applyTransferLimitChange(CustomerTransferLimitPolicyCommand command);
}
