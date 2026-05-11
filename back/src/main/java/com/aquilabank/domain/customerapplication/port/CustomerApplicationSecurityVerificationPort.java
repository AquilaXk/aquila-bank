package com.aquilabank.domain.customerapplication.port;

public interface CustomerApplicationSecurityVerificationPort {

  void verifyTotp(long userId, String totpCode);
}
