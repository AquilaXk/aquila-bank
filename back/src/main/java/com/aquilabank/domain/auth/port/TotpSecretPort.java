package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.GeneratedTotpSecret;
import java.time.Instant;

/** TOTP secret 생성, 보호 저장, code 검증을 security adapter로 숨깁니다. */
public interface TotpSecretPort {

  GeneratedTotpSecret generate(String loginId);

  boolean matches(String secretCiphertext, String secretNonce, String totpCode, Instant now);
}
