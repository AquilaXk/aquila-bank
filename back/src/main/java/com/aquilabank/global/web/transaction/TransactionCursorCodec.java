package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.global.security.HkdfSha256;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** keyset cursor의 무결성 검증 및 직렬화 비용 최소화를 위한 binary HMAC transport codec */
public final class TransactionCursorCodec {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String DEFAULT_HMAC_SECRET = "aquila-bank-transaction-cursor-hmac-v1";
  static final String HKDF_CONTEXT_INFO = "aquila-bank:transaction-cursor:hmac-sha256:v1";
  private static final int PAYLOAD_BYTES = Long.BYTES + Long.BYTES + Integer.BYTES + Long.BYTES;
  private static final int HMAC_BYTES = 32;
  private static final int TOTAL_BYTES = PAYLOAD_BYTES + HMAC_BYTES;
  private static final int MAX_ENCODED_LENGTH = 128;
  private static final int MAX_NANO = 999_999_999;
  private static final VarHandle LONG_VIEW =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
  private static volatile SecretKeySpec activeHmacKey = bootstrapHmacKey();
  private static final ThreadLocal<Object[]> THREAD_LOCAL_MAC_SLOT =
      ThreadLocal.withInitial(() -> new Object[2]);
  private static final ThreadLocal<byte[]> THREAD_LOCAL_MAC_SCRATCH =
      ThreadLocal.withInitial(() -> new byte[HMAC_BYTES]);
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private TransactionCursorCodec() {}

  private static SecretKeySpec bootstrapHmacKey() {
    return resolveHmacKeyOrNull(
        System.getProperty("transaction.cursor.hmac-secret"),
        System.getProperty("aquila.transaction.cursor.hmac-secret"),
        System.getenv("AQUILA_TRANSACTION_CURSOR_HMAC_SECRET"),
        firstNonBlank(
            System.getProperty("security.jwt.secret"), System.getenv("SECURITY_JWT_SECRET"), null),
        System.getProperty("spring.profiles.active"),
        System.getenv("SPRING_PROFILES_ACTIVE"));
  }

  public static void configure(
      String primarySecret,
      String legacyPropertySecret,
      String fallbackJwtSecret,
      String activeProfiles) {
    activeHmacKey =
        resolveHmacKey(
            primarySecret, legacyPropertySecret, null, fallbackJwtSecret, activeProfiles, null);
  }

  static SecretKeySpec resolveHmacKey(
      String primarySecret,
      String legacyPropertySecret,
      String envSecret,
      String fallbackJwtSecret,
      String activeProfilesProperty,
      String activeProfilesEnv) {
    SecretKeySpec resolvedKey =
        resolveHmacKeyOrNull(
            primarySecret,
            legacyPropertySecret,
            envSecret,
            fallbackJwtSecret,
            activeProfilesProperty,
            activeProfilesEnv);
    if (resolvedKey == null) {
      throw new IllegalStateException(
          "Transaction cursor HMAC secret or security.jwt.secret must be configured outside dev/test profiles");
    }
    return resolvedKey;
  }

