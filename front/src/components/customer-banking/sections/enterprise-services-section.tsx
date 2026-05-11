import { enterpriseServiceItems } from "@/lib/customer-banking/constants";

const fulfillmentDetails = [
  {
    title: "공과금 상세",
    headline: "지로/지방세/아파트관리비 납부",
    description: "기관 선택, 납부번호 조회, 납부 예정금액 확인, 납부확인증 발급 흐름을 한 화면에 배치합니다.",
    fields: ["납부기관", "전자납부번호", "출금계좌", "납부예정일"],
    steps: ["기관 선택", "납부번호 조회", "금액 확인", "확인증 발급"],
    status: "조회/확인증 화면 준비",
  },
  {
    title: "오픈뱅킹 상세",
    headline: "타행 계좌 연결 및 통합조회",
    description: "동의 상태, 연결 은행, 대표 계좌, 잔액 갱신 시각을 고객이 반복 조회하기 쉬운 표 형태로 제공합니다.",
    fields: ["은행", "계좌 별칭", "동의 만료일", "최근 동기화"],
    steps: ["은행 선택", "약관 동의", "계좌 확인", "통합조회"],
    status: "연결 상태 read-only",
  },
  {
    title: "예금상품 상세",
    headline: "정기예금/입출금 상품 비교",
    description: "금리, 가입 기간, 우대 조건, 중도해지 기준을 실제 상품몰처럼 비교 가능한 정보 구조로 확장합니다.",
    fields: ["상품명", "기본금리", "우대조건", "가입기간"],
    steps: ["상품 비교", "유의사항 확인", "예상 이자 계산", "상담 연결"],
    status: "상품 상세 안내",
  },
  {
    title: "대출 상세",
    headline: "한도조회/상환조회/서류 안내",
    description: "한도조회 준비 정보와 상환 스케줄, 필요 서류, 금리 변동 안내를 분리해 보여줍니다.",
    fields: ["대출 유형", "예상 한도", "상환 방식", "필요 서류"],
    steps: ["기본정보 확인", "한도 사전조회", "서류 안내", "상담 예약"],
    status: "조회형 상세",
  },
  {
    title: "외환 상세",
    headline: "환율/외화예금/해외송금 준비",
    description: "통화별 환율, 우대율, 외화예금 가능 여부, 해외송금 준비 정보를 은행권 외환 메뉴처럼 묶습니다.",
    fields: ["통화", "고시환율", "우대율", "송금 목적"],
    steps: ["환율 조회", "우대 조건 확인", "외화계좌 선택", "송금 준비"],
    status: "환율/준비 정보",
  },
];

export function EnterpriseServicesSection() {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>부가업무</p>
          <h1>공과금 · 오픈뱅킹 · 금융상품</h1>
        </div>
      </div>

      <div className="enterprise-service-grid">
        {enterpriseServiceItems.map((item) => (
          <article className="enterprise-service-card" key={item.title}>
            <span>{item.category}</span>
            <strong>{item.title}</strong>
            <p>{item.description}</p>
            <small>{item.status}</small>
          </article>
        ))}
      </div>

      <div className="bank-table-wrap">
        <table className="bank-table">
          <caption>부가업무 처리 기준</caption>
          <thead>
            <tr>
              <th>업무</th>
              <th>상용 화면 기준</th>
              <th>현재 범위</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>공과금</td>
              <td>기관 선택, 납부번호 조회, 납부 확인증</td>
              <td>read-only 메뉴</td>
            </tr>
            <tr>
              <td>오픈뱅킹</td>
              <td>타행 계좌 연결, 잔액 통합조회, 해지</td>
              <td>read-only 메뉴</td>
            </tr>
            <tr>
              <td>예금상품</td>
              <td>상품 목록, 금리, 가입 전 유의사항</td>
              <td>상품 안내</td>
            </tr>
            <tr>
              <td>대출</td>
              <td>한도조회, 신청, 상환 조회</td>
              <td>상담 안내</td>
            </tr>
            <tr>
              <td>외환</td>
              <td>환율 조회, 외화예금, 해외송금</td>
              <td>환율 조회</td>
            </tr>
          </tbody>
        </table>
      </div>

      <section className="fulfillment-detail-grid" aria-label="부가업무 상세 화면">
        {fulfillmentDetails.map((item) => (
          <article className="fulfillment-detail-card" key={item.title}>
            <div className="fulfillment-detail-head">
              <span>{item.title}</span>
              <strong>{item.headline}</strong>
              <p>{item.description}</p>
            </div>
            <dl className="detail-list">
              <div>
                <dt>필수 입력</dt>
                <dd>{item.fields.join(" / ")}</dd>
              </div>
              <div>
                <dt>현재 상태</dt>
                <dd>{item.status}</dd>
              </div>
            </dl>
            <ol className="process-strip" aria-label={`${item.title} 처리 단계`}>
              {item.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </article>
        ))}
      </section>
    </section>
  );
}
