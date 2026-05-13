package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import java.util.List;

public interface CustomerApplicationSelfServiceUseCase {

  List<CustomerApplicationDetails> findByUserId(long userId, int limit);

  CustomerApplicationDetails getByUserIdAndReference(long userId, String applicationReference);

  CustomerApplicationDetails cancel(long userId, String applicationReference, String requestId);
}
