package com.aquilabank.global.web.notification;

import java.time.Instant;

/** proxy/idle timeout 사이에 연결 유지를 확인하는 heartbeat payload */
public record NotificationStreamHeartbeatResponse(Instant sentAt) {}
