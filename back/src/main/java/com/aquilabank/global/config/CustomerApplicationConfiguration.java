package com.aquilabank.global.config;

import com.aquilabank.domain.auth.model.TotpOperationVerifyCommand;
import com.aquilabank.domain.auth.usecase.TotpOperationVerifyUseCase;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReferencePort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationSecurityVerificationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationWritePort;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationService;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSubmitUseCase;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 고객 업무 신청 접수 use case와 인증 adapter를 조립합니다. */
@Configuration
public class CustomerApplicationConfiguration {

  @Bean
  CustomerApplicationReferencePort customerApplicationReferencePort() {
    return applicationType -> "CSA-" + UUID.randomUUID();
  }

  @Bean
  CustomerApplicationSecurityVerificationPort customerApplicationSecurityVerificationPort(
      TotpOperationVerifyUseCase totpOperationVerifyUseCase) {
    return (userId, totpCode) ->
        totpOperationVerifyUseCase.verify(new TotpOperationVerifyCommand(userId, totpCode));
  }

  @Bean
  CustomerApplicationSubmitUseCase customerApplicationSubmitUseCase(
      CustomerApplicationWritePort writePort,
      CustomerApplicationSecurityVerificationPort securityVerificationPort,
      CustomerApplicationReferencePort referencePort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    CustomerApplicationService service =
        new CustomerApplicationService(
            writePort, securityVerificationPort, referencePort, authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      var result = transactionTemplate.execute(status -> service.submit(command));
      if (result == null) {
        throw new IllegalStateException("customer application transaction returned null result");
      }
      return result;
    };
  }
}
