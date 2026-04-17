package com.aquilabank.global.security;

import com.aquilabank.global.config.OutboxOpsProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** 내부 outbox ops 경로는 단순 shared token 규칙으로만 보호합니다. */
@Component
public class OutboxOpsTokenGuard {

  private final OutboxOpsProperties outboxOpsProperties;

  public OutboxOpsTokenGuard(OutboxOpsProperties outboxOpsProperties) {
    this.outboxOpsProperties = outboxOpsProperties;
  }

  public void validate(HttpServletRequest request) {
    String token = request.getHeader(outboxOpsProperties.tokenHeader());
    if (token == null || token.isBlank() || !outboxOpsProperties.token().equals(token)) {
      throw new BootstrapApiAccessDeniedException("outbox ops token is invalid");
    }
  }
}
