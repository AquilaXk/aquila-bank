import type { MenuSection } from '@/lib/customer-banking/types';
import { BankNoticeStrip, WorkTabs } from '../common';

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
      <WorkTabs
        active="개인뱅킹"
        items={["개인뱅킹", "조회", "이체", "인증센터", "고객센터"].map((label) => ({
          id: label,
          label,
          onClick: () => {
            if (label === "개인뱅킹") {
              onMove("dashboard");
            }
            if (label === "조회") {
              onMove("accounts");
            }
            if (label === "이체") {
              onMove("transfer");
            }
            if (label === "인증센터") {
              onMove("security");
            }
            if (label === "고객센터") {
              onMove("supportCenter");
            }
          },
        }))}
      />
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
      <BankNoticeStrip
        items={[
          { label: "이용시간", value: "00:30~23:30" },
          { label: "보안등급", value: "개인 인증 완료 후 이체 가능" },
          { label: "상담", value: "평일 09:00~18:00" },
          { label: "서비스", value: "조회/이체/인증 업무 제공" },
        ]}
      />
      <div className="summary-grid">
        <article className="summary-box">
          <span>전계좌조회</span>
          <strong>계좌 목록/잔액</strong>
          <button onClick={() => onMove("accounts")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>즉시이체</span>
          <strong>즉시이체/취소</strong>
          <button onClick={() => onMove("transfer")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>거래내역조회</span>
          <strong>거래내역</strong>
          <button onClick={() => onMove("transactions")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>인증센터</span>
          <strong>인증서/보안업무</strong>
          <button onClick={() => onMove("security")} type="button">
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
              <td>계좌와 기간 조건 조회</td>
              <td>다음 조회 가능</td>
            </tr>
            <tr>
              <td>이체</td>
              <td>요청 단위 중복 방지</td>
              <td>중복 방지</td>
            </tr>
            <tr>
              <td>알림</td>
              <td>실시간 알림과 알림함 동시 제공</td>
              <td>실시간 알림</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}
