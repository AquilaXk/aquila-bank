-- requestId audit lookup은 trace_id + id cursor로 작은 batch만 추적합니다.
CREATE INDEX idx_ledger_entry_trace_id_id
    ON ledger_entry (trace_id, id)
    WHERE trace_id IS NOT NULL;
