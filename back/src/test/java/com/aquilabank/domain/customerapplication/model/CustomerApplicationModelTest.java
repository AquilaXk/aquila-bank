package com.aquilabank.domain.customerapplication.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class CustomerApplicationModelTest {

  @Test
  void rejectsPayloadNullValues() {
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("field", null);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CustomerApplicationSubmitCommand(
                7L, 101L, CustomerApplicationType.BILL_PAYMENT, "bill-001", "123456", payload));
  }
}
