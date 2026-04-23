package com.aquilabank.global.auth;

import java.util.Optional;
import java.util.regex.Pattern;

/** 별도 contact 모델이 없으므로 현재는 loginId 형식으로만 EMAIL/SMS 전달 대상을 판별합니다. */
public final class PasswordRecoveryDestinationResolver {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

  public Optional<ResolvedDestination> resolve(String loginId) {
    if (loginId == null) {
      return Optional.empty();
    }
    String value = loginId.trim();
    if (EMAIL_PATTERN.matcher(value).matches()) {
      return Optional.of(new ResolvedDestination(PasswordRecoveryChannel.EMAIL, value));
    }
    if (E164_PHONE_PATTERN.matcher(value).matches()) {
      return Optional.of(new ResolvedDestination(PasswordRecoveryChannel.SMS, value));
    }
    return Optional.empty();
  }

  public record ResolvedDestination(PasswordRecoveryChannel channel, String value) {}

  public enum PasswordRecoveryChannel {
    EMAIL,
    SMS
  }
}
