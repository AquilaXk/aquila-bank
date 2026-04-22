package com.aquilabank.global.web.account;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** account list cursor는 account_id keyset 값을 HTTP transport에서 opaque하게 숨깁니다. */
final class AccountListCursorCodec {

  private AccountListCursorCodec() {}

  static String encode(long accountId) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Long.toString(accountId).getBytes(StandardCharsets.UTF_8));
  }

  static long decode(String cursor) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      long accountId = Long.parseLong(decoded);
      if (accountId <= 0) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return accountId;
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }
}
