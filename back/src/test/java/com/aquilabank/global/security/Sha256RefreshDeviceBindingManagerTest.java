package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Sha256RefreshDeviceBindingManagerTest {

  @Test
  void createsUrlSafeOpaqueTokenAndDeterministicHash() {
    Sha256RefreshDeviceBindingManager manager = new Sha256RefreshDeviceBindingManager();

    String rawToken = manager.createToken();
    String tokenHash = manager.hash(rawToken);

    assertThat(rawToken).isNotBlank();
    assertThat(rawToken).doesNotContain("=");
    assertThat(tokenHash).hasSize(64);
    assertThat(tokenHash).isEqualTo(manager.hash(rawToken));
  }

  @Test
  void rejectsBlankToken() {
    Sha256RefreshDeviceBindingManager manager = new Sha256RefreshDeviceBindingManager();

    assertThrows(IllegalArgumentException.class, () -> manager.hash(" "));
  }
}
