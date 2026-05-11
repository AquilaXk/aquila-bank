CREATE TABLE customer_service_application (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_reference VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    account_id BIGINT,
    application_type VARCHAR(48) NOT NULL CHECK (
        application_type IN (
            'BILL_PAYMENT',
            'OPEN_BANKING_CONNECTION',
            'DEPOSIT_PRODUCT_APPLICATION',
            'LOAN_APPLICATION',
            'FOREIGN_EXCHANGE_APPLICATION',
            'CERTIFICATE_ISSUANCE',
            'CERTIFICATE_REGISTRATION',
            'SECURITY_MEDIA_APPLICATION',
            'TRANSFER_LIMIT_CHANGE',
            'INCIDENT_REPORT'
        )
    ),
    application_status VARCHAR(24) NOT NULL CHECK (
        application_status IN ('SUBMITTED', 'IN_REVIEW', 'COMPLETED', 'REJECTED', 'CANCELLED')
    ),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    mfa_verified BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_verified_at TIMESTAMPTZ,
    payload JSONB NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_customer_service_application_reference UNIQUE (application_reference),
    CONSTRAINT uq_customer_service_application_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_customer_service_application_user
        FOREIGN KEY (user_id) REFERENCES bank_user (id),
    CONSTRAINT fk_customer_service_application_account
        FOREIGN KEY (account_id) REFERENCES bank_account (id)
);

-- 고객별 최근 신청 조회와 운영 콘솔 필터를 나눠 작은 index 두 개로 유지합니다.
CREATE INDEX idx_customer_service_application_user_cursor
    ON customer_service_application (user_id, submitted_at DESC, id DESC);

CREATE INDEX idx_customer_service_application_type_status_cursor
    ON customer_service_application (application_type, application_status, submitted_at DESC, id DESC);
