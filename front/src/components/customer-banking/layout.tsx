import Image from "next/image";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import type { AccountItem, AccountPreferenceItem, CustomerSession } from "@/lib/api/types";
import {
  mainMenus,
  noticeItems,
  quickMenus,
  recommendedKeywords,
  serviceMapGroups,
} from "@/lib/customer-banking/constants";
import { formatDateTime, formatMinorAmount } from "@/lib/customer-banking/format";
import {
  subscribeColorScheme,
  toggleStoredColorScheme,
  type ColorScheme,
} from "@/lib/customer-banking/theme";
import type { MenuSection, ServiceSearchItem } from "@/lib/customer-banking/types";
import {
  BellIcon,
  ChevronRightIcon,
  CommandIcon,
  EyeIcon,
  EyeOffIcon,
  MenuSectionIcon,
  MoonIcon,
  SearchIcon,
  SunIcon,
} from "../fintech-ui/icons";
import { BankNoticeStrip } from "./common";

type MoveHandler = (section: MenuSection) => void;

const IME_KEY_CODE = 229;
const IME_COMPOSITION_COOLDOWN_MS = 50;
const MASKED_BALANCE = "••••••";

export function BankHeader({
  activeSection,
  balanceMasked,
  mobileSearchOpen,
  searchQuery,
  searchResults,
  serviceMapOpen,
  session,
  unreadCount,
  onMobileSearchToggle,
  onMove,
  onOpenCommandPalette,
  onSearchChange,
  onSearchSelect,
  onServiceMapToggle,
  onToggleBalanceMask,
}: {
  activeSection: MenuSection;
  balanceMasked: boolean;
  mobileSearchOpen: boolean;
  searchQuery: string;
  searchResults: ServiceSearchItem[];
  serviceMapOpen: boolean;
  session: CustomerSession | null;
  unreadCount: number | null;
  onMobileSearchToggle: () => void;
  onMove: MoveHandler;
  onOpenCommandPalette: () => void;
  onSearchChange: (value: string) => void;
  onSearchSelect: (item: ServiceSearchItem) => void;
  onServiceMapToggle: () => void;
  onToggleBalanceMask: () => void;
}) {
  const isAuthenticated = Boolean(session);
  const hasSearchQuery = searchQuery.trim().length > 0;
  const [colorScheme, setColorScheme] = useState<ColorScheme>("light");
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const searchPanelRef = useRef<HTMLDivElement>(null);
  const compositionEndedAtRef = useRef(0);

  useEffect(() => {
    return subscribeColorScheme(setColorScheme);
  }, []);

  useEffect(() => {
    setHighlightedIndex(0);
  }, [searchQuery, searchResults.length]);

  useEffect(() => {
    const handleOutsidePointerDown = (event: globalThis.PointerEvent) => {
      if (
        hasSearchQuery &&
        searchPanelRef.current &&
        event.target instanceof Node &&
        !searchPanelRef.current.contains(event.target)
      ) {
        onSearchChange("");
      }
    };

    document.addEventListener("pointerdown", handleOutsidePointerDown);
    return () => {
      document.removeEventListener("pointerdown", handleOutsidePointerDown);
    };
  }, [hasSearchQuery, onSearchChange]);

  function toggleColorScheme() {
    setColorScheme((current) => toggleStoredColorScheme(current));
  }

  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    const inComposition =
      event.nativeEvent.isComposing ||
      event.keyCode === IME_KEY_CODE ||
      performance.now() - compositionEndedAtRef.current < IME_COMPOSITION_COOLDOWN_MS;
    if (inComposition) {
      return;
    }

    if (event.key === "Escape") {
      if (hasSearchQuery) {
        event.preventDefault();
        onSearchChange("");
      } else if (mobileSearchOpen) {
        event.preventDefault();
        onMobileSearchToggle();
      }
      return;
    }

    if (!hasSearchQuery || searchResults.length === 0) {
      return;
    }

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setHighlightedIndex((idx) => (idx + 1) % searchResults.length);
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      setHighlightedIndex((idx) => (idx - 1 + searchResults.length) % searchResults.length);
      return;
    }

    if (event.key === "Enter") {
      event.preventDefault();
      const target = searchResults[highlightedIndex] ?? searchResults[0];
      if (target) {
        onSearchSelect(target);
      }
    }
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

  const activeMenuLabel =
    mainMenus.find((item) => item.id === activeSection)?.label ?? "뱅킹홈";

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
            aria-label="빠른 메뉴 팔레트 열기"
            className="utility-link"
            onClick={onOpenCommandPalette}
            type="button"
          >
            <CommandIcon size={13} />
            <span>빠른실행</span>
          </button>
          <button
            aria-label="테마 전환"
            aria-pressed={colorScheme === "dark"}
            className="utility-link theme-toggle-button"
            onClick={toggleColorScheme}
            type="button"
          >
            {colorScheme === "dark" ? <SunIcon size={13} /> : <MoonIcon size={13} />}
            <span>{colorScheme === "dark" ? "라이트 모드" : "다크 모드"}</span>
          </button>
          <button
            aria-controls="utility-search-panel"
            aria-expanded={mobileSearchOpen}
            aria-label={mobileSearchOpen ? "검색 닫기" : "검색 열기"}
            className="mobile-search-toggle"
            onClick={onMobileSearchToggle}
            type="button"
          >
            <SearchIcon size={14} />
            <span>검색</span>
          </button>
          <div
            ref={searchPanelRef}
            className={
              mobileSearchOpen
                ? "utility-search spacing-safe-search open"
                : "utility-search spacing-safe-search"
            }
            id="utility-search-panel"
          >
            <div className="search-field">
              <label className="sr-only" htmlFor="utility-search-input">
                통합검색
              </label>
              <div className="search-input-row">
                <SearchIcon className="search-leading-icon" size={14} />
                <input
                  ref={searchInputRef}
                  aria-activedescendant={
                    hasSearchQuery && searchResults[highlightedIndex]
                      ? `search-option-${searchResults[highlightedIndex].id}`
                      : undefined
                  }
                  aria-controls="service-search-listbox"
                  aria-label="통합검색"
                  id="utility-search-input"
                  onChange={(event) => onSearchChange(event.target.value)}
                  onCompositionEnd={() => {
                    compositionEndedAtRef.current = performance.now();
                  }}
                  onKeyDown={handleSearchKeyDown}
                  placeholder="업무·계좌 검색 (⌘K)"
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
              <div
                aria-label="통합검색 결과"
                className="service-search-panel"
                id="service-search-listbox"
                role="listbox"
              >
                {searchResults.length > 0 ? (
                  searchResults.map((item, index) => {
                    const isHighlighted = index === highlightedIndex;
                    return (
                      <button
                        aria-selected={isHighlighted}
                        className={
                          isHighlighted
                            ? "service-search-item highlighted"
                            : "service-search-item"
                        }
                        id={`search-option-${item.id}`}
                        key={item.id}
                        onClick={() => onSearchSelect(item)}
                        onMouseEnter={() => setHighlightedIndex(index)}
                        role="option"
                        type="button"
                      >
                        <span>{item.group}</span>
                        <strong>{item.label}</strong>
                        <small>{item.description}</small>
                      </button>
                    );
                  })
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
              height={28}
              priority
              src="/brand-mascot.png"
              width={28}
            />
          </span>
          <div>
            <strong>Aquila Bank</strong>
            <small>개인인터넷뱅킹</small>
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
              ? (session?.userId ? `회원 ${session.userId}` : "인증 회원")
              : "보호모드"}
          </strong>
          <span>{isAuthenticated ? "세션 연결" : "인증 필요"}</span>
        </div>
      </div>
      <div className="bank-service-strip" aria-label="뱅킹 이용 상태">
        <span className="bank-breadcrumb" aria-label="현재 위치">
          홈 › 개인뱅킹 › <strong>{activeMenuLabel}</strong>
        </span>
        <span>
          보안등급 <strong>{isAuthenticated ? "정상" : "보호모드"}</strong>
        </span>
        <span>{isAuthenticated ? "HTTPS 보안접속" : "민감정보 보호"}</span>
        <span>평일 09:00-18:00 상담</span>
        <span>{isAuthenticated ? "서비스 상태 정상" : "인증센터 로그인"}</span>
        {isAuthenticated ? (
          <div className="header-compact-utility">
            <button
              aria-pressed={balanceMasked}
              className="header-utility-pill"
              onClick={onToggleBalanceMask}
              type="button"
            >
              {balanceMasked ? <EyeIcon size={13} /> : <EyeOffIcon size={13} />}
              <span>{balanceMasked ? "금액 표시" : "금액 숨김"}</span>
            </button>
            <button
              aria-label="알림함 이동"
              className="header-utility-pill"
              onClick={() => onMove("notifications")}
              type="button"
            >
              <BellIcon size={13} />
              <span>알림</span>
              {unreadCount !== null && unreadCount > 0 ? (
                <span className="header-unread-badge">{unreadCount}</span>
              ) : null}
            </button>
          </div>
        ) : null}
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
          <div aria-hidden="true" className="side-item-icon">
            <MenuSectionIcon section={item.id} size={15} />
          </div>
          <strong>{item.label}</strong>
          <span>{item.group}</span>
        </button>
      ))}
    </aside>
  );
}

