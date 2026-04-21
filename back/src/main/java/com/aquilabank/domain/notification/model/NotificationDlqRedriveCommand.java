package com.aquilabank.domain.notification.model;

/** DLQ redrive 실행자와 request trace를 함께 전달하는 command */
public record NotificationDlqRedriveCommand(
    NotificationDlqRedriveTarget target, String actor, String requestId) {

  public NotificationDlqRedriveCommand {
    if (target == null) {
      throw new IllegalArgumentException("target is required");
    }
    validateText(actor, "actor");
    validateText(requestId, "requestId");
  }

  private static void validateText(String value, String name) {
    if (value == null || value.isBlank() || value.length() > 120) {
      throw new IllegalArgumentException("%s must be between 1 and 120 characters".formatted(name));
    }
  }
}
