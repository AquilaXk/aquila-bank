import type { MenuSection } from '@/lib/customer-banking/types';

export function DashboardSection({
  hasSession,
  isBusy,
  onMove,
  onRefresh,
}: {
  hasSession: boolean;
  isBusy: boolean;
  onMove: (section: MenuSection) => void;
  onRefresh: () => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>개인뱅킹</p>
          <h1>뱅킹 업무</h1>
        </div>
        <div className="button-row">
          <button onClick={() => onMove("accounts")} type="button">
            계좌조회
          </button>
          <button onClick={() => onMove("transfer")} type="button">
            이체
          </button>
          <button disabled={!hasSession || isBusy} onClick={onRefresh} type="button">
            토큰 재발급
          </button>
        </div>
      </div>
      <div className="summary-grid">
        <article className="summary-box">
          <span>조회</span>
          <strong>계좌 목록/잔액</strong>
          <button onClick={() => onMove("accounts")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>이체</span>
          <strong>즉시이체/취소</strong>
          <button onClick={() => onMove("transfer")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>조회</span>
          <strong>거래내역</strong>
          <button onClick={() => onMove("transactions")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>알림</span>
          <strong>알림함/설정</strong>
          <button onClick={() => onMove("notifications")} type="button">
            바로가기
          </button>
        </article>
      </div>
      <div className="bank-table-wrap">
        <table className="bank-table">
          <caption>업무별 처리 기준</caption>
          <thead>
            <tr>
              <th>업무</th>
              <th>기준</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>거래내역 조회</td>
              <td>계좌, 기간, limit, cursor 기반</td>
              <td>bounded query</td>
            </tr>
            <tr>
              <td>이체</td>
              <td>요청 단위 Idempotency-Key 발급</td>
              <td>중복 방지</td>
            </tr>
            <tr>
              <td>알림</td>
              <td>SSE 연결 상태와 inbox 동시 제공</td>
              <td>실시간</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}
