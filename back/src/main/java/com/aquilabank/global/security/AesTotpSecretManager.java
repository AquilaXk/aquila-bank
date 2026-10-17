package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.GeneratedTotpSecret;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.standard.util.Base32Codec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** TOTP secret은 AES-GCM으로 보호 저장하고 code 계산은 RFC 6238 기본값으로 고정합니다. */
public class AesTotpSecretManager implements TotpSecretPort {

  private static final int SECRET_BYTE_LENGTH = 20;
  private static final int NONCE_BYTE_LENGTH = 12;
  private static final int GCM_TAG_BIT_LENGTH = 128;
  private static final int CODE_DIGITS = 6;
  private static final int OTP_MODULUS = 1_000_000;
  private static final long TIME_STEP_SECONDS = 30L;
  private static final int ALLOWED_DRIFT_STEPS = 1;

  private final SecretKeySpec secretKeySpec;
  private final String issuer;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesTotpSecretManager(
      SecurityTotpProperties securityTotpProperties, String fallbackSecretEncryptionKey) {
    String secretEncryptionKey = securityTotpProperties.secretEncryptionKey();
    if (secretEncryptionKey == null || secretEncryptionKey.isBlank()) {
      secretEncryptionKey = fallbackSecretEncryptionKey;
    }
    if (secretEncryptionKey == null || secretEncryptionKey.isBlank()) {
      throw new IllegalStateException("security.totp.secret-encryption-key is required");
    }
    this.secretKeySpec =
        new SecretKeySpec(sha256(secretEncryptionKey.getBytes(StandardCharsets.UTF_8)), "AES");
    this.issuer =
        securityTotpProperties.issuer() == null || securityTotpProperties.issuer().isBlank()
            ? "Aquila Bank"
            : securityTotpProperties.issuer().trim();
  }

  @Override
  public GeneratedTotpSecret generate(String loginId) {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    byte[] rawSecret = new byte[SECRET_BYTE_LENGTH];
    secureRandom.nextBytes(rawSecret);
    try {
      byte[] nonce = new byte[NONCE_BYTE_LENGTH];
      secureRandom.nextBytes(nonce);

      String secretKey = Base32Codec.encode(rawSecret);
      String secretCiphertext = encode(encrypt(rawSecret, nonce));
      String secretNonce = encode(nonce);
      return new GeneratedTotpSecret(
          secretKey, buildOtpauthUri(loginId, secretKey), secretCiphertext, secretNonce);
    } finally {
      Arrays.fill(rawSecret, (byte) 0);
    }
  }

  @Override
  public boolean matches(
      String secretCiphertext, String secretNonce, String totpCode, Instant now) {
    if (secretCiphertext == null || secretCiphertext.isBlank()) {
      throw new IllegalArgumentException("secretCiphertext is required");
    }
    if (secretNonce == null || secretNonce.isBlank()) {
      throw new IllegalArgumentException("secretNonce is required");
    }
    int candidateOtp = parseSixDigitCode(totpCode);
    if (candidateOtp < 0) {
      return false;
    }
    if (now == null) {
      throw new IllegalArgumentException("now is required");
    }

    byte[] rawSecret = decrypt(decode(secretCiphertext), decode(secretNonce));
    try {
      Mac mac = initHmacSha1(rawSecret);
      byte[] counterBytes = new byte[Long.BYTES];
      long counter = now.getEpochSecond() / TIME_STEP_SECONDS;
      boolean matched = false;
      // 드리프트 윈도우[-1, 0, +1] 전체를 상수 시간에 평가해 타이밍 부채널을 방지합니다.
      for (long step = -ALLOWED_DRIFT_STEPS; step <= ALLOWED_DRIFT_STEPS; step++) {
        int expectedOtp = generateOtp(mac, counterBytes, counter + step);
        matched |= (expectedOtp == candidateOtp);
      }
      return matched;
    } finally {
      Arrays.fill(rawSecret, (byte) 0);
    }
  }

  private static int parseSixDigitCode(String totpCode) {
    if (totpCode == null || totpCode.length() != CODE_DIGITS) {
      return -1;
    }
    int parsed = 0;
    for (int i = 0; i < CODE_DIGITS; i++) {
      char ch = totpCode.charAt(i);
      if (ch < '0' || ch > '9') {
        return -1;
      }
      parsed = parsed * 10 + (ch - '0');
    }
    return parsed;
  }

  private Mac initHmacSha1(byte[] rawSecret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(rawSecret, "HmacSHA1"));
      return mac;
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("TOTP code generation failed", ex);
    }
  }

  private int generateOtp(Mac mac, byte[] counterBytes, long counter) {
    long value = counter;
    for (int i = Long.BYTES - 1; i >= 0; i--) {
      counterBytes[i] = (byte) (value & 0xffL);
      value >>>= 8;
    }
    byte[] digest = mac.doFinal(counterBytes);
    int offset = digest[digest.length - 1] & 0x0f;
    int binary =
        ((digest[offset] & 0x7f) << 24)
            | ((digest[offset + 1] & 0xff) << 16)
            | ((digest[offset + 2] & 0xff) << 8)
            | (digest[offset + 3] & 0xff);
    return binary % OTP_MODULUS;
  }

  private byte[] encrypt(byte[] rawSecret, byte[] nonce) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, nonce));
      return cipher.doFinal(rawSecret);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("TOTP secret encryption failed", ex);
    }
  }

  private byte[] decrypt(byte[] ciphertext, byte[] nonce) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, nonce));
      return cipher.doFinal(ciphertext);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("TOTP secret decryption failed", ex);
    }
  }

  private String buildOtpauthUri(String loginId, String secretKey) {
    String label = encodeUriPart(issuer + ":" + loginId);
    return "otpauth://totp/"
        + label
        + "?secret="
        + encodeUriPart(secretKey)
        + "&issuer="
        + encodeUriPart(issuer)
        + "&algorithm=SHA1&digits="
        + CODE_DIGITS
        + "&period="
        + TIME_STEP_SECONDS;
  }

  private String encodeUriPart(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private byte[] decode(String value) {
    return Base64.getUrlDecoder().decode(value);
  }

  private byte[] sha256(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
