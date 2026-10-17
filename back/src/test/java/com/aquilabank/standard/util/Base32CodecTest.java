package com.aquilabank.standard.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class Base32CodecTest {

  @Test
  void roundTripsVariousByteLengths() {
    for (int length = 1; length <= 20; length++) {
      byte[] original = new byte[length];
      for (int index = 0; index < length; index++) {
        original[index] = (byte) (index * 17 + 3);
      }
      String encoded = Base32Codec.encode(original);
      assertArrayEquals(original, Base32Codec.decode(encoded));
      assertArrayEquals(original, Base32Codec.decode(encoded.toLowerCase(Locale.ROOT)));
    }
  }

  @Test
  void decodesLowercaseSeparatorsAndWhitespace() {
    byte[] expected = "hello!".getBytes(StandardCharsets.US_ASCII);
    String encoded = Base32Codec.encode(expected);
    String formatted =
        " \t\n\r"
            + encoded.substring(0, 4).toLowerCase(Locale.ROOT)
            + "-"
            + encoded.substring(4)
            + " - \n";

    assertArrayEquals(expected, Base32Codec.decode(formatted));
  }

  @Test
  void rejectsInvalidInputs() {
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.encode(null));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.encode(new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode(null));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("   "));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode(" - \t\n\r "));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("JBSW=3DP"));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("JBSWY3D0"));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("JBSWY3D8"));
    assertThrows(IllegalArgumentException.class, () -> Base32Codec.decode("JBSWY3D\u0100"));
    assertThat(Base32Codec.encode(new byte[] {(byte) 0xff})).isEqualTo("74");
  }
}
