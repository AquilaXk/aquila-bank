package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Sha256RefreshTokenManagerTest {

  @Test
  void createsOpaqueTokenAndDeterministicHash() {
    Sha256RefreshTokenManager manager = new Sha256RefreshTokenManager();

    String token = manager.createToken();
    String hash = manager.hash(token);

    assertThat(token).isNotBlank().doesNotContain("=");
    assertThat(hash).hasSize(64).isEqualTo(manager.hash(token));
    assertThat(manager.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(manager.generateOpaqueToken(16)).isNotBlank();
  }

  @Test
  void rejectsInvalidArgumentsAndUnsupportedDigestAlgorithm() {
    Sha256RefreshTokenManager manager = new Sha256RefreshTokenManager();

    assertThrows(IllegalArgumentException.class, () -> manager.hash(null));
    assertThrows(IllegalArgumentException.class, () -> manager.hash(" "));
    assertThrows(IllegalArgumentException.class, () -> manager.generateOpaqueToken(0));
    assertThrows(
        IllegalStateException.class,
        () -> AbstractSha256TokenManager.messageDigest("UNSUPPORTED-SHA-999"));
  }
}
