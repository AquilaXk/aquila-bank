package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.BackupCodeGenerateCommand;
import com.aquilabank.domain.auth.model.BackupCodeIssueCommand;
import com.aquilabank.domain.auth.model.BackupCodeIssueResult;
import com.aquilabank.domain.auth.model.GeneratedBackupCode;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import com.aquilabank.domain.auth.port.BackupCodeWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** backup code 묶음 교체는 현재 TOTP 재검증 뒤 같은 tx 안에서만 허용합니다. */
public final class BackupCodeGenerateService implements BackupCodeGenerateUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final TotpCredentialLoadPort totpCredentialLoadPort;
  private final TotpSecretPort totpSecretPort;
  private final BackupCodeSecretPort backupCodeSecretPort;
  private final BackupCodeWritePort backupCodeWritePort;
  private final int backupCodeCount;
  private final Clock clock;

  public BackupCodeGenerateService(
      UserCredentialLoadPort userCredentialLoadPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpSecretPort totpSecretPort,
      BackupCodeSecretPort backupCodeSecretPort,
      BackupCodeWritePort backupCodeWritePort,
      int backupCodeCount,
      Clock clock) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.totpCredentialLoadPort = totpCredentialLoadPort;
    this.totpSecretPort = totpSecretPort;
    this.backupCodeSecretPort = backupCodeSecretPort;
    this.backupCodeWritePort = backupCodeWritePort;
    this.backupCodeCount = backupCodeCount;
    this.clock = clock;
  }

  @Override
  public BackupCodeIssueResult issue(BackupCodeGenerateCommand command) {
    Instant now = Instant.now(clock);
    loadActiveUser(command.userId());
    TotpCredential credential =
        totpCredentialLoadPort
            .findCredentialByUserIdForUpdate(command.userId())
            .orElseThrow(this::invalidIssue);
    if (credential.credentialStatus() != TotpCredentialStatus.ACTIVE) {
      throw invalidIssue();
    }
    if (!totpSecretPort.matches(
        credential.secretCiphertext(), credential.secretNonce(), command.totpCode(), now)) {
      throw invalidIssue();
    }

    List<GeneratedBackupCode> generatedItems = backupCodeSecretPort.generate(backupCodeCount);
    // 재발급 race에서도 active 묶음이 한 세트만 남도록 이전 code를 먼저 supersede 합니다.
    backupCodeWritePort.supersedeActiveByUserId(command.userId(), now);
    for (GeneratedBackupCode item : generatedItems) {
      backupCodeWritePort.issue(new BackupCodeIssueCommand(command.userId(), item.codeHash(), now));
    }
    return new BackupCodeIssueResult(
        generatedItems.stream().map(GeneratedBackupCode::plainCode).toList(),
        generatedItems.size());
  }

  private LoginUser loadActiveUser(long userId) {
    LoginUser user =
        userCredentialLoadPort.findByUserIdForUpdate(userId).orElseThrow(this::invalidIssue);
    if (user.status() != UserStatus.ACTIVE) {
      throw invalidIssue();
    }
    return user;
  }

  private InvalidCredentialsException invalidIssue() {
    return new InvalidCredentialsException("backup code issue failed");
  }
}
