package com.aquilabank.global.config;

import com.aquilabank.domain.account.port.AccountPersonalizationPort;
import com.aquilabank.domain.account.usecase.AccountPersonalizationService;
import com.aquilabank.domain.account.usecase.AccountPersonalizationUseCase;
import com.aquilabank.domain.ledger.port.SavedRecipientPort;
import com.aquilabank.domain.ledger.port.ScheduledTransferPort;
import com.aquilabank.domain.ledger.usecase.SavedRecipientService;
import com.aquilabank.domain.ledger.usecase.SavedRecipientUseCase;
import com.aquilabank.domain.ledger.usecase.ScheduledTransferService;
import com.aquilabank.domain.ledger.usecase.ScheduledTransferUseCase;
import com.aquilabank.domain.transaction.port.TransactionAnnotationPort;
import com.aquilabank.domain.transaction.usecase.TransactionAnnotationService;
import com.aquilabank.domain.transaction.usecase.TransactionAnnotationUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 수취인 주소록, 계좌 개인화/포켓, 거래 분류·메모, 예약·자동이체 유스케이스 조립 설정입니다. */
@Configuration
public class BankingUxExtensionsConfiguration {

  @Bean
  SavedRecipientUseCase savedRecipientUseCase(SavedRecipientPort savedRecipientPort) {
    return new SavedRecipientService(savedRecipientPort, Clock.systemUTC());
  }

  @Bean
  ScheduledTransferUseCase scheduledTransferUseCase(ScheduledTransferPort scheduledTransferPort) {
    return new ScheduledTransferService(scheduledTransferPort, Clock.systemUTC());
  }

  @Bean
  AccountPersonalizationUseCase accountPersonalizationUseCase(
      AccountPersonalizationPort personalizationPort) {
    return new AccountPersonalizationService(personalizationPort, Clock.systemUTC());
  }

  @Bean
  TransactionAnnotationUseCase transactionAnnotationUseCase(
      TransactionAnnotationPort annotationPort) {
    return new TransactionAnnotationService(annotationPort, Clock.systemUTC());
  }
}
