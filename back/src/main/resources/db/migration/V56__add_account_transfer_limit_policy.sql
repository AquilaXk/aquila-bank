CREATE TABLE account_transfer_limit_policy (
    account_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    single_transfer_limit_minor BIGINT NOT NULL CHECK (single_transfer_limit_minor > 0),
    daily_transfer_limit_minor BIGINT NOT NULL CHECK (daily_transfer_limit_minor >= single_transfer_limit_minor),
    source_application_reference VARCHAR(64) NOT NULL,
    approved_by VARCHAR(120) NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_account_transfer_limit_policy_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id),
    CONSTRAINT fk_account_transfer_limit_policy_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id),
    CONSTRAINT fk_account_transfer_limit_policy_application
        FOREIGN KEY (source_application_reference)
        REFERENCES customer_service_application (application_reference)
);

CREATE INDEX idx_account_transfer_limit_policy_user_account
    ON account_transfer_limit_policy (user_id, account_id);

COMMENT ON TABLE account_transfer_limit_policy IS
    '승인/실행된 이체한도 변경 신청을 실제 송금 정책에서 조회하는 계좌별 override';
