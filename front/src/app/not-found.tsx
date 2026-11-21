import Link from "next/link";

export default function NotFoundPage() {
  return (
    <main className="bank-error-page" id="main-content">
      <section className="bank-error-card">
        <p className="field-note">안내</p>
        <h1>요청하신 화면을 찾을 수 없습니다</h1>
        <p className="rail-copy">
          주소가 변경되었거나 존재하지 않는 경로입니다. 개인 인터넷뱅킹 첫 화면으로 이동하세요.
        </p>
        <div className="button-row">
          <Link className="header-utility-pill" href="/">
            뱅킹홈으로 이동
          </Link>
        </div>
      </section>
    </main>
  );
}
