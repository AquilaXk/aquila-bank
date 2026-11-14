package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquilabank.domain.auth.model.GeneratedTotpSecret;
import com.aquilabank.standard.util.Base32Codec;
import com.aquilabank.standard.util.Sha256Hex;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AesTotpSecretManagerTest {

  private static final String TEST_KEY = "test-totp-secret-encryption-key-material";
  private static final Instant NOW = Instant.parse("2026-04-20T10:00:15Z");

  @Test
  void generatesSecretAndMatchesAcrossAllowedDriftWindow() throws Exception {
    AesTotpSecretManager manager =
        new AesTotpSecretManager(
            new SecurityTotpProperties("Aquila Bank", TEST_KEY, 300, 300, 5), "fallback-key");

    GeneratedTotpSecret secret = manager.generate("alice@example.com");

    assertThat(secret.secretKey()).isNotBlank();
    assertThat(secret.secretCiphertext()).startsWith("v2:");
    assertThat(secret.otpauthUri())
        .startsWith("otpauth://totp/Aquila%20Bank%3Aalice%40example.com?secret=")
        .contains("&issuer=Aquila%20Bank&algorithm=SHA1&digits=6&period=30");

    byte[] rawSecret = Base32Codec.decode(secret.secretKey());
    long currentStep = Math.floorDiv(NOW.getEpochSecond(), 30L);

    assertEquals(
        OptionalLong.of(currentStep - 1),
        manager.matchTimeStep(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep - 1),
            NOW));
    assertEquals(
        OptionalLong.of(currentStep),
        manager.matchTimeStep(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep),
            NOW));
    assertEquals(
        OptionalLong.of(currentStep + 1),
        manager.matchTimeStep(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep + 1),
            NOW));
    assertTrue(
        manager
            .matchTimeStep(
                secret.secretCiphertext(),
                secret.secretNonce(),
                totpCodeAtStep(rawSecret, currentStep - 2),
                NOW)
            .isEmpty());
    assertTrue(
        manager
            .matchTimeStep(
                secret.secretCiphertext(),
                secret.secretNonce(),
                totpCodeAtStep(rawSecret, currentStep + 2),
                NOW)
            .isEmpty());
  }

  @Test
  void usesFallbackEncryptionKeyAndDefaultIssuerWhenPropertiesAreBlank() {
    AesTotpSecretManager manager =
        new AesTotpSecretManager(new SecurityTotpProperties(" ", " ", 300, 300, 5), TEST_KEY);

    GeneratedTotpSecret secret = manager.generate("alice");
    assertThat(secret.otpauthUri()).contains("issuer=Aquila%20Bank");

    AesTotpSecretManager nullPropsManager =
        new AesTotpSecretManager(new SecurityTotpProperties(null, null, 300, 300, 5), TEST_KEY);
    assertThat(nullPropsManager.generate("alice").otpauthUri()).contains("issuer=Aquila%20Bank");
  }

  @Test
  void rejectsMissingEncryptionKeyAndInvalidArguments() {
    assertThrows(
        IllegalStateException.class,
        () -> new AesTotpSecretManager(new SecurityTotpProperties("Aquila", " ", 300, 300, 5), ""));
    assertThrows(
        IllegalStateException.class,
        () ->
            new AesTotpSecretManager(
                new SecurityTotpProperties("Aquila", null, 300, 300, 5), null));

    AesTotpSecretManager manager =
        new AesTotpSecretManager(
            new SecurityTotpProperties("Aquila Bank", TEST_KEY, 300, 300, 5), null);
    GeneratedTotpSecret secret = manager.generate("alice");

    assertThrows(IllegalArgumentException.class, () -> manager.generate(null));
    assertThrows(IllegalArgumentException.class, () -> manager.generate(" "));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matchTimeStep(null, secret.secretNonce(), "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matchTimeStep(" ", secret.secretNonce(), "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matchTimeStep(secret.secretCiphertext(), null, "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matchTimeStep(secret.secretCiphertext(), " ", "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.matchTimeStep(secret.secretCiphertext(), secret.secretNonce(), "123456", null));

    assertFalse(
        manager
            .matchTimeStep(secret.secretCiphertext(), secret.secretNonce(), null, NOW)
            .isPresent());
    assertFalse(
        manager
            .matchTimeStep(secret.secretCiphertext(), secret.secretNonce(), "12345", NOW)
            .isPresent());
    assertFalse(
        manager
            .matchTimeStep(secret.secretCiphertext(), secret.secretNonce(), "1234567", NOW)
            .isPresent());
    assertFalse(
        manager
            .matchTimeStep(secret.secretCiphertext(), secret.secretNonce(), "/23456", NOW)
            .isPresent());
    assertFalse(
        manager
            .matchTimeStep(secret.secretCiphertext(), secret.secretNonce(), ":23456", NOW)
            .isPresent());
    assertFalse(
        manager
            .matchTimeStep(secret.secretCiphertext(), secret.secretNonce(), "12345a", NOW)
            .isPresent());

    assertThrows(
        IllegalStateException.class,
        () -> manager.matchTimeStep("aaaa", secret.secretNonce(), "123456", NOW));
    assertThrows(
        IllegalStateException.class,
        () -> manager.matchTimeStep("v2:aaaa", secret.secretNonce(), "123456", NOW));
  }

  @Test
  void decryptsLegacyUnprefixedSha256Ciphertext() throws Exception {
    AesTotpSecretManager manager =
        new AesTotpSecretManager(
            new SecurityTotpProperties("Aquila Bank", TEST_KEY, 300, 300, 5), null);
    byte[] rawSecret =
        new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    byte[] nonce = new byte[] {10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};
    SecretKeySpec legacyKey =
        new SecretKeySpec(Sha256Hex.digest(TEST_KEY.getBytes(StandardCharsets.UTF_8)), "AES");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new GCMParameterSpec(128, nonce));
    String legacyCiphertext =
        Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(rawSecret));
    String encodedNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);

    long step = Math.floorDiv(NOW.getEpochSecond(), 30L);
    String otp = totpCodeAtStep(rawSecret, step);
    assertEquals(
        OptionalLong.of(step), manager.matchTimeStep(legacyCiphertext, encodedNonce, otp, NOW));

    // 동일 키라도 레거시 암호문에 v2 접두사를 붙이면 HKDF 키만 사용하므로 복호화가 실패해야 한다.
    assertThrows(
        IllegalStateException.class,
        () -> manager.matchTimeStep("v2:" + legacyCiphertext, encodedNonce, otp, NOW));
  }

  private static String totpCodeAtStep(byte[] rawSecret, long step) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec(rawSecret, "HmacSHA1"));
    byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
    int offset = digest[digest.length - 1] & 0x0f;
    int binary =
        ((digest[offset] & 0x7f) << 24)
            | ((digest[offset + 1] & 0xff) << 16)
            | ((digest[offset + 2] & 0xff) << 8)
            | (digest[offset + 3] & 0xff);
    return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
  }
}
