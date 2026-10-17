-- flyway:allow-breaking-change Drop redundant outbox queue index covered by idx_outbox_event_dispatch_queue and harden provider metric summary trigger lock order
DROP INDEX IF EXISTS idx_outbox_event_publish_queue;
DROP INDEX IF EXISTS idx_outbox_event_status_created_at;

CREATE OR REPLACE FUNCTION provider_delivery_metric_summary_track()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    item RECORD;
    current_queue_name VARCHAR(64) := TG_ARGV[0];
    metric_type_status CONSTANT VARCHAR(32) := 'STATUS';
    metric_type_skip_reason CONSTANT VARCHAR(32) := 'SKIP_REASON';
    status_skipped CONSTANT VARCHAR(32) := 'SKIPPED';
BEGIN
    -- old/new transition delta를 합산한 뒤 (metric_type, metric_name) 오름차순으로만 갱신해 동시 워커 교착을 막습니다.
    IF TG_OP = 'INSERT' THEN
        FOR item IN
            SELECT metric_type,
                   metric_name,
                   SUM(delta)::BIGINT AS metric_delta
            FROM (
                SELECT metric_type_status AS metric_type,
                       delivery_status AS metric_name,
                       COUNT(*)::BIGINT AS delta
                FROM new_rows
                GROUP BY delivery_status
                UNION ALL
                SELECT metric_type_skip_reason AS metric_type,
                       skip_reason AS metric_name,
                       COUNT(*)::BIGINT AS delta
                FROM new_rows
                WHERE delivery_status = status_skipped
                  AND skip_reason IS NOT NULL
                GROUP BY skip_reason
            ) deltas
            GROUP BY metric_type, metric_name
            HAVING SUM(delta) <> 0
            ORDER BY metric_type ASC, metric_name ASC
        LOOP
            PERFORM provider_delivery_metric_summary_adjust(
                current_queue_name,
                item.metric_type,
                item.metric_name,
                item.metric_delta
            );
        END LOOP;
    ELSIF TG_OP = 'UPDATE' THEN
        FOR item IN
            SELECT metric_type,
                   metric_name,
                   SUM(delta)::BIGINT AS metric_delta
            FROM (
                SELECT metric_type_status AS metric_type,
                       delivery_status AS metric_name,
                       -COUNT(*)::BIGINT AS delta
                FROM old_rows
                GROUP BY delivery_status
                UNION ALL
                SELECT metric_type_skip_reason AS metric_type,
                       skip_reason AS metric_name,
                       -COUNT(*)::BIGINT AS delta
                FROM old_rows
                WHERE delivery_status = status_skipped
                  AND skip_reason IS NOT NULL
                GROUP BY skip_reason
                UNION ALL
                SELECT metric_type_status AS metric_type,
                       delivery_status AS metric_name,
                       COUNT(*)::BIGINT AS delta
                FROM new_rows
                GROUP BY delivery_status
                UNION ALL
                SELECT metric_type_skip_reason AS metric_type,
                       skip_reason AS metric_name,
                       COUNT(*)::BIGINT AS delta
                FROM new_rows
                WHERE delivery_status = status_skipped
                  AND skip_reason IS NOT NULL
                GROUP BY skip_reason
            ) deltas
            GROUP BY metric_type, metric_name
            HAVING SUM(delta) <> 0
            ORDER BY metric_type ASC, metric_name ASC
        LOOP
            PERFORM provider_delivery_metric_summary_adjust(
                current_queue_name,
                item.metric_type,
                item.metric_name,
                item.metric_delta
            );
        END LOOP;
    ELSIF TG_OP = 'DELETE' THEN
        FOR item IN
            SELECT metric_type,
                   metric_name,
                   SUM(delta)::BIGINT AS metric_delta
            FROM (
                SELECT metric_type_status AS metric_type,
                       delivery_status AS metric_name,
                       -COUNT(*)::BIGINT AS delta
                FROM old_rows
                GROUP BY delivery_status
                UNION ALL
                SELECT metric_type_skip_reason AS metric_type,
                       skip_reason AS metric_name,
                       -COUNT(*)::BIGINT AS delta
                FROM old_rows
                WHERE delivery_status = status_skipped
                  AND skip_reason IS NOT NULL
                GROUP BY skip_reason
            ) deltas
            GROUP BY metric_type, metric_name
            HAVING SUM(delta) <> 0
            ORDER BY metric_type ASC, metric_name ASC
        LOOP
            PERFORM provider_delivery_metric_summary_adjust(
                current_queue_name,
                item.metric_type,
                item.metric_name,
                item.metric_delta
            );
        END LOOP;
    END IF;

    RETURN NULL;
END;
$$;
