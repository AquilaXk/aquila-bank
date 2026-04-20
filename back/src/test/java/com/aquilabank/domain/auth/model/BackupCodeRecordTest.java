package com.aquilabank.domain.auth.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BackupCodeRecordTest {

  @Test
  void createsValidBackupCodeRecord() {
    BackupCodeRecord record =
        new BackupCodeRecord(
            1L,
            7L,
            "a".repeat(64),
            BackupCodeStatus.ACTIVE,
            null,
            Instant.parse("2026-04-20T09:30:00Z"));

    assertThat(record.userId()).isEqualTo(7L);
    assertThat(record.codeStatus()).isEqualTo(BackupCodeStatus.ACTIVE);
  }

  @Test
  void rejectsInvalidIdentifiersAndBlankHash() {
    Instant createdAt = Instant.parse("2026-04-20T09:30:00Z");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BackupCodeRecord(0L, 7L, "a".repeat(64), BackupCodeStatus.ACTIVE, null, createdAt));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BackupCodeRecord(1L, 0L, "a".repeat(64), BackupCodeStatus.ACTIVE, null, createdAt));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BackupCodeRecord(1L, 7L, " ", BackupCodeStatus.ACTIVE, null, createdAt));
  }
}
