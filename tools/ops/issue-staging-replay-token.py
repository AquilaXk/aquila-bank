#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import os
import pathlib
import secrets
import sys
import time


MAX_BIGINT = 9_223_372_036_854_775_807
MIN_RUN_LOCAL_SESSION_ID = 9_000_000_000_000_000_000
DEFAULT_USER_ID = 55
DEFAULT_LOGIN_ID = "staging-fixture-user"
DEFAULT_TTL_SECONDS = 7200


def fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    raise SystemExit(1)


def positive_int(name: str, default: int | None = None) -> int:
    raw = os.environ.get(name)
    if raw is None or raw == "":
        if default is None:
            fail(f"{name} is required")
        return default
    if not raw.isdigit() or int(raw) <= 0:
        fail(f"{name} must be a positive integer")
    return int(raw)


def base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def decode_backend_env() -> str:
    encoded = os.environ.get("OCI_A1_BACKEND_ENV_B64") or os.environ.get("BACKEND_ENV_B64")
    if not encoded:
        fail("OCI_A1_BACKEND_ENV_B64 is required")

    cleaned = "".join(encoded.split())
    padded = cleaned + ("=" * (-len(cleaned) % 4))
    for decoder in (
        lambda value: base64.b64decode(value, validate=True),
        base64.urlsafe_b64decode,
    ):
        try:
            return decoder(padded).decode("utf-8")
        except Exception:
            continue
    fail("Failed to decode OCI_A1_BACKEND_ENV_B64")


def parse_backend_env(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export ") :].lstrip()
        if "=" not in line:
            continue
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip()
        if not name:
            continue
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        result[name] = value
    return result


def require_backend_value(values: dict[str, str], name: str) -> str:
    value = values.get(name, "")
    if not value:
        fail(f"{name} is required in OCI_A1_BACKEND_ENV_B64")
    return value


def random_session_id() -> int:
    # sequence 기반 운영 session과 충돌하지 않도록 run-local 전용 high range를 사용한다.
    return MIN_RUN_LOCAL_SESSION_ID + secrets.randbelow(MAX_BIGINT - MIN_RUN_LOCAL_SESSION_ID)


def resolve_ttl_seconds() -> int:
    raw = os.environ.get("STAGING_REPLAY_TOKEN_TTL_SECONDS")
    if raw:
        return positive_int("STAGING_REPLAY_TOKEN_TTL_SECONDS")
    raw = os.environ.get("MIXED_WORKLOAD_REPLAY_TOKEN_TTL_SECONDS")
    if raw:
        return positive_int("MIXED_WORKLOAD_REPLAY_TOKEN_TTL_SECONDS")
    return DEFAULT_TTL_SECONDS


def resolve_session_id() -> tuple[int, str]:
    raw = os.environ.get("STAGING_REPLAY_SESSION_ID")
    if raw:
        return positive_int("STAGING_REPLAY_SESSION_ID"), "env"
    return random_session_id(), "generated"


def issue_token(secret: str, issuer: str, user_id: int, subject: str, session_id: int, ttl_seconds: int) -> str:
    now = int(time.time())
    payload: dict[str, int | str] = {
        "sub": subject,
        "iat": now,
        "exp": now + ttl_seconds,
        "user_id": user_id,
        "session_id": session_id,
    }
    if issuer:
        payload["iss"] = issuer

    header = {"alg": "HS256", "typ": "JWT"}
    header_segment = base64url(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    payload_segment = base64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signing_input = f"{header_segment}.{payload_segment}".encode("ascii")
    signature = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    return f"{header_segment}.{payload_segment}.{base64url(signature)}"


def write_token(path: pathlib.Path, token: str) -> None:
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            output.write(token)
    finally:
        os.chmod(path, 0o600)


def main() -> None:
    output_path_raw = os.environ.get("STAGING_REPLAY_TOKEN_OUTPUT_FILE", "")
    if not output_path_raw:
        fail("STAGING_REPLAY_TOKEN_OUTPUT_FILE is required")
    output_path = pathlib.Path(output_path_raw)
    if not output_path.parent.is_dir():
        fail("STAGING_REPLAY_TOKEN_OUTPUT_FILE parent directory must exist")

    backend_values = parse_backend_env(decode_backend_env())
    secret = require_backend_value(backend_values, "SECURITY_JWT_SECRET")
    issuer = backend_values.get("SECURITY_JWT_ISSUER", "")
    user_id = positive_int("STAGING_REPLAY_USER_ID", DEFAULT_USER_ID)
    subject = os.environ.get("STAGING_REPLAY_LOGIN_ID", DEFAULT_LOGIN_ID)
    if not subject:
        fail("STAGING_REPLAY_LOGIN_ID must not be blank")

    ttl_seconds = resolve_ttl_seconds()
    session_id, session_id_source = resolve_session_id()

    token = issue_token(secret, issuer, user_id, subject, session_id, ttl_seconds)
    write_token(output_path, token)
    print(
        "[issue-staging-replay-token] wrote "
        f"token_file={output_path} user_id={user_id} "
        f"session_id_source={session_id_source} ttl_seconds={ttl_seconds}"
    )


if __name__ == "__main__":
    main()
