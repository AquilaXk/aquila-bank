// 카드 내용을 데이터로 분리해 초기 랜딩 화면을 나중에 쉽게 교체하거나 확장할 수 있게 합니다.
const highlights = [
  {
    title: "Real-time Alerts",
    text: "이벤트 기반 알림 흐름을 기준으로 중복 발행 방지와 재시도 전략을 프런트 UX와 함께 검증합니다.",
  },
  {
    title: "Massive Transaction Search",
    text: "1억 건 규모 거래 조회를 가정하고 기간, 계좌, 상태, 금액 축을 빠르게 좁히는 인터페이스를 준비합니다.",
  },
  {
    title: "Operational Readiness",
    text: "권한, 감사, 장애 대응, 성능 계측을 서비스 화면과 운영 화면 모두에서 확인할 수 있게 확장합니다.",
  },
];

export default function HomePage() {
  return (
    <main className="shell">
      <section className="hero">
        {/* hero 영역에서 왜 repo를 처음부터 front/back으로 나눴는지 설명합니다. */}
        <p className="eyebrow">Aquila Bank Workspace</p>
        <h1>고신뢰 웹뱅킹을 위한 front/back 분리 워크스페이스</h1>
        <p className="lead">
          실시간 알림과 초대형 거래 조회를 함께 다루는 구조를 기준으로 프런트와
          백엔드를 분리했습니다. 이제 고객 채널, 운영 콘솔, API, 비동기 처리
          경계를 독립적으로 확장할 수 있습니다.
        </p>
        <div className="hero-grid">
          <div className="metric">
            <span className="metric-label">Target Volume</span>
            <strong>100M+</strong>
          </div>
          <div className="metric">
            <span className="metric-label">Notification Mode</span>
            <strong>Real-time</strong>
          </div>
          <div className="metric">
            <span className="metric-label">Architecture</span>
            <strong>front / back</strong>
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <p className="eyebrow">Initial Direction</p>
          <h2>초기 프런트 골격</h2>
        </div>
        <div className="cards">
          {/* 이 카드들은 현재 워크스페이스가 맞추려는 세 가지 delivery 축을 보여줍니다. */}
          {highlights.map((item) => (
            <article className="card" key={item.title}>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
