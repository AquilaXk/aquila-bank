package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InternalAuthOpsScriptSmokeTest {

  @TempDir Path tempDir;

  @Test
  void userStatusScriptRejectsMissingArgs() throws Exception {
    InternalAuthOpsScriptSmokeSupport.ScriptResult result =
        InternalAuthOpsScriptSmokeSupport.run(
            tempDir,
            InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC,
            InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC.args().subList(0, 7));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC.usagePrefix());
    assertThat(result.curlArgs()).isEmpty();
  }

  @Test
  void membershipStatusScriptRejectsMissingArgs() throws Exception {
    InternalAuthOpsScriptSmokeSupport.ScriptResult result =
        InternalAuthOpsScriptSmokeSupport.run(
            tempDir,
            InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC,
            InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC.args().subList(0, 8));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC.usagePrefix());
    assertThat(result.curlArgs()).isEmpty();
  }

  @Test
  void userStatusScriptBuildsExpectedCurlRequest() throws Exception {
    InternalAuthOpsScriptSmokeSupport.ScriptResult result =
        InternalAuthOpsScriptSmokeSupport.run(
            tempDir,
            InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC,
            InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC.args());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.curlArgs())
        .containsExactlyElementsOf(
            InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC.expectedCurlArgs());
  }

  @Test
  void membershipStatusScriptBuildsExpectedCurlRequest() throws Exception {
    InternalAuthOpsScriptSmokeSupport.ScriptResult result =
        InternalAuthOpsScriptSmokeSupport.run(
            tempDir,
            InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC,
            InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC.args());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.curlArgs())
        .containsExactlyElementsOf(
            InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC.expectedCurlArgs());
  }
}
