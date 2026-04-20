package com.aquilabank.domain.auth.port;

/** refresh device binding raw token 생성과 hash 계산을 분리하는 port 입니다. */
public interface RefreshDeviceBindingSecretPort {

  String createToken();

  String hash(String rawToken);
}
