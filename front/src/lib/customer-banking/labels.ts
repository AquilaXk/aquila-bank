const ACCOUNT_STATUS_MAP = new Map<string, string>([
  ["ACTIVE", "정상"],
  ["RESTRICTED", "거래제한"],
  ["LOCKED", "거래정지"],
  ["SUSPENDED", "지급정지"],
  ["FROZEN", "지급정지"],
  ["DORMANT", "휴면"],
  ["CLOSED", "해지"],
]);

const DIRECTION_MAP = new Map<string, string>([
  ["DEBIT", "출금"],
  ["CREDIT", "입금"],
]);

const TRANSACTION_STATUS_MAP = new Map<string, string>([
  ["BOOKED", "처리완료"],
  ["POSTED", "기장완료"],
  ["PENDING", "처리대기"],
  ["FAILED", "실패"],
  ["REVERSED", "취소완료"],
  ["CANCELLED", "취소"],
  ["REJECTED", "거절"],
]);

const APPLICATION_TYPE_MAP = new Map<string, string>([
  ["BILL_PAYMENT", "공과금/지로 납부"],
  ["OPEN_BANKING_CONNECTION", "오픈뱅킹 연결"],
  ["DEPOSIT_PRODUCT_APPLICATION", "예·적금 가입 신청"],
  ["LOAN_APPLICATION", "대출 신청"],
  ["FOREIGN_EXCHANGE_APPLICATION", "외환 신청"],
  ["CERTIFICATE_ISSUANCE", "증명서 발급"],
  ["CERTIFICATE_REGISTRATION", "타행 인증서 등록"],
  ["SECURITY_MEDIA_APPLICATION", "보안매체 발급"],
  ["TRANSFER_LIMIT_CHANGE", "이체한도 변경"],
  ["INCIDENT_REPORT", "사고/분실 신고"],
]);

const APPLICATION_STATUS_MAP = new Map<string, string>([
  ["SUBMITTED", "접수완료"],
  ["REVIEWING", "심사중"],
  ["IN_REVIEW", "심사중"],
  ["APPROVED", "승인완료"],
  ["PENDING_EXTERNAL", "외부 처리 대기"],
  ["EXECUTED", "처리완료"],
  ["COMPLETED", "처리완료"],
  ["REJECTED", "반려"],
  ["CANCELLED", "취소완료"],
  ["FAILED", "처리실패"],
]);

const PROCESSING_MODE_MAP = new Map<string, string>([
  ["AUTO_EXECUTED", "즉시 자동처리"],
  ["AUTOMATED", "자동 처리"],
  ["MANUAL_REVIEW", "담당자 심사"],
  ["EXTERNAL_CALLBACK", "외부 연동 확인"],
]);

const REVERSAL_REASON_MAP = new Map<string, string>([
  ["CUSTOMER_REQUEST", "고객 요청"],
  ["DUPLICATE", "중복 이체"],
  ["WRONG_AMOUNT", "금액 오입력"],
  ["WRONG_TARGET", "수취계좌 오류"],
  ["FRAUD_REPORTED", "의심 거래 신고"],
]);

const NOTIFICATION_EVENT_TYPE_MAP = new Map<string, string>([
  ["TRANSFER_COMPLETED", "이체 완료"],
  ["TRANSFER_REVERSED", "이체 취소"],
  ["ACCOUNT_STATUS_CHANGED", "계좌 상태 변경"],
  ["SECURITY_ALERT", "보안 알림"],
  ["MFA_CHALLENGE", "추가 인증"],
  ["APPLICATION_UPDATED", "신청 상태 변경"],
  ["SYSTEM_NOTICE", "시스템 안내"],
]);

const NOTIFICATION_CATEGORY_MAP = new Map<string, string>([
  ["TRANSFER", "이체"],
  ["SECURITY", "보안"],
  ["ACCOUNT", "계좌"],
  ["CUSTOMER_SERVICE", "고객센터"],
  ["MARKETING", "안내"],
  ["SYSTEM", "시스템"],
]);

const NOTIFICATION_CHANNEL_MAP = new Map<string, string>([
  ["IN_APP", "웹 알림"],
  ["SSE", "실시간 푸시"],
  ["EMAIL", "이메일"],
  ["SMS", "문자(SMS)"],
  ["PUSH", "앱 푸시"],
]);

const FEE_POLICY_MAP = new Map<string, string>([
  ["WAIVED", "수수료 면제"],
  ["FREE", "무료"],
  ["STANDARD", "일반 수수료"],
]);

const SESSION_STATUS_MAP = new Map<string, string>([
  ["ACTIVE", "활성"],
  ["REVOKED", "해지됨"],
  ["EXPIRED", "만료됨"],
]);

function lookupLabel(map: Map<string, string>, raw?: string | null, fallback = "-"): string {
  if (!raw) {
    return fallback;
  }
  const normalized = raw.trim().toUpperCase();
  return map.get(normalized) ?? raw;
}

export function getAccountStatusLabel(status?: string | null): string {
  return lookupLabel(ACCOUNT_STATUS_MAP, status);
}

export function getDirectionLabel(direction?: string | null): string {
  return lookupLabel(DIRECTION_MAP, direction);
}

export function getTransactionStatusLabel(status?: string | null): string {
  return lookupLabel(TRANSACTION_STATUS_MAP, status);
}

export function getApplicationTypeLabel(type?: string | null): string {
  return lookupLabel(APPLICATION_TYPE_MAP, type);
}

export function getApplicationStatusLabel(status?: string | null): string {
  return lookupLabel(APPLICATION_STATUS_MAP, status);
}

export function getProcessingModeLabel(mode?: string | null): string {
  return lookupLabel(PROCESSING_MODE_MAP, mode);
}

export function getReversalReasonLabel(reason?: string | null): string {
  return lookupLabel(REVERSAL_REASON_MAP, reason);
}

export function getNotificationEventTypeLabel(eventType?: string | null): string {
  return lookupLabel(NOTIFICATION_EVENT_TYPE_MAP, eventType);
}

export function getNotificationCategoryLabel(category?: string | null): string {
  return lookupLabel(NOTIFICATION_CATEGORY_MAP, category);
}

export function getNotificationChannelLabel(channel?: string | null): string {
  return lookupLabel(NOTIFICATION_CHANNEL_MAP, channel);
}

export function getFeePolicyLabel(policy?: string | null): string {
  return lookupLabel(FEE_POLICY_MAP, policy);
}

export function getSessionStatusLabel(status?: string | null): string {
  return lookupLabel(SESSION_STATUS_MAP, status);
}
