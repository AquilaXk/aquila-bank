package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.GeneratedBackupCode;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** backup code는 1회성 수단이라 복호화 없이 사람이 읽기 쉬운 값과 hash만 만듭니다. */
public class Sha256BackupCodeManager implements BackupCodeSecretPort {

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 8;
  private static final int SEGMENT_LENGTH = 4;

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
      items.add(new GeneratedBackupCode(format(normalized), sha256Hex(normalized)));
    }
    return items;
  }

  @Override
  public String hash(String plainCode) {
    return sha256Hex(normalize(plainCode));
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
    // 입력 formatting 차이로 hash가 달라지지 않게 hyphen/대소문자를 제거한 뒤 검증합니다.
    for (int index = 0; index < normalized.length(); index++) {
      if (ALPHABET.indexOf(normalized.charAt(index)) < 0) {
        throw new IllegalArgumentException("plainCode contains unsupported characters");
      }
    }
    return normalized;
  }

  private String sha256Hex(String value) {
    return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
  }

  private byte[] sha256(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
