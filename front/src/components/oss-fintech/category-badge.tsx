/*
 * Horizon CategoryBadge & Spending Category Progress Row
 *
 * 참조 구현: adrianhajdin/banking (MIT License, Copyright (c) 2024 Adrian Hajdin - JS Mastery)
 * - components/TransactionsTable.tsx (CategoryBadge)
 * - components/Category.tsx
 * - constants/index.ts (transactionCategoryStyles, topCategoryStyles)
 */

import type { TransactionCategory } from "@/lib/api/types";
import { getTransactionCategoryLabel } from "@/lib/customer-banking/labels";

function resolveCategoryVariant(category: TransactionCategory | string | null | undefined): string {
  switch (category) {
    case "FOOD":
      return "cat-rose";
    case "SALARY":
    case "SAVINGS":
      return "cat-emerald";
    case "HOUSING":
    case "UTILITY":
      return "cat-amber";
    case "TRANSFER":
      return "cat-indigo";
    case "LIVING":
      return "cat-blue";
    default:
      return "cat-slate";
  }
}

export function HorizonCategoryBadge({
  category,
}: {
  category?: TransactionCategory | string | null;
}) {
  const variant = resolveCategoryVariant(category);
  const label = getTransactionCategoryLabel(category);

  return (
    <span className={`oss-category-badge ${variant}`}>
      <span aria-hidden="true" className="oss-category-dot" />
      <span>{label}</span>
    </span>
  );
}
