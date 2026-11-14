package com.aquilabank.global.web.account;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Base64;

/** account list cursor는 account_id keyset 값을 8바이트 big-endian 이진 페이로드로 인코딩합니다. */
final class AccountListCursorCodec {

  private static final int PAYLOAD_BYTES = Long.BYTES;
  private static final int MAX_ENCODED_LENGTH = 16;
  private static final VarHandle LONG_VIEW =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private AccountListCursorCodec() {}

  static String encode(long accountId) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    byte[] payload = new byte[PAYLOAD_BYTES];
    LONG_VIEW.set(payload, 0, accountId);
    return URL_ENCODER.encodeToString(payload);
  }

  static long decode(String cursor) {
    if (cursor == null || cursor.isBlank() || cursor.length() > MAX_ENCODED_LENGTH) {
      throw new IllegalArgumentException("cursor format is invalid");
    }
    try {
      byte[] payload = URL_DECODER.decode(cursor);
      if (payload.length != PAYLOAD_BYTES) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      long accountId = (long) LONG_VIEW.get(payload, 0);
      if (accountId <= 0) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return accountId;
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }
}
