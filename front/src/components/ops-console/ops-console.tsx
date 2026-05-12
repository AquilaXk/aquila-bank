"use client";

import { useMemo, useState } from "react";
import {
  accountAuditByRequestIdPath,
  authAuditByRequestIdPath,
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

      <section className="ops-lookups" aria-label="exact lookup">
        <div className="ops-lookup-title">
          <strong>Ledger / Snapshot / Auth / Account exact lookup</strong>
          <span>destructive recovery와 status update는 이 MVP 범위에서 제외합니다.</span>
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
