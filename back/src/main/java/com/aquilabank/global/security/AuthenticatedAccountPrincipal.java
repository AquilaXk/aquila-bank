package com.aquilabank.global.security;

/** Security principal shared by JWT auth and bootstrap header auth. */
public record AuthenticatedAccountPrincipal(long accountId, String subject) {}
