import type { FormEvent } from 'react';
import type { NotificationItem, NotificationPreferenceItem, NotificationQueryResponse } from '@/lib/api/types';
import { formatDateTime } from '@/lib/customer-banking/format';
import { BankNoticeStrip, WorkTabs } from '../common';

export function NotificationsSection({
  filters,
  isBusy,
  mode,
  notifications,
  preferences,
  selectedIds,
  slice,
  sseStatus,
  unreadCount,
  onBulkAction,
  onConnect,
  onDisconnect,
  onFilterChange,
  onLoadPreferences,
  onLoadUnreadCount,
  onModeChange,
  onNext,
  onPreferenceChange,
  onSearch,
  onToggleId,
  onUpdatePreferences,
}: {
  filters: {
    limit: string;
    cursor: string;
    readStatus: string;
    eventType: string;
    from: string;
    to: string;
  };
  isBusy: boolean;
  mode: "inbox" | "search";
  notifications: NotificationItem[];
  preferences: NotificationPreferenceItem[];
  selectedIds: number[];
  slice: NotificationQueryResponse | null;
  sseStatus: {
    state: string;
    lastEventAt: string;
    lastEventId: string;
  };
  unreadCount: number | null;
  onBulkAction: (
    action: "read" | "archive" | "delete",
    notificationId?: number,
  ) => void;
  onConnect: () => void;
  onDisconnect: () => void;
  onFilterChange: (value: {
    limit: string;
    cursor: string;
    readStatus: string;
    eventType: string;
    from: string;
    to: string;
  }) => void;
  onLoadPreferences: () => void;
  onLoadUnreadCount: () => void;
  onModeChange: (mode: "inbox" | "search") => void;
  onNext: () => void;
  onPreferenceChange: (value: NotificationPreferenceItem[]) => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
  onToggleId: (notificationId: number) => void;
  onUpdatePreferences: () => void;
}) {
  return (
    <section className="task-section">
      <WorkTabs
        active={mode === "inbox" ? "알림함" : "조건검색"}
        items={[
          { id: "알림함", label: "알림함", onClick: () => onModeChange("inbox") },
          { id: "조건검색", label: "조건검색", onClick: () => onModeChange("search") },
          { id: "수신설정", label: "수신설정", onClick: onLoadPreferences },
        ]}
      />
      <div className="section-title">
        <div>
          <p>고객센터</p>
          <h1>알림</h1>
        </div>
        <div className="button-row">
          <button disabled={isBusy} onClick={onLoadUnreadCount} type="button">
            미확인 조회
          </button>
          <button disabled={isBusy} onClick={onConnect} type="button">
            실시간 연결
          </button>
          <button onClick={onDisconnect} type="button">
            연결 해제
          </button>
        </div>
      </div>

      <div className="notification-status">
        <div>
          <span>실시간 연결 상태</span>
          <strong>{sseStatus.state}</strong>
        </div>
        <div>
          <span>미확인</span>
          <strong>{unreadCount ?? "-"}</strong>
        </div>
        <div>
          <span>최근 알림</span>
          <strong>{sseStatus.lastEventId || "-"}</strong>
        </div>
        <div>
          <span>수신시각</span>
          <strong>{formatDateTime(sseStatus.lastEventAt)}</strong>
        </div>
      </div>
      <BankNoticeStrip
        items={[
          { label: "알림함", value: `${notifications.length}건` },
          { label: "조건검색", value: mode === "search" ? "사용 중" : "대기" },
          { label: "미확인", value: unreadCount == null ? "조회 전" : `${unreadCount}건` },
          { label: "다음 조회", value: slice?.hasNext ? "가능" : "없음" },
        ]}
      />

      <form className="bank-form filter-form" onSubmit={onSearch}>
        <div className="panel-toolbar inline-toolbar">
          <div className="tab-switch" role="tablist" aria-label="알림 조회 구분">
            <button
              aria-selected={mode === "inbox"}
              className={mode === "inbox" ? "active" : ""}
              onClick={() => onModeChange("inbox")}
              role="tab"
              type="button"
            >
              알림함
            </button>
            <button
              aria-selected={mode === "search"}
              className={mode === "search" ? "active" : ""}
              onClick={() => onModeChange("search")}
              role="tab"
              type="button"
            >
              조건검색
            </button>
          </div>
          <div className="button-row compact">
            <button disabled={isBusy} type="submit">
              조회
            </button>
            <button disabled={!slice?.nextCursor || isBusy} onClick={onNext} type="button">
              다음 조회
            </button>
          </div>
        </div>
        <div className="filter-grid notification-filter">
          <label>
            <span>건수</span>
            <select
              onChange={(event) =>
                onFilterChange({ ...filters, limit: event.target.value })
              }
              value={filters.limit}
            >
              <option value="20">20</option>
              <option value="50">50</option>
              <option value="100">100</option>
            </select>
          </label>
          <label>
            <span>읽음상태</span>
            <select
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, readStatus: event.target.value })
              }
              value={filters.readStatus}
            >
              <option value="ALL">전체</option>
              <option value="READ">확인</option>
              <option value="UNREAD">미확인</option>
            </select>
          </label>
          <label>
            <span>이벤트 유형</span>
            <input
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, eventType: event.target.value })
              }
              value={filters.eventType}
            />
          </label>
          <label>
            <span>시작일시</span>
            <input
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, from: event.target.value })
              }
              type="datetime-local"
              value={filters.from}
            />
          </label>
          <label>
            <span>종료일시</span>
            <input
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, to: event.target.value })
              }
              type="datetime-local"
              value={filters.to}
            />
          </label>
        </div>
      </form>

      <div className="split-work">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>알림함</strong>
              <span>
                {slice
                  ? `${notifications.length}건 / 다음 조회 ${slice.hasNext ? "가능" : "없음"}`
                  : "조회 전"}
              </span>
            </div>
            <div className="button-row compact">
              <span className="bulk-label">선택 일괄 처리</span>
              <button
                disabled={selectedIds.length === 0 || isBusy}
                onClick={() => onBulkAction("read")}
                type="button"
              >
                읽음
              </button>
              <button
                disabled={selectedIds.length === 0 || isBusy}
                onClick={() => onBulkAction("archive")}
                type="button"
              >
                보관
              </button>
              <button
                disabled={selectedIds.length === 0 || isBusy}
                onClick={() => onBulkAction("delete")}
                type="button"
              >
                삭제
              </button>
            </div>
          </div>
          <div className="bank-table-wrap">
            <table className="bank-table">
              <caption>알림 목록</caption>
              <thead>
                <tr>
                  <th>선택</th>
                  <th>상태</th>
                  <th>유형</th>
                  <th>제목</th>
                  <th>수신시각</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {notifications.length === 0 ? (
                  <tr>
                    <td colSpan={6}>조회된 알림이 없습니다.</td>
                  </tr>
                ) : (
                  notifications.map((item) => (
                    <tr key={item.notificationId}>
                      <td>
                        <input
                          aria-label={`${item.notificationId} 선택`}
                          checked={selectedIds.includes(item.notificationId)}
                          onChange={() => onToggleId(item.notificationId)}
                          type="checkbox"
                        />
                      </td>
                      <td>
                        <span className={item.read ? "status-badge" : "status-badge unread"}>
                          {item.read ? "확인" : "미확인"}
                        </span>
                      </td>
                      <td>{item.eventType}</td>
                      <td>
                        <strong>{item.title}</strong>
                        <p className="table-message">{item.message}</p>
                      </td>
                      <td>{formatDateTime(item.createdAt)}</td>
                      <td>
                        <button
                          disabled={item.read || isBusy}
                          onClick={() => onBulkAction("read", item.notificationId)}
                          type="button"
                        >
                          읽음
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="detail-panel">
          <div className="form-heading">
            <strong>알림 수신 설정</strong>
            <span>{preferences.length}건</span>
          </div>
          <div className="button-row compact preference-actions">
            <button disabled={isBusy} onClick={onLoadPreferences} type="button">
              설정 조회
            </button>
            <button disabled={preferences.length === 0 || isBusy} onClick={onUpdatePreferences} type="button">
              저장
            </button>
          </div>
          {preferences.length === 0 ? (
            <p className="rail-copy">알림 설정을 조회하세요.</p>
          ) : (
            <div className="preference-list">
              {preferences.map((item, index) => (
                <label className="preference-row" key={`${item.category}-${item.channel}`}>
                  <input
                    checked={item.enabled}
                    onChange={(event) => {
                      const nextItems = [...preferences];
                      nextItems[index] = {
                        ...item,
                        enabled: event.target.checked,
                      };
                      onPreferenceChange(nextItems);
                    }}
                    type="checkbox"
                  />
                  <span>
                    <strong>{item.category}</strong>
                    <small>{item.channel}</small>
                  </span>
                </label>
              ))}
            </div>
          )}
        </aside>
      </div>
    </section>
  );
}
