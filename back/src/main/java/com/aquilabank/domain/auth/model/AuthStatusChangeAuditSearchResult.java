package com.aquilabank.domain.auth.model;

import java.util.List;

public record AuthStatusChangeAuditSearchResult(
    List<AuthStatusChangeAuditItem> items, String nextCursor) {

  public AuthStatusChangeAuditSearchResult {
    items = List.copyOf(items == null ? List.of() : items);
    if (nextCursor != null && nextCursor.isBlank()) {
      nextCursor = null;
    }
  }
}
