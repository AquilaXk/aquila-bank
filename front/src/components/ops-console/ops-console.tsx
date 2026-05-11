"use client";

import { useMemo, useState } from "react";
import {
  accountAuditByRequestIdPath,
  authAuditByRequestIdPath,
  buildOpsRequests,
  getOpsJson,
  ledgerAuditByRequestIdPath,
  ledgerAuditByTransactionPath,
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
    <main className="ops-shell">
      <header className="ops-header">
        <div>
          <strong>Aquila Bank Ops Console</strong>
          <span>read-only internal surface</span>
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
