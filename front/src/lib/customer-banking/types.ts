import type { CustomerApplicationResponse, CustomerApplicationType } from "@/lib/api/types";

export type MenuSection =
  | "dashboard"
  | "accounts"
  | "transfer"
  | "transactions"
  | "notifications"
  | "security"
  | "securityHub"
  | "supportCenter"
  | "enterpriseServices";

export type AlertMessage = {
  type: "info" | "success" | "error";
  text: string;
};

export type ServiceSearchItem = {
  id: string;
  label: string;
  group: string;
  section: MenuSection;
  description: string;
  keywords: string[];
  requiresSession: boolean;
};

export type CustomerApplicationSubmitInput = {
  applicationType: CustomerApplicationType;
  accountId?: string;
  totpCode: string;
  payload: Record<string, unknown>;
  successMessage: string;
};

export type CustomerApplicationSubmitHandler = (
  input: CustomerApplicationSubmitInput,
) => Promise<boolean>;

export type CustomerApplicationLatestResult = CustomerApplicationResponse | null;
