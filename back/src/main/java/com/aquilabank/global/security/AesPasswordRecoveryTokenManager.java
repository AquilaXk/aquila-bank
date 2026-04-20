package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.GeneratedPasswordRecoveryToken;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** recovery token은 평문 노출 없이 exact lookup hash와 AES-GCM 저장값을 함께 만듭니다. */
public class AesPasswordRecoveryTokenManager implements PasswordRecoverySecretPort {

  private static final int TOKEN_BYTE_LENGTH = 32;
  private static final int NONCE_BYTE_LENGTH = 12;
  private static final int GCM_TAG_BIT_LENGTH = 128;

  private final SecretKeySpec secretKeySpec;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesPasswordRecoveryTokenManager(
      PasswordRecoveryProperties passwordRecoveryProperties, String fallbackSecret) {
    String secretEncryptionKey = passwordRecoveryProperties.secretEncryptionKey();
    if (secretEncryptionKey == null || secretEncryptionKey.isBlank()) {
      secretEncryptionKey = fallbackSecret;
    }
    if (secretEncryptionKey == null || secretEncryptionKey.isBlank()) {
      throw new IllegalStateException("security.password-recovery.secret-encryption-key is required");
    }
    this.secretKeySpec =
        new SecretKeySpec(sha256(secretEncryptionKey.getBytes(StandardCharsets.UTF_8)), "AES");
  }

  @Override
  public GeneratedPasswordRecoveryToken generate() {
    byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(tokenBytes);

    String plainToken = encode(tokenBytes);
    byte[] plainTokenBytes = plainToken.getBytes(StandardCharsets.UTF_8);
    byte[] nonce = new byte[NONCE_BYTE_LENGTH];
    secureRandom.nextBytes(nonce);

    return new GeneratedPasswordRecoveryToken(
        plainToken,
        sha256Hex(plainTokenBytes),
        encode(encrypt(plainTokenBytes, nonce)),
        encode(nonce));
  }

  @Override
  public String hash(String plainToken) {
    if (plainToken == null || plainToken.isBlank()) {
      throw new IllegalArgumentException("plainToken is required");
    }
    return sha256Hex(plainToken.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String reveal(String tokenCiphertext, String tokenNonce) {
    if (tokenCiphertext == null || tokenCiphertext.isBlank()) {
      throw new IllegalArgumentException("tokenCiphertext is required");
    }
    if (tokenNonce == null || tokenNonce.isBlank()) {
      throw new IllegalArgumentException("tokenNonce is required");
    }
    return new String(decrypt(decode(tokenCiphertext), decode(tokenNonce)), StandardCharsets.UTF_8);
  }

  private byte[] encrypt(byte[] plainTokenBytes, byte[] nonce) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, nonce));
      return cipher.doFinal(plainTokenBytes);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("password recovery token encryption failed", ex);
    }
  }

  private byte[] decrypt(byte[] tokenCiphertext, byte[] nonce) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, nonce));
      return cipher.doFinal(tokenCiphertext);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("password recovery token decryption failed", ex);
    }
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
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private String sha256Hex(byte[] value) {
    return HexFormat.of().formatHex(sha256(value));
  }
}