export function RightRail({
  accountPreferencesMap,
  accounts = [],
  balanceMasked = false,
  isBusy,
  primaryAccountId,
  recentVisitedMenus,
  session,
  onMove,
  onRefresh,
}: {
  accountPreferencesMap?: ReadonlyMap<number, AccountPreferenceItem>;
  accounts?: AccountItem[];
  balanceMasked?: boolean;
  isBusy: boolean;
  primaryAccountId?: number | null;
  recentVisitedMenus: Array<{ label: string; section: MenuSection }>;
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
        <div className="rail-user-header">
          <div className="rail-user-avatar" aria-hidden="true">
            {isAuthenticated && session?.userId ? String(session.userId).slice(-2) : "AQ"}
          </div>
          <div className="rail-user-meta">
            <span>로그인 상태</span>
            <strong>{isAuthenticated ? "정상" : "보호모드"}</strong>
          </div>
          <span
            className={`rail-status-pill ${isAuthenticated ? "active" : ""}`}
          >
            {isAuthenticated ? "연결됨" : "대기"}
          </span>
        </div>

        {isAuthenticated && session ? (
          <dl className="session-summary">
            <div>
              <dt>접속 고객</dt>
              <dd className="tabular-nums">{session.userId ? `${session.userId}번 고객` : "-"}</dd>
            </div>
            <div>
              <dt>인증 방식</dt>
              <dd>보안 세션</dd>
            </div>
            <div>
              <dt>만료</dt>
              <dd className="tabular-nums">{formatDateTime(session.expiresAt)}</dd>
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

        {isAuthenticated && accounts.length > 0 ? (
          <div className="rail-account-mini-list" aria-label="보유계좌 바로가기">
            {accounts.slice(0, 3).map((acc) => {
              const alias =
                accountPreferencesMap?.get(acc.accountId)?.customAlias || acc.displayName;
              const isPrimary =
                Boolean(accountPreferencesMap?.get(acc.accountId)?.primaryAccount) ||
                acc.accountId === primaryAccountId;
              return (
                <button
                  className="rail-account-mini-item"
                  key={acc.accountId}
                  onClick={() => onMove("accounts")}
                  type="button"
                >
                  <div>
                    <strong>{alias}</strong>
                    <small className="font-mono tabular-nums">
                      {isPrimary ? "대표 · " : ""}
                      {acc.accountNumber}
                    </small>
                  </div>
                  <span className="font-mono tabular-nums">
                    {balanceMasked
                      ? MASKED_BALANCE
                      : formatMinorAmount(acc.availableBalanceMinor, acc.currencyCode)}
                  </span>
                </button>
              );
            })}
          </div>
        ) : null}
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
              <span aria-hidden="true" className="quick-tile-icon">
                <MenuSectionIcon section={item.section} size={15} />
              </span>
              <span>{item.label}</span>
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
            {recentVisitedMenus.map((item) => (
              <button
                key={`${item.section}-${item.label}`}
                onClick={() => onMove(item.section)}
                type="button"
              >
                <span className="ab-inline-icon">
                  <MenuSectionIcon section={item.section} size={14} />
                  <span>{item.label}</span>
                </span>
                <ChevronRightIcon size={14} />
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

const MOBILE_BOTTOM_ITEMS: Array<{ id: MenuSection; label: string }> = [
  { id: "dashboard", label: "홈" },
  { id: "accounts", label: "계좌" },
  { id: "transfer", label: "이체" },
  { id: "transactions", label: "내역" },
  { id: "security", label: "인증" },
];

export function MobileBottomNav({
  activeSection,
  onMove,
}: {
  activeSection: MenuSection;
  onMove: MoveHandler;
}) {
  return (
    <nav aria-label="모바일 하단 메뉴" className="mobile-bottom-nav">
      {MOBILE_BOTTOM_ITEMS.map((item) => (
        <button
          aria-current={activeSection === item.id ? "page" : undefined}
          className={activeSection === item.id ? "active" : undefined}
          key={item.id}
          onClick={() => onMove(item.id)}
          type="button"
        >
          <MenuSectionIcon section={item.id} size={16} />
          <span>{item.label}</span>
        </button>
      ))}
    </nav>
  );
}

export function BankFooter({ onMove }: { onMove: MoveHandler }) {
  return (
    <footer className="bank-footer" aria-label="인터넷뱅킹 안내">
      <div className="bank-footer-inner">
        <div className="bank-footer-links">
          <button onClick={() => onMove("supportCenter")} type="button">
            전자금융거래기본약관
          </button>
          <button onClick={() => onMove("security")} type="button">
            개인정보처리방침
          </button>
          <button onClick={() => onMove("securityHub")} type="button">
            전자금융사기예방안내
          </button>
          <button onClick={() => onMove("supportCenter")} type="button">
            고객센터
          </button>
        </div>
        <div className="bank-footer-meta">
          <span>대표문의 1599-8000 (평일 09:00~18:00)</span>
          <span>Aquila Bank 개인인터넷뱅킹</span>
        </div>
      </div>
    </footer>
  );
}
