import type { OpsRequest } from "./types";

export function resolveOpsBaseUrl(): string {
  return (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/+$/, "");
}

function appendSearchParams(path: string, params: Record<string, unknown>): string {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    searchParams.set(key, String(value));
  });
  const query = searchParams.toString();
  return query ? `${path}?${query}` : path;
}

export async function getOpsJson(
  baseUrl: string,
  token: string,
  path: string,
): Promise<unknown> {
  const response = await fetch(`${baseUrl.replace(/\/+$/, "")}${path}`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
    },
    credentials: "include",
  });

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message =
      typeof body === "object" && body !== null && "message" in body
        ? String((body as { message?: unknown }).message)
        : response.statusText;
    throw new Error(message);
  }

  return body;
}

export function buildOpsRequests(limit: string): OpsRequest[] {
  return [
    {
      label: "Outbox summary",
      path: "/internal/api/v1/outbox/summary",
    },
    {
      label: "Outbox failed events",
      path: appendSearchParams("/internal/api/v1/outbox/failed-events", { limit }),
    },
    {
      label: "Notification summary",
      path: "/internal/api/v1/outbox/notification/summary",
    },
    {
      label: "Notification DLQ",
      path: appendSearchParams("/internal/api/v1/outbox/notification/dlq-events", { limit }),
    },
    {
      label: "Channel quarantine",
      path: appendSearchParams(
        "/internal/api/v1/outbox/notification-channel/quarantined-events",
        { limit },
      ),
    },
    {
      label: "Snapshot drift",
      path: appendSearchParams("/internal/api/v1/ledger/snapshot-reconciliation/drifts", {
        limit,
      }),
    },
    {
      label: "Command idempotency summary",
      path: "/internal/api/v1/ledger/command-idempotency/summary",
    },
    {
      label: "Command idempotency stale",
      path: appendSearchParams("/internal/api/v1/ledger/command-idempotency/stale-started", {
        limit,
      }),
    },
    {
      label: "Auth audit search",
      path: appendSearchParams("/internal/api/v1/auth/status-change-audits", { size: limit }),
    },
  ];
}

export function ledgerAuditByRequestIdPath(requestId: string, limit: string): string {
  return appendSearchParams(
    `/internal/api/v1/ledger/audit/request-ids/${encodeURIComponent(requestId)}/entries`,
    { limit },
  );
}

export function ledgerAuditByTransactionPath(
  transactionReference: string,
  limit: string,
): string {
  return appendSearchParams(
    `/internal/api/v1/ledger/audit/transactions/${encodeURIComponent(
      transactionReference,
    )}/entries`,
    { limit },
  );
}

export function accountAuditByRequestIdPath(requestId: string): string {
  return appendSearchParams("/internal/api/v1/accounts/status-change-audits/by-request-id", {
    requestId,
  });
}

export function authAuditByRequestIdPath(requestId: string): string {
  return appendSearchParams("/internal/api/v1/auth/status-change-audits/by-request-id", {
    requestId,
  });
}
