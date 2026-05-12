"use client";

import { useMemo, useState } from "react";
import {
  accountAuditByRequestIdPath,
  accountStatusPath,
  authMembershipStatusPath,
  authAuditByRequestIdPath,
  authUserStatusPath,
  buildOpsRequests,
  commandIdempotencyRecoveryPath,
  getOpsJson,
  ledgerSnapshotRecoveryPath,
  ledgerAuditByRequestIdPath,
  ledgerAuditByTransactionPath,
  notificationChannelRedrivePath,
  notificationDlqRedrivePath,
  outboxStaleRecoveryPath,
  postOpsJson,
  putOpsJson,
  resolveOpsBaseUrl,
} from "@/lib/ops-console/client";
import { initialOpsResult } from "@/lib/ops-console/types";
import type { OpsFormState, OpsRequest, OpsResult } from "@/lib/ops-console/types";

type ResultMap = Record<string, OpsResult>;

function createInitialResults(items: OpsRequest[]): ResultMap {
  return items.reduce<ResultMap>((result, item) => {
    result[item.label] = initialOpsResult;
    return result;
  }, {});
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}

function toNonNegativeNumber(value: string): number | null {
  const numberValue = Number(value);
  return Number.isInteger(numberValue) && numberValue >= 0 ? numberValue : null;
}

function toPositiveNumber(value: string): number | null {
  const numberValue = Number(value);
  return Number.isInteger(numberValue) && numberValue > 0 ? numberValue : null;
}

function ResultPanel({ result }: { result: OpsResult }) {
  if (result.status === "idle") {
    return <pre className="ops-result muted">조회 전</pre>;
  }
  if (result.status === "loading") {
    return <pre className="ops-result muted">조회 중</pre>;
  }
  if (result.status === "error") {
    return <pre className="ops-result error">{result.error}</pre>;
  }
  return (
    <pre className="ops-result">
      {JSON.stringify(result.data, null, 2)}
    </pre>
  );
}

