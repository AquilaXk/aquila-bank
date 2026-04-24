CREATE TABLE provider_delivery_metric_summary (
    queue_name VARCHAR(64) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_provider_delivery_metric_summary
        PRIMARY KEY (queue_name, metric_type, metric_name),
    CONSTRAINT chk_provider_delivery_metric_summary_queue
        CHECK (queue_name IN ('notification_channel', 'password_recovery')),
    CONSTRAINT chk_provider_delivery_metric_summary_type
        CHECK (metric_type IN ('STATUS', 'SKIP_REASON')),
    CONSTRAINT chk_provider_delivery_metric_summary_count
        CHECK (metric_count >= 0),
    CONSTRAINT chk_provider_delivery_metric_summary_name
        CHECK (BTRIM(metric_name) <> '')
);

COMMENT ON TABLE provider_delivery_metric_summary IS
    'Prometheus provider delivery scrape가 원본 outbox table count를 반복하지 않도록 bounded summary row를 저장합니다.';

COMMENT ON COLUMN provider_delivery_metric_summary.queue_name IS
    'metric tag queue 값과 같은 provider delivery queue 이름입니다.';

COMMENT ON COLUMN provider_delivery_metric_summary.metric_type IS
    'STATUS 또는 SKIP_REASON summary 구분입니다.';

