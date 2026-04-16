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
      SecurityContextHolder.clearContext();
    }
  }
}
