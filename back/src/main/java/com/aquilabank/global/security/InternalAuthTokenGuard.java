package com.aquilabank.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** 내부 auth 관리 surface가 같은 shared token 검증 규칙을 재사용하도록 묶습니다. */
@Component
public class InternalAuthTokenGuard {

  private final AuthBootstrapApiProperties authBootstrapApiProperties;

  public InternalAuthTokenGuard(AuthBootstrapApiProperties authBootstrapApiProperties) {
    this.authBootstrapApiProperties = authBootstrapApiProperties;
  }

  public void validate(HttpServletRequest httpServletRequest) {
    String bootstrapToken = httpServletRequest.getHeader(authBootstrapApiProperties.tokenHeader());

    // permitAll endpoint라도 shared token 검증이 없으면 내부 auth 관리 경로가 외부 요청에 그대로 열립니다.
    if (bootstrapToken == null
        || bootstrapToken.isBlank()
        || !authBootstrapApiProperties.token().equals(bootstrapToken)) {
      throw new BootstrapApiAccessDeniedException("bootstrap token is invalid");
    }
  }
}