CREATE OR REPLACE FUNCTION provider_delivery_metric_summary_adjust(
    p_queue_name VARCHAR,
    p_metric_type VARCHAR,
    p_metric_name VARCHAR,
    p_delta BIGINT
) RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_metric_name IS NULL OR p_delta = 0 THEN
        RETURN;
    END IF;

    IF p_delta > 0 THEN
        INSERT INTO provider_delivery_metric_summary (
            queue_name,
            metric_type,
            metric_name,
            metric_count,
            updated_at
        )
        VALUES (
            p_queue_name,
            p_metric_type,
            p_metric_name,
            p_delta,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (queue_name, metric_type, metric_name)
        DO UPDATE SET
            metric_count = provider_delivery_metric_summary.metric_count + EXCLUDED.metric_count,
            updated_at = CURRENT_TIMESTAMP;
        RETURN;
    END IF;

    UPDATE provider_delivery_metric_summary
    SET metric_count = GREATEST(metric_count + p_delta, 0),
        updated_at = CURRENT_TIMESTAMP
    WHERE queue_name = p_queue_name
      AND metric_type = p_metric_type
      AND metric_name = p_metric_name;
END;
$$;

CREATE OR REPLACE FUNCTION provider_delivery_metric_summary_track()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    item RECORD;
    current_queue_name VARCHAR(64) := TG_ARGV[0];
BEGIN
    -- statement-level transition table로 bulk DML도 summary row별 한 번만 갱신합니다.
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        FOR item IN
            SELECT delivery_status AS metric_name,
                   COUNT(*)::BIGINT AS metric_count
            FROM old_rows
            GROUP BY delivery_status
        LOOP
            PERFORM provider_delivery_metric_summary_adjust(
                current_queue_name,
                'STATUS',
                item.metric_name,
                -item.metric_count
            );
        END LOOP;

        FOR item IN
            SELECT skip_reason AS metric_name,
                   COUNT(*)::BIGINT AS metric_count
            FROM old_rows
            WHERE delivery_status = 'SKIPPED'
              AND skip_reason IS NOT NULL
            GROUP BY skip_reason
        LOOP
            PERFORM provider_delivery_metric_summary_adjust(
                current_queue_name,
                'SKIP_REASON',
                item.metric_name,
                -item.metric_count
            );
        END LOOP;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        FOR item IN
            SELECT delivery_status AS metric_name,
                   COUNT(*)::BIGINT AS metric_count
            FROM new_rows
            GROUP BY delivery_status
        LOOP
            PERFORM provider_delivery_metric_summary_adjust(
                current_queue_name,
                'STATUS',
                item.metric_name,
                item.metric_count
            );
        END LOOP;

        FOR item IN
            SELECT skip_reason AS metric_name,
                   COUNT(*)::BIGINT AS metric_count
            FROM new_rows
            WHERE delivery_status = 'SKIPPED'
              AND skip_reason IS NOT NULL
            GROUP BY skip_reason
        LOOP
            PERFORM provider_delivery_metric_summary_adjust(
                current_queue_name,
                'SKIP_REASON',
                item.metric_name,
                item.metric_count
            );
        END LOOP;
    END IF;

    RETURN NULL;
END;
$$;

INSERT INTO provider_delivery_metric_summary (
    queue_name,
    metric_type,
    metric_name,
    metric_count,
    updated_at
)
SELECT 'notification_channel',
       'STATUS',
       delivery_status,
       COUNT(*)::BIGINT,
       CURRENT_TIMESTAMP
FROM notification_channel_outbox
GROUP BY delivery_status;

INSERT INTO provider_delivery_metric_summary (
    queue_name,
    metric_type,
    metric_name,
    metric_count,
    updated_at
)
SELECT 'notification_channel',
       'SKIP_REASON',
       skip_reason,
       COUNT(*)::BIGINT,
       CURRENT_TIMESTAMP
FROM notification_channel_outbox
WHERE delivery_status = 'SKIPPED'
  AND skip_reason IS NOT NULL
GROUP BY skip_reason;

INSERT INTO provider_delivery_metric_summary (
    queue_name,
    metric_type,
    metric_name,
    metric_count,
    updated_at
)
SELECT 'password_recovery',
       'STATUS',
       delivery_status,
       COUNT(*)::BIGINT,
       CURRENT_TIMESTAMP
FROM auth_password_recovery_delivery_outbox
GROUP BY delivery_status;

INSERT INTO provider_delivery_metric_summary (
    queue_name,
    metric_type,
    metric_name,
    metric_count,
    updated_at
)
SELECT 'password_recovery',
       'SKIP_REASON',
       skip_reason,
       COUNT(*)::BIGINT,
       CURRENT_TIMESTAMP
FROM auth_password_recovery_delivery_outbox
WHERE delivery_status = 'SKIPPED'
  AND skip_reason IS NOT NULL
GROUP BY skip_reason;

CREATE TRIGGER trg_notification_channel_outbox_metric_summary_insert
AFTER INSERT ON notification_channel_outbox
REFERENCING NEW TABLE AS new_rows
FOR EACH STATEMENT
EXECUTE FUNCTION provider_delivery_metric_summary_track('notification_channel');

CREATE TRIGGER trg_notification_channel_outbox_metric_summary_update
AFTER UPDATE ON notification_channel_outbox
REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows
FOR EACH STATEMENT
EXECUTE FUNCTION provider_delivery_metric_summary_track('notification_channel');

CREATE TRIGGER trg_notification_channel_outbox_metric_summary_delete
AFTER DELETE ON notification_channel_outbox
REFERENCING OLD TABLE AS old_rows
FOR EACH STATEMENT
EXECUTE FUNCTION provider_delivery_metric_summary_track('notification_channel');

CREATE TRIGGER trg_auth_password_recovery_delivery_metric_summary_insert
AFTER INSERT ON auth_password_recovery_delivery_outbox
REFERENCING NEW TABLE AS new_rows
FOR EACH STATEMENT
EXECUTE FUNCTION provider_delivery_metric_summary_track('password_recovery');

CREATE TRIGGER trg_auth_password_recovery_delivery_metric_summary_update
AFTER UPDATE ON auth_password_recovery_delivery_outbox
REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows
FOR EACH STATEMENT
EXECUTE FUNCTION provider_delivery_metric_summary_track('password_recovery');

CREATE TRIGGER trg_auth_password_recovery_delivery_metric_summary_delete
AFTER DELETE ON auth_password_recovery_delivery_outbox
REFERENCING OLD TABLE AS old_rows
FOR EACH STATEMENT
EXECUTE FUNCTION provider_delivery_metric_summary_track('password_recovery');
