package com.aquilabank.global.web.notification;

import java.time.Instant;

/** stream 연결 직후 client 가 handshake 상태를 확인하는 최소 payload */
public record NotificationStreamConnectedResponse(Instant connectedAt) {}
