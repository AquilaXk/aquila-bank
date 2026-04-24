package com.aquilabank.domain.notification.model;

/** provider 미호출 완료 사유를 SENT 상태와 분리해 운영자가 바로 구분합니다. */
public enum NotificationChannelDeliverySkipReason {
  VERIFIED_CONTACT_MISSING,
  PROVIDER_URL_MISSING
}
