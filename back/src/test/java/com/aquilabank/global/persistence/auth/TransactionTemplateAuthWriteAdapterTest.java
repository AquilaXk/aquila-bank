package com.aquilabank.global.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionTemplateAuthWriteAdapterTest {

  private TransactionTemplate newTemplate() {
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(org.mockito.ArgumentMatchers.any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    return new TransactionTemplate(txManager);
  }

  @Test
  void executesStandardTransactionAndCommitOnAuthFailureModes() {
    TransactionTemplateAuthWriteAdapter standard =
        new TransactionTemplateAuthWriteAdapter(newTemplate(), false, null);
    assertThat(standard.execute(() -> "ok")).isEqualTo("ok");

    TransactionTemplateAuthWriteAdapter commitOnFailure =
        new TransactionTemplateAuthWriteAdapter(newTemplate(), true, "unexpected null");
    assertThat(commitOnFailure.execute(() -> "ok")).isEqualTo("ok");

    InvalidCredentialsException thrown =
        assertThrows(
            InvalidCredentialsException.class,
            () ->
                commitOnFailure.execute(
                    () -> {
                      throw new InvalidCredentialsException("failed");
                    }));
    assertThat(thrown).hasMessage("failed");

    IllegalStateException nullEx =
        assertThrows(IllegalStateException.class, () -> commitOnFailure.execute(() -> null));
    assertThat(nullEx).hasMessage("unexpected null");

    TransactionTemplateAuthWriteAdapter nullable =
        new TransactionTemplateAuthWriteAdapter(newTemplate(), true, null);
    assertThat(nullable.<String>execute(() -> null)).isNull();
  }
}
