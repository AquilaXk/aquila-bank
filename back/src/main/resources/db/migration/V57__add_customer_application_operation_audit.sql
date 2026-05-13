CREATE TABLE customer_service_application_operation_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_reference VARCHAR(64) NOT NULL,
    action VARCHAR(24) NOT NULL CHECK (
        action IN ('START_REVIEW', 'APPROVE', 'REJECT', 'CANCEL', 'EXECUTE')
    ),
    before_status VARCHAR(24) NOT NULL,
    after_status VARCHAR(24) NOT NULL,
    actor_subject VARCHAR(120) NOT NULL,
    reason VARCHAR(300),
    request_id VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    execution_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_customer_service_application_operation_audit_application
        FOREIGN KEY (application_reference)
        REFERENCES customer_service_application (application_reference)
);

CREATE INDEX idx_customer_service_application_operation_audit_reference_cursor
    ON customer_service_application_operation_audit (application_reference, processed_at DESC, id DESC);

CREATE INDEX idx_customer_service_application_operation_audit_actor_cursor
    ON customer_service_application_operation_audit (actor_subject, processed_at DESC, id DESC);

COMMENT ON TABLE customer_service_application_operation_audit IS
    '고객 신청 review/approve/execute/cancel 단계별 감사 이력. processed_by 덮어쓰기 컬럼의 사후 감사 한계를 보완한다.';
