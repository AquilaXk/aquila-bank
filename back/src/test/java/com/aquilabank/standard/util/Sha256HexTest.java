package com.aquilabank.standard.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sha256HexTest {

  @Test
  void hashesStringsAndByteArraysDeterministically() {
    String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    assertThat(Sha256Hex.hashHex("abc")).isEqualTo(expected);
    assertThat(Sha256Hex.hashHex("abc".getBytes(StandardCharsets.UTF_8))).isEqualTo(expected);
    assertThat(Sha256Hex.hashHex(""))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    assertThat(Sha256Hex.hashHex("아퀼라"))
        .isEqualTo(Sha256Hex.hashHex("아퀼라".getBytes(StandardCharsets.UTF_8)));
    assertThat(Sha256Hex.digest("abc".getBytes(StandardCharsets.UTF_8))).hasSize(32);
  }

  @Test
  void rejectsNullInputsAndUnsupportedAlgorithms() {
    assertThatThrownBy(() -> Sha256Hex.hashHex((String) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("value must not be null");
    assertThatThrownBy(() -> Sha256Hex.hashHex((byte[]) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("bytes must not be null");
    assertThatThrownBy(() -> Sha256Hex.messageDigest("UNSUPPORTED-SHA-999"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("UNSUPPORTED-SHA-999 is not available");
  }
}
