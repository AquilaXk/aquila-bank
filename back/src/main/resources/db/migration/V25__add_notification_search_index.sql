CREATE INDEX idx_notification_inbox_account_event_type_cursor
    ON notification_inbox (account_id, event_type, created_at DESC, id DESC)
    WHERE archived_at IS NULL;

COMMENT ON INDEX idx_notification_inbox_account_event_type_cursor IS
    'notification search account path용. account_id + event_type exact filter 뒤 created_at DESC, id DESC keyset 정렬 유지';
