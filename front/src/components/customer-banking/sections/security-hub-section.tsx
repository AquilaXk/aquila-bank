import { securityHubItems } from "@/lib/customer-banking/constants";

export function SecurityHubSection() {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>보안센터</p>
          <h1>인증서 · OTP · 보안매체</h1>
        </div>
      </div>

      <div className="security-hub-grid">
        {securityHubItems.map((item) => (
          <article className="security-hub-card" key={item.title}>
            <strong>{item.title}</strong>
            <p>{item.description}</p>
            <span>{item.status}</span>
          </article>
        ))}
      </div>

      <section className="table-panel">
        <div className="panel-toolbar">
          <div>
            <strong>보안매체별 적용 업무</strong>
            <span>실제 비밀값 저장 없이 화면 기준만 제공합니다.</span>
          </div>
        </div>
        <div className="bank-table-wrap">
          <table className="bank-table">
            <caption>보안매체별 적용 업무</caption>
            <thead>
              <tr>
                <th>구분</th>
                <th>주요 업무</th>
                <th>확인 단계</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>공동인증서</td>
                <td>고위험 이체, 인증서 관리</td>
                <td>비밀번호/전자서명</td>
              </tr>
              <tr>
                <td>금융인증서</td>
                <td>로그인, 조회, 일부 이체</td>
                <td>클라우드 인증</td>
              </tr>
              <tr>
                <td>OTP</td>
                <td>즉시이체, 한도 상향</td>
                <td>일회용 비밀번호</td>
              </tr>
              <tr>
                <td>보안매체</td>
                <td>보안카드, 모바일 OTP</td>
                <td>매체별 등급 확인</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}
