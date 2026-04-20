package com.aquilabank.domain.auth.port;

/** remember device raw token 생성과 hash 계산을 분리하는 port 입니다. */
public interface RememberDeviceSecretPort {

  String createToken();

  String hash(String rawToken);
}
