"use client";

import Link from "next/link";

export default function GlobalErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="bank-error-page" id="main-content">
      <section className="bank-error-card" role="alert">
        <p className="field-note">서비스 이용 안내</p>
        <h1>일시적인 화면 오류가 발생했습니다</h1>
        <p className="rail-copy">
          잠시 후 다시 시도하시거나 뱅킹홈으로 이동하여 업무를 이어서 진행하세요.
        </p>
        {error.digest ? (
          <p className="field-note tabular-nums">오류 식별번호: {error.digest}</p>
        ) : null}
        <div className="button-row">
          <button onClick={() => reset()} type="button">
            다시 시도
          </button>
          <Link className="header-utility-pill" href="/">
            뱅킹홈으로 이동
          </Link>
        </div>
      </section>
    </main>
  );
}
