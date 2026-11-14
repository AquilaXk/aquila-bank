package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.GeneratedBackupCode;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import com.aquilabank.standard.util.Sha256Hex;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** backup code는 1회성 수단이라 복호화 없이 사람이 읽기 쉬운 값과 hash만 만듭니다. */
public class Sha256BackupCodeManager implements BackupCodeSecretPort {

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 8;
  private static final int SEGMENT_LENGTH = 4;
  private static final int MAX_INPUT_LENGTH = 64;
  private static final byte[] NORMALIZED_ASCII = new byte[128];

  static {
    for (int index = 0; index < ALPHABET.length(); index++) {
      char upper = ALPHABET.charAt(index);
      NORMALIZED_ASCII[upper] = (byte) upper;
      char lower = Character.toLowerCase(upper);
      NORMALIZED_ASCII[lower] = (byte) upper;
    }
  }

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public List<GeneratedBackupCode> generate(int count) {
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }

    List<GeneratedBackupCode> items = new ArrayList<>(count);
    Set<Long> seen = HashSet.newHashSet(count);
    byte[] randomBytes = new byte[CODE_LENGTH];
    byte[] normalizedAscii = new byte[CODE_LENGTH];
    char[] formatted = new char[CODE_LENGTH + 1];
    formatted[SEGMENT_LENGTH] = '-';

    while (items.size() < count) {
      // 32진 알파벳은 바이트 하위 5비트로 모듈로 편향 없이 추출되며 8글자를 40비트 long으로 패킹해 중복을 검사한다.
      secureRandom.nextBytes(randomBytes);
      long packed = 0L;
      for (int index = 0; index < CODE_LENGTH; index++) {
        int symbol = randomBytes[index] & 0x1F;
        char ch = ALPHABET.charAt(symbol);
        packed = (packed << 5) | symbol;
        normalizedAscii[index] = (byte) ch;
        formatted[index < SEGMENT_LENGTH ? index : index + 1] = ch;
      }
      if (seen.add(packed)) {
        items.add(
            new GeneratedBackupCode(new String(formatted), Sha256Hex.hashHex(normalizedAscii)));
      }
    }
    return items;
  }

  @Override
  public String hash(String plainCode) {
    return Sha256Hex.hashHex(normalizeToAscii(plainCode));
  }

  private byte[] normalizeToAscii(String plainCode) {
    if (plainCode == null || plainCode.isBlank()) {
      throw new IllegalArgumentException("plainCode is required");
    }
    if (plainCode.length() > MAX_INPUT_LENGTH) {
      throw new IllegalArgumentException("plainCode exceeds maximum length");
    }

    byte[] ascii = new byte[CODE_LENGTH];
    int len = 0;
    boolean hasUnsupported = false;
    // 단일 패스로 구분자를 건너뛰며 ASCII 룩업 테이블로 대소문자 정규화와 유효 문자 검증을 마친다.
    for (int index = 0, length = plainCode.length(); index < length; index++) {
      char current = plainCode.charAt(index);
      if (current == '-' || Character.isWhitespace(current)) {
        continue;
      }
      if (len == CODE_LENGTH) {
        throw new IllegalArgumentException("plainCode must be 8 characters");
      }
      byte normalizedByte = current < NORMALIZED_ASCII.length ? NORMALIZED_ASCII[current] : 0;
      if (normalizedByte == 0) {
        hasUnsupported = true;
      }
      ascii[len++] = normalizedByte;
    }
    if (len != CODE_LENGTH) {
      throw new IllegalArgumentException("plainCode must be 8 characters");
    }
    if (hasUnsupported) {
      throw new IllegalArgumentException("plainCode contains unsupported characters");
    }
    return ascii;
  }
}
