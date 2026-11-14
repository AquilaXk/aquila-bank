package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.auth.model.GeneratedPasswordRecoveryToken;
import org.junit.jupiter.api.Test;

class AesPasswordRecoveryTokenManagerTest {

  @Test
  void generatesTokenThatRoundTripsThroughHashAndReveal() {
    AesPasswordRecoveryTokenManager manager =
        new AesPasswordRecoveryTokenManager(
            new PasswordRecoveryProperties(900, ""), "fallback-secret");

    GeneratedPasswordRecoveryToken generatedToken = manager.generate();

    assertThat(generatedToken.plainToken()).isNotBlank();
    assertThat(generatedToken.tokenCiphertext()).isNotBlank();
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
  }
}
