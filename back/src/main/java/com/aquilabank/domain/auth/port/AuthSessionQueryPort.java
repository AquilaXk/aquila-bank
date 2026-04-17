package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.AuthSessionSummary;
import java.time.Instant;
import java.util.List;

/** 현재 user refresh token session 목록 조회를 읽기 port로 분리합니다. */
public interface AuthSessionQueryPort {

  List<AuthSessionSummary> findActiveSessionsByUserId(long userId, Instant now, int size);
}
