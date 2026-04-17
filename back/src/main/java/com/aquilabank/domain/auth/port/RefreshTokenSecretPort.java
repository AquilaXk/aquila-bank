package com.aquilabank.domain.auth.port;

/** refresh token raw 값 생성과 hash 계산을 분리하는 port 입니다. */
public interface RefreshTokenSecretPort {

  String createToken();

  String hash(String rawToken);
}
