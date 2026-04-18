package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.TotpEnrollmentStartCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentStartResult;
import com.aquilabank.domain.auth.model.TotpEnrollmentVerifyCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentVerifyResult;

public interface TotpEnrollmentUseCase {

  TotpEnrollmentStartResult start(TotpEnrollmentStartCommand command);

  TotpEnrollmentVerifyResult verify(TotpEnrollmentVerifyCommand command);
}
