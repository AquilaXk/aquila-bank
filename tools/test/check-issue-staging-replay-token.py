#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import os
import pathlib
import stat
import subprocess
import sys
import tempfile
import time


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "tools" / "ops" / "issue-staging-replay-token.py"
MIN_RUN_LOCAL_SESSION_ID = 9_000_000_000_000_000_000


def fail(message: str) -> None:
    raise AssertionError(message)


def encode_env(value: str) -> str:
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


def decode_segment(value: str) -> dict:
    padded = value + ("=" * (-len(value) % 4))
    return json.loads(base64.urlsafe_b64decode(padded.encode("ascii")))


def run_issuer(
    extra_env: dict[str, str], path_prefix: pathlib.Path | None = None
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.update(extra_env)
    if path_prefix is not None:
        env["PATH"] = f"{path_prefix}{os.pathsep}{env.get('PATH', '')}"
    return subprocess.run(
        [sys.executable, str(SCRIPT)],
        cwd=REPO_ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def assert_ephemeral_token_is_signed() -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        output_file = pathlib.Path(temp_dir) / "replay-token.jwt"
        secret = "contract-secret-with-enough-entropy"
        issuer = "https://staging.example.test"
        env_b64 = encode_env(
            "\n".join(
                [
                    "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres/aquila",
                    f"SECURITY_JWT_SECRET={secret}",
                    f"SECURITY_JWT_ISSUER={issuer}",
                    "",
                ]
            )
        )

        before = int(time.time())
        result = run_issuer(
            {
                "OCI_A1_BACKEND_ENV_B64": env_b64,
                "STAGING_REPLAY_TOKEN_OUTPUT_FILE": str(output_file),
                "STAGING_REPLAY_USER_ID": "55",
                "STAGING_REPLAY_LOGIN_ID": "staging-fixture-user",
                "STAGING_REPLAY_SESSION_ID": "123456789",
                "STAGING_REPLAY_TOKEN_TTL_SECONDS": "7200",
            }
        )
        after = int(time.time())

        if result.returncode != 0:
            fail(f"issuer should succeed, stderr={result.stderr!r}, stdout={result.stdout!r}")
        if not output_file.is_file():
            fail("issuer should create token output file")

        token = output_file.read_text(encoding="utf-8").strip()
        if not token or token in result.stdout or token in result.stderr:
            fail("issuer should write token only to the output file")
        if secret in result.stdout or secret in result.stderr:
            fail("issuer output must not leak JWT secret")
        if "session_id_source=env" not in result.stdout:
            fail(f"issuer should mark explicit session id source: {result.stdout!r}")

        mode = stat.S_IMODE(output_file.stat().st_mode)
        if mode != 0o600:
            fail(f"token file mode should be 0600, got {oct(mode)}")

        parts = token.split(".")
        if len(parts) != 3:
            fail("issuer should write a compact JWT")

        header = decode_segment(parts[0])
        payload = decode_segment(parts[1])
        expected_signature = hmac.new(
            secret.encode("utf-8"),
            f"{parts[0]}.{parts[1]}".encode("ascii"),
            hashlib.sha256,
        ).digest()
        actual_signature = base64.urlsafe_b64decode(parts[2] + ("=" * (-len(parts[2]) % 4)))

        if not hmac.compare_digest(actual_signature, expected_signature):
            fail("JWT signature should be HS256 over the backend JWT secret")
        if header != {"alg": "HS256", "typ": "JWT"}:
            fail(f"unexpected JWT header: {header!r}")
        if payload.get("iss") != issuer:
            fail(f"issuer claim mismatch: {payload!r}")
        if payload.get("sub") != "staging-fixture-user":
            fail(f"subject claim mismatch: {payload!r}")
        if payload.get("user_id") != 55:
            fail(f"user_id claim mismatch: {payload!r}")
        if payload.get("session_id") != 123456789:
            fail(f"session_id claim mismatch: {payload!r}")
        if payload.get("exp") - payload.get("iat") != 7200:
            fail(f"ttl claim mismatch: {payload!r}")
        if not (before <= payload.get("iat") <= after):
            fail(f"iat claim should be current run time: {payload!r}")


def assert_empty_session_id_uses_reserved_generated_range() -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        output_file = pathlib.Path(temp_dir) / "generated-session.jwt"
        secret = "contract-secret-with-enough-entropy"
        env_b64 = encode_env(f"SECURITY_JWT_SECRET={secret}\n")
        result = run_issuer(
            {
                "OCI_A1_BACKEND_ENV_B64": env_b64,
                "STAGING_REPLAY_TOKEN_OUTPUT_FILE": str(output_file),
                "STAGING_REPLAY_SESSION_ID": "",
            }
        )

        if result.returncode != 0:
            fail(f"issuer should generate session id, stderr={result.stderr!r}, stdout={result.stdout!r}")
        if "session_id_source=generated" not in result.stdout:
            fail(f"issuer should mark generated session id source: {result.stdout!r}")

        token = output_file.read_text(encoding="utf-8").strip()
        payload = decode_segment(token.split(".")[1])
        session_id = payload.get("session_id")
        if not isinstance(session_id, int) or session_id < MIN_RUN_LOCAL_SESSION_ID:
            fail(f"generated session id should use reserved high range: {payload!r}")


def assert_database_sequence_session_id_is_used_when_available() -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = pathlib.Path(temp_dir)
        fake_bin = temp_path / "bin"
        fake_bin.mkdir()
        psql_log = temp_path / "psql-args.log"
        psql = fake_bin / "psql"
        psql.write_text(
            "\n".join(
                [
                    "#!/usr/bin/env python3",
                    "import pathlib",
                    "import sys",
                    f"pathlib.Path({str(psql_log)!r}).write_text(' '.join(sys.argv), encoding='utf-8')",
                    "print('345678901')",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        psql.chmod(0o700)

        output_file = temp_path / "database-sequence.jwt"
        secret = "contract-secret-with-enough-entropy"
        env_b64 = encode_env(f"SECURITY_JWT_SECRET={secret}\n")
        result = run_issuer(
            {
                "OCI_A1_BACKEND_ENV_B64": env_b64,
                "STAGING_REPLAY_TOKEN_OUTPUT_FILE": str(output_file),
                "STAGING_REPLAY_DATABASE_URL": "postgresql://fixture:secret@127.0.0.1/aquila",
                "STAGING_REPLAY_SESSION_ID": "",
            },
            path_prefix=fake_bin,
        )

        if result.returncode != 0:
            fail(f"issuer should use database sequence, stderr={result.stderr!r}, stdout={result.stdout!r}")
        if "session_id_source=database_sequence" not in result.stdout:
            fail(f"issuer should mark database sequence source: {result.stdout!r}")

        token = output_file.read_text(encoding="utf-8").strip()
        payload = decode_segment(token.split(".")[1])
        if payload.get("session_id") != 345678901:
            fail(f"issuer should use psql sequence result: {payload!r}")
        psql_args = psql_log.read_text(encoding="utf-8")
        if "postgresql://fixture:secret@127.0.0.1/aquila" in result.stdout + result.stderr:
            fail("issuer output must not leak database URL")
        if "pg_get_serial_sequence" not in psql_args:
            fail(f"issuer should query auth_refresh_token_session sequence: {psql_args!r}")


def assert_auto_backend_env_prefers_live_docker_container() -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = pathlib.Path(temp_dir)
        fake_bin = temp_path / "bin"
        fake_bin.mkdir()
        docker = fake_bin / "docker"
        docker.write_text(
            "\n".join(
                [
                    "#!/usr/bin/env python3",
                    "import sys",
                    "if sys.argv[1:4] == ['ps', '--format', '{{.Names}}']:",
                    "    print('aquila-backend')",
                    "    raise SystemExit(0)",
                    "if sys.argv[1:4] == ['exec', 'aquila-backend', 'env']:",
                    "    print('SECURITY_JWT_SECRET=live-container-secret-with-enough-entropy')",
                    "    print('SECURITY_JWT_ISSUER=https://live.staging.example.test')",
                    "    raise SystemExit(0)",
                    "raise SystemExit(1)",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        docker.chmod(0o700)

        output_file = temp_path / "live-docker.jwt"
        encoded_secret = "encoded-secret-must-not-sign-token"
        env_b64 = encode_env(f"SECURITY_JWT_SECRET={encoded_secret}\n")
        result = run_issuer(
            {
                "OCI_A1_BACKEND_ENV_B64": env_b64,
                "STAGING_REPLAY_TOKEN_BACKEND_ENV_SOURCE": "auto",
                "STAGING_REPLAY_TOKEN_OUTPUT_FILE": str(output_file),
                "STAGING_REPLAY_SESSION_ID": "987654321",
            },
            path_prefix=fake_bin,
        )

        if result.returncode != 0:
            fail(f"issuer should prefer live docker backend env, stderr={result.stderr!r}")
        if "backend_env_source=live-docker" not in result.stdout:
            fail(f"issuer should report live docker env source: {result.stdout!r}")

        token = output_file.read_text(encoding="utf-8").strip()
        parts = token.split(".")
        payload = decode_segment(parts[1])
        expected_signature = hmac.new(
            b"live-container-secret-with-enough-entropy",
            f"{parts[0]}.{parts[1]}".encode("ascii"),
            hashlib.sha256,
        ).digest()
        actual_signature = base64.urlsafe_b64decode(parts[2] + ("=" * (-len(parts[2]) % 4)))
        if not hmac.compare_digest(actual_signature, expected_signature):
            fail("auto source should sign with live backend container secret")
        if payload.get("iss") != "https://live.staging.example.test":
            fail(f"auto source should use live backend issuer: {payload!r}")


def assert_missing_secret_fails_without_token() -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        output_file = pathlib.Path(temp_dir) / "missing-secret.jwt"
        env_b64 = encode_env("SECURITY_JWT_ISSUER=https://staging.example.test\n")
        result = run_issuer(
            {
                "OCI_A1_BACKEND_ENV_B64": env_b64,
                "STAGING_REPLAY_TOKEN_OUTPUT_FILE": str(output_file),
            }
        )

        if result.returncode == 0:
            fail("issuer should fail when SECURITY_JWT_SECRET is absent")
        if output_file.exists():
            fail("issuer should not create a token without signing secret")
        if "SECURITY_JWT_SECRET" not in result.stderr:
            fail(f"failure should identify missing secret without leaking values: {result.stderr!r}")


def main() -> None:
    print("[issue-staging-replay-token] signed token contract")
    assert_ephemeral_token_is_signed()
    print("[issue-staging-replay-token] generated session contract")
    assert_empty_session_id_uses_reserved_generated_range()
    print("[issue-staging-replay-token] database sequence contract")
    assert_database_sequence_session_id_is_used_when_available()
    print("[issue-staging-replay-token] live docker env source contract")
    assert_auto_backend_env_prefers_live_docker_container()
    print("[issue-staging-replay-token] missing secret contract")
    assert_missing_secret_fails_without_token()
    print("ok")


if __name__ == "__main__":
    main()