export function OpsConsole() {
  const enabled = process.env.NEXT_PUBLIC_OPS_CONSOLE_ENABLED === "true";
  const [form, setForm] = useState<OpsFormState>({
    baseUrl: resolveOpsBaseUrl(),
    token: "",
    limit: "20",
    requestId: "",
    transactionReference: "",
    accountRequestId: "",
    authRequestId: "",
    outboxConfirmation: "",
    dlqPartition: "",
    dlqOffset: "",
    dlqConfirmation: "",
    channelEventId: "",
    channelConfirmation: "",
    idempotencyConfirmation: "",
    snapshotAccountId: "",
    snapshotReason: "",
    snapshotConfirmation: "",
    authStatusUserId: "",
    authStatusValue: "DISABLED",
    authStatusReasonCode: "OPS_MANUAL",
    authStatusReasonDetail: "",
    authStatusRequestId: "",
    authStatusConfirmation: "",
    membershipStatusUserId: "",
    membershipStatusAccountId: "",
    membershipStatusValue: "REVOKED",
    membershipStatusReasonCode: "OPS_MANUAL",
    membershipStatusReasonDetail: "",
    membershipStatusRequestId: "",
    membershipStatusConfirmation: "",
    accountStatusAccountId: "",
    accountStatusValue: "LOCKED",
    accountStatusReasonCode: "OPS_MANUAL",
    accountStatusReasonDetail: "",
    accountStatusRequestId: "",
    accountStatusConfirmation: "",
  });
  const requests = useMemo(() => buildOpsRequests(form.limit), [form.limit]);
  const [results, setResults] = useState<ResultMap>(() => createInitialResults(requests));

  if (!enabled) {
    return (
      <main className="ops-shell disabled">
        <section className="ops-disabled">
          <strong>운영 콘솔 비활성</strong>
          <span>NEXT_PUBLIC_OPS_CONSOLE_ENABLED=true 환경에서만 노출됩니다.</span>
        </section>
      </main>
    );
  }

  function updateField(name: keyof OpsFormState, value: string): void {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function runRequest(label: string, path: string): Promise<void> {
    if (!form.baseUrl || !form.token) {
      setResults((current) => ({
        ...current,
        [label]: {
          status: "error",
          data: null,
          error: "base URL과 internal service token을 입력하세요.",
        },
      }));
      return;
    }

    setResults((current) => ({
      ...current,
      [label]: { status: "loading", data: null, error: "" },
    }));
    try {
      const data = await getOpsJson(form.baseUrl, form.token, path);
      setResults((current) => ({
        ...current,
        [label]: { status: "success", data, error: "" },
      }));
    } catch (error) {
      setResults((current) => ({
        ...current,
        [label]: { status: "error", data: null, error: toErrorMessage(error) },
      }));
    }
  }

  async function runAction(
    label: string,
    path: string,
    confirmation: string,
    requiredConfirmation: string,
    body?: unknown,
  ): Promise<void> {
    if (!form.baseUrl || !form.token) {
      setResults((current) => ({
        ...current,
        [label]: {
          status: "error",
          data: null,
          error: "base URL과 internal service token을 입력하세요.",
        },
      }));
      return;
    }
    if (confirmation.trim() !== requiredConfirmation) {
      setResults((current) => ({
        ...current,
        [label]: {
          status: "error",
          data: null,
          error: `${requiredConfirmation} 확인 문구를 정확히 입력하세요.`,
        },
      }));
      return;
    }

    setResults((current) => ({
      ...current,
      [label]: { status: "loading", data: null, error: "" },
    }));
    try {
      const data = await postOpsJson(form.baseUrl, form.token, path, body);
      setResults((current) => ({
        ...current,
        [label]: { status: "success", data, error: "" },
      }));
    } catch (error) {
      setResults((current) => ({
        ...current,
        [label]: { status: "error", data: null, error: toErrorMessage(error) },
      }));
    }
  }

  async function runStatusAction({
    auditPath,
    auditResultLabel,
    body,
    confirmation,
    label,
    path,
    requestId,
    requiredConfirmation,
  }: {
    auditPath: string;
    auditResultLabel: string;
    body: unknown;
    confirmation: string;
    label: string;
    path: string;
    requestId: string;
    requiredConfirmation: string;
  }): Promise<void> {
    if (!form.baseUrl || !form.token) {
      setResults((current) => ({
        ...current,
        [label]: {
          status: "error",
          data: null,
          error: "base URL과 internal service token을 입력하세요.",
        },
      }));
      return;
    }
    if (!requestId.trim()) {
      setResults((current) => ({
        ...current,
        [label]: { status: "error", data: null, error: "requestId를 입력하세요." },
      }));
      return;
    }
    if (confirmation.trim() !== requiredConfirmation) {
      setResults((current) => ({
        ...current,
        [label]: {
          status: "error",
          data: null,
          error: `${requiredConfirmation} 확인 문구를 정확히 입력하세요.`,
        },
      }));
      return;
    }

    setResults((current) => ({
      ...current,
      [label]: { status: "loading", data: null, error: "" },
      [auditResultLabel]: { status: "loading", data: null, error: "" },
    }));
    try {
      const data = await putOpsJson(form.baseUrl, form.token, path, requestId, body);
      setResults((current) => ({
        ...current,
        [label]: { status: "success", data, error: "" },
      }));
      try {
        const audit = await getOpsJson(form.baseUrl, form.token, auditPath);
        setResults((current) => ({
          ...current,
          [auditResultLabel]: { status: "success", data: audit, error: "" },
        }));
      } catch (auditError) {
        setResults((current) => ({
          ...current,
          [auditResultLabel]: {
            status: "error",
            data: null,
            error: toErrorMessage(auditError),
          },
        }));
      }
    } catch (error) {
      setResults((current) => ({
        ...current,
        [label]: { status: "error", data: null, error: toErrorMessage(error) },
        [auditResultLabel]: { status: "idle", data: null, error: "" },
      }));
    }
  }

  function runDlqRedrive(): void {
    const partition = toNonNegativeNumber(form.dlqPartition);
    const offset = toNonNegativeNumber(form.dlqOffset);
    if (partition === null || offset === null) {
      setResults((current) => ({
        ...current,
        ["Notification DLQ redrive"]: {
          status: "error",
          data: null,
          error: "DLQ partition과 offset은 0 이상의 정수로 입력하세요.",
        },
      }));
      return;
    }
    void runAction(
      "Notification DLQ redrive",
      notificationDlqRedrivePath(),
      form.dlqConfirmation,
      "RUN DLQ",
      { partition, offset },
    );
  }

  function runChannelRedrive(): void {
    const id = toNonNegativeNumber(form.channelEventId);
    if (id === null || id <= 0) {
      setResults((current) => ({
        ...current,
        ["Channel quarantine redrive"]: {
          status: "error",
          data: null,
          error: "Channel quarantined event ID는 양의 정수로 입력하세요.",
        },
      }));
      return;
    }
    void runAction(
      "Channel quarantine redrive",
      notificationChannelRedrivePath(form.channelEventId),
      form.channelConfirmation,
      "RUN CHANNEL",
    );
  }

  function runSnapshotRecovery(): void {
    const accountId = toNonNegativeNumber(form.snapshotAccountId);
    if (accountId === null || accountId <= 0 || !form.snapshotReason.trim()) {
      setResults((current) => ({
        ...current,
        ["Ledger snapshot recovery"]: {
          status: "error",
          data: null,
          error: "Snapshot accountId와 사유를 입력하세요.",
        },
      }));
      return;
    }
    void runAction(
      "Ledger snapshot recovery",
      ledgerSnapshotRecoveryPath(form.snapshotAccountId),
      form.snapshotConfirmation,
      "RUN SNAPSHOT",
      { reason: form.snapshotReason.trim() },
    );
  }

  function runLookup(label: string, path: string, requiredValue: string): void {
    if (!requiredValue.trim()) {
      setResults((current) => ({
        ...current,
        [label]: { status: "error", data: null, error: "조회 키를 입력하세요." },
      }));
      return;
    }
    void runRequest(label, path);
  }

  function runAuthUserStatusUpdate(): void {
    const userId = toPositiveNumber(form.authStatusUserId);
    if (
      userId === null ||
      !form.authStatusReasonDetail.trim() ||
      !form.authStatusRequestId.trim()
    ) {
      setResults((current) => ({
        ...current,
        ["Auth user status update"]: {
          status: "error",
          data: null,
          error: "userId, requestId, reasonDetail을 입력하세요.",
        },
      }));
      return;
    }
    updateField("authRequestId", form.authStatusRequestId);
    void runStatusAction({
      auditPath: authAuditByRequestIdPath(form.authStatusRequestId),
      auditResultLabel: "Auth audit by request",
      body: {
        userStatus: form.authStatusValue,
        reasonCode: form.authStatusReasonCode,
        reasonDetail: form.authStatusReasonDetail.trim(),
      },
      confirmation: form.authStatusConfirmation,
      label: "Auth user status update",
      path: authUserStatusPath(form.authStatusUserId),
      requestId: form.authStatusRequestId,
      requiredConfirmation: `CHANGE USER ${form.authStatusUserId}`,
    });
  }

  function runMembershipStatusUpdate(): void {
    const userId = toPositiveNumber(form.membershipStatusUserId);
    const accountId = toPositiveNumber(form.membershipStatusAccountId);
    if (
      userId === null ||
      accountId === null ||
      !form.membershipStatusReasonDetail.trim() ||
      !form.membershipStatusRequestId.trim()
    ) {
      setResults((current) => ({
        ...current,
        ["Auth membership status update"]: {
          status: "error",
          data: null,
          error: "userId, accountId, requestId, reasonDetail을 입력하세요.",
        },
      }));
      return;
    }
    updateField("authRequestId", form.membershipStatusRequestId);
    void runStatusAction({
      auditPath: authAuditByRequestIdPath(form.membershipStatusRequestId),
      auditResultLabel: "Auth audit by request",
      body: {
        membershipStatus: form.membershipStatusValue,
        reasonCode: form.membershipStatusReasonCode,
        reasonDetail: form.membershipStatusReasonDetail.trim(),
      },
      confirmation: form.membershipStatusConfirmation,
      label: "Auth membership status update",
      path: authMembershipStatusPath(
        form.membershipStatusUserId,
        form.membershipStatusAccountId,
      ),
      requestId: form.membershipStatusRequestId,
      requiredConfirmation: `CHANGE MEMBERSHIP ${form.membershipStatusUserId}/${form.membershipStatusAccountId}`,
    });
  }

  function runAccountStatusUpdate(): void {
    const accountId = toPositiveNumber(form.accountStatusAccountId);
    if (
      accountId === null ||
      !form.accountStatusReasonDetail.trim() ||
      !form.accountStatusRequestId.trim()
    ) {
      setResults((current) => ({
        ...current,
        ["Account status update"]: {
          status: "error",
          data: null,
          error: "accountId, requestId, reasonDetail을 입력하세요.",
        },
      }));
      return;
    }
    updateField("accountRequestId", form.accountStatusRequestId);
    void runStatusAction({
      auditPath: accountAuditByRequestIdPath(form.accountStatusRequestId),
      auditResultLabel: "Account audit by request",
      body: {
        accountStatus: form.accountStatusValue,
      },
      confirmation: form.accountStatusConfirmation,
      label: "Account status update",
      path: accountStatusPath(form.accountStatusAccountId),
      requestId: form.accountStatusRequestId,
      requiredConfirmation: `CHANGE ACCOUNT ${form.accountStatusAccountId}`,
    });
  }

  const lookupItems: OpsRequest[] = [
    {
      label: "Ledger audit by request",
      path: ledgerAuditByRequestIdPath(form.requestId, form.limit),
    },
    {
      label: "Ledger audit by transaction",
      path: ledgerAuditByTransactionPath(form.transactionReference, form.limit),
    },
    {
      label: "Account audit by request",
      path: accountAuditByRequestIdPath(form.accountRequestId),
    },
    {
      label: "Auth audit by request",
      path: authAuditByRequestIdPath(form.authRequestId),
    },
  ];

  return (
    <main className="ops-shell ops-control-room">
      <header className="ops-header">
        <div>
          <strong>Aquila Bank Ops Console</strong>
          <span className="ops-readonly-badge">Read-only Surface</span>
          <span className="ops-write-badge">Guarded Recovery Actions</span>
        </div>
        <a href="/" aria-label="고객 웹뱅킹으로 이동">
          고객뱅킹
        </a>
      </header>

      <section className="ops-guard">
        <label>
          API base URL
          <input
            value={form.baseUrl}
            onChange={(event) => updateField("baseUrl", event.target.value)}
            placeholder="https://bank.aquilaxk.site"
          />
        </label>
        <label>
          internal service token
          <input
            value={form.token}
            onChange={(event) => updateField("token", event.target.value)}
            placeholder="Bearer token은 저장하지 않습니다"
            type="password"
          />
        </label>
        <label>
          page size
          <input
            inputMode="numeric"
            max="100"
            min="1"
            value={form.limit}
            onChange={(event) => updateField("limit", event.target.value)}
          />
        </label>
      </section>

      <section className="ops-grid" aria-label="운영 조회">
        <div className="ops-section-map" aria-label="read-only coverage">
          <span>Outbox</span>
          <span>DLQ</span>
          <span>Ledger</span>
          <span>Snapshot</span>
          <span>Auth</span>
          <span>Account</span>
        </div>
        {requests.map((item) => (
          <article className="ops-card" key={item.label}>
            <div className="ops-card-head">
              <div>
                <strong>{item.label}</strong>
                <span>{item.path}</span>
              </div>
              <button type="button" onClick={() => void runRequest(item.label, item.path)}>
                조회
              </button>
            </div>
            <ResultPanel result={results[item.label] ?? initialOpsResult} />
          </article>
        ))}
      </section>

      <section className="ops-actions" aria-label="Recovery Actions">
        <div className="ops-lookup-title">
          <strong>Recovery Actions</strong>
          <span>권한은 internal service token scope로 검증되며 token은 저장하지 않습니다.</span>
        </div>
        <div className="ops-action-grid">
          <article className="ops-action-card">
            <div>
              <strong>Outbox stale sending recovery</strong>
              <span>{outboxStaleRecoveryPath()}</span>
            </div>
            <label>
              Confirm phrase
              <input
                value={form.outboxConfirmation}
                onChange={(event) => updateField("outboxConfirmation", event.target.value)}
                placeholder="RUN OUTBOX"
              />
            </label>
            <button
              className="ops-danger-button"
              type="button"
              onClick={() =>
                void runAction(
                  "Outbox stale recovery",
                  outboxStaleRecoveryPath(),
                  form.outboxConfirmation,
                  "RUN OUTBOX",
                )
              }
            >
              stale outbox 복구
            </button>
            <ResultPanel result={results["Outbox stale recovery"] ?? initialOpsResult} />
          </article>

          <article className="ops-action-card">
            <div>
              <strong>Notification DLQ redrive</strong>
              <span>{notificationDlqRedrivePath()}</span>
            </div>
            <div className="ops-action-fields">
              <label>
                DLQ partition
                <input
                  inputMode="numeric"
                  value={form.dlqPartition}
                  onChange={(event) => updateField("dlqPartition", event.target.value)}
                />
              </label>
              <label>
                DLQ offset
                <input
                  inputMode="numeric"
                  value={form.dlqOffset}
                  onChange={(event) => updateField("dlqOffset", event.target.value)}
                />
              </label>
            </div>
            <label>
              Confirm phrase
              <input
                value={form.dlqConfirmation}
                onChange={(event) => updateField("dlqConfirmation", event.target.value)}
                placeholder="RUN DLQ"
              />
            </label>
            <button className="ops-danger-button" type="button" onClick={runDlqRedrive}>
              DLQ redrive
            </button>
            <ResultPanel result={results["Notification DLQ redrive"] ?? initialOpsResult} />
          </article>

          <article className="ops-action-card">
            <div>
              <strong>Channel quarantine redrive</strong>
              <span>{notificationChannelRedrivePath("{id}")}</span>
            </div>
            <label>
              Channel quarantined event ID
              <input
                inputMode="numeric"
                value={form.channelEventId}
                onChange={(event) => updateField("channelEventId", event.target.value)}
              />
            </label>
            <label>
              Confirm phrase
              <input
                value={form.channelConfirmation}
                onChange={(event) => updateField("channelConfirmation", event.target.value)}
                placeholder="RUN CHANNEL"
              />
            </label>
            <button className="ops-danger-button" type="button" onClick={runChannelRedrive}>
              channel redrive
            </button>
            <ResultPanel result={results["Channel quarantine redrive"] ?? initialOpsResult} />
          </article>

          <article className="ops-action-card">
            <div>
              <strong>Command idempotency stale recovery</strong>
              <span>{commandIdempotencyRecoveryPath()}</span>
            </div>
            <label>
              Confirm phrase
              <input
                value={form.idempotencyConfirmation}
                onChange={(event) =>
                  updateField("idempotencyConfirmation", event.target.value)
                }
                placeholder="RUN IDEMPOTENCY"
              />
            </label>
            <button
              className="ops-danger-button"
              type="button"
              onClick={() =>
                void runAction(
                  "Command idempotency recovery",
                  commandIdempotencyRecoveryPath(),
                  form.idempotencyConfirmation,
                  "RUN IDEMPOTENCY",
                )
              }
            >
              stale command 복구
            </button>
            <ResultPanel result={results["Command idempotency recovery"] ?? initialOpsResult} />
          </article>

          <article className="ops-action-card">
            <div>
              <strong>Ledger snapshot recovery</strong>
              <span>{ledgerSnapshotRecoveryPath("{accountId}")}</span>
            </div>
            <div className="ops-action-fields">
              <label>
                Snapshot accountId
                <input
                  inputMode="numeric"
                  value={form.snapshotAccountId}
                  onChange={(event) => updateField("snapshotAccountId", event.target.value)}
                />
              </label>
              <label>
                Snapshot reason
                <input
                  maxLength={200}
                  value={form.snapshotReason}
                  onChange={(event) => updateField("snapshotReason", event.target.value)}
                />
              </label>
            </div>
            <label>
              Confirm phrase
              <input
                value={form.snapshotConfirmation}
                onChange={(event) => updateField("snapshotConfirmation", event.target.value)}
                placeholder="RUN SNAPSHOT"
              />
            </label>
            <button className="ops-danger-button" type="button" onClick={runSnapshotRecovery}>
              snapshot 복구
            </button>
            <ResultPanel result={results["Ledger snapshot recovery"] ?? initialOpsResult} />
          </article>
        </div>
      </section>

      <section className="ops-actions ops-status-actions" aria-label="Status Change Actions">
        <div className="ops-lookup-title">
          <strong>AUTH_ADMIN / ACCOUNT_ADMIN status changes</strong>
          <span>requestId, reasonCode, reasonDetail, 확인 문구를 모두 입력한 뒤 실행합니다.</span>
        </div>
        <div className="ops-action-grid">
          <article className="ops-action-card ops-status-card">
            <div>
              <strong>Auth user status update</strong>
              <span>/internal/api/v1/auth/users/{"{userId}"}/status</span>
            </div>
            <div className="ops-action-fields">
              <label>
                User ID
                <input
                  inputMode="numeric"
                  value={form.authStatusUserId}
                  onChange={(event) => updateField("authStatusUserId", event.target.value)}
                />
              </label>
              <label>
                User status
                <select
                  value={form.authStatusValue}
                  onChange={(event) => updateField("authStatusValue", event.target.value)}
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="LOCKED">LOCKED</option>
                  <option value="DISABLED">DISABLED</option>
                </select>
              </label>
            </div>
            <div className="ops-action-fields">
              <label>
                Reason code
                <select
                  value={form.authStatusReasonCode}
                  onChange={(event) =>
                    updateField("authStatusReasonCode", event.target.value)
                  }
                >
                  <option value="OPS_MANUAL">OPS_MANUAL</option>
                  <option value="FRAUD_REVIEW">FRAUD_REVIEW</option>
                  <option value="USER_REQUEST">USER_REQUEST</option>
                  <option value="ACCOUNT_CLOSURE">ACCOUNT_CLOSURE</option>
                </select>
              </label>
              <label>
                Request ID
                <input
                  value={form.authStatusRequestId}
                  onChange={(event) => updateField("authStatusRequestId", event.target.value)}
                  placeholder="auth-user-status-..."
                />
              </label>
            </div>
            <label>
              Reason detail
              <input
                maxLength={200}
                value={form.authStatusReasonDetail}
                onChange={(event) =>
                  updateField("authStatusReasonDetail", event.target.value)
                }
              />
            </label>
            <label>
              Confirm phrase
              <input
                value={form.authStatusConfirmation}
                onChange={(event) =>
                  updateField("authStatusConfirmation", event.target.value)
                }
                placeholder={`CHANGE USER ${form.authStatusUserId || "{userId}"}`}
              />
            </label>
            <button className="ops-danger-button" type="button" onClick={runAuthUserStatusUpdate}>
              user status 변경
            </button>
            <ResultPanel result={results["Auth user status update"] ?? initialOpsResult} />
          </article>

          <article className="ops-action-card ops-status-card">
            <div>
              <strong>Auth membership status update</strong>
              <span>
                /internal/api/v1/auth/users/{"{userId}"}/memberships/{"{accountId}"}/status
              </span>
            </div>
            <div className="ops-action-fields">
              <label>
                User ID
                <input
                  inputMode="numeric"
                  value={form.membershipStatusUserId}
                  onChange={(event) =>
                    updateField("membershipStatusUserId", event.target.value)
                  }
                />
              </label>
              <label>
                Account ID
                <input
                  inputMode="numeric"
                  value={form.membershipStatusAccountId}
                  onChange={(event) =>
                    updateField("membershipStatusAccountId", event.target.value)
                  }
                />
              </label>
            </div>
            <div className="ops-action-fields">
              <label>
                Membership status
                <select
                  value={form.membershipStatusValue}
                  onChange={(event) =>
                    updateField("membershipStatusValue", event.target.value)
                  }
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="REVOKED">REVOKED</option>
                </select>
              </label>
              <label>
                Reason code
                <select
                  value={form.membershipStatusReasonCode}
                  onChange={(event) =>
                    updateField("membershipStatusReasonCode", event.target.value)
                  }
                >
                  <option value="OPS_MANUAL">OPS_MANUAL</option>
                  <option value="FRAUD_REVIEW">FRAUD_REVIEW</option>
                  <option value="USER_REQUEST">USER_REQUEST</option>
                  <option value="ACCOUNT_CLOSURE">ACCOUNT_CLOSURE</option>
                </select>
              </label>
            </div>
            <label>
              Request ID
              <input
                value={form.membershipStatusRequestId}
                onChange={(event) =>
                  updateField("membershipStatusRequestId", event.target.value)
                }
                placeholder="auth-membership-status-..."
              />
            </label>
            <label>
              Reason detail
              <input
                maxLength={200}
                value={form.membershipStatusReasonDetail}
                onChange={(event) =>
                  updateField("membershipStatusReasonDetail", event.target.value)
                }
              />
            </label>
            <label>
              Confirm phrase
              <input
                value={form.membershipStatusConfirmation}
                onChange={(event) =>
                  updateField("membershipStatusConfirmation", event.target.value)
                }
                placeholder={`CHANGE MEMBERSHIP ${form.membershipStatusUserId || "{userId}"}/${form.membershipStatusAccountId || "{accountId}"}`}
              />
            </label>
            <button className="ops-danger-button" type="button" onClick={runMembershipStatusUpdate}>
              membership status 변경
            </button>
            <ResultPanel
              result={results["Auth membership status update"] ?? initialOpsResult}
            />
          </article>

          <article className="ops-action-card ops-status-card">
            <div>
              <strong>Account status update</strong>
              <span>/internal/api/v1/accounts/{"{accountId}"}/status</span>
            </div>
            <div className="ops-action-fields">
              <label>
                Account ID
                <input
                  inputMode="numeric"
                  value={form.accountStatusAccountId}
                  onChange={(event) =>
                    updateField("accountStatusAccountId", event.target.value)
                  }
                />
              </label>
              <label>
                Account status
                <select
                  value={form.accountStatusValue}
                  onChange={(event) => updateField("accountStatusValue", event.target.value)}
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="LOCKED">LOCKED</option>
                  <option value="CLOSED">CLOSED</option>
                </select>
              </label>
            </div>
            <div className="ops-action-fields">
              <label>
                Reason code
                <select
                  value={form.accountStatusReasonCode}
                  onChange={(event) =>
                    updateField("accountStatusReasonCode", event.target.value)
                  }
                >
                  <option value="OPS_MANUAL">OPS_MANUAL</option>
                  <option value="FRAUD_REVIEW">FRAUD_REVIEW</option>
                  <option value="USER_REQUEST">USER_REQUEST</option>
                  <option value="ACCOUNT_CLOSURE">ACCOUNT_CLOSURE</option>
                </select>
              </label>
              <label>
                Request ID
                <input
                  value={form.accountStatusRequestId}
                  onChange={(event) =>
                    updateField("accountStatusRequestId", event.target.value)
                  }
                  placeholder="account-status-..."
                />
              </label>
            </div>
            <label>
              Reason detail
              <input
                maxLength={200}
                value={form.accountStatusReasonDetail}
                onChange={(event) =>
                  updateField("accountStatusReasonDetail", event.target.value)
                }
              />
            </label>
            <p className="ops-risk-note">
              Account status API는 현재 requestId audit을 기준으로 추적합니다.
            </p>
            <label>
              Confirm phrase
              <input
                value={form.accountStatusConfirmation}
                onChange={(event) =>
                  updateField("accountStatusConfirmation", event.target.value)
                }
                placeholder={`CHANGE ACCOUNT ${form.accountStatusAccountId || "{accountId}"}`}
              />
            </label>
            <button className="ops-danger-button" type="button" onClick={runAccountStatusUpdate}>
              account status 변경
            </button>
            <ResultPanel result={results["Account status update"] ?? initialOpsResult} />
          </article>
        </div>
      </section>

      <section className="ops-lookups" aria-label="exact lookup">
        <div className="ops-lookup-title">
          <strong>Ledger / Snapshot / Auth / Account exact lookup</strong>
          <span>write action 이후 같은 requestId로 감사 조회 결과를 확인합니다.</span>
        </div>
        <div className="ops-lookup-fields">
          <label>
            Ledger requestId
            <input
              value={form.requestId}
              onChange={(event) => updateField("requestId", event.target.value)}
            />
            <button
              type="button"
              onClick={() =>
                runLookup(lookupItems[0].label, lookupItems[0].path, form.requestId)
              }
            >
              Ledger 조회
            </button>
          </label>
          <label>
            Ledger transactionReference
            <input
              value={form.transactionReference}
              onChange={(event) =>
                updateField("transactionReference", event.target.value)
              }
            />
            <button
              type="button"
              onClick={() =>
                runLookup(
                  lookupItems[1].label,
                  lookupItems[1].path,
                  form.transactionReference,
                )
              }
            >
              거래 조회
            </button>
          </label>
          <label>
            Account audit requestId
            <input
              value={form.accountRequestId}
              onChange={(event) => updateField("accountRequestId", event.target.value)}
            />
            <button
              type="button"
              onClick={() =>
                runLookup(
                  lookupItems[2].label,
                  lookupItems[2].path,
                  form.accountRequestId,
                )
              }
            >
              Account 조회
            </button>
          </label>
          <label>
            Auth audit requestId
            <input
              value={form.authRequestId}
              onChange={(event) => updateField("authRequestId", event.target.value)}
            />
            <button
              type="button"
              onClick={() =>
                runLookup(lookupItems[3].label, lookupItems[3].path, form.authRequestId)
              }
            >
              Auth 조회
            </button>
          </label>
        </div>
        <div className="ops-grid compact">
          {lookupItems.map((item) => (
            <article className="ops-card" key={item.label}>
              <div className="ops-card-head">
                <div>
                  <strong>{item.label}</strong>
                  <span>{item.path}</span>
                </div>
              </div>
              <ResultPanel result={results[item.label] ?? initialOpsResult} />
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
