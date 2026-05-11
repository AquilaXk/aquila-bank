export type MenuSection =
  | "dashboard"
  | "accounts"
  | "transfer"
  | "transactions"
  | "notifications"
  | "security";

export type AlertMessage = {
  type: "info" | "success" | "error";
  text: string;
};
