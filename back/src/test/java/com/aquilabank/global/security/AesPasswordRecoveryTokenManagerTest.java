package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.auth.model.GeneratedPasswordRecoveryToken;
import com.aquilabank.standard.util.Sha256Hex;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AesPasswordRecoveryTokenManagerTest {

  @Test
  void generatesTokenThatRoundTripsThroughHashAndReveal() {
    AesPasswordRecoveryTokenManager manager =
        new AesPasswordRecoveryTokenManager(
            new PasswordRecoveryProperties(900, ""), "fallback-secret");

    GeneratedPasswordRecoveryToken generatedToken = manager.generate();

    assertThat(generatedToken.plainToken()).isNotBlank();
    assertThat(generatedToken.tokenCiphertext()).startsWith("v2:");
    assertThat(generatedToken.tokenNonce()).isNotBlank();
    assertThat(generatedToken.tokenHash()).isEqualTo(manager.hash(generatedToken.plainToken()));
    assertThat(manager.reveal(generatedToken.tokenCiphertext(), generatedToken.tokenNonce()))
        .isEqualTo(generatedToken.plainToken());
  }

  @Test
  void hashesPlainTokenDeterministically() {
    AesPasswordRecoveryTokenManager manager =
        new AesPasswordRecoveryTokenManager(
            new PasswordRecoveryProperties(900, "property-secret"), "fallback-secret");

    String firstHash = manager.hash("plain-token");
    String secondHash = manager.hash("plain-token");
    String differentHash = manager.hash("plain-token-2");

    assertThat(firstHash).hasSize(64);
    assertThat(firstHash).isEqualTo(secondHash);
    assertThat(firstHash).isNotEqualTo(differentHash);
  }

  @Test
  void rejectsBlankInputsForHashAndReveal() {
    AesPasswordRecoveryTokenManager manager =
        new AesPasswordRecoveryTokenManager(
            new PasswordRecoveryProperties(900, "property-secret"), "fallback-secret");

    assertThrows(IllegalArgumentException.class, () -> manager.hash(" "));
    assertThrows(IllegalArgumentException.class, () -> manager.reveal(" ", "nonce"));
    assertThrows(IllegalArgumentException.class, () -> manager.reveal("cipher", " "));
  }

  @Test
  void separatesAesDomainKeysEvenWithIdenticalSharedSecretMaterial() {
    String sharedSecret = "shared-jwt-secret-key-material";
    AesPasswordRecoveryTokenManager recoveryManager =
        new AesPasswordRecoveryTokenManager(new PasswordRecoveryProperties(900, sharedSecret), "");
    AesTotpSecretManager totpManager =
        new AesTotpSecretManager(
            new SecurityTotpProperties("Aquila Bank", sharedSecret, 300, 300, 5), "");

    com.aquilabank.domain.auth.model.GeneratedTotpSecret totpSecret = totpManager.generate("alice");

    assertThrows(
        IllegalStateException.class,
        () -> recoveryManager.reveal(totpSecret.secretCiphertext(), totpSecret.secretNonce()));
    assertThrows(
        IllegalStateException.class,
        () -> recoveryManager.reveal("v2:aaaa", totpSecret.secretNonce()));
  }

  @Test
  void revealsLegacyUnprefixedSha256CiphertextAndBlocksFallbackForV2() throws Exception {
    String encryptionKey = "legacy-recovery-encryption-secret";
    AesPasswordRecoveryTokenManager manager =
        new AesPasswordRecoveryTokenManager(new PasswordRecoveryProperties(900, encryptionKey), "");
    byte[] nonce = new byte[] {21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32};
    String plainToken = "legacy-recovery-plain-token-value";
    SecretKeySpec legacyKey =
        new SecretKeySpec(Sha256Hex.digest(encryptionKey.getBytes(StandardCharsets.UTF_8)), "AES");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new GCMParameterSpec(128, nonce));
    String legacyCiphertext =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8)));
    String encodedNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);

    assertThat(manager.reveal(legacyCiphertext, encodedNonce)).isEqualTo(plainToken);
    assertThrows(
        IllegalStateException.class, () -> manager.reveal("v2:" + legacyCiphertext, encodedNonce));
  }
}
