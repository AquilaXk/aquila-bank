package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.OutboxEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoggingOutboxEventPublisherTest {

  @Test
  void samplesInfoLogsDuringLargeBacklogPublish(CapturedOutput output) {
    LoggingOutboxEventPublisher publisher = new LoggingOutboxEventPublisher();

    for (int i = 1; i <= 1001; i++) {
      publisher.publish(event(i));
    }

    assertThat(output).contains("key-1");
    assertThat(output).contains("key-1000");
    assertThat(output).doesNotContain("key-2");
    assertThat(output).doesNotContain("key-999");
  }

  private OutboxEvent event(long id) {
    Instant now = Instant.parse("2026-04-27T00:00:00Z");
    return new OutboxEvent(
        id, "transaction", "tx-" + id, "TRANSFER_BOOKED", "key-" + id, "{}", 0, now, now);
  }
}
