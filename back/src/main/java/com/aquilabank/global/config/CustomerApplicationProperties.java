package com.aquilabank.global.config;

import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitChangePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 고객 신청별 운영 정책값. domain에는 검증 policy 값만 전달합니다. */
@ConfigurationProperties(prefix = "customer-application")
public record CustomerApplicationProperties(TransferLimitChange transferLimitChange) {

  public CustomerApplicationProperties {
    transferLimitChange =
        transferLimitChange == null
            ? new TransferLimitChange(50_000_000L, 200_000_000L)
            : transferLimitChange;
  }

  public CustomerTransferLimitChangePolicy transferLimitChangePolicy() {
    return new CustomerTransferLimitChangePolicy(
        transferLimitChange.maxSingleTransferLimitMinor(),
        transferLimitChange.maxDailyTransferLimitMinor());
  }

  public record TransferLimitChange(
      long maxSingleTransferLimitMinor, long maxDailyTransferLimitMinor) {

    public TransferLimitChange {
      if (maxSingleTransferLimitMinor <= 0) {
        throw new IllegalArgumentException(
            "customer-application.transfer-limit-change.max-single-transfer-limit-minor must be positive");
      }
      if (maxDailyTransferLimitMinor <= 0) {
        throw new IllegalArgumentException(
            "customer-application.transfer-limit-change.max-daily-transfer-limit-minor must be positive");
      }
      if (maxDailyTransferLimitMinor < maxSingleTransferLimitMinor) {
        throw new IllegalArgumentException(
            "customer-application.transfer-limit-change.max-daily-transfer-limit-minor must be greater than or equal to max-single-transfer-limit-minor");
      }
    }
  }
}
