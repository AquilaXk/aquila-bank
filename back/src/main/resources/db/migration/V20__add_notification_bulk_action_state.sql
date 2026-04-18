-- account principal 은 inbox row 자체를 숨기고, JWT user 는 shared inbox 보존을 위해 per-user 상태로 archive/delete 를 분리합니다.
ALTER TABLE notification_inbox
    ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_notification_inbox_account_visible_cursor
    ON notification_inbox (account_id, created_at DESC, id DESC)
    WHERE archived_at IS NULL;

CREATE INDEX idx_notification_inbox_account_visible_unread
    ON notification_inbox (account_id, read_at, id DESC)
    WHERE archived_at IS NULL;

ALTER TABLE notification_user_read_state
    ALTER COLUMN read_at DROP NOT NULL;

ALTER TABLE notification_user_read_state
    ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE notification_user_read_state
    ADD COLUMN deleted_at TIMESTAMPTZ;
