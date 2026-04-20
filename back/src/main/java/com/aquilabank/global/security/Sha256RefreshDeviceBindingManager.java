package com.aquilabank.global.security;

import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** refresh device binding token도 opaque random + SHA-256 hash 저장으로 제한합니다. */
public class Sha256RefreshDeviceBindingManager implements RefreshDeviceBindingSecretPort {

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String createToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  public String hash(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("rawToken is required");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
