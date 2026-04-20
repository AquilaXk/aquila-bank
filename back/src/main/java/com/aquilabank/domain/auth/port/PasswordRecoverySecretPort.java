package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.GeneratedPasswordRecoveryToken;

/** recovery token 생성과 조회용 복호화를 security adapter로 분리합니다. */
public interface PasswordRecoverySecretPort {

  GeneratedPasswordRecoveryToken generate();

  String hash(String plainToken);

  String reveal(String tokenCiphertext, String tokenNonce);
}
