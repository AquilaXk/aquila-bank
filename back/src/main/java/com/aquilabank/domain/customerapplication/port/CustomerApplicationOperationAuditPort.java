package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationOperationAuditEntry;

/** customer application review/approve/execute/cancel 감사 row append port입니다. */
public interface CustomerApplicationOperationAuditPort {

  void append(CustomerApplicationOperationAuditEntry entry);
}
