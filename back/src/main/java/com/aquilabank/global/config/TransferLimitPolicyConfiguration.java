package com.aquilabank.global.config;

import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.ledger.port.TransferLimitPolicyOverrideReadPort;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import com.aquilabank.domain.ledger.usecase.TransferLimitPolicyService;
import com.aquilabank.domain.ledger.usecase.TransferLimitPolicyUseCase;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** transfer command 진입 전에 적용할 계좌 단위 한도 정책을 조립합니다. */
@Configuration
@EnableConfigurationProperties(TransferLimitPolicyProperties.class)
public class TransferLimitPolicyConfiguration {

  @Bean
  TransferLimitPolicyUseCase transferLimitPolicyUseCase(
      TransferLimitUsageReadPort usageReadPort,
      TransferLimitPolicyOverrideReadPort overrideReadPort,
      TransferLimitPolicyProperties properties) {
    return new TransferLimitPolicyService(
        usageReadPort,
        overrideReadPort,
        Clock.systemUTC(),
        ZoneId.of(properties.businessZoneId()),
        new TransferLimitPolicy(
            properties.singleTransferLimitMinor(), properties.dailyTransferLimitMinor()));
  }
}
