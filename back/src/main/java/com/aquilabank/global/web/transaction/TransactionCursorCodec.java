package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** keyset cursor의 무결성 검증 및 직렬화 비용 최소화를 위한 binary HMAC transport codec */
public final class TransactionCursorCodec {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int PAYLOAD_BYTES = Long.BYTES + Integer.BYTES + Long.BYTES;
  private static final int HMAC_BYTES = 32;
  private static final int TOTAL_BYTES = PAYLOAD_BYTES + HMAC_BYTES;
  private static final int MAX_NANO = 999_999_999;
  private static final SecretKeySpec HMAC_KEY =
      new SecretKeySpec(
          "aquila-bank-transaction-cursor-hmac-v1".getBytes(StandardCharsets.UTF_8),
          HMAC_ALGORITHM);
  private static final ThreadLocal<Mac> THREAD_LOCAL_MAC =
      ThreadLocal.withInitial(TransactionCursorCodec::createInitializedMac);
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private TransactionCursorCodec() {}

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
      if (raw.length != TOTAL_BYTES) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      byte[] expectedMac = computeMac(raw);
      byte[] actualMac = Arrays.copyOfRange(raw, PAYLOAD_BYTES, TOTAL_BYTES);
      if (!MessageDigest.isEqual(expectedMac, actualMac)) {
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
    } catch (RuntimeException ex) {
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

  private static byte[] computeMac(byte[] raw) {
    Mac mac = THREAD_LOCAL_MAC.get();
    mac.reset();
    mac.update(raw, 0, PAYLOAD_BYTES);
    return mac.doFinal();
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
