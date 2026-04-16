package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.LoginResult;

/** 인증 완료 user 정보로 access token을 발급하는 port */
public interface AuthTokenIssuePort {

  LoginResult issue(long userId, String subject);
}
