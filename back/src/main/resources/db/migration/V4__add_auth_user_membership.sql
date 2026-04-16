CREATE TABLE bank_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id VARCHAR(80) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    user_status VARCHAR(16) NOT NULL CHECK (user_status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bank_user_login_id UNIQUE (login_id)
);

CREATE TABLE user_account_membership (
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    membership_role VARCHAR(16) NOT NULL CHECK (membership_role IN ('OWNER', 'MEMBER', 'VIEWER')),
    membership_status VARCHAR(16) NOT NULL CHECK (membership_status IN ('ACTIVE', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, account_id),
    CONSTRAINT fk_user_account_membership_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id),
    CONSTRAINT fk_user_account_membership_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id)
);

CREATE INDEX idx_user_account_membership_account_user
    ON user_account_membership (account_id, user_id);
