package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.domain.auth.model.GeneratedBackupCode;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Sha256BackupCodeManagerTest {

  @Test
  void generatesUniqueReadableBackupCodesWithDeterministicHashes() {
    Sha256BackupCodeManager manager = new Sha256BackupCodeManager();

    List<GeneratedBackupCode> backupCodes = manager.generate(10);

    assertThat(backupCodes).hasSize(10);
    assertThat(backupCodes.stream().map(GeneratedBackupCode::plainCode).collect(Collectors.toSet()))
        .hasSize(10);
    assertThat(backupCodes.stream().map(GeneratedBackupCode::codeHash).collect(Collectors.toSet()))
        .hasSize(10);
    assertThat(backupCodes)
        .allSatisfy(
            item -> {
              assertThat(item.plainCode()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
              assertThat(item.codeHash()).hasSize(64);
              assertThat(item.codeHash()).isEqualTo(manager.hash(item.plainCode()));
            });
  }

  @Test
  void hashNormalizesCaseAndHyphen() {
    Sha256BackupCodeManager manager = new Sha256BackupCodeManager();

    String firstHash = manager.hash("abcd-efgh");
    String secondHash = manager.hash("ABCDEFGH");

    assertThat(firstHash).isEqualTo(secondHash);
  }

  @Test
  void rejectsBlankCodeAndNonPositiveCount() {
    Sha256BackupCodeManager manager = new Sha256BackupCodeManager();

    assertThrows(IllegalArgumentException.class, () -> manager.generate(0));
    assertThrows(IllegalArgumentException.class, () -> manager.hash(" "));
  }
}
