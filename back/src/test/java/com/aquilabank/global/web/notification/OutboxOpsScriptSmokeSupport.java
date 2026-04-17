package com.aquilabank.global.web.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** fake curl 로 실제 네트워크 없이 outbox ops script argv 만 캡처합니다. */
final class OutboxOpsScriptSmokeSupport {

  static final ScriptSpec FAILED_EVENTS_SPEC =
      new ScriptSpec(
          "outbox-failed-events",
          repoRoot().resolve("tools/ops/outbox-find-failed-events.sh"),
          "usage: tools/ops/outbox-find-failed-events.sh",
          List.of("http://localhost:8080", "test-outbox-ops-token", "20"),
          List.of(
              "--fail-with-body",
              "--silent",
              "--show-error",
              "--get",
              "--header",
              "X-Outbox-Ops-Token: test-outbox-ops-token",
              "--data-urlencode",
              "limit=20",
              "http://localhost:8080/internal/api/v1/outbox/failed-events"),
          List.of(
              "tools/ops/outbox-find-failed-events.sh \\",
              "http://localhost:8080 \\",
              "\"$OUTBOX_OPS_TOKEN\" \\",
              "\"X-Outbox-Ops-Token: ${OUTBOX_OPS_TOKEN}\"",
              "\"http://localhost:8080/internal/api/v1/outbox/summary\"",
              "\"http://localhost:8080/actuator/health\"",
              "OUT_OF_SERVICE",
              "503"));

  static final ScriptSpec RECOVER_STALE_SENDING_SPEC =
      new ScriptSpec(
          "outbox-recover-stale-sending",
          repoRoot().resolve("tools/ops/outbox-recover-stale-sending.sh"),
          "usage: tools/ops/outbox-recover-stale-sending.sh",
          List.of("http://localhost:8080", "test-outbox-ops-token"),
          List.of(
              "--fail-with-body",
              "--silent",
              "--show-error",
              "--request",
              "POST",
              "--header",
              "X-Outbox-Ops-Token: test-outbox-ops-token",
              "http://localhost:8080/internal/api/v1/outbox/recovery/stale-sending"),
          List.of(
              "tools/ops/outbox-recover-stale-sending.sh \\",
              "http://localhost:8080 \\",
              "\"$OUTBOX_OPS_TOKEN\"",
              "stale `SENDING` row 를 `PENDING` 으로 되돌리는",
              "`OUTBOX_OPS_ENABLED=false`"));

  static final ScriptSpec NOTIFICATION_SUMMARY_SPEC =
      new ScriptSpec(
          "notification-consumer-summary",
          repoRoot().resolve("tools/ops/notification-get-consumer-summary.sh"),
          "usage: tools/ops/notification-get-consumer-summary.sh",
          List.of("http://localhost:8080", "test-outbox-ops-token"),
          List.of(
              "--fail-with-body",
              "--silent",
              "--show-error",
              "--get",
              "--header",
              "X-Outbox-Ops-Token: test-outbox-ops-token",
              "http://localhost:8080/internal/api/v1/outbox/notification/summary"),
          List.of(
              "tools/ops/notification-get-consumer-summary.sh \\",
              "\"X-Outbox-Ops-Token: ${OUTBOX_OPS_TOKEN}\"",
              "\"http://localhost:8080/internal/api/v1/outbox/notification/summary\"",
              "consumer lag 와 DLQ count"));

  static final ScriptSpec NOTIFICATION_DLQ_EVENTS_SPEC =
      new ScriptSpec(
          "notification-dlq-events",
          repoRoot().resolve("tools/ops/notification-find-dlq-events.sh"),
          "usage: tools/ops/notification-find-dlq-events.sh",
          List.of("http://localhost:8080", "test-outbox-ops-token", "20"),
          List.of(
              "--fail-with-body",
              "--silent",
              "--show-error",
              "--get",
              "--header",
              "X-Outbox-Ops-Token: test-outbox-ops-token",
              "--data-urlencode",
              "limit=20",
              "http://localhost:8080/internal/api/v1/outbox/notification/dlq-events"),
          List.of(
              "tools/ops/notification-find-dlq-events.sh \\",
              "\"X-Outbox-Ops-Token: ${OUTBOX_OPS_TOKEN}\"",
              "\"http://localhost:8080/internal/api/v1/outbox/notification/dlq-events\"",
              "poison message 최근 항목"));

