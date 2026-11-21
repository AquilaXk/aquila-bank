/*
 * First-Party Transaction Category Badge
 */

import type { TransactionCategory } from "@/lib/api/types";
import { getTransactionCategoryLabel } from "@/lib/customer-banking/labels";

const CATEGORY_CLASS_MAP: Record<string, string> = {
  FOOD: "cat-rose",
  SALARY: "cat-emerald",
  SAVINGS: "cat-emerald",
  HOUSING: "cat-amber",
  UTILITY: "cat-amber",
  TRANSFER: "cat-indigo",
  LIVING: "cat-blue",
};

export function CategoryBadge({
  category,
}: {
  category?: TransactionCategory | string | null;
}) {
  const variant = category ? (CATEGORY_CLASS_MAP[category] ?? "cat-slate") : "cat-slate";
  const label = getTransactionCategoryLabel(category);

  return (
    <span className={`ab-category-badge ${variant}`}>
      <span aria-hidden="true" className="ab-category-dot" />
      <span>{label}</span>
    </span>
  );
}
