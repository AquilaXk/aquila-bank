import Image from "next/image";
import type { CustomerSession } from "@/lib/api/types";
import {
  mainMenus,
  noticeItems,
  quickMenus,
  recentMenus,
  recommendedKeywords,
  serviceMapGroups,
} from "@/lib/customer-banking/constants";
import { formatDateTime } from "@/lib/customer-banking/format";
import type { MenuSection } from "@/lib/customer-banking/types";
import { BankNoticeStrip } from "./common";

type MoveHandler = (section: MenuSection) => void;

export function BankHeader({
  activeSection,
  mobileSearchOpen,
  searchQuery,
  serviceMapOpen,
  session,
  sessionRequired,
  onMobileSearchToggle,
  onMove,
  onSearchChange,
  onServiceMapToggle,
}: {
  activeSection: MenuSection;
  mobileSearchOpen: boolean;
  searchQuery: string;
  serviceMapOpen: boolean;
  session: CustomerSession | null;
  sessionRequired: boolean;
  onMobileSearchToggle: () => void;
  onMove: MoveHandler;
  onSearchChange: (value: string) => void;
  onServiceMapToggle: () => void;
}) {
  const isAuthenticated = Boolean(session);

  function moveFromServiceMap(section: MenuSection) {
    onServiceMapToggle();
    onMove(section);
  }

  return (
    <header className="bank-header">
      <div className="utility-bar mobile-compact-utility" aria-label="상단 유틸리티">
        <div className="utility-left">
          <button className="utility-link active" type="button">
            개인
          </button>
          <button className="utility-link" type="button">
            기업
          </button>
          <button
            className="utility-link"
            onClick={() => onMove("security")}
            type="button"
          >
            인증센터
          </button>
          <button
            className="utility-link"
            onClick={() => onMove("supportCenter")}
            type="button"
          >
            고객센터
          </button>
        </div>
        <div className="utility-right">
          <button
            aria-controls="service-map-panel"
            aria-expanded={serviceMapOpen}
            className="utility-link service-map"
            onClick={onServiceMapToggle}
            type="button"
          >
            전체서비스
          </button>
          <button
            aria-controls="utility-search-panel"
            aria-expanded={mobileSearchOpen}
            className="mobile-search-toggle"
            onClick={onMobileSearchToggle}
            type="button"
          >
            검색
          </button>
          <div
            className={mobileSearchOpen ? "utility-search open" : "utility-search"}
            id="utility-search-panel"
          >
            <label className="search-field">
              <span>통합검색</span>
              <input
                aria-label="통합검색"
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="업무명 또는 메뉴 검색"
                value={searchQuery}
              />
            </label>
            <div className="keyword-list" aria-label="추천검색어">
              <span>추천검색어</span>
              {recommendedKeywords.map((keyword) => (
                <button
                  key={keyword}
                  onClick={() => onSearchChange(keyword)}
                  type="button"
                >
                  {keyword}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
      <div className="brand-row">
        <div className="brand-mark" aria-label="Aquila Bank">
          <span className="brand-symbol" aria-hidden="true">
            <Image
              alt=""
              height={34}
              priority
              src="/brand-mascot.png"
              width={34}
            />
          </span>
          <div>
            <strong>Aquila Bank</strong>
            <small>Personal Internet Banking</small>
          </div>
        </div>
        <nav className="primary-nav mobile-compact-primary-nav" aria-label="주요 메뉴">
          {mainMenus.map((item) => (
            <button
              aria-current={activeSection === item.id ? "page" : undefined}
              className={activeSection === item.id ? "nav-tab active" : "nav-tab"}
              key={item.id}
              onClick={() => onMove(item.id)}
              type="button"
            >
              {item.label}
            </button>
          ))}
        </nav>
      </div>
      <div className="bank-service-strip gothic-state-strip" aria-label="뱅킹 이용 상태">
        <span>
          보안등급 <strong>{isAuthenticated ? "정상" : "보호모드"}</strong>
        </span>
        <span>{isAuthenticated ? "HTTPS 보안접속" : "민감정보 보호"}</span>
        <span>평일 09:00-18:00 상담</span>
        <span>{isAuthenticated ? "서비스 상태 정상" : "인증센터 로그인"}</span>
      </div>
      <div className="layout-health-strip" aria-label="화면 이용 상태">
        <span>개인뱅킹</span>
        <span>{isAuthenticated ? "로그인 정상" : "보호모드"}</span>
        <span>
          {isAuthenticated
            ? sessionRequired
              ? "로그인 필요"
              : "업무 가능"
            : "로그인 후 업무 가능"}
        </span>
      </div>
      {serviceMapOpen ? (
        <section
          aria-label="전체서비스 메뉴"
          className="service-map-panel"
          id="service-map-panel"
        >
          <div className="service-map-title">
            <strong>전체서비스 메뉴</strong>
            <span>개인뱅킹</span>
          </div>
          <div className="service-map-grid">
            {serviceMapGroups.map((group) => (
              <div className="service-map-group" key={group.title}>
                <strong>{group.title}</strong>
                {group.items.map((item) => (
                  <button
                    key={`${group.title}-${item.label}`}
                    onClick={() => moveFromServiceMap(item.section)}
                    type="button"
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            ))}
          </div>
        </section>
      ) : null}
    </header>
  );
}

export function SideMenu({
  activeSection,
  onMove,
}: {
  activeSection: MenuSection;
  onMove: MoveHandler;
}) {
  return (
    <aside className="side-menu compact-menu mobile-compact-side-menu" aria-label="개인뱅킹 메뉴">
      <div className="side-title">개인뱅킹</div>
      <a className="mobile-work-jump" href="#bank-work-area">
        모바일 업무 바로가기
      </a>
      {mainMenus.map((item) => (
        <button
          aria-current={activeSection === item.id ? "page" : undefined}
          className={activeSection === item.id ? "side-item active" : "side-item"}
          key={item.id}
          onClick={() => onMove(item.id)}
          type="button"
        >
          <span>{item.group}</span>
          <strong>{item.label}</strong>
        </button>
      ))}
    </aside>
  );
}

export function RightRail({
  isBusy,
  session,
  onMove,
  onRefresh,
}: {
  isBusy: boolean;
  session: CustomerSession | null;
  onMove: MoveHandler;
  onRefresh: () => void;
}) {
  const isAuthenticated = Boolean(session);

  return (
    <aside
      className={`right-rail aligned-rail ${isAuthenticated ? "" : "guest-protected"}`.trim()}
      aria-label="빠른 업무"
    >
      <section className="rail-panel login-panel">
        <div className="rail-heading">
          <span>로그인 상태</span>
          <strong>{isAuthenticated ? "정상" : "보호모드"}</strong>
        </div>
        {isAuthenticated && session ? (
          <dl className="session-summary">
            <div>
              <dt>User ID</dt>
              <dd>{session.userId ?? "-"}</dd>
            </div>
            <div>
              <dt>세션 방식</dt>
              <dd>{session.tokenType}</dd>
            </div>
            <div>
              <dt>만료</dt>
              <dd>{formatDateTime(session.expiresAt)}</dd>
            </div>
          </dl>
        ) : (
          <p className="rail-copy sensitive-placeholder">
            로그인 후 업무 가능. 고객별 조회, 이체, 거래내역은 인증 후 표시됩니다.
          </p>
        )}
        <div className="button-row compact rail-auth-actions">
          <button onClick={() => onMove("security")} type="button">
            인증센터
          </button>
          <button disabled={!session || isBusy} onClick={onRefresh} type="button">
            재발급
          </button>
        </div>
      </section>

      <section className="rail-panel">
        <div className="rail-heading">
          <span>빠른메뉴</span>
        </div>
        <div className="quick-grid">
          {quickMenus.map((item) => (
            <button
              key={item.label}
              onClick={() => onMove(item.section)}
              type="button"
            >
              {item.label}
            </button>
          ))}
        </div>
      </section>

      <section className="rail-panel recent-list">
        <div className="rail-heading">
          <span>{isAuthenticated ? "최근 이용 메뉴" : "최근 이용 메뉴 잠김"}</span>
        </div>
        {isAuthenticated ? (
          <div className="recent-menu-list">
            {recentMenus.map((item) => (
              <button
                key={item.label}
                onClick={() => onMove(item.section)}
                type="button"
              >
                {item.label}
              </button>
            ))}
          </div>
        ) : (
          <p className="rail-copy sensitive-placeholder">
            최근 이용 내역은 로그인 후 확인할 수 있습니다.
          </p>
        )}
      </section>

      <section className="rail-panel security-notice">
        <div className="rail-heading">
          <span>보안알림</span>
        </div>
        <BankNoticeStrip
          items={[
            { label: "보안등급", value: isAuthenticated ? "정상" : "로그인 후 확인" },
            { label: "접속상태", value: "HTTPS" },
            {
              label: "인증수단",
              value: isAuthenticated ? "세션 활성" : "인증센터 로그인",
            },
            { label: "민감정보", value: isAuthenticated ? "표시 가능" : "보호모드" },
          ]}
        />
      </section>

      <section className="rail-panel notice-list">
        <div className="rail-heading">
          <span>공지사항</span>
        </div>
        <ul>
          {noticeItems.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>
    </aside>
  );
}
