package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;

/** 외부 기관이 필요한 신청 실행 adapter port입니다. provider 계약 없이는 실패 결과만 반환합니다. */
public interface CustomerApplicationExternalExecutionPort {

  CustomerApplicationExecutionResult execute(
      CustomerApplicationDetails application, String actorSubject, String requestId);
}
