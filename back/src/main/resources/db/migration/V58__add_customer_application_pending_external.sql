-- flyway:allow-breaking-change application_status CHECK constraint is widened for external provider callback state.
ALTER TABLE customer_service_application
    DROP CONSTRAINT IF EXISTS customer_service_application_application_status_check;

ALTER TABLE customer_service_application
    ADD CONSTRAINT customer_service_application_application_status_check
    CHECK (
        application_status IN (
            'SUBMITTED',
            'REVIEWING',
            'APPROVED',
            'PENDING_EXTERNAL',
            'EXECUTED',
            'FAILED',
            'REJECTED',
            'CANCELLED',
            'IN_REVIEW',
            'COMPLETED'
        )
    );

COMMENT ON COLUMN customer_service_application.execution_result IS
    '신청 실행 결과 payload. 외부 provider dispatch 성공 시 PENDING_EXTERNAL 상태와 callback 대기 정보를 저장한다.';
