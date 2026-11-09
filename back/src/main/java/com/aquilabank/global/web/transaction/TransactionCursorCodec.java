package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** keyset cursor의 무결성 검증 및 직렬화 비용 최소화를 위한 binary HMAC transport codec */
public final class TransactionCursorCodec {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String DEFAULT_HMAC_SECRET = "aquila-bank-transaction-cursor-hmac-v1";
  private static final int PAYLOAD_BYTES = Long.BYTES + Integer.BYTES + Long.BYTES;
  private static final int HMAC_BYTES = 32;
  private static final int TOTAL_BYTES = PAYLOAD_BYTES + HMAC_BYTES;
  private static final int MAX_NANO = 999_999_999;
  private static final VarHandle LONG_VIEW =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
  private static final SecretKeySpec HMAC_KEY =
      resolveHmacKey(
          System.getProperty("aquila.transaction.cursor.hmac-secret"),
          System.getenv("AQUILA_TRANSACTION_CURSOR_HMAC_SECRET"));
  private static final ThreadLocal<Mac> THREAD_LOCAL_MAC =
      ThreadLocal.withInitial(TransactionCursorCodec::createInitializedMac);
  private static final ThreadLocal<byte[]> THREAD_LOCAL_MAC_SCRATCH =
      ThreadLocal.withInitial(() -> new byte[HMAC_BYTES]);
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private TransactionCursorCodec() {}

  static SecretKeySpec resolveHmacKey(String propertySecret, String envSecret) {
    String resolved =
        propertySecret != null && !propertySecret.isBlank()
            ? propertySecret
            : (envSecret != null && !envSecret.isBlank() ? envSecret : DEFAULT_HMAC_SECRET);
    return new SecretKeySpec(resolved.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }

  public static String encode(TransactionCursor cursor) {
    ByteBuffer buffer = ByteBuffer.allocate(TOTAL_BYTES);
    buffer.putLong(cursor.bookedAt().getEpochSecond());
    buffer.putInt(cursor.bookedAt().getNano());
    buffer.putLong(cursor.id());
    byte[] raw = buffer.array();
    signInto(raw);
    return URL_ENCODER.encodeToString(raw);
  }

  public static TransactionCursor decode(String rawCursor) {
    if (rawCursor == null || rawCursor.isBlank()) {
      throw new IllegalArgumentException("cursor format is invalid");
    }
    try {
      byte[] raw = URL_DECODER.decode(rawCursor);
      if (raw.length != TOTAL_BYTES || !verifyMac(raw)) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      ByteBuffer buffer = ByteBuffer.wrap(raw, 0, PAYLOAD_BYTES);
      long epochSecond = buffer.getLong();
      int nano = buffer.getInt();
      long id = buffer.getLong();
      if (nano < 0 || nano > MAX_NANO || id <= 0) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return new TransactionCursor(Instant.ofEpochSecond(epochSecond, nano), id);
    } catch (RuntimeException | GeneralSecurityException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }

  private static void signInto(byte[] raw) {
    try {
      Mac mac = THREAD_LOCAL_MAC.get();
      mac.reset();
      mac.update(raw, 0, PAYLOAD_BYTES);
      mac.doFinal(raw, PAYLOAD_BYTES);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to sign transaction cursor", ex);
    }
  }

  private static boolean verifyMac(byte[] raw) throws GeneralSecurityException {
    Mac mac = THREAD_LOCAL_MAC.get();
    byte[] scratch = THREAD_LOCAL_MAC_SCRATCH.get();
    mac.reset();
    mac.update(raw, 0, PAYLOAD_BYTES);
    mac.doFinal(scratch, 0);
    // 32바이트 HMAC을 64비트 워드 4개 XOR 누적으로 상수 시간 비교해 배열 복사 할당을 제거합니다.
    long w0 = (long) LONG_VIEW.get(raw, 20);
    long w1 = (long) LONG_VIEW.get(raw, 28);
    long w2 = (long) LONG_VIEW.get(raw, 36);
    long w3 = (long) LONG_VIEW.get(raw, 44);
    long s0 = (long) LONG_VIEW.get(scratch, 0);
    long s1 = (long) LONG_VIEW.get(scratch, 8);
    long s2 = (long) LONG_VIEW.get(scratch, 16);
    long s3 = (long) LONG_VIEW.get(scratch, 24);
    long diff = (w0 ^ s0) | (w1 ^ s1) | (w2 ^ s2) | (w3 ^ s3);
    return diff == 0L;
  }

  private static Mac createInitializedMac() {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(HMAC_KEY);
      return mac;
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to initialize HmacSHA256 for cursor codec", ex);
    }
  }
}
