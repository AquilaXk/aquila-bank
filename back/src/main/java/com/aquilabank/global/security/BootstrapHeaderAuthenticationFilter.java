package com.aquilabank.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 정식 로그인 구현 전 로컬 도구와 테스트에 쓰는 bootstrap 인증 filter */
public class BootstrapHeaderAuthenticationFilter extends OncePerRequestFilter {

  private final String accountIdHeader;
  private final String subjectHeader;

  public BootstrapHeaderAuthenticationFilter(String accountIdHeader, String subjectHeader) {
    this.accountIdHeader = accountIdHeader;
    this.subjectHeader = subjectHeader;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 실제 security chain에서 인증이 끝난 요청 우선 통과
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    String rawAccountId = request.getHeader(accountIdHeader);
    if (rawAccountId == null || rawAccountId.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      long accountId = Long.parseLong(rawAccountId);
      if (accountId <= 0) {
        throw new NumberFormatException("accountId must be positive");
      }

      String subject = request.getHeader(subjectHeader);
      AuthenticatedAccountPrincipal principal =
          new AuthenticatedAccountPrincipal(accountId, subject == null ? "bootstrap" : subject);
      UsernamePasswordAuthenticationToken authentication =
          UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (NumberFormatException ex) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "invalid account header");
    } finally {
      // 요청 단위 bootstrap auth 종료 후 context 정리
      SecurityContextHolder.clearContext();
    }
  }
}
