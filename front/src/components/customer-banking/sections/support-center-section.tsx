import { supportCenterItems } from "@/lib/customer-banking/constants";

const faqItems = [
  {
    question: "FAQ 이체확인증은 어디서 발급하나요?",
    answer: "이체 완료 후 완료증 영역에서 거래번호를 확인하고 증명서 메뉴의 이체확인증 발급으로 이동합니다.",
  },
  {
    question: "OTP 오류 횟수가 초과되면 어떻게 하나요?",
    answer: "사고신고 접수에서 보안매체 오류 초기화를 선택하고 본인확인 후 재등록 흐름을 진행합니다.",
  },
  {
    question: "오픈뱅킹 연결 계좌가 보이지 않습니다.",
    answer: "동의 만료일과 은행별 점검 시간을 확인한 뒤 오픈뱅킹 상세의 통합조회 갱신을 다시 실행합니다.",
  },
];

const certificateItems = [
  {
    title: "이체확인증",
    description: "거래번호, 출금계좌, 입금계좌, 이체금액, 수수료, 처리시각을 확인증 형태로 제공합니다.",
    scope: "즉시이체 완료 거래",
  },
  {
    title: "잔액증명서",
    description: "기준일, 계좌, 통화, 잔액, 발급 목적을 확인한 뒤 증명서 발급 화면으로 이어집니다.",
    scope: "예금/입출금 계좌",
  },
  {
    title: "거래내역확인서",
    description: "조회 기간, 거래 상태, 입출금 구분, 증명 대상 거래를 선택하는 화면 구조를 제공합니다.",
    scope: "거래내역 조회 결과",
  },
];

export function SupportCenterSection() {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>고객센터</p>
          <h1>고객지원 · 사고신고 · 이체한도</h1>
        </div>
      </div>

      <div className="support-service-grid">
        {supportCenterItems.map((item) => (
          <article className="support-service-card" key={item.title}>
            <strong>{item.title}</strong>
            <p>{item.description}</p>
            <button type="button">{item.action}</button>
          </article>
        ))}
      </div>

      <section className="table-panel">
        <div className="panel-toolbar">
          <div>
            <strong>사고신고 우선순위</strong>
            <span>분실/도용 의심 업무는 실행 화면과 분리해 즉시 찾을 수 있게 둡니다.</span>
          </div>
        </div>
        <div className="bank-table-wrap">
          <table className="bank-table">
            <caption>사고신고 우선순위</caption>
            <thead>
              <tr>
                <th>신고 유형</th>
                <th>대상</th>
                <th>초기 조치</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>보안매체 분실</td>
                <td>OTP, 보안카드</td>
                <td>사용 정지 후 재발급</td>
              </tr>
              <tr>
                <td>인증서 도용 의심</td>
                <td>공동인증서, 금융인증서</td>
                <td>폐기 및 재등록</td>
              </tr>
              <tr>
                <td>이체한도 관리</td>
                <td>1회/1일 한도</td>
                <td>보안등급 확인</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="support-detail-grid" aria-label="고객센터 상세 업무">
        <article className="faq-list">
          <div className="panel-toolbar">
            <div>
              <strong>FAQ</strong>
              <span>반복 문의는 업무 화면 바로 옆에서 확인합니다.</span>
            </div>
          </div>
          {faqItems.map((item) => (
            <details key={item.question}>
              <summary>{item.question}</summary>
              <p>{item.answer}</p>
            </details>
          ))}
        </article>

        <article className="incident-form">
          <div className="panel-toolbar">
            <div>
              <strong>사고신고 접수</strong>
              <span>분실/도용/오류 신고를 한 화면에서 접수 준비합니다.</span>
            </div>
          </div>
          <div className="form-grid">
            <label>
              <span>신고 유형</span>
              <select defaultValue="security-media">
                <option value="security-media">보안매체 분실</option>
                <option value="certificate">인증서 도용 의심</option>
                <option value="transfer">미확인 이체</option>
                <option value="otp-error">OTP 오류 초과</option>
              </select>
            </label>
            <label>
              <span>대상 계좌/매체</span>
              <input defaultValue="선택 대기" readOnly />
            </label>
            <label>
              <span>긴급 연락처</span>
              <input defaultValue="본인 인증 후 표시" readOnly />
            </label>
            <label>
              <span>처리 상태</span>
              <input defaultValue="접수 전 확인" readOnly />
            </label>
          </div>
          <button type="button">신고 접수 준비</button>
        </article>

        <article className="certificate-list">
          <div className="panel-toolbar">
            <div>
              <strong>증명서 발급</strong>
              <span>완료증, 잔액, 거래내역 증명서를 업무별로 분리합니다.</span>
            </div>
          </div>
          {certificateItems.map((item) => (
            <div className="certificate-item" key={item.title}>
              <strong>{item.title}</strong>
              <p>{item.description}</p>
              <span>{item.scope}</span>
              <button type="button">발급 화면 보기</button>
            </div>
          ))}
        </article>
      </section>
    </section>
  );
}
