package com.aquilabank.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** 내부 auth 상태 변경 실패 로그도 request body를 읽을 수 있게 request를 감쌉니다. */
public class InternalAuthStatusAuditRequestCachingFilter extends OncePerRequestFilter {

  private static final int REQUEST_CACHE_LIMIT = 2048;
  private static final String USER_PATH_PREFIX = "/internal/api/v1/auth/users/";
  private static final String MEMBERSHIP_SEGMENT = "/memberships/";
  private static final String STATUS_SUFFIX = "/status";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !isUserStatusPath(path) && !isMembershipStatusPath(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request instanceof ContentCachingRequestWrapper) {
      filterChain.doFilter(request, response);
      return;
    }
    filterChain.doFilter(new ContentCachingRequestWrapper(request, REQUEST_CACHE_LIMIT), response);
  }

  private boolean isUserStatusPath(String path) {
    return path.startsWith(USER_PATH_PREFIX)
        && path.endsWith(STATUS_SUFFIX)
        && !path.contains(MEMBERSHIP_SEGMENT);
  }

  private boolean isMembershipStatusPath(String path) {
    return path.startsWith(USER_PATH_PREFIX)
        && path.contains(MEMBERSHIP_SEGMENT)
        && path.endsWith(STATUS_SUFFIX);
  }
}
