package com.aquilabank.global.security;

import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;

/** refresh device binding token도 opaque random + SHA-256 hash 저장으로 제한합니다. */
public class Sha256RefreshDeviceBindingManager extends AbstractSha256TokenManager
    implements RefreshDeviceBindingSecretPort {}
