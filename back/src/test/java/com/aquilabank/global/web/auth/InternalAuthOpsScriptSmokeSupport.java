package com.aquilabank.global.web.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** fake curl로 실제 네트워크 없이 script argv/body만 캡처합니다. */
final class InternalAuthOpsScriptSmokeSupport {

  static final ScriptSpec USER_STATUS_SPEC =
      new ScriptSpec(
          "user-status",
          repoRoot().resolve("tools/ops/internal-auth-update-user-status.sh"),
          "usage: tools/ops/internal-auth-update-user-status.sh",
          List.of(
              "http://localhost:8080",
              "test-auth-bootstrap-api-token",
              "ops-admin",
              "auth-user-disable-20260416-001",
              "21",
              "DISABLED",
              "FRAUD_REVIEW",
              "fraud-review"),
          List.of(
              "--fail-with-body",
              "--silent",
              "--show-error",
              "--request",
              "PUT",
              "--header",
              "Content-Type: application/json",
              "--header",
              "X-Auth-Bootstrap-Token: test-auth-bootstrap-api-token",
              "--header",
              "X-Subject: ops-admin",
              "--header",
              "X-Request-Id: auth-user-disable-20260416-001",
              "--data",
              "{\"userStatus\":\"DISABLED\",\"reasonCode\":\"FRAUD_REVIEW\",\"reasonDetail\":\"fraud-review\"}",
              "http://localhost:8080/internal/api/v1/auth/users/21/status"),
          List.of(
              "tools/ops/internal-auth-update-user-status.sh \\",
              "http://localhost:8080 \\",
              "\"$SECURITY_AUTH_BOOTSTRAP_API_TOKEN\" \\",
              "auth-user-disable-20260416-001 \\",
              "DISABLED \\",
              "FRAUD_REVIEW \\",
              "fraud-review",
              "\"X-Auth-Bootstrap-Token: ${SECURITY_AUTH_BOOTSTRAP_API_TOKEN}\"",
              "\"X-Subject: ops-admin\"",
              "\"X-Request-Id: auth-user-disable-20260416-001\"",
              "\"reasonCode\":\"FRAUD_REVIEW\"",
              "\"reasonDetail\":\"fraud-review\"",
              "\"http://localhost:8080/internal/api/v1/auth/users/21/status\""));

  static final ScriptSpec MEMBERSHIP_STATUS_SPEC =
      new ScriptSpec(
          "membership-status",
          repoRoot().resolve("tools/ops/internal-auth-update-membership-status.sh"),
          "usage: tools/ops/internal-auth-update-membership-status.sh",
          List.of(
              "http://localhost:8080",
              "test-auth-bootstrap-api-token",
              "ops-admin",
              "auth-membership-revoke-20260416-001",
              "21",
              "1001",
              "REVOKED",
              "OPS_MANUAL",
              "manual-revoke"),
          List.of(
              "--fail-with-body",
              "--silent",
              "--show-error",
              "--request",
              "PUT",
              "--header",
              "Content-Type: application/json",
              "--header",
              "X-Auth-Bootstrap-Token: test-auth-bootstrap-api-token",
              "--header",
              "X-Subject: ops-admin",
              "--header",
              "X-Request-Id: auth-membership-revoke-20260416-001",
              "--data",
              "{\"membershipStatus\":\"REVOKED\",\"reasonCode\":\"OPS_MANUAL\",\"reasonDetail\":\"manual-revoke\"}",
              "http://localhost:8080/internal/api/v1/auth/users/21/memberships/1001/status"),
          List.of(
              "tools/ops/internal-auth-update-membership-status.sh \\",
              "http://localhost:8080 \\",
              "\"$SECURITY_AUTH_BOOTSTRAP_API_TOKEN\" \\",
              "auth-membership-revoke-20260416-001 \\",
              "1001 \\",
              "REVOKED \\",
              "OPS_MANUAL \\",
              "manual-revoke",
              "\"X-Auth-Bootstrap-Token: ${SECURITY_AUTH_BOOTSTRAP_API_TOKEN}\"",
              "\"X-Subject: ops-admin\"",
              "\"X-Request-Id: auth-membership-revoke-20260416-001\"",
              "\"reasonCode\":\"OPS_MANUAL\"",
              "\"reasonDetail\":\"manual-revoke\"",
              "\"http://localhost:8080/internal/api/v1/auth/users/21/memberships/1001/status\""));

  private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(5);

  private InternalAuthOpsScriptSmokeSupport() {}

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
