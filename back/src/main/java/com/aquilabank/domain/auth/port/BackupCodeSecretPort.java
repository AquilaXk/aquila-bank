package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.GeneratedBackupCode;
import java.util.List;

/** backup code 생성과 hash 계산을 security adapter로 분리합니다. */
public interface BackupCodeSecretPort {

  List<GeneratedBackupCode> generate(int count);

  String hash(String plainCode);
}
