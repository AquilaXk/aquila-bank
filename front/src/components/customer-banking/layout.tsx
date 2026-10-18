import Image from "next/image";
import { useEffect, useState } from "react";
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
import type { MenuSection, ServiceSearchItem } from "@/lib/customer-banking/types";
import { BankNoticeStrip } from "./common";

type MoveHandler = (section: MenuSection) => void;

export function BankHeader({
  activeSection,
  mobileSearchOpen,
  searchQuery,
  searchResults,
  serviceMapOpen,
  session,
  onMobileSearchToggle,
  onMove,
  onSearchChange,
  onSearchSelect,
  onServiceMapToggle,
}: {
  activeSection: MenuSection;
  mobileSearchOpen: boolean;
  searchQuery: string;
  searchResults: ServiceSearchItem[];
  serviceMapOpen: boolean;
  session: CustomerSession | null;
  onMobileSearchToggle: () => void;
  onMove: MoveHandler;
  onSearchChange: (value: string) => void;
  onSearchSelect: (item: ServiceSearchItem) => void;
  onServiceMapToggle: () => void;
}) {
  const isAuthenticated = Boolean(session);
  const hasSearchQuery = searchQuery.trim().length > 0;
  const [colorScheme, setColorScheme] = useState<"light" | "dark">("light");

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const resolveScheme = (stored: string | null): "light" | "dark" => {
      if (stored === "light" || stored === "dark") {
        return stored;
      }
      return media.matches ? "dark" : "light";
    };
    const applyScheme = (stored: string | null) => {
      const meta = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
      if (stored === "light" || stored === "dark") {
        if (meta) meta.content = stored;
        document.documentElement.dataset.theme = stored;
      } else {
        if (meta) meta.content = "light dark";
        delete document.documentElement.dataset.theme;
      }
      setColorScheme(resolveScheme(stored));
    };

    try {
      applyScheme(localStorage.getItem("color-scheme"));
    } catch {
      setColorScheme(media.matches ? "dark" : "light");
    }

    const handleStorage = (event: StorageEvent) => {
      if (event.key === "color-scheme") {
        applyScheme(event.newValue);
      }
    };
    const handleMediaChange = () => {
      try {
        const stored = localStorage.getItem("color-scheme");
        if (stored !== "light" && stored !== "dark") {
          setColorScheme(media.matches ? "dark" : "light");
        }
      } catch {
        setColorScheme(media.matches ? "dark" : "light");
      }
    };

    window.addEventListener("storage", handleStorage);
    media.addEventListener("change", handleMediaChange);
    return () => {
      window.removeEventListener("storage", handleStorage);
      media.removeEventListener("change", handleMediaChange);
    };
  }, []);

  function toggleColorScheme() {
    const nextScheme: "light" | "dark" = colorScheme === "dark" ? "light" : "dark";
    const systemDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
    const matchesSystem =
      (nextScheme === "dark" && systemDark) || (nextScheme === "light" && !systemDark);
    const meta = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
    try {
      if (matchesSystem) {
        localStorage.removeItem("color-scheme");
        if (meta) meta.content = "light dark";
        delete document.documentElement.dataset.theme;
      } else {
        localStorage.setItem("color-scheme", nextScheme);
        if (meta) meta.content = nextScheme;
        document.documentElement.dataset.theme = nextScheme;
      }
    } catch {
      if (meta) meta.content = nextScheme;
      document.documentElement.dataset.theme = nextScheme;
    }
    setColorScheme(nextScheme);
  }

  const topNavItems: Array<{
    id: string;
    label: string;
    section: MenuSection;
    activeSections: MenuSection[];
  }> = [
    {
      id: "personal",
      label: "개인",
      section: "dashboard",
      activeSections: ["dashboard", "accounts", "transfer", "transactions"],
    },
    {
      id: "business",
      label: "기업",
      section: "enterpriseServices",
      activeSections: ["enterpriseServices"],
    },
    {
      id: "certificate",
      label: "인증센터",
      section: "security",
      activeSections: ["security", "securityHub"],
    },
    {
      id: "support",
      label: "고객센터",
      section: "supportCenter",
      activeSections: ["supportCenter", "notifications"],
    },
  ];
  const activeTopNavId =
    topNavItems.find((item) => item.activeSections.includes(activeSection))?.id ??
    "personal";

  function moveFromServiceMap(section: MenuSection) {
    onServiceMapToggle();
    onMove(section);
  }

  return (
    <header className="bank-header">
      <div className="utility-bar mobile-compact-utility" aria-label="상단 유틸리티">
        <div className="utility-left top-channel-nav scroll-affordance-x">
          {topNavItems.map((item) => {
            const isActive = activeTopNavId === item.id;

            return (
              <button
                aria-current={isActive ? "page" : undefined}
                className={
                  isActive
                    ? "utility-link top-channel-link active"
                    : "utility-link top-channel-link"
                }
                key={item.id}
                onClick={() => onMove(item.section)}
                type="button"
              >
                {item.label}
              </button>
            );
          })}
        </div>
        <div className="utility-right">
          <button
            aria-controls="service-map-panel"
            aria-expanded={serviceMapOpen}
            className="utility-link service-map spacing-safe-service-map"
            onClick={onServiceMapToggle}
            type="button"
          >
            전체서비스
          </button>
          <button
            aria-label="테마 전환"
            aria-pressed={colorScheme === "dark"}
            className="utility-link theme-toggle-button"
            onClick={toggleColorScheme}
            type="button"
          >
            {colorScheme === "dark" ? "라이트 모드" : "다크 모드"}
          </button>
          <button
            aria-controls="utility-search-panel"
            aria-expanded={mobileSearchOpen}
            aria-label={mobileSearchOpen ? "검색 닫기" : "검색 열기"}
            className="mobile-search-toggle"
            onClick={onMobileSearchToggle}
            type="button"
          >
            검색
          </button>
          <div
            className={
              mobileSearchOpen
                ? "utility-search spacing-safe-search open"
                : "utility-search spacing-safe-search"
            }
            id="utility-search-panel"
          >
            <div className="search-field">
              <label htmlFor="utility-search-input">통합검색</label>
              <div className="search-input-row">
                <input
                  aria-label="통합검색"
                  id="utility-search-input"
                  onChange={(event) => onSearchChange(event.target.value)}
                  placeholder="업무명 또는 메뉴 검색"
                  value={searchQuery}
                />
                {hasSearchQuery ? (
                  <button
                    aria-label="검색어 지우기"
                    className="search-clear-button"
                    onClick={() => onSearchChange("")}
                    type="button"
                  >
                    지우기
                  </button>
                ) : null}
              </div>
            </div>
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
            {hasSearchQuery ? (
              <div className="service-search-panel" aria-label="통합검색 결과">
                {searchResults.length > 0 ? (
                  searchResults.map((item) => (
                    <button
                      className="service-search-item"
                      key={item.id}
                      onClick={() => onSearchSelect(item)}
                      type="button"
                    >
                      <span>{item.group}</span>
                      <strong>{item.label}</strong>
                      <small>{item.description}</small>
                    </button>
                  ))
                ) : (
                  <div className="service-search-empty">검색 결과 없음</div>
                )}
              </div>
            ) : null}
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
        <nav
          className="primary-nav mobile-compact-primary-nav scroll-affordance-x"
          aria-label="주요 메뉴"
        >
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
        <div className="header-user-chip" aria-label="사용자 칩">
          <span
            className={isAuthenticated ? "chip-dot active" : "chip-dot idle"}
            aria-hidden="true"
          />
          <strong>
            {isAuthenticated
              ? (session?.userId ? `회원 ID: ${session.userId}` : "인증 회원")
              : "보호모드"}
          </strong>
          <span>{isAuthenticated ? "세션 연결" : "인증 필요"}</span>
        </div>
      </div>
      <div className="bank-service-strip" aria-label="뱅킹 이용 상태">
        <span>
          보안등급 <strong>{isAuthenticated ? "정상" : "보호모드"}</strong>
        </span>
        <span>{isAuthenticated ? "HTTPS 보안접속" : "민감정보 보호"}</span>
        <span>평일 09:00-18:00 상담</span>
        <span>{isAuthenticated ? "서비스 상태 정상" : "인증센터 로그인"}</span>
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
    <aside
      className="side-menu compact-menu mobile-compact-side-menu spacing-safe-side-menu scroll-affordance-x"
      aria-label="개인뱅킹 메뉴"
    >
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
              <dt>접속 고객</dt>
              <dd>{session.userId ? `${session.userId}번 고객` : "-"}</dd>
            </div>
            <div>
              <dt>인증 방식</dt>
              <dd>보안 세션</dd>
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
            { label: "피싱예방", value: "공식 도메인 확인" },
            {
              label: "이체보호",
              value: isAuthenticated ? "OTP·추가인증 적용" : "인증 후 이용 가능",
            },
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
