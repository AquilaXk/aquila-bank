package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class HkdfSha256Test {

  private static final HexFormat HEX = HexFormat.of();

  @Test
  void matchesRfc5869AppendixA1TestCase1() {
    byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
    byte[] salt = HEX.parseHex("000102030405060708090a0b0c");
    byte[] info = HEX.parseHex("f0f1f2f3f4f5f6f7f8f9");
    byte[] expectedOkm =
        HEX.parseHex(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");

    byte[] actualOkm = HkdfSha256.deriveBytes(ikm, salt, info, 42);
    assertThat(actualOkm).isEqualTo(expectedOkm);

    byte[] derived1 = HkdfSha256.deriveBytes(ikm, salt, "aquila-bank:context-1", 42);
    byte[] derived2 = HkdfSha256.deriveBytes(ikm, salt, "aquila-bank:context-2", 42);
    byte[] defaultSalt = HkdfSha256.deriveBytes(ikm, new byte[0], "aquila-bank:context-1", 32);
    byte[] nullSalt = HkdfSha256.deriveBytes(ikm, null, "aquila-bank:context-1", 32);

    assertThat(derived1).hasSize(42);
    assertThat(derived1).isNotEqualTo(derived2);
    assertThat(defaultSalt).isEqualTo(nullSalt);
  }

  @Test
  void validatesParameters() {
    byte[] ikm = new byte[] {1, 2, 3};
    assertThrows(IllegalArgumentException.class, () -> HkdfSha256.deriveAes256Key(null, "ctx"));
    assertThrows(IllegalArgumentException.class, () -> HkdfSha256.deriveAes256Key(" ", "ctx"));
    assertThrows(IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(null, null, "c", 32));
    assertThrows(
        IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(new byte[0], null, "c", 32));
    assertThrows(
        IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(ikm, null, (String) null, 32));
    assertThrows(IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(ikm, null, "", 32));
    assertThrows(
        IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(ikm, null, (byte[]) null, 32));
    assertThrows(
        IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(ikm, null, new byte[0], 32));
    assertThrows(IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(ikm, null, "c", 0));
    assertThrows(
        IllegalArgumentException.class, () -> HkdfSha256.deriveBytes(ikm, null, "c", 255 * 32 + 1));
    assertThrows(
        IllegalStateException.class,
        () -> HkdfSha256.createMac("UNSUPPORTED-HMAC-999", new byte[] {1}));
  }
}
