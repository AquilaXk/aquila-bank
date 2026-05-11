import { supportCenterItems } from "@/lib/customer-banking/constants";

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
    </section>
  );
}