  private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(5);

  private OutboxOpsScriptSmokeSupport() {}

  static ScriptResult run(Path tempDir, ScriptSpec spec, List<String> args)
      throws IOException, InterruptedException {
    Path fakeCurlDir = Files.createDirectories(tempDir.resolve("fake-bin"));
    Path fakeCurlArgsFile = tempDir.resolve(spec.name() + "-curl-args.bin");
    writeFakeCurl(fakeCurlDir.resolve("curl"), fakeCurlArgsFile);

    ProcessBuilder processBuilder =
        new ProcessBuilder(buildCommand(spec.scriptPath().toString(), args));
    processBuilder.directory(repoRoot().toFile());
    processBuilder.environment().put("PATH", fakeCurlDir + pathSeparator() + currentPath());
    processBuilder.environment().put("FAKE_CURL_ARGS_FILE", fakeCurlArgsFile.toString());

    Process process = processBuilder.start();
    boolean finished = process.waitFor(SCRIPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("script execution timed out: " + spec.name());
    }

    return new ScriptResult(
        process.exitValue(),
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8),
        readCapturedArgs(fakeCurlArgsFile));
  }

  static String readReadme() throws IOException {
    return Files.readString(repoRoot().resolve("back/README.md"), StandardCharsets.UTF_8);
  }

  private static List<String> buildCommand(String scriptPath, List<String> args) {
    List<String> command = new java.util.ArrayList<>();
    command.add("bash");
    command.add(scriptPath);
    command.addAll(args);
    return command;
  }

  private static List<String> readCapturedArgs(Path fakeCurlArgsFile) throws IOException {
    if (!Files.exists(fakeCurlArgsFile)) {
      return List.of();
    }
    String raw = Files.readString(fakeCurlArgsFile, StandardCharsets.UTF_8);
    if (raw.isEmpty()) {
      return List.of();
    }
    String[] parts = raw.split("\n", -1);
    int size = raw.endsWith("\n") ? parts.length - 1 : parts.length;
    return List.of(parts).subList(0, size);
  }

  private static void writeFakeCurl(Path fakeCurlPath, Path fakeCurlArgsFile) throws IOException {
    String script =
        """
        #!/usr/bin/env bash
        set -euo pipefail
        : "${FAKE_CURL_ARGS_FILE:?}"
        : > "${FAKE_CURL_ARGS_FILE}"
        for arg in "$@"; do
          printf '%s\n' "$arg" >> "${FAKE_CURL_ARGS_FILE}"
        done
        exit 0
        """;
    Files.writeString(fakeCurlPath, script, StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(
          fakeCurlPath,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      fakeCurlPath.toFile().setExecutable(true);
    }
    if (Files.notExists(fakeCurlArgsFile)) {
      Files.createFile(fakeCurlArgsFile);
    }
  }

  private static Path repoRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    if (Files.exists(current.resolve("back/README.md"))) {
      return current;
    }
    Path parent = current.getParent();
    if (parent != null && Files.exists(parent.resolve("back/README.md"))) {
      return parent;
    }
    throw new IllegalStateException("repo root is not found from " + current);
  }

  private static String currentPath() {
    String path = System.getenv("PATH");
    return path == null ? "" : path;
  }

  private static String pathSeparator() {
    return System.getProperty("path.separator");
  }

  record ScriptSpec(
      String name,
      Path scriptPath,
      String usagePrefix,
      List<String> args,
      List<String> expectedCurlArgs,
      List<String> requiredReadmeSnippets) {}

  record ScriptResult(int exitCode, String stdout, String stderr, List<String> curlArgs) {}
}
