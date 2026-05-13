package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitChangePolicy;
import org.junit.jupiter.api.Test;

class CustomerApplicationPropertiesTest {

  @Test
  void createsTransferLimitChangePolicyWithDefaults() {
    CustomerTransferLimitChangePolicy policy =
        new CustomerApplicationProperties(null).transferLimitChangePolicy();

    assertThat(policy.maxSingleTransferLimitMinor()).isEqualTo(50_000_000L);
    assertThat(policy.maxDailyTransferLimitMinor()).isEqualTo(200_000_000L);
  }

  @Test
  void rejectsInvalidTransferLimitChangeProperties() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CustomerApplicationProperties.TransferLimitChange(0L, 1_000L));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CustomerApplicationProperties.TransferLimitChange(1_000L, 0L));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CustomerApplicationProperties.TransferLimitChange(2_000L, 1_000L));
  }
}
