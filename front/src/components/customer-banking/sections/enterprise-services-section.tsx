import { enterpriseServiceItems } from "@/lib/customer-banking/constants";

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
    </section>
  );
}
