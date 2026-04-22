package com.aquilabank.global.config;

import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertUseCase;
import com.aquilabank.domain.auth.usecase.UserBootstrapUseCase;
import com.aquilabank.domain.bootstrap.usecase.BootstrapBulkImportService;
import com.aquilabank.domain.bootstrap.usecase.BootstrapBulkImportUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** bulk import는 요청 하나를 같은 DB transaction으로 묶어 중간 실패 partial write를 막습니다. */
@Configuration
public class BootstrapBulkImportConfiguration {

  @Bean
  BootstrapBulkImportUseCase bootstrapBulkImportUseCase(
      AccountBootstrapUseCase accountBootstrapUseCase,
      UserBootstrapUseCase userBootstrapUseCase,
      UserAccountMembershipUpsertUseCase userAccountMembershipUpsertUseCase,
      PlatformTransactionManager platformTransactionManager) {
    BootstrapBulkImportService service =
        new BootstrapBulkImportService(
            accountBootstrapUseCase, userBootstrapUseCase, userAccountMembershipUpsertUseCase);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> transactionTemplate.execute(status -> service.importItems(command));
  }
}
