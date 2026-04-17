package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotificationOpsScriptSmokeTest {

  @TempDir Path tempDir;

  @Test
  void notificationSummaryScriptRejectsMissingArgs() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_SUMMARY_SPEC,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_SUMMARY_SPEC.args().subList(0, 1));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(OutboxOpsScriptSmokeSupport.NOTIFICATION_SUMMARY_SPEC.usagePrefix());
    assertThat(result.curlArgs()).isEmpty();
  }

  @Test
  void notificationDlqEventsScriptRejectsMissingArgs() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_DLQ_EVENTS_SPEC,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_DLQ_EVENTS_SPEC.args().subList(0, 2));

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(OutboxOpsScriptSmokeSupport.NOTIFICATION_DLQ_EVENTS_SPEC.usagePrefix());
    assertThat(result.curlArgs()).isEmpty();
  }

  @Test
  void notificationSummaryScriptBuildsExpectedCurlRequest() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_SUMMARY_SPEC,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_SUMMARY_SPEC.args());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.curlArgs())
        .containsExactlyElementsOf(
            OutboxOpsScriptSmokeSupport.NOTIFICATION_SUMMARY_SPEC.expectedCurlArgs());
  }

  @Test
  void notificationDlqEventsScriptBuildsExpectedCurlRequest() throws Exception {
    OutboxOpsScriptSmokeSupport.ScriptResult result =
        OutboxOpsScriptSmokeSupport.run(
            tempDir,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_DLQ_EVENTS_SPEC,
            OutboxOpsScriptSmokeSupport.NOTIFICATION_DLQ_EVENTS_SPEC.args());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEmpty();
    assertThat(result.curlArgs())
        .containsExactlyElementsOf(
            OutboxOpsScriptSmokeSupport.NOTIFICATION_DLQ_EVENTS_SPEC.expectedCurlArgs());
  }

  @Test
  void readmeContainsNotificationSummaryRunbook() throws Exception {
    assertReadmeContains(OutboxOpsScriptSmokeSupport.NOTIFICATION_SUMMARY_SPEC);
  }

  @Test
  void readmeContainsNotificationDlqRunbook() throws Exception {
    assertReadmeContains(OutboxOpsScriptSmokeSupport.NOTIFICATION_DLQ_EVENTS_SPEC);
  }

  private void assertReadmeContains(OutboxOpsScriptSmokeSupport.ScriptSpec spec) throws Exception {
    String readme = OutboxOpsScriptSmokeSupport.readReadme();
    for (String snippet : spec.requiredReadmeSnippets()) {
      assertThat(readme).contains(snippet);
    }
  }
}
