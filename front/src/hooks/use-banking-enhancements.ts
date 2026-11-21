import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { BankToastItem } from "@/components/customer-banking/common";
import { AquilaBankApiClient } from "@/lib/api/client";
import type {
  AccountPreferenceItem,
  CreateScheduledTransferRequest,
  NotificationItem,
  SaveAccountPreferenceRequest,
  SavedRecipientItem,
  SaveRecipientRequest,
  SaveSavingsPocketRequest,
  SaveTransactionAnnotationRequest,
  SavingsPocketItem,
  ScheduledTransferItem,
  ScheduledTransferStatus,
  TransactionAnnotationItem,
  TransactionItem,
} from "@/lib/api/types";
import { mainMenus, recentMenus } from "@/lib/customer-banking/constants";
import { toErrorMessage } from "@/lib/customer-banking/format";
import type { AlertMessage, MenuSection } from "@/lib/customer-banking/types";

const MAX_RECENT_MENUS = 6;
const MAX_VISIBLE_TOASTS = 3;
const IME_KEY_CODE = 229;

const MENU_LABEL_BY_SECTION = new Map<MenuSection, string>(
  mainMenus.map((item) => [item.id, item.label]),
);

export function useBankingEnhancements(params: {
  api: AquilaBankApiClient;
  isAuthenticated: boolean;
  authGeneration: number;
  activeSection: MenuSection;
  selectedAccountId: number | null;
  transactions: TransactionItem[];
  transactionAccountId: number | null;
  lastIncomingNotification: NotificationItem | null;
  onAlert: (alert: AlertMessage) => void;
}) {
  const {
    api,
    isAuthenticated,
    authGeneration,
    activeSection,
    selectedAccountId,
    transactions,
    transactionAccountId,
    lastIncomingNotification,
    onAlert,
  } = params;

  const [recipients, setRecipients] = useState<SavedRecipientItem[]>([]);
  const [scheduledTransfers, setScheduledTransfers] = useState<ScheduledTransferItem[]>([]);
  const [accountPreferences, setAccountPreferences] = useState<AccountPreferenceItem[]>([]);
  const [savingsPockets, setSavingsPockets] = useState<SavingsPocketItem[]>([]);
  const [dashboardTransactions, setDashboardTransactions] = useState<TransactionItem[]>([]);
  const [annotationsList, setAnnotationsList] = useState<TransactionAnnotationItem[]>([]);
  const [balanceMasked, setBalanceMasked] = useState(false);
  const [visitedMenus, setVisitedMenus] = useState<Array<{ label: string; section: MenuSection }>>(
    recentMenus,
  );
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const [toasts, setToasts] = useState<BankToastItem[]>([]);
  const [mutating, setMutating] = useState(false);
  const toastSeqRef = useRef(0);
  const lastToastedNotifIdRef = useRef<number | null>(null);

  const pushToast = useCallback(
    (toast: Omit<BankToastItem, "id">) => {
      toastSeqRef.current += 1;
      const id = `toast-${toastSeqRef.current}`;
      setToasts((prev) => [...prev.slice(-(MAX_VISIBLE_TOASTS - 1)), { ...toast, id }]);
    },
    [],
  );

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const toggleBalanceMasked = useCallback(() => {
    setBalanceMasked((prev) => !prev);
  }, []);

  // 로그아웃·세션 교체 시 개인화 상태를 즉시 초기화해 잔여 정보 노출을 막는다.
  useEffect(() => {
    if (!isAuthenticated) {
      setRecipients([]);
      setScheduledTransfers([]);
      setAccountPreferences([]);
      setSavingsPockets([]);
      setDashboardTransactions([]);
      setAnnotationsList([]);
      setBalanceMasked(false);
      setVisitedMenus(recentMenus);
      setToasts([]);
      lastToastedNotifIdRef.current = null;
      return;
    }

    const controller = new AbortController();
    void Promise.allSettled([
      api.getSavedRecipients({ signal: controller.signal }).then((res) => {
        if (!controller.signal.aborted) {
          setRecipients(res.items);
        }
      }),
      api.getScheduledTransfers({ signal: controller.signal }).then((res) => {
        if (!controller.signal.aborted) {
          setScheduledTransfers(res.items);
        }
      }),
      api.getAccountPreferences({ signal: controller.signal }).then((res) => {
        if (!controller.signal.aborted) {
          setAccountPreferences(res.items);
        }
      }),
    ]);

    return () => controller.abort();
  }, [api, authGeneration, isAuthenticated]);

  // 선택된 계좌의 목표 저축 포켓 및 대시보드 최근 입출금 추이를 조회한다.
  useEffect(() => {
    if (!isAuthenticated || !selectedAccountId || selectedAccountId <= 0) {
      setSavingsPockets([]);
      setDashboardTransactions([]);
      return;
    }
    const controller = new AbortController();
    const toDate = new Date();
    const fromDate = new Date(toDate.getTime() - 90 * 24 * 60 * 60 * 1000);
    void api
      .getSavingsPockets(selectedAccountId, { signal: controller.signal })
      .then((res) => {
        if (!controller.signal.aborted) {
          setSavingsPockets(res.items);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setSavingsPockets([]);
        }
      });
    void api
      .getTransactions(
        {
          accountId: selectedAccountId,
          from: fromDate.toISOString(),
          to: toDate.toISOString(),
          limit: 12,
          responseShape: "full",
        },
        { signal: controller.signal },
      )
      .then((res) => {
        if (!controller.signal.aborted) {
          setDashboardTransactions(Array.isArray(res.items) ? res.items : []);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setDashboardTransactions([]);
        }
      });
    return () => controller.abort();
  }, [api, authGeneration, isAuthenticated, selectedAccountId]);

  // 거래내역 목록 변경 시 분류/메모 오버레이를 백엔드 상한(50건) 이내로 일괄 조회한다(N+1 방지).
  useEffect(() => {
    const activeRows = transactions.length > 0 ? transactions : dashboardTransactions;
    const firstTxAccountId = activeRows[0]?.accountId;
    const accountId =
      firstTxAccountId && firstTxAccountId > 0
        ? firstTxAccountId
        : (transactionAccountId ?? selectedAccountId);
    if (!isAuthenticated || !accountId || accountId <= 0 || activeRows.length === 0) {
      setAnnotationsList([]);
      return;
    }
    const references: string[] = [];
    for (let i = 0; i < activeRows.length && references.length < 50; i += 1) {
      const ref = activeRows[i].transactionReference;
      if (ref) {
        references.push(ref);
      }
    }
    if (references.length === 0) {
      setAnnotationsList([]);
      return;
    }
    const controller = new AbortController();
    void api
      .getTransactionAnnotations(accountId, references, { signal: controller.signal })
      .then((res) => {
        if (!controller.signal.aborted) {
          setAnnotationsList(res.items);
        }
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [api, dashboardTransactions, isAuthenticated, selectedAccountId, transactionAccountId, transactions]);

  // 실시간 SSE 알림 수신 시 중복 없이 토스트를 띄운다.
  useEffect(() => {
    if (!isAuthenticated || !lastIncomingNotification) {
      return;
    }
    if (lastToastedNotifIdRef.current === lastIncomingNotification.notificationId) {
      return;
    }
    lastToastedNotifIdRef.current = lastIncomingNotification.notificationId;
    pushToast({
      title: lastIncomingNotification.title || "새 뱅킹 알림",
      message: lastIncomingNotification.message || "새로운 알림이 도착했습니다.",
      tone: "info",
    });
  }, [isAuthenticated, lastIncomingNotification, pushToast]);

  // 인증 상태에서 방문한 화면을 최근 이용 메뉴 링으로 유지한다.
  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    const label = MENU_LABEL_BY_SECTION.get(activeSection);
    if (!label) {
      return;
    }
    setVisitedMenus((prev) => {
      const next = [{ label, section: activeSection }];
      for (let i = 0; i < prev.length && next.length < MAX_RECENT_MENUS; i += 1) {
        if (prev[i].section !== activeSection || prev[i].label !== label) {
          next.push(prev[i]);
        }
      }
      return next;
    });
  }, [activeSection, isAuthenticated]);

  // Cmd+K / Ctrl+K 전역 빠른 검색 단축키
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        if (event.isComposing || event.keyCode === IME_KEY_CODE) {
          return;
        }
        event.preventDefault();
        setCommandPaletteOpen((prev) => !prev);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  const accountPreferencesMap = useMemo(() => {
    const map = new Map<number, AccountPreferenceItem>();
    for (let i = 0; i < accountPreferences.length; i += 1) {
      map.set(accountPreferences[i].accountId, accountPreferences[i]);
    }
    return map;
  }, [accountPreferences]);

  const primaryAccountId = useMemo(() => {
    for (let i = 0; i < accountPreferences.length; i += 1) {
      if (accountPreferences[i].primaryAccount) {
        return accountPreferences[i].accountId;
      }
    }
    return null;
  }, [accountPreferences]);

  const annotationsMap = useMemo(() => {
    const map = new Map<string, TransactionAnnotationItem>();
    for (let i = 0; i < annotationsList.length; i += 1) {
      map.set(annotationsList[i].transactionReference, annotationsList[i]);
    }
    return map;
  }, [annotationsList]);

  const handleSaveRecipient = useCallback(
    async (request: SaveRecipientRequest): Promise<boolean> => {
      setMutating(true);
      try {
        const saved = await api.saveRecipient(request);
        setRecipients((prev) => [
          saved,
          ...prev.filter((item) => item.recipientId !== saved.recipientId),
        ]);
        pushToast({
          title: "자주 쓰는 계좌 저장",
          message: `${saved.nickname} 계좌를 등록했습니다.`,
          tone: "success",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleDeleteRecipient = useCallback(
    async (recipientId: number): Promise<boolean> => {
      setMutating(true);
      try {
        await api.deleteRecipient(recipientId);
        setRecipients((prev) => prev.filter((item) => item.recipientId !== recipientId));
        pushToast({
          title: "수취인 삭제",
          message: "선택한 자주 쓰는 계좌를 삭제했습니다.",
          tone: "info",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleCreateScheduledTransfer = useCallback(
    async (request: CreateScheduledTransferRequest): Promise<boolean> => {
      setMutating(true);
      try {
        const created = await api.createScheduledTransfer(request);
        setScheduledTransfers((prev) => [created, ...prev]);
        onAlert({ type: "success", text: "예약·자동이체 규칙이 등록되었습니다." });
        pushToast({
          title: "예약이체 등록 완료",
          message: `${created.targetAccountNumber} 계좌 예약이 등록되었습니다.`,
          tone: "success",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleUpdateScheduledTransferStatus = useCallback(
    async (
      scheduleId: number,
      ruleStatus: ScheduledTransferStatus,
      totpCode?: string,
    ): Promise<boolean> => {
      setMutating(true);
      try {
        const updated = await api.updateScheduledTransferStatus(
          scheduleId,
          ruleStatus,
          totpCode,
        );
        setScheduledTransfers((prev) =>
          ruleStatus === "CANCELLED"
            ? prev.filter((item) => item.scheduleId !== scheduleId)
            : prev.map((item) => (item.scheduleId === scheduleId ? updated : item)),
        );
        pushToast({
          title: "예약이체 상태 변경",
          message:
            ruleStatus === "CANCELLED"
              ? "예약이체를 해지했습니다."
              : ruleStatus === "PAUSED"
                ? "예약이체를 일시중지했습니다."
                : "예약이체를 다시 활성화했습니다.",
          tone: "success",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleSaveAccountPreference = useCallback(
    async (accountId: number, request: SaveAccountPreferenceRequest): Promise<boolean> => {
      setMutating(true);
      try {
        const saved = await api.saveAccountPreference(accountId, request);
        setAccountPreferences((prev) => {
          const filtered = prev.filter((item) => item.accountId !== saved.accountId);
          const adjusted = saved.primaryAccount
            ? filtered.map((item) => ({ ...item, primaryAccount: false }))
            : filtered;
          return [saved, ...adjusted];
        });
        pushToast({
          title: "계좌 개인화 저장",
          message: "계좌 별칭 및 대표계좌 설정을 저장했습니다.",
          tone: "success",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleCreateSavingsPocket = useCallback(
    async (accountId: number, request: SaveSavingsPocketRequest): Promise<boolean> => {
      setMutating(true);
      try {
        const created = await api.createSavingsPocket(accountId, request);
        setSavingsPockets((prev) => [created, ...prev]);
        pushToast({
          title: "저축 포켓 생성",
          message: `${created.pocketName} 포켓을 만들었습니다.`,
          tone: "success",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleUpdateSavingsPocket = useCallback(
    async (
      accountId: number,
      pocketId: number,
      request: SaveSavingsPocketRequest,
    ): Promise<boolean> => {
      setMutating(true);
      try {
        const updated = await api.updateSavingsPocket(accountId, pocketId, request);
        setSavingsPockets((prev) =>
          prev.map((item) => (item.pocketId === pocketId ? updated : item)),
        );
        pushToast({
          title: "저축 포켓 반영",
          message: `${updated.pocketName} 금액을 갱신했습니다.`,
          tone: "success",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleDeleteSavingsPocket = useCallback(
    async (accountId: number, pocketId: number): Promise<boolean> => {
      setMutating(true);
      try {
        await api.deleteSavingsPocket(accountId, pocketId);
        setSavingsPockets((prev) => prev.filter((item) => item.pocketId !== pocketId));
        pushToast({
          title: "저축 포켓 삭제",
          message: "선택한 포켓을 삭제했습니다.",
          tone: "info",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  const handleSaveTransactionAnnotation = useCallback(
    async (
      transactionReference: string,
      request: SaveTransactionAnnotationRequest,
    ): Promise<boolean> => {
      setMutating(true);
      try {
        const saved = await api.saveTransactionAnnotation(transactionReference, request);
        setAnnotationsList((prev) => [
          saved,
          ...prev.filter(
            (item) => item.transactionReference !== saved.transactionReference,
          ),
        ]);
        pushToast({
          title: "거래 메모 저장",
          message: "거래 분류와 메모를 저장했습니다.",
          tone: "success",
        });
        return true;
      } catch (error) {
        onAlert({ type: "error", text: toErrorMessage(error) });
        return false;
      } finally {
        setMutating(false);
      }
    },
    [api, onAlert, pushToast],
  );

  return {
    recipients,
    scheduledTransfers,
    accountPreferencesMap,
    primaryAccountId,
    savingsPockets,
    dashboardTransactions,
    annotationsMap,
    balanceMasked,
    visitedMenus,
    commandPaletteOpen,
    toasts,
    enhancementsBusy: mutating,
    setCommandPaletteOpen,
    toggleBalanceMasked,
    pushToast,
    dismissToast,
    handleSaveRecipient,
    handleDeleteRecipient,
    handleCreateScheduledTransfer,
    handleUpdateScheduledTransferStatus,
    handleSaveAccountPreference,
    handleCreateSavingsPocket,
    handleUpdateSavingsPocket,
    handleDeleteSavingsPocket,
    handleSaveTransactionAnnotation,
  };
}
