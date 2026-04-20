package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class Sha256RememberDeviceManagerTest {

  @Test
  void generatesUniqueTokensWithDeterministicHashes() {
    Sha256RememberDeviceManager manager = new Sha256RememberDeviceManager();

    var tokens = IntStream.range(0, 10).mapToObj(index -> manager.createToken()).toList();

    assertThat(tokens).hasSize(10).doesNotHaveDuplicates();
    assertThat(tokens)
        .allSatisfy(
            token -> {
              assertThat(token).isNotBlank();
              assertThat(manager.hash(token)).hasSize(64);
              assertThat(manager.hash(token)).isEqualTo(manager.hash(token));
            });
  }

  @Test
  void rejectsBlankRawToken() {
    Sha256RememberDeviceManager manager = new Sha256RememberDeviceManager();

    assertThrows(IllegalArgumentException.class, () -> manager.hash(" "));
  }
}
