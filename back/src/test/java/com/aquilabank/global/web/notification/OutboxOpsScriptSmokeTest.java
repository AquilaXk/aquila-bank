package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutboxOpsScriptSmokeTest {

  @TempDir Path tempDir;

  @Test
  void failedEventsScriptRejectsMissingArgs() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.FAILED_EVENTS_SPEC,
            OutboxOpsScriptSmokeSupport.FAILED_EVENTS_SPEC.args().subList(0, 2));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(OutboxOpsScriptSmokeSupport.FAILED_EVENTS_SPEC.usagePrefix());
    assertThat(result.curlArgs()).isEmpty();
  }

  @Test
  void recoverStaleSendingScriptRejectsMissingArgs() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.RECOVER_STALE_SENDING_SPEC,
            OutboxOpsScriptSmokeSupport.RECOVER_STALE_SENDING_SPEC.args().subList(0, 1));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(OutboxOpsScriptSmokeSupport.RECOVER_STALE_SENDING_SPEC.usagePrefix());
    assertThat(result.curlArgs()).isEmpty();
  }

  @Test
  void failedEventsScriptBuildsExpectedCurlRequest() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.FAILED_EVENTS_SPEC,
            OutboxOpsScriptSmokeSupport.FAILED_EVENTS_SPEC.args());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.curlArgs())
        .containsExactlyElementsOf(
            OutboxOpsScriptSmokeSupport.FAILED_EVENTS_SPEC.expectedCurlArgs());
  }

  @Test
  void recoverStaleSendingScriptBuildsExpectedCurlRequest() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.RECOVER_STALE_SENDING_SPEC,
            OutboxOpsScriptSmokeSupport.RECOVER_STALE_SENDING_SPEC.args());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.curlArgs())
        .containsExactlyElementsOf(
            OutboxOpsScriptSmokeSupport.RECOVER_STALE_SENDING_SPEC.expectedCurlArgs());
  }

  @Test
  void readmeContainsOutboxFailedEventsRunbook() throws Exception {
    assertReadmeContains(OutboxOpsScriptSmokeSupport.FAILED_EVENTS_SPEC);
  }

  @Test
  void readmeContainsOutboxRecoveryRunbook() throws Exception {
    assertReadmeContains(OutboxOpsScriptSmokeSupport.RECOVER_STALE_SENDING_SPEC);
  }

  private void assertReadmeContains(OutboxOpsScriptSmokeSupport.ScriptSpec spec) throws Exception {
    String readme = OutboxOpsScriptSmokeSupport.readReadme();
    for (String snippet : spec.requiredReadmeSnippets()) {
      assertThat(readme).contains(snippet);
    }
  }
}
