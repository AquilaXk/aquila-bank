package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationStreamHeartbeatResponseTest {

  @Test
  void exposesSentAtRecordContract() {
    Instant sentAt = Instant.parse("2026-04-20T09:30:00Z");
    NotificationStreamHeartbeatResponse response = new NotificationStreamHeartbeatResponse(sentAt);

    assertThat(response.sentAt()).isEqualTo(sentAt);
    assertThat(response).isEqualTo(new NotificationStreamHeartbeatResponse(sentAt));
    assertThat(response.hashCode())
        .isEqualTo(new NotificationStreamHeartbeatResponse(sentAt).hashCode());
    assertThat(response.toString()).contains("NotificationStreamHeartbeatResponse");
  }
}
