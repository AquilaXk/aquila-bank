package com.aquilabank.global.security;

import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;

/** raw remember device token은 랜덤으로 만들고 저장은 SHA-256 hash만 남깁니다. */
public class Sha256RememberDeviceManager extends AbstractSha256TokenManager
    implements RememberDeviceSecretPort {}
