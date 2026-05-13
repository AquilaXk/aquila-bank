package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;

public interface CustomerApplicationExecutorPort {

  CustomerApplicationExecutionResult execute(
      CustomerApplicationDetails application, String actorSubject, String requestId);
}
