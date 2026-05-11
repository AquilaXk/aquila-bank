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
