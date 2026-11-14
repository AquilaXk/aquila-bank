package com.aquilabank.global.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AccountListCursorCodecTest {

  @Test
  void encodesAndDecodesCompactBinaryCursor() {
    String encoded = AccountListCursorCodec.encode(555L);

    assertThat(encoded).hasSize(11);
    assertThat(AccountListCursorCodec.decode(encoded)).isEqualTo(555L);
  }

  @Test
  void rejectsInvalidOrOversizedCursorPayloads() {
    String wrongLength = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[4]);
    String nonPositiveId = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]);

    assertThatThrownBy(() -> AccountListCursorCodec.encode(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accountId must be positive");
    assertThatThrownBy(() -> AccountListCursorCodec.decode(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> AccountListCursorCodec.decode(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> AccountListCursorCodec.decode("A".repeat(17)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> AccountListCursorCodec.decode("invalid!base64"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> AccountListCursorCodec.decode(wrongLength))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> AccountListCursorCodec.decode(nonPositiveId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
  }
}
