package com.aquilabank.domain.auth.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Base64;

public record AuthStatusChangeAuditCursor(Instant createdAt, long auditId) {

  private static final int PAYLOAD_BYTES = Long.BYTES + Integer.BYTES + Long.BYTES;
  private static final int MAX_ENCODED_LENGTH = 32;
  private static final int MAX_NANO = 999_999_999;
  private static final VarHandle LONG_VIEW =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
  private static final VarHandle INT_VIEW =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  public AuthStatusChangeAuditCursor {
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (auditId <= 0) {
      throw new IllegalArgumentException("auditId must be positive");
    }
  }

  public String encode() {
    byte[] payload = new byte[PAYLOAD_BYTES];
    LONG_VIEW.set(payload, 0, createdAt.getEpochSecond());
    INT_VIEW.set(payload, Long.BYTES, createdAt.getNano());
    LONG_VIEW.set(payload, Long.BYTES + Integer.BYTES, auditId);
    return URL_ENCODER.encodeToString(payload);
  }

  public static AuthStatusChangeAuditCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    if (cursor.length() > MAX_ENCODED_LENGTH) {
      throw new IllegalArgumentException("cursor is invalid");
    }
    try {
      byte[] payload = URL_DECODER.decode(cursor);
      if (payload.length != PAYLOAD_BYTES) {
        throw new IllegalArgumentException("cursor is invalid");
      }
      long epochSecond = (long) LONG_VIEW.get(payload, 0);
      int nano = (int) INT_VIEW.get(payload, Long.BYTES);
      long auditId = (long) LONG_VIEW.get(payload, Long.BYTES + Integer.BYTES);
      if (nano < 0 || nano > MAX_NANO) {
        throw new IllegalArgumentException("cursor is invalid");
      }
      return new AuthStatusChangeAuditCursor(Instant.ofEpochSecond(epochSecond, nano), auditId);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor is invalid", ex);
    }
  }
}
