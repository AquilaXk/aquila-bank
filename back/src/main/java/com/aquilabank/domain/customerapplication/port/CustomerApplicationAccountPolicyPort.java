package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationAccountPolicyCheck;

public interface CustomerApplicationAccountPolicyPort {

  CustomerApplicationAccountPolicyCheck checkTransferLimitChange(long userId, long accountId);
}
