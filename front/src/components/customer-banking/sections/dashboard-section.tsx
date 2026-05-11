import type { MenuSection } from '@/lib/customer-banking/types';
import { BankNoticeStrip, WorkTabs } from '../common';
import {
  bankingNewsItems,
  favoriteServiceItems,
  serviceHourItems,
} from '@/lib/customer-banking/constants';

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
          { label: "업무현황", value: "조회/이체/인증 정상" },
        ]}
      />
      <div className="work-summary-strip" aria-label="개인뱅킹 업무현황">
        <span>조회 결과 대기</span>
        <span>이체 처리 대기</span>
        <span>알림 수신 대기</span>
        <span>인증서 관리</span>
      </div>
      <div className="bank-home-grid">
        <section className="table-panel bank-home-panel">
          <div className="panel-toolbar">
            <strong>자주찾는서비스</strong>
            <span>개인뱅킹</span>
          </div>
          <div className="bank-service-list">
            {favoriteServiceItems.map((item) => (
              <button
                key={item.label}
                onClick={() => onMove(item.section)}
                type="button"
              >
                <span>{item.group}</span>
                <strong>{item.label}</strong>
              </button>
            ))}
          </div>
        </section>
        <section className="table-panel bank-home-panel">
          <div className="panel-toolbar">
            <strong>새소식</strong>
            <span>공지</span>
          </div>
          <ul className="bank-news-list">
            {bankingNewsItems.map((item) => (
              <li key={item}>
                <button type="button">{item}</button>
              </li>
            ))}
          </ul>
        </section>
        <section className="table-panel bank-home-panel hours-panel">
          <div className="panel-toolbar">
            <strong>서비스 이용시간</strong>
            <span>상태</span>
          </div>
          <table className="service-hours-table">
            <caption>서비스 이용시간</caption>
            <thead>
              <tr>
                <th>업무</th>
                <th>시간</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {serviceHourItems.map((item) => (
                <tr key={item.task}>
                  <td>{item.task}</td>
                  <td>{item.time}</td>
                  <td>{item.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
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
              <td>실시간 알림과 알림함</td>
              <td>실시간 알림</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}
