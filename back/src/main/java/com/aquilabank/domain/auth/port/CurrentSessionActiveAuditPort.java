package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;

public interface CurrentSessionActiveAuditPort {

  void record(CurrentSessionActiveAuditEvent event);
}
