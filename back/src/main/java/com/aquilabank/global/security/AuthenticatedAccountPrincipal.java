package com.aquilabank.global.security;

public record AuthenticatedAccountPrincipal(long accountId, String subject) {}
