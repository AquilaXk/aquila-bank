-- PUBLISHED cleanup 은 dispatch queue 조회와 분리해 published_at 기준 partial index를 별도로 탑니다.
CREATE INDEX idx_outbox_event_published_cleanup
    ON outbox_event (published_at, id)
    WHERE publish_status = 'PUBLISHED' AND published_at IS NOT NULL;
