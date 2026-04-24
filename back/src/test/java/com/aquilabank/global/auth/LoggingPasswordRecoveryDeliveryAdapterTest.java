package com.aquilabank.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryResult;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoggingPasswordRecoveryDeliveryAdapterTest {

  @Test
  void logsDeliveryMetadataWithoutPlainRecoveryToken(CapturedOutput output) {
    LoggingPasswordRecoveryDeliveryAdapter adapter = new LoggingPasswordRecoveryDeliveryAdapter();

    PasswordRecoveryDeliveryResult result =
        adapter.deliver(
            new PasswordRecoveryDeliveryCommand(
                "request-1",
                7L,
                VerifiedContactChannel.EMAIL,
                "alice@example.com",
                "plain-recovery-token",
                Instant.parse("2026-04-22T00:15:00Z"),
                Instant.parse("2026-04-22T00:00:00Z")));

    assertThat(result.sent()).isTrue();
    assertThat(output).contains("request-1");
    assertThat(output).contains("userId=7");
    assertThat(output).doesNotContain("plain-recovery-token");
  }
}
