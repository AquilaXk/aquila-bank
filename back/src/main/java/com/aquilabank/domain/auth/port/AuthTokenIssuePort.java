package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.IssuedAccessToken;
import java.time.Instant;

/** 인증 완료 user 정보로 JWT access token만 발급하는 port */
public interface AuthTokenIssuePort {

  IssuedAccessToken issue(long userId, String subject, Instant issuedAt);
}
