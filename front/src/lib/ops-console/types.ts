export type OpsResult = {
  status: "idle" | "loading" | "success" | "error";
  data: unknown;
  error: string;
};

export type OpsRequest = {
  label: string;
  path: string;
};

export type OpsFormState = {
  baseUrl: string;
  token: string;
  limit: string;
  requestId: string;
  transactionReference: string;
  accountRequestId: string;
  authRequestId: string;
  outboxConfirmation: string;
  dlqPartition: string;
  dlqOffset: string;
  dlqConfirmation: string;
  channelEventId: string;
  channelConfirmation: string;
  idempotencyConfirmation: string;
  snapshotAccountId: string;
  snapshotReason: string;
  snapshotConfirmation: string;
  authStatusUserId: string;
  authStatusValue: string;
  authStatusReasonCode: string;
  authStatusReasonDetail: string;
  authStatusRequestId: string;
  authStatusConfirmation: string;
  membershipStatusUserId: string;
  membershipStatusAccountId: string;
  membershipStatusValue: string;
  membershipStatusReasonCode: string;
  membershipStatusReasonDetail: string;
  membershipStatusRequestId: string;
  membershipStatusConfirmation: string;
  accountStatusAccountId: string;
  accountStatusValue: string;
  accountStatusReasonCode: string;
  accountStatusReasonDetail: string;
  accountStatusRequestId: string;
  accountStatusConfirmation: string;
};

export const initialOpsResult: OpsResult = {
  status: "idle",
  data: null,
  error: "",
};
