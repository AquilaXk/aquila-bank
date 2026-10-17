package com.aquilabank.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

/** 모든 요청에 request-id를 부여해 로그와 ledger trace를 같은 키로 묶습니다. */
public class RequestIdFilter extends OncePerRequestFilter {

  private static final int MAX_REQUEST_ID_LENGTH = 96;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request);
    RequestTraceContext.set(requestId);
    response.setHeader(RequestTraceContext.REQUEST_ID_HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      RequestTraceContext.clear();
    }
  }

  private String resolveRequestId(HttpServletRequest request) {
    String rawRequestId = request.getHeader(RequestTraceContext.REQUEST_ID_HEADER);
    if (rawRequestId == null) {
      return UUID.randomUUID().toString();
    }
    String candidate = rawRequestId.trim();
    if (!isValidRequestId(candidate)) {
      return UUID.randomUUID().toString();
    }
    return candidate;
  }

  private static boolean isValidRequestId(String candidate) {
    if (candidate.isEmpty() || candidate.length() > MAX_REQUEST_ID_LENGTH) {
      return false;
    }
    for (int index = 0; index < candidate.length(); index++) {
      if (!isAllowedRequestIdChar(candidate.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAllowedRequestIdChar(char ch) {
    return (ch >= 'a' && ch <= 'z')
        || (ch >= 'A' && ch <= 'Z')
        || (ch >= '0' && ch <= '9')
        || ch == '-'
        || ch == '_'
        || ch == '.'
        || ch == ':';
  }
}
