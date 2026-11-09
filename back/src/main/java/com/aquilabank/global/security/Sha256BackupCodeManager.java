package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.GeneratedBackupCode;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import com.aquilabank.standard.util.Sha256Hex;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** backup code는 1회성 수단이라 복호화 없이 사람이 읽기 쉬운 값과 hash만 만듭니다. */
public class Sha256BackupCodeManager implements BackupCodeSecretPort {

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 8;
  private static final int SEGMENT_LENGTH = 4;
  private static final byte[] LOOKUP = new byte[128];

  static {
    Arrays.fill(LOOKUP, (byte) -1);
    for (int index = 0; index < ALPHABET.length(); index++) {
      LOOKUP[ALPHABET.charAt(index)] = (byte) index;
    }
  }

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public List<GeneratedBackupCode> generate(int count) {
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }

    List<GeneratedBackupCode> items = new ArrayList<>(count);
    Set<String> seen = new HashSet<>(count);
    while (items.size() < count) {
      String normalized = generateNormalizedCode();
      if (!seen.add(normalized)) {
        continue;
      }
      items.add(new GeneratedBackupCode(format(normalized), Sha256Hex.hashHex(normalized)));
    }
    return items;
  }

  @Override
  public String hash(String plainCode) {
    return Sha256Hex.hashHex(normalize(plainCode));
  }

  private String generateNormalizedCode() {
    StringBuilder builder = new StringBuilder(CODE_LENGTH);
    for (int index = 0; index < CODE_LENGTH; index++) {
      builder.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
    }
    return builder.toString();
  }

  private String format(String normalized) {
    return normalized.substring(0, SEGMENT_LENGTH) + "-" + normalized.substring(SEGMENT_LENGTH);
  }

  private String normalize(String plainCode) {
    if (plainCode == null || plainCode.isBlank()) {
      throw new IllegalArgumentException("plainCode is required");
    }

    StringBuilder builder = new StringBuilder(CODE_LENGTH);
    for (int index = 0; index < plainCode.length(); index++) {
      char current = plainCode.charAt(index);
      if (current == '-' || Character.isWhitespace(current)) {
        continue;
      }
      builder.append(Character.toUpperCase(current));
    }

    String normalized = builder.toString();
    if (normalized.length() != CODE_LENGTH) {
      throw new IllegalArgumentException("plainCode must be 8 characters");
    }
    // 입력 formatting 차이로 hash가 달라지지 않게 hyphen/대소문자를 제거한 뒤 O(1) 룩업 테이블로 검증합니다.
    for (int index = 0; index < normalized.length(); index++) {
      char ch = normalized.charAt(index);
      if (ch >= LOOKUP.length || LOOKUP[ch] < 0) {
        throw new IllegalArgumentException("plainCode contains unsupported characters");
      }
    }
    return normalized;
  }
}
