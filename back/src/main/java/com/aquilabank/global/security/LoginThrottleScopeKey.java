package com.aquilabank.global.security;

/** 스코프 프리픽스가 결합된 스로틀 키를 IPv6 콜론 충돌 없이 안전하게 분해합니다. */
final class LoginThrottleScopeKey {

  private static final String[] SCOPES = {
    "password_recovery",
    "mfa_totp_challenge",
    "mfa_backup_code_challenge",
    "password_recovery_confirm",
    "refresh",
    "login"
  };

  private final String scope;
  private final String ipAddress;

  private LoginThrottleScopeKey(String scope, String ipAddress) {
    this.scope = scope;
    this.ipAddress = ipAddress;
  }

  static LoginThrottleScopeKey parse(String throttleKey) {
    if (throttleKey == null || throttleKey.isBlank()) {
      return new LoginThrottleScopeKey("login", "unknown");
    }
    for (String candidate : SCOPES) {
      int prefixLen = candidate.length();
      if (throttleKey.length() > prefixLen
          && throttleKey.charAt(prefixLen) == ':'
          && throttleKey.startsWith(candidate)) {
        String rawIp = throttleKey.substring(prefixLen + 1);
        return new LoginThrottleScopeKey(candidate, rawIp.isBlank() ? "unknown" : rawIp);
      }
    }
    return new LoginThrottleScopeKey("login", throttleKey);
  }

  String scope() {
    return scope;
  }

  String ipAddress() {
    return ipAddress;
  }
}
