import { securityHubItems } from "@/lib/customer-banking/constants";

const registrationFlows = [
  {
    title: "공동인증서 등록",
    description: "인증서 선택, 비밀번호 확인, 타기관 인증서 등록 여부, 만료일 확인을 실제 인증센터 흐름처럼 분리합니다.",
    steps: ["인증서 선택", "비밀번호 확인", "타기관 등록 확인", "등록 완료"],
    checks: ["브라우저 저장소 저장 금지", "서명 원문 미보관", "만료일/발급기관 표시"],
  },
  {
    title: "금융인증서 등록",
    description: "클라우드 인증 요청, 휴대폰 본인확인, 간편 비밀번호 확인, 기기 등록 상태를 보여줍니다.",
    steps: ["본인확인", "클라우드 인증", "기기 확인", "사용 등록"],
    checks: ["세션 내 요청번호만 유지", "인증 완료 시각 표시", "재등록 경로 제공"],
  },
  {
    title: "OTP 등록",
    description: "실물 OTP와 모바일 OTP를 나눠 일련번호, 제조사, 보안등급, 이체한도 반영 상태를 확인합니다.",
    steps: ["매체 선택", "일련번호 확인", "OTP 검증", "한도 반영"],
    checks: ["OTP 원문 저장 금지", "오류 횟수 안내", "분실 신고 연결"],
  },
  {
    title: "보안매체 등록",
    description: "보안카드, 모바일 OTP, 대체 인증수단을 은행권 보안매체 관리 화면 기준으로 정리합니다.",
    steps: ["매체 종류 선택", "본인확인", "매체 상태 확인", "업무별 적용"],
    checks: ["등급별 한도 표시", "해지/재발급 분리", "고위험 업무 안내"],
  },
];

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

      <section className="registration-flow-grid" aria-label="인증센터 등록 흐름">
        {registrationFlows.map((flow) => (
          <article className="registration-flow-card" key={flow.title}>
            <div>
              <span>인증센터</span>
              <strong>{flow.title}</strong>
              <p>{flow.description}</p>
            </div>
            <ol className="process-strip" aria-label={`${flow.title} 단계`}>
              {flow.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
            <ul className="check-list">
              {flow.checks.map((check) => (
                <li key={check}>{check}</li>
              ))}
            </ul>
          </article>
        ))}
      </section>
    </section>
  );
}
