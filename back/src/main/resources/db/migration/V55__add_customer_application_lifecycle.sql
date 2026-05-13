ALTER TABLE customer_service_application
    DROP CONSTRAINT IF EXISTS customer_service_application_application_status_check;

ALTER TABLE customer_service_application
    ADD CONSTRAINT customer_service_application_application_status_check
    CHECK (
        application_status IN (
            'SUBMITTED',
            'REVIEWING',
            'APPROVED',
            'EXECUTED',
            'FAILED',
            'REJECTED',
            'CANCELLED',
            'IN_REVIEW',
            'COMPLETED'
        )
    );

ALTER TABLE customer_service_application
    ADD COLUMN status_reason VARCHAR(300),
    ADD COLUMN processed_by VARCHAR(120),
    ADD COLUMN processed_at TIMESTAMPTZ,
    ADD COLUMN execution_result JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN customer_service_application.status_reason IS
    '운영 검토/실행/실패 사유. 외부 실행 adapter 미구성 같은 미처리 사유를 명확히 남긴다.';

COMMENT ON COLUMN customer_service_application.execution_result IS
    '신청 실행 결과 payload. 외부 기관 연동이 없는 경우 실패 사유와 타입만 저장한다.';
