package com.aquilabank.global.config;

import com.aquilabank.domain.auth.model.TotpOperationVerifyCommand;
import com.aquilabank.domain.auth.usecase.TotpOperationVerifyUseCase;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationProcessingMode;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationAccountPolicyPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExecutorPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExternalExecutionPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationAuditPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReadPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReferencePort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationSecurityVerificationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationWritePort;
import com.aquilabank.domain.customerapplication.port.CustomerTransferLimitPolicyPort;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationExecutorService;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationExternalCallbackService;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationExternalCallbackUseCase;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationOperationService;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationOperationUseCase;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSelfService;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSelfServiceUseCase;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationService;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSubmitUseCase;
import com.aquilabank.global.customerapplication.WebhookCustomerApplicationExternalExecutionAdapter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** 고객 업무 신청 접수 use case와 인증 adapter를 조립합니다. */
@Configuration
@EnableConfigurationProperties({
  CustomerApplicationProperties.class,
  CustomerApplicationExternalExecutionProperties.class
})
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
      CustomerApplicationProperties properties,
      PlatformTransactionManager platformTransactionManager) {
    CustomerApplicationService service =
        new CustomerApplicationService(
            writePort,
            securityVerificationPort,
            referencePort,
            authClock,
            properties.transferLimitChangePolicy());
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      var result = transactionTemplate.execute(status -> service.submit(command));
      if (result == null) {
        throw new IllegalStateException("customer application transaction returned null result");
      }
      return result;
    };
  }

  @Bean
  CustomerApplicationExternalExecutionPort customerApplicationExternalExecutionPort(
      CustomerApplicationExternalExecutionProperties properties) {
    if (properties.enabled()) {
      return new WebhookCustomerApplicationExternalExecutionAdapter(
          customerApplicationExternalExecutionRestClient(properties), properties);
    }
    return (application, actorSubject, requestId) ->
        CustomerApplicationExecutionResult.failed(
            "EXTERNAL_EXECUTION_NOT_CONFIGURED",
            Map.of(
                "applicationType",
                application.applicationType().name(),
                "processingMode",
                CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED.name()));
  }

  @Bean
  CustomerApplicationExecutorPort customerApplicationExecutorPort(
      CustomerTransferLimitPolicyPort transferLimitPolicyPort,
      CustomerApplicationAccountPolicyPort accountPolicyPort,
      CustomerApplicationExternalExecutionPort externalExecutionPort,
      CustomerApplicationProperties properties) {
    return new CustomerApplicationExecutorService(
        transferLimitPolicyPort,
        accountPolicyPort,
        externalExecutionPort,
        properties.transferLimitChangePolicy());
  }

  @Bean
  CustomerApplicationOperationUseCase customerApplicationOperationUseCase(
      CustomerApplicationOperationPort operationPort,
      CustomerApplicationOperationAuditPort operationAuditPort,
      CustomerApplicationExecutorPort executorPort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    CustomerApplicationOperationService service =
        new CustomerApplicationOperationService(
            operationPort, operationAuditPort, executorPort, authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      var result = transactionTemplate.execute(status -> service.apply(command));
      if (result == null) {
        throw new IllegalStateException(
            "customer application operation transaction returned null result");
      }
      return result;
    };
  }

  @Bean
  CustomerApplicationExternalCallbackUseCase customerApplicationExternalCallbackUseCase(
      CustomerApplicationOperationPort operationPort,
      CustomerApplicationOperationAuditPort operationAuditPort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    CustomerApplicationExternalCallbackService service =
        new CustomerApplicationExternalCallbackService(
            operationPort, operationAuditPort, authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      var result = transactionTemplate.execute(status -> service.apply(command));
      if (result == null) {
        throw new IllegalStateException(
            "customer application callback transaction returned null result");
      }
      return result;
    };
  }

  @Bean
  CustomerApplicationSelfServiceUseCase customerApplicationSelfServiceUseCase(
      CustomerApplicationReadPort readPort,
      CustomerApplicationOperationPort operationPort,
      CustomerApplicationOperationAuditPort operationAuditPort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    CustomerApplicationSelfService service =
        new CustomerApplicationSelfService(readPort, operationPort, operationAuditPort, authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return new CustomerApplicationSelfServiceUseCase() {
      @Override
      public List<CustomerApplicationDetails> findByUserId(long userId, int limit) {
        return service.findByUserId(userId, limit);
      }

      @Override
      public CustomerApplicationDetails getByUserIdAndReference(
          long userId, String applicationReference) {
        return service.getByUserIdAndReference(userId, applicationReference);
      }

      @Override
      public CustomerApplicationDetails cancel(
          long userId, String applicationReference, String requestId) {
        return transactionTemplate.execute(
            status -> service.cancel(userId, applicationReference, requestId));
      }
    };
  }

  private RestClient customerApplicationExternalExecutionRestClient(
      CustomerApplicationExternalExecutionProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
