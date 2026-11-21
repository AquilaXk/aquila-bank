package com.aquilabank.domain.account.model;

import com.aquilabank.standard.util.DomainGuards;
import java.time.Instant;
import java.util.Objects;

/** 사용자별 계좌 별칭 및 대표계좌 개인화 설정 모델입니다. */
public record UserAccountPreference(
    long userId, long accountId, String customAlias, boolean primaryAccount, Instant updatedAt) {

  public UserAccountPreference {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    customAlias = DomainGuards.requireOptionalText(customAlias, 60, "customAlias");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}
