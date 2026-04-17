package com.aquilabank.global.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** LISTEN/NOTIFY payload 를 고정 포맷 JSON 으로 유지해 배포 간 호환성을 맞춥니다. */
@Component
public class NotificationSseFanoutSignalCodec {

  private final ObjectMapper objectMapper;

  public NotificationSseFanoutSignalCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encode(NotificationSseFanoutSignal signal) {
    try {
      return objectMapper.writeValueAsString(signal);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("notification SSE fanout signal encode failed", ex);
    }
  }

  public NotificationSseFanoutSignal decode(String payload) {
    try {
      return objectMapper.readValue(payload, NotificationSseFanoutSignal.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("notification SSE fanout signal decode failed", ex);
    }
  }
}
