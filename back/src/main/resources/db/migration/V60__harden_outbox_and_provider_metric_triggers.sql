-- flyway:allow-breaking-change Drop redundant outbox queue index covered by idx_outbox_event_dispatch_queue and harden provider metric summary trigger lock order
DROP INDEX IF EXISTS idx_outbox_event_publish_queue;
DROP INDEX IF EXISTS idx_outbox_event_status_created_at;

CREATE OR REPLACE FUNCTION provider_delivery_metric_extract(
    p_delivery_status VARCHAR,
    p_skip_reason VARCHAR
)
RETURNS TABLE(metric_type VARCHAR(32), metric_name VARCHAR(64))
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT v.metric_type, v.metric_name
    FROM (
        VALUES
            ('STATUS'::VARCHAR(32), p_delivery_status::VARCHAR(64)),
            ('SKIP_REASON'::VARCHAR(32), CASE WHEN p_delivery_status = 'SKIPPED' THEN p_skip_reason::VARCHAR(64) END)
    ) AS v(metric_type, metric_name)
    WHERE v.metric_name IS NOT NULL;
$$;

CREATE OR REPLACE FUNCTION provider_delivery_metric_summary_track()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    current_queue_name VARCHAR(64) := TG_ARGV[0];
BEGIN
    -- transition table의 상태/스킵 사유 변동을 (metric_type, metric_name) 오름차순으로 일괄 반영해 동시 워커 교착을 막습니다.
    IF TG_OP = 'INSERT' THEN
        PERFORM provider_delivery_metric_summary_adjust(
            current_queue_name,
            ins_d.metric_type,
            ins_d.metric_name,
            ins_d.metric_delta
        )
        FROM (
            SELECT m.metric_type,
                   m.metric_name,
                   COUNT(*)::BIGINT AS metric_delta
            FROM new_rows nr
            CROSS JOIN LATERAL provider_delivery_metric_extract(nr.delivery_status, nr.skip_reason) m
            GROUP BY m.metric_type, m.metric_name
            ORDER BY m.metric_type ASC, m.metric_name ASC
        ) ins_d;
    ELSIF TG_OP = 'UPDATE' THEN
        PERFORM provider_delivery_metric_summary_adjust(
            current_queue_name,
            upd_d.metric_type,
            upd_d.metric_name,
            upd_d.metric_delta
        )
        FROM (
            SELECT um.metric_type,
                   um.metric_name,
                   SUM(s.weight)::BIGINT AS metric_delta
            FROM (
                SELECT o.delivery_status, o.skip_reason, -1::BIGINT AS weight FROM old_rows o
                UNION ALL
                SELECT n.delivery_status, n.skip_reason, 1::BIGINT AS weight FROM new_rows n
            ) s
            CROSS JOIN LATERAL provider_delivery_metric_extract(s.delivery_status, s.skip_reason) um
            GROUP BY um.metric_type, um.metric_name
            HAVING SUM(s.weight) <> 0
            ORDER BY um.metric_type ASC, um.metric_name ASC
        ) upd_d;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM provider_delivery_metric_summary_adjust(
            current_queue_name,
            del_d.metric_type,
            del_d.metric_name,
            del_d.metric_delta
        )
        FROM (
            SELECT dm.metric_type,
                   dm.metric_name,
                   -COUNT(*)::BIGINT AS metric_delta
            FROM old_rows dr
            CROSS JOIN LATERAL provider_delivery_metric_extract(dr.delivery_status, dr.skip_reason) dm
            GROUP BY dm.metric_type, dm.metric_name
            ORDER BY dm.metric_type ASC, dm.metric_name ASC
        ) del_d;
    END IF;

    RETURN NULL;
END;
$$;
