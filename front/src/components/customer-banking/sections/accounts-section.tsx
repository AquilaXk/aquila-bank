import { useEffect, useState } from "react";
import type {
  AccountItem,
  AccountPreferenceItem,
  AccountSummaryResponse,
  SaveAccountPreferenceRequest,
  SaveSavingsPocketRequest,
  SavingsPocketItem,
} from "@/lib/api/types";
import { tryKrwToMinor } from "@/lib/customer-banking/currency";
import { formatDateTime, formatMinorAmount } from "@/lib/customer-banking/format";
import { getAccountStatusLabel } from "@/lib/customer-banking/labels";
import { HorizonBankCard } from "../../oss-fintech/bank-card";
import { PiggyBankIcon } from "../../oss-fintech/icons";
import { BarList, CategoryBar, ProgressCircle, type TremorColor } from "../../oss-fintech/tremor-primitives";
import {
  BankActionBar,
  BankCurrencyInput,
  BankNoticeStrip,
  BankSelect,
  BankTable,
  BankToolbar,
  CopyButton,
  EmptyState,
  PaginationBar,
  StatusBadge,
  WorkStateGrid,
  WorkTabs,
} from "../common";

const TREMOR_BAR_COLORS: TremorColor[] = ["blue", "emerald", "indigo", "amber", "slate"];
const MASKED_BALANCE = "••••••";

function getAccountStatusTone(status: string): "success" | "warn" | "danger" | "muted" {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "SUSPENDED":
    case "FROZEN":
      return "danger";
    case "RESTRICTED":
      return "warn";
    default:
      return "muted";
  }
}

