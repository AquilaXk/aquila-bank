CREATE TABLE bank_user_verified_contact (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contact_channel VARCHAR(16) NOT NULL,
    provider_destination VARCHAR(255) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_user_verified_contact_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_bank_user_verified_contact_user_channel UNIQUE (user_id, contact_channel),
    CONSTRAINT chk_bank_user_verified_contact_channel
        CHECK (contact_channel IN ('EMAIL', 'SMS')),
    CONSTRAINT chk_bank_user_verified_contact_destination
        CHECK (BTRIM(provider_destination) <> '')
);

CREATE INDEX idx_bank_user_verified_contact_user
    ON bank_user_verified_contact (user_id, contact_channel);

INSERT INTO bank_user_verified_contact (
    user_id,
    contact_channel,
    provider_destination,
    verified_at,
    created_at,
    updated_at
)
SELECT id,
       'EMAIL',
       login_id,
       updated_at,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM bank_user
WHERE user_status = 'ACTIVE'
  AND login_id ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$'
ON CONFLICT (user_id, contact_channel) DO NOTHING;

INSERT INTO bank_user_verified_contact (
    user_id,
    contact_channel,
    provider_destination,
    verified_at,
    created_at,
    updated_at
)
SELECT id,
       'SMS',
       login_id,
       updated_at,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM bank_user
WHERE user_status = 'ACTIVE'
  AND login_id ~ '^\+[1-9][0-9]{7,14}$'
ON CONFLICT (user_id, contact_channel) DO NOTHING;

COMMENT ON TABLE bank_user_verified_contact IS
    'login_id와 분리된 사용자별 verified provider destination입니다.';

COMMENT ON INDEX idx_bank_user_verified_contact_user IS
    'provider delivery와 admin 조회가 user_id,contact_channel exact lookup으로 접근합니다.';