  static SecretKeySpec resolveHmacKeyOrNull(
      String primarySecret,
      String legacyPropertySecret,
      String envSecret,
      String fallbackJwtSecret,
      String activeProfilesProperty,
      String activeProfilesEnv) {
    String explicitSecret = firstNonBlank(primarySecret, legacyPropertySecret, envSecret);
    if (explicitSecret != null) {
      return new SecretKeySpec(explicitSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }
    if (fallbackJwtSecret != null && !fallbackJwtSecret.isBlank()) {
      byte[] derived =
          HkdfSha256.deriveBytes(
              fallbackJwtSecret.getBytes(StandardCharsets.UTF_8),
              null,
              HKDF_CONTEXT_INFO,
              HMAC_BYTES);
      try {
        return new SecretKeySpec(derived, HMAC_ALGORITHM);
      } finally {
        Arrays.fill(derived, (byte) 0);
      }
    }
    if (isDevOrTestProfile(activeProfilesProperty, activeProfilesEnv)) {
      return new SecretKeySpec(
          DEFAULT_HMAC_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }
    return null;
  }

  private static String firstNonBlank(String first, String second, String third) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    if (third != null && !third.isBlank()) {
      return third;
    }
    return null;
  }

  private static boolean isDevOrTestProfile(
      String activeProfilesProperty, String activeProfilesEnv) {
    String profiles =
        activeProfilesProperty != null && !activeProfilesProperty.isBlank()
            ? activeProfilesProperty
            : activeProfilesEnv;
    if (profiles == null || profiles.isBlank()) {
      return false;
    }
    int start = 0;
    int length = profiles.length();
    while (start <= length) {
      int comma = profiles.indexOf(',', start);
      int end = comma < 0 ? length : comma;
      String token = profiles.substring(start, end).trim();
      if ("dev".equals(token) || "test".equals(token) || "local".equals(token)) {
        return true;
      }
      if (comma < 0) {
        break;
      }
      start = comma + 1;
    }
    return false;
  }

  public static String encode(long accountId, TransactionCursor cursor) {
    SecretKeySpec hmacKey = requireHmacKey();
    if (accountId <= 0 || cursor == null) {
      throw new IllegalArgumentException("accountId and cursor must be valid");
    }
    Mac mac = obtainInitializedMac(hmacKey);
    ByteBuffer buffer = ByteBuffer.allocate(TOTAL_BYTES);
    buffer.putLong(accountId);
    buffer.putLong(cursor.bookedAt().getEpochSecond());
    buffer.putInt(cursor.bookedAt().getNano());
    buffer.putLong(cursor.id());
    byte[] raw = buffer.array();
    signInto(mac, raw);
    return URL_ENCODER.encodeToString(raw);
  }

  public static TransactionCursor decode(long expectedAccountId, String rawCursor) {
    SecretKeySpec hmacKey = requireHmacKey();
    if (expectedAccountId <= 0
        || rawCursor == null
        || rawCursor.isBlank()
        || rawCursor.length() > MAX_ENCODED_LENGTH) {
      throw new IllegalArgumentException("cursor format is invalid");
    }
    Mac mac = obtainInitializedMac(hmacKey);
    try {
      byte[] raw = URL_DECODER.decode(rawCursor);
      if (raw.length != TOTAL_BYTES || !verifyMac(mac, raw)) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      ByteBuffer buffer = ByteBuffer.wrap(raw, 0, PAYLOAD_BYTES);
      long accountId = buffer.getLong();
      long epochSecond = buffer.getLong();
      int nano = buffer.getInt();
      long id = buffer.getLong();
      if (accountId != expectedAccountId || nano < 0 || nano > MAX_NANO || id <= 0) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return new TransactionCursor(Instant.ofEpochSecond(epochSecond, nano), id);
    } catch (RuntimeException | GeneralSecurityException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }

  private static SecretKeySpec requireHmacKey() {
    SecretKeySpec key = activeHmacKey;
    if (key == null) {
      throw new IllegalStateException(
          "Transaction cursor HMAC secret or security.jwt.secret must be configured outside dev/test profiles");
    }
    return key;
  }

  private static void signInto(Mac mac, byte[] raw) {
    try {
      mac.reset();
      mac.update(raw, 0, PAYLOAD_BYTES);
      mac.doFinal(raw, PAYLOAD_BYTES);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to sign transaction cursor", ex);
    }
  }

  private static boolean verifyMac(Mac mac, byte[] raw) throws GeneralSecurityException {
    byte[] scratch = THREAD_LOCAL_MAC_SCRATCH.get();
    mac.reset();
    mac.update(raw, 0, PAYLOAD_BYTES);
    mac.doFinal(scratch, 0);
    // 계좌 바인딩(28바이트 payload) 뒤 32바이트 HMAC을 64비트 워드 4개 XOR 누적으로 상수 시간 비교합니다.
    long w0 = (long) LONG_VIEW.get(raw, 28);
    long w1 = (long) LONG_VIEW.get(raw, 36);
    long w2 = (long) LONG_VIEW.get(raw, 44);
    long w3 = (long) LONG_VIEW.get(raw, 52);
    long s0 = (long) LONG_VIEW.get(scratch, 0);
    long s1 = (long) LONG_VIEW.get(scratch, 8);
    long s2 = (long) LONG_VIEW.get(scratch, 16);
    long s3 = (long) LONG_VIEW.get(scratch, 24);
    long diff = (w0 ^ s0) | (w1 ^ s1) | (w2 ^ s2) | (w3 ^ s3);
    return diff == 0L;
  }

  private static Mac obtainInitializedMac(SecretKeySpec hmacKey) {
    Object[] slot = THREAD_LOCAL_MAC_SLOT.get();
    if (slot[0] == hmacKey && slot[1] != null) {
      return (Mac) slot[1];
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(hmacKey);
      slot[0] = hmacKey;
      slot[1] = mac;
      return mac;
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to initialize HmacSHA256 for cursor codec", ex);
    }
  }
}