export function AccountsSection({
  accountCursor,
  accountLimit,
  accountPreferencesMap,
  accounts,
  balanceMasked = false,
  isBusy,
  onAccountLimitChange,
  onCreateSavingsPocket,
  onDeleteSavingsPocket,
  onLoadAccountDetail,
  onLoadAccounts,
  onLoadNextAccounts,
  onSaveAccountPreference,
  onUpdateSavingsPocket,
  savingsPockets = [],
  selectedAccount,
}: {
  accountCursor: string;
  accountLimit: number;
  accountPreferencesMap?: ReadonlyMap<number, AccountPreferenceItem>;
  accounts: AccountItem[];
  balanceMasked?: boolean;
  isBusy: boolean;
  onAccountLimitChange: (value: number) => void;
  onCreateSavingsPocket?: (accountId: number, request: SaveSavingsPocketRequest) => Promise<boolean>;
  onDeleteSavingsPocket?: (accountId: number, pocketId: number) => Promise<boolean>;
  onLoadAccountDetail: (accountId: number) => void;
  onLoadAccounts: () => void;
  onLoadNextAccounts: () => void;
  onSaveAccountPreference?: (accountId: number, request: SaveAccountPreferenceRequest) => Promise<boolean>;
  onUpdateSavingsPocket?: (accountId: number, pocketId: number, request: SaveSavingsPocketRequest) => Promise<boolean>;
  savingsPockets?: SavingsPocketItem[];
  selectedAccount: AccountSummaryResponse | null;
}) {
  const selectedAccountId = selectedAccount?.accountId;
  const selectedPref = selectedAccountId ? accountPreferencesMap?.get(selectedAccountId) : undefined;
  const [aliasInput, setAliasInput] = useState("");
  const [primaryChecked, setPrimaryChecked] = useState(false);
  const [pocketName, setPocketName] = useState("");
  const [pocketTargetKrw, setPocketTargetKrw] = useState("");
  const [pocketSavedKrw, setPocketSavedKrw] = useState("0");

  useEffect(() => {
    setAliasInput(selectedPref?.customAlias ?? "");
    setPrimaryChecked(Boolean(selectedPref?.primaryAccount));
  }, [selectedAccountId, selectedPref?.customAlias, selectedPref?.primaryAccount]);

  // 단일 패스 순회로 상태별 건수와 통화별 출금가능금액 합계를 동시에 집계한다(통화 간 단순 합산 방지).
  let activeCount = 0;
  let suspendedCount = 0;
  let restrictedCount = 0;
  let lowBalanceCount = 0;
  const totalsByCurrency = new Map<string, { totalMinor: number; accounts: AccountItem[] }>();
  for (let i = 0; i < accounts.length; i += 1) {
    const account = accounts[i];
    if (account.accountStatus === "ACTIVE") {
      activeCount += 1;
    } else if (
      account.accountStatus === "SUSPENDED" ||
      account.accountStatus === "FROZEN"
    ) {
      suspendedCount += 1;
    } else if (account.accountStatus === "RESTRICTED") {
      restrictedCount += 1;
    }
    if (account.availableBalanceMinor <= 0) {
      lowBalanceCount += 1;
    } else {
      const group = totalsByCurrency.get(account.currencyCode) ?? {
        totalMinor: 0,
        accounts: [],
      };
      group.totalMinor += account.availableBalanceMinor;
      group.accounts.push(account);
      totalsByCurrency.set(account.currencyCode, group);
    }
  }
  const currencySummaryGroups = Array.from(totalsByCurrency.entries());

  return (
    <section className="task-section compact-work-section">
      <WorkTabs
        active={selectedAccount ? "계좌상세" : "전계좌조회"}
        items={[
          { id: "전계좌조회", label: "전계좌조회", onClick: onLoadAccounts },
          {
            id: "계좌상세",
            label: "계좌상세",
            onClick: () => {
              if (selectedAccount) {
                onLoadAccountDetail(selectedAccount.accountId);
              } else if (accounts.length > 0) {
                onLoadAccountDetail(accounts[0].accountId);
              } else {
                onLoadAccounts();
              }
            },
          },
        ]}
      />
      <div className="section-title">
        <div>
          <p>조회</p>
          <h1>계좌조회</h1>
        </div>
        <BankToolbar label="계좌조회 상단 업무 버튼">
          <BankSelect
            label="건수"
            onChange={(value) => onAccountLimitChange(Number(value))}
            options={[
              { label: "10", value: 10 },
              { label: "20", value: 20 },
              { label: "50", value: 50 },
            ]}
            value={accountLimit}
          />
          <button disabled={isBusy} onClick={onLoadAccounts} type="button">
            조회
          </button>
          <button disabled={!accountCursor || isBusy} onClick={onLoadNextAccounts} type="button">
            다음
          </button>
        </BankToolbar>
      </div>
      <BankNoticeStrip
        items={[
          { label: "조회구분", value: "전계좌조회" },
          { label: "표시건수", value: `${accountLimit}건` },
          { label: "상태", value: "정상/제한 계좌 포함" },
          { label: "통화", value: "계좌별 통화 표시" },
        ]}
      />
      <WorkStateGrid
        label="조회 업무 상태"
        items={[
          { label: "계좌목록", value: accounts.length > 0 ? `${accounts.length}건` : "조회 전" },
          {
            label: "상세조회",
            value: selectedAccount
              ? getAccountStatusLabel(selectedAccount.accountStatus)
              : "미선택",
          },
          { label: "다음조회", value: accountCursor ? "가능" : "없음" },
          { label: "처리상태", value: isBusy ? "조회 중" : "대기" },
        ]}
      />

      <div className="account-status-grid" aria-label="계좌 상태 요약">
        <div>
          <span>출금가능</span>
          <strong className="tabular-nums">{activeCount}건</strong>
          <small>정상 계좌</small>
        </div>
        <div>
          <span>지급정지</span>
          <strong className="tabular-nums">{suspendedCount}건</strong>
          <small>출금 제한</small>
        </div>
        <div>
          <span>거래제한</span>
          <strong className="tabular-nums">{restrictedCount}건</strong>
          <small>업무 확인 필요</small>
        </div>
        <div>
          <span>잔액부족</span>
          <strong className="tabular-nums">{lowBalanceCount}건</strong>
          <small>출금 가능액 확인</small>
        </div>
      </div>

      {accounts.length > 0 ? (
        <div className="oss-bank-card-grid" aria-label="계좌 카드 요약">
          {accounts.map((account) => {
            const pref = accountPreferencesMap?.get(account.accountId);
            return (
              <HorizonBankCard
                account={{
                  ...account,
                  customAlias: pref?.customAlias,
                  isPrimary: Boolean(pref?.primaryAccount),
                }}
                balanceMasked={balanceMasked}
                key={account.accountId}
                onSelect={onLoadAccountDetail}
                selected={account.accountId === selectedAccountId}
              />
            );
          })}
        </div>
      ) : null}

      {currencySummaryGroups.length > 0 ? (
        <div className="currency-summary-grid" aria-label="통화별 출금가능잔액 비중">
          {currencySummaryGroups.map(([currencyCode, group]) => (
            <div className="currency-summary-card" key={currencyCode}>
              <div className="balance-chart-head">
                <span>{currencyCode} 총 출금가능잔액</span>
                <strong className="tabular-nums font-mono">
                  {balanceMasked
                    ? MASKED_BALANCE
                    : formatMinorAmount(group.totalMinor, currencyCode)}
                </strong>
              </div>
              <CategoryBar
                colors={TREMOR_BAR_COLORS}
                label={`${currencyCode} 계좌별 비중`}
                values={group.accounts.map((account) => account.availableBalanceMinor)}
              />
              <BarList
                data={group.accounts.map((account, idx) => ({
                  key: String(account.accountId),
                  name:
                    accountPreferencesMap?.get(account.accountId)?.customAlias ||
                    account.displayName,
                  value: account.availableBalanceMinor,
                  formattedValue: balanceMasked
                    ? MASKED_BALANCE
                    : formatMinorAmount(account.availableBalanceMinor, account.currencyCode),
                  color: TREMOR_BAR_COLORS[idx % TREMOR_BAR_COLORS.length],
                }))}
              />
            </div>
          ))}
        </div>
      ) : null}

      <div className="account-grid">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>보유계좌</strong>
              <span>계좌별 잔액과 상태 · {accounts.length}건</span>
            </div>
          </div>
          <div className="table-scroll-hint">표는 좌우로 스크롤해서 볼 수 있습니다.</div>
          <BankTable caption="계좌 목록" className="dense-bank-table">
            <thead>
              <tr>
                <th>계좌번호</th>
                <th>계좌명</th>
                <th>상태</th>
                <th>출금가능액</th>
                <th>선택</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {accounts.length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    <EmptyState
                      title="계좌 조회 전"
                      description="전계좌조회 버튼으로 보유계좌를 확인하세요."
                    />
                  </td>
                </tr>
              ) : (
                accounts.map((account) => (
                  <tr
                    className={account.accountId === selectedAccountId ? "selected-row" : undefined}
                    key={account.accountId}
                  >
                    <td className="tabular-nums">{account.accountNumber}</td>
                    <td>
                      {accountPreferencesMap?.get(account.accountId)?.customAlias ||
                        account.displayName}
                      {accountPreferencesMap?.get(account.accountId)?.primaryAccount ? (
                        <StatusBadge className="ml-1" tone="success">
                          대표계좌
                        </StatusBadge>
                      ) : null}
                    </td>
                    <td>
                      <StatusBadge tone={getAccountStatusTone(account.accountStatus)}>
                        {getAccountStatusLabel(account.accountStatus)}
                      </StatusBadge>
                    </td>
                    <td className="amount-cell font-mono tabular-nums">
                      {balanceMasked
                        ? MASKED_BALANCE
                        : formatMinorAmount(
                            account.availableBalanceMinor,
                            account.currencyCode,
                          )}
                    </td>
                    <td>
                      {account.accountId === selectedAccountId ? (
                        <StatusBadge tone="success">선택계좌 선택됨</StatusBadge>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td>
                      <button
                        disabled={isBusy}
                        onClick={() => onLoadAccountDetail(account.accountId)}
                        type="button"
                      >
                        상세
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </BankTable>
          <PaginationBar
            disabled={isBusy}
            hasNext={Boolean(accountCursor)}
            label="계좌 목록 페이지 탐색"
            onNext={onLoadNextAccounts}
          />
        </section>

        <aside className="detail-panel account-detail-work-panel">
          <div className="form-heading">
            <strong>계좌 상태</strong>
            <span>
              {selectedAccount ? getAccountStatusLabel(selectedAccount.accountStatus) : "미선택"}
            </span>
          </div>
          {selectedAccount ? (
            <>
              <div className="balance-card">
                <span>{selectedPref?.customAlias || selectedAccount.displayName}</span>
                <strong className="font-mono tabular-nums">
                  {balanceMasked
                    ? MASKED_BALANCE
                    : formatMinorAmount(
                        selectedAccount.availableBalanceMinor,
                        selectedAccount.currencyCode,
                      )}
                </strong>
                <small>출금가능금액</small>
              </div>
              <dl className="detail-list">
                <div>
                  <dt>계좌번호</dt>
                  <dd className="tabular-nums inline-copy-cell">
                    <span>{selectedAccount.accountNumber}</span>
                    <CopyButton label="계좌번호" value={selectedAccount.accountNumber} />
                  </dd>
                </div>
                <div>
                  <dt>보류금액</dt>
                  <dd className="tabular-nums">
                    {balanceMasked
                      ? MASKED_BALANCE
                      : formatMinorAmount(
                          selectedAccount.pendingBalanceMinor,
                          selectedAccount.currencyCode,
                        )}
                  </dd>
                </div>
                <div>
                  <dt>잔액 갱신</dt>
                  <dd className="tabular-nums">{formatDateTime(selectedAccount.balanceUpdatedAt)}</dd>
                </div>
              </dl>
              <BankActionBar label="선택 계좌 상세 액션">
                <button
                  disabled={isBusy}
                  onClick={() => onLoadAccountDetail(selectedAccount.accountId)}
                  type="button"
                >
                  잔액 새로고침
                </button>
              </BankActionBar>
              {onSaveAccountPreference ? (
                <div className="bank-form passive compact">
                  <div className="form-heading">
                    <strong>계좌 별칭 · 대표계좌 설정</strong>
                    <span>{primaryChecked ? "대표계좌 지정" : "일반"}</span>
                  </div>
                  <label>
                    <span>계좌 별칭</span>
                    <input
                      maxLength={60}
                      onChange={(event) => setAliasInput(event.target.value)}
                      placeholder="예: 월급통장, 생활비"
                      value={aliasInput}
                    />
                  </label>
                  <label className="checkbox-line">
                    <input
                      checked={primaryChecked}
                      onChange={(event) => setPrimaryChecked(event.target.checked)}
                      type="checkbox"
                    />
                    <span>이 계좌를 대표계좌로 지정</span>
                  </label>
                  <button
                    disabled={isBusy}
                    onClick={() => {
                      void onSaveAccountPreference(selectedAccount.accountId, {
                        customAlias: aliasInput.trim(),
                        primaryAccount: primaryChecked,
                      });
                    }}
                    type="button"
                  >
                    계좌 설정 저장
                  </button>
                </div>
              ) : null}
              {onCreateSavingsPocket ? (
                <div className="bank-form passive compact" aria-label="목표 저축 포켓">
                  <div className="form-heading">
                    <strong>목표 저축 포켓</strong>
                    <span className="tabular-nums">{savingsPockets.length}개</span>
                  </div>
                  {savingsPockets.length > 0 ? (
                    <div className="pocket-grid">
                      {savingsPockets.map((pocket) => {
                        const pct = Math.min(
                          100,
                          Math.round(
                            (pocket.savedAmountMinor / Math.max(1, pocket.targetAmountMinor)) * 100,
                          ),
                        );
                        return (
                          <div className="pocket-card" key={pocket.pocketId}>
                            <div className="oss-pocket-row">
                              <ProgressCircle
                                label={`${pocket.pocketName} 달성률`}
                                radius={26}
                                strokeWidth={6}
                                value={pct}
                                variant={pct >= 100 ? "emerald" : "blue"}
                              >
                                <span className="tabular-nums">{pct}%</span>
                              </ProgressCircle>
                              <div className="oss-pocket-meta">
                                <div className="form-heading">
                                  <strong className="oss-inline-icon">
                                    <PiggyBankIcon size={15} />
                                    <span>{pocket.pocketName}</span>
                                  </strong>
                                  <span className="tabular-nums">{pct}%</span>
                                </div>
                                <small className="tabular-nums font-mono">
                                  {balanceMasked
                                    ? MASKED_BALANCE
                                    : `${formatMinorAmount(pocket.savedAmountMinor, pocket.currencyCode)} / ${formatMinorAmount(pocket.targetAmountMinor, pocket.currencyCode)}`}
                                </small>
                              </div>
                            </div>
                            <div className="button-row compact">
                              {onUpdateSavingsPocket ? (
                                <button
                                  disabled={isBusy}
                                  onClick={() =>
                                    void onUpdateSavingsPocket(
                                      selectedAccount.accountId,
                                      pocket.pocketId,
                                      {
                                        pocketName: pocket.pocketName,
                                        targetAmountMinor: pocket.targetAmountMinor,
                                        savedAmountMinor: pocket.savedAmountMinor + 1_000_000,
                                      },
                                    )
                                  }
                                  type="button"
                                >
                                  +1만원 적립
                                </button>
                              ) : null}
                              {onDeleteSavingsPocket ? (
                                <button
                                  disabled={isBusy}
                                  onClick={() =>
                                    void onDeleteSavingsPocket(
                                      selectedAccount.accountId,
                                      pocket.pocketId,
                                    )
                                  }
                                  type="button"
                                >
                                  삭제
                                </button>
                              ) : null}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="field-note">등록된 목표 저축 포켓이 없습니다.</p>
                  )}
                  <div className="form-grid compact">
                    <label>
                      <span>포켓 이름</span>
                      <input
                        maxLength={60}
                        onChange={(event) => setPocketName(event.target.value)}
                        placeholder="예: 비상금, 여행자금"
                        value={pocketName}
                      />
                    </label>
                    <BankCurrencyInput
                      label="목표금액"
                      onValueChange={setPocketTargetKrw}
                      placeholder="목표 원화 금액"
                      value={pocketTargetKrw}
                    />
                    <BankCurrencyInput
                      label="초기 적립액"
                      onValueChange={setPocketSavedKrw}
                      placeholder="초기 적립 원화"
                      value={pocketSavedKrw}
                    />
                  </div>
                  <button
                    disabled={isBusy || !pocketName.trim() || !(tryKrwToMinor(pocketTargetKrw) ?? 0)}
                    onClick={() => {
                      const targetMinor = tryKrwToMinor(pocketTargetKrw) ?? 0;
                      const savedMinor = tryKrwToMinor(pocketSavedKrw) ?? 0;
                      if (!pocketName.trim() || targetMinor <= 0) {
                        return;
                      }
                      void onCreateSavingsPocket(selectedAccount.accountId, {
                        pocketName: pocketName.trim(),
                        targetAmountMinor: targetMinor,
                        savedAmountMinor: savedMinor,
                      }).then((ok) => {
                        if (ok) {
                          setPocketName("");
                          setPocketTargetKrw("");
                          setPocketSavedKrw("0");
                        }
                      });
                    }}
                    type="button"
                  >
                    저축 포켓 추가
                  </button>
                </div>
              ) : null}
            </>
          ) : (
            <EmptyState
              title="계좌 미선택"
              description="보유계좌 목록에서 상세 버튼을 선택하세요."
            />
          )}
        </aside>
      </div>
    </section>
  );
}
