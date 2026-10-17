package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquilabank.domain.auth.model.GeneratedTotpSecret;
import com.aquilabank.standard.util.Base32Codec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
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
    assertThat(secret.otpauthUri())
        .startsWith("otpauth://totp/Aquila%20Bank%3Aalice%40example.com?secret=")
        .contains("&issuer=Aquila%20Bank&algorithm=SHA1&digits=6&period=30");

    byte[] rawSecret = Base32Codec.decode(secret.secretKey());
    long currentStep = NOW.getEpochSecond() / 30L;

    assertTrue(
        manager.matches(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep - 1),
            NOW));
    assertTrue(
        manager.matches(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep),
            NOW));
    assertTrue(
        manager.matches(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep + 1),
            NOW));
    assertFalse(
        manager.matches(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep - 2),
            NOW));
    assertFalse(
        manager.matches(
            secret.secretCiphertext(),
            secret.secretNonce(),
            totpCodeAtStep(rawSecret, currentStep + 2),
            NOW));
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
        () -> manager.matches(null, secret.secretNonce(), "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matches(" ", secret.secretNonce(), "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matches(secret.secretCiphertext(), null, "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matches(secret.secretCiphertext(), " ", "123456", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> manager.matches(secret.secretCiphertext(), secret.secretNonce(), "123456", null));

    assertFalse(manager.matches(secret.secretCiphertext(), secret.secretNonce(), null, NOW));
    assertFalse(manager.matches(secret.secretCiphertext(), secret.secretNonce(), "12345", NOW));
    assertFalse(manager.matches(secret.secretCiphertext(), secret.secretNonce(), "1234567", NOW));
    assertFalse(manager.matches(secret.secretCiphertext(), secret.secretNonce(), "/23456", NOW));
    assertFalse(manager.matches(secret.secretCiphertext(), secret.secretNonce(), ":23456", NOW));
    assertFalse(manager.matches(secret.secretCiphertext(), secret.secretNonce(), "12345a", NOW));

    assertThrows(
        IllegalStateException.class,
        () -> manager.matches("aaaa", secret.secretNonce(), "123456", NOW));
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
