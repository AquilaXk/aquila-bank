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
    String requestId = request.getHeader(RequestTraceContext.REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return requestId;
  }
}
