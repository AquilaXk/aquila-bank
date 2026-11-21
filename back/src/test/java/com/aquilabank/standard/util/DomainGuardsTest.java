package com.aquilabank.standard.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DomainGuardsTest {

  @Test
  void validatesNumericAndTextBounds() {
    assertThat(DomainGuards.requirePositive(1L, "id")).isEqualTo(1L);
    assertThat(DomainGuards.requireNonNegative(0L, "amount")).isZero();
    assertThat(DomainGuards.requireText("  hello  ", 10, "text")).isEqualTo("hello");
    assertThat(DomainGuards.requireOptionalText(null, 10, "memo")).isEmpty();
    assertThat(DomainGuards.requireOptionalText("  note  ", 10, "memo")).isEqualTo("note");
    assertThat(DomainGuards.requireCurrencyCode("KRW")).isEqualTo("KRW");

    assertThatThrownBy(() -> DomainGuards.requirePositive(0L, "id"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireNonNegative(-1L, "amount"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireText(null, 10, "text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireText("  ", 10, "text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireText("123456", 5, "text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireOptionalText("123456", 5, "memo"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireCurrencyCode(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireCurrencyCode("KR"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireCurrencyCode("krw"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireCurrencyCode("K1W"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainGuards.requireCurrencyCode("KRw"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
