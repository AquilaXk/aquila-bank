package com.aquilabank.global.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 5869 HKDF-SHA256으로 단일 마스터 시크릿에서 용도별 암호키를 파생합니다. */
public final class HkdfSha256 {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int HASH_LENGTH_BYTES = 32;
  private static final int MAX_OUTPUT_BYTES = 255 * HASH_LENGTH_BYTES;

  private HkdfSha256() {}

  public static SecretKeySpec deriveAes256Key(String masterSecret, String info) {
    if (masterSecret == null || masterSecret.isBlank()) {
      throw new IllegalArgumentException("masterSecret is required");
    }
    byte[] derived =
        deriveBytes(masterSecret.getBytes(StandardCharsets.UTF_8), null, info, HASH_LENGTH_BYTES);
    try {
      return new SecretKeySpec(derived, "AES");
    } finally {
      Arrays.fill(derived, (byte) 0);
    }
  }

  public static byte[] deriveBytes(byte[] ikm, byte[] salt, String info, int outputLengthBytes) {
    if (info == null || info.isEmpty()) {
      throw new IllegalArgumentException("info must not be empty");
    }
    return deriveBytes(ikm, salt, info.getBytes(StandardCharsets.UTF_8), outputLengthBytes);
  }

  public static byte[] deriveBytes(
      byte[] ikm, byte[] salt, byte[] infoBytes, int outputLengthBytes) {
    if (ikm == null || ikm.length == 0) {
      throw new IllegalArgumentException("ikm must not be empty");
    }
    if (infoBytes == null || infoBytes.length == 0) {
      throw new IllegalArgumentException("info must not be empty");
    }
    if (outputLengthBytes <= 0 || outputLengthBytes > MAX_OUTPUT_BYTES) {
      throw new IllegalArgumentException("outputLengthBytes must be between 1 and 8160");
    }
    byte[] effectiveSalt = (salt == null || salt.length == 0) ? new byte[HASH_LENGTH_BYTES] : salt;
    byte[] prk = extract(effectiveSalt, ikm);
    byte[] okm = new byte[outputLengthBytes];
    byte[] previous = new byte[0];
    try {
      Mac mac = createMac(prk);
      int offset = 0;
      int counter = 1;
      while (offset < outputLengthBytes) {
        mac.reset();
        if (previous.length > 0) {
          mac.update(previous);
        }
        mac.update(infoBytes);
        mac.update((byte) counter);
        byte[] block = mac.doFinal();
        int copyLength = Math.min(block.length, outputLengthBytes - offset);
        System.arraycopy(block, 0, okm, offset, copyLength);
        offset += copyLength;
        Arrays.fill(previous, (byte) 0);
        previous = block;
        counter++;
      }
      return okm;
    } finally {
      Arrays.fill(prk, (byte) 0);
      Arrays.fill(previous, (byte) 0);
    }
  }

  private static byte[] extract(byte[] salt, byte[] ikm) {
    return createMac(salt).doFinal(ikm);
  }

  private static Mac createMac(byte[] key) {
    return createMac(HMAC_ALGORITHM, key);
  }

  static Mac createMac(String algorithm, byte[] key) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      return mac;
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("HKDF-SHA256 MAC initialization failed", ex);
    }
  }
}
