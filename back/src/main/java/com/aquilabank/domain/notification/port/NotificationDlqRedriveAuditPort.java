package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveAuditEntry;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveAuditItem;
import java.util.List;

public interface NotificationDlqRedriveAuditPort {

  void append(NotificationDlqRedriveAuditEntry item);

  List<NotificationDlqRedriveAuditItem> findBySource(
      String sourceTopic, int sourcePartition, long sourceOffset);
}
