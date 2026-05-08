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


def run_issuer(extra_env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.update(extra_env)
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
    print("[issue-staging-replay-token] missing secret contract")
    assert_missing_secret_fails_without_token()
    print("ok")


if __name__ == "__main__":
    main()
