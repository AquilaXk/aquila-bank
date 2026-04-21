package com.aquilabank.global.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 송금 risk policy 기본 한도 설정 */
@ConfigurationProperties(prefix = "ledger.transfer-limit")
public record TransferLimitPolicyProperties(
    long singleTransferLimitMinor, long dailyTransferLimitMinor, String businessZoneId) {

  public TransferLimitPolicyProperties {
    if (singleTransferLimitMinor <= 0) {
      throw new IllegalArgumentException(
          "ledger.transfer-limit.single-transfer-limit-minor must be positive");
    }
    if (dailyTransferLimitMinor <= 0) {
      throw new IllegalArgumentException(
          "ledger.transfer-limit.daily-transfer-limit-minor must be positive");
    }
    if (dailyTransferLimitMinor < singleTransferLimitMinor) {
      throw new IllegalArgumentException(
          "ledger.transfer-limit.daily-transfer-limit-minor must be greater than or equal to single-transfer-limit-minor");
    }
    if (businessZoneId == null || businessZoneId.isBlank()) {
      throw new IllegalArgumentException("ledger.transfer-limit.business-zone-id is required");
    }
    ZoneId.of(businessZoneId);
  }
}
