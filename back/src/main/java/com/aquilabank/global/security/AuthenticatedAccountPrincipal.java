package com.aquilabank.global.security;

/** JWT auth와 bootstrap header auth가 함께 쓰는 account principal */
public record AuthenticatedAccountPrincipal(long accountId, String subject) {}
