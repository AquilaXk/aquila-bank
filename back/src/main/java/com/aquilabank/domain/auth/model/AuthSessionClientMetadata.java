package com.aquilabank.domain.auth.model;

/** auth session 저장에 필요한 client 메타데이터 최소값입니다. */
public record AuthSessionClientMetadata(String deviceName, String ipAddress) {

  public AuthSessionClientMetadata {
    if (deviceName == null || deviceName.isBlank()) {
      throw new IllegalArgumentException("deviceName is required");
    }
    if (ipAddress == null || ipAddress.isBlank()) {
      throw new IllegalArgumentException("ipAddress is required");
    }
  }
}
