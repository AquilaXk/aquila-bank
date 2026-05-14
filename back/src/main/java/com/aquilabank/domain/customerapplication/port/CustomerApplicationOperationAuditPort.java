package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationOperationAuditEntry;
import java.util.Set;

/** customer application review/approve/execute/cancel 감사 row append port입니다. */
public interface CustomerApplicationOperationAuditPort {

  void append(CustomerApplicationOperationAuditEntry entry);

  boolean existsByReferenceAndActorAndActions(
      String applicationReference, String actorSubject, Set<CustomerApplicationAction> actions);
}
