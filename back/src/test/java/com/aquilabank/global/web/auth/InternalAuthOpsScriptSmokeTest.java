package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InternalAuthOpsScriptSmokeTest {

  @TempDir Path tempDir;

  @Test
  void statusChangeAuditLookupScriptRejectsMissingArgs() throws Exception {
    InternalAuthOpsScriptSmokeSupport.ScriptResult result =
        InternalAuthOpsScriptSmokeSupport.run(
            tempDir,
            InternalAuthOpsScriptSmokeSupport.STATUS_CHANGE_AUDIT_LOOKUP_SPEC,
            InternalAuthOpsScriptSmokeSupport.STATUS_CHANGE_AUDIT_LOOKUP_SPEC.args().subList(0, 2));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(InternalAuthOpsScriptSmokeSupport.STATUS_CHANGE_AUDIT_LOOKUP_SPEC.usagePrefix());
    assertThat(result.curlArgs()).isEmpty();
  }

  @Test
  void userStatusScriptRejectsMissingArgs() throws Exception {
    InternalAuthOpsScriptSmokeSupport.ScriptResult result =
        InternalAuthOpsScriptSmokeSupport.run(
            tempDir,
            InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC,
            InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC.args().subList(0, 6));

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
            InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC.args().subList(0, 7));

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

  @Test
  void statusChangeAuditLookupScriptBuildsExpectedCurlRequest() throws Exception {
    InternalAuthOpsScriptSmokeSupport.ScriptResult result =
        InternalAuthOpsScriptSmokeSupport.run(
            tempDir,
            InternalAuthOpsScriptSmokeSupport.STATUS_CHANGE_AUDIT_LOOKUP_SPEC,
            InternalAuthOpsScriptSmokeSupport.STATUS_CHANGE_AUDIT_LOOKUP_SPEC.args());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.curlArgs())
        .containsExactlyElementsOf(
            InternalAuthOpsScriptSmokeSupport.STATUS_CHANGE_AUDIT_LOOKUP_SPEC.expectedCurlArgs());
  }

  @Test
  void readmeUserStatusExamplesMatchSmokeSpec() throws Exception {
    assertReadmeContains(InternalAuthOpsScriptSmokeSupport.USER_STATUS_SPEC);
  }

  @Test
  void readmeMembershipStatusExamplesMatchSmokeSpec() throws Exception {
    assertReadmeContains(InternalAuthOpsScriptSmokeSupport.MEMBERSHIP_STATUS_SPEC);
  }

  @Test
  void readmeStatusChangeAuditLookupExamplesMatchSmokeSpec() throws Exception {
    assertReadmeContains(InternalAuthOpsScriptSmokeSupport.STATUS_CHANGE_AUDIT_LOOKUP_SPEC);
  }

  private void assertReadmeContains(InternalAuthOpsScriptSmokeSupport.ScriptSpec spec)
      throws Exception {
    String readme = InternalAuthOpsScriptSmokeSupport.readReadme();
    for (String snippet : spec.requiredReadmeSnippets()) {
      assertThat(readme).contains(snippet);
    }
  }
}
