package com.aquilabank.domain.account.port;

import com.aquilabank.domain.account.model.AccountStatusChangeAuditSummary;
import java.util.Optional;

/** 계좌 상태 변경 감사 row requestId exact lookup을 저장소 계층으로 위임합니다. */
public interface AccountStatusChangeAuditQueryPort {

  Optional<AccountStatusChangeAuditSummary> findByRequestId(String requestId);
}
