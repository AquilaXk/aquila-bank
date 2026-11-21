-- 뱅킹 사용자 경험 확장을 위한 수취인 주소록, 계좌 개인화, 목표 저축 포켓, 거래 분류/메모 오버레이, 예약·자동이체 테이블을 추가합니다.
CREATE TABLE user_saved_recipient (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES bank_user (id),
    nickname VARCHAR(60) NOT NULL,
    institution_code VARCHAR(20) NOT NULL DEFAULT 'AQUILA',
    account_number VARCHAR(40) NOT NULL,
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_saved_recipient UNIQUE (user_id, institution_code, account_number)
);

CREATE INDEX idx_user_saved_recipient_user_fav
    ON user_saved_recipient (user_id, favorite DESC, id DESC);

CREATE TABLE user_account_preference (
    user_id BIGINT NOT NULL REFERENCES bank_user (id),
    account_id BIGINT NOT NULL REFERENCES bank_account (id),
    custom_alias VARCHAR(60) NOT NULL DEFAULT '',
    primary_account BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, account_id)
);

CREATE INDEX idx_user_account_preference_primary
    ON user_account_preference (user_id, primary_account DESC, account_id ASC);

CREATE UNIQUE INDEX uq_user_account_preference_primary_true
    ON user_account_preference (user_id)
    WHERE primary_account = TRUE;

CREATE TABLE account_savings_pocket (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES bank_user (id),
    account_id BIGINT NOT NULL REFERENCES bank_account (id),
    pocket_name VARCHAR(60) NOT NULL,
    target_amount_minor BIGINT NOT NULL CHECK (target_amount_minor > 0),
    saved_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (saved_amount_minor >= 0),
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_account_savings_pocket_user_account
    ON account_savings_pocket (user_id, account_id, id DESC);

CREATE TABLE user_transaction_annotation (
    user_id BIGINT NOT NULL REFERENCES bank_user (id),
    account_id BIGINT NOT NULL REFERENCES bank_account (id),
    transaction_reference VARCHAR(80) NOT NULL,
    category VARCHAR(32) NOT NULL,
    memo VARCHAR(200) NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, account_id, transaction_reference)
);

CREATE TABLE scheduled_transfer_rule (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES bank_user (id),
    source_account_id BIGINT NOT NULL REFERENCES bank_account (id),
    target_account_number VARCHAR(40) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code VARCHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    summary VARCHAR(120) NOT NULL,
    schedule_type VARCHAR(16) NOT NULL CHECK (schedule_type IN ('ONCE', 'WEEKLY', 'MONTHLY')),
    scheduled_day INT NOT NULL CHECK (scheduled_day BETWEEN 1 AND 31),
    next_execution_at TIMESTAMPTZ NOT NULL,
    rule_status VARCHAR(16) NOT NULL CHECK (rule_status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scheduled_transfer_user_status
    ON scheduled_transfer_rule (user_id, rule_status, next_execution_at ASC, id DESC);
