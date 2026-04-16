package com.aquilabank.global.security;

/** JWT user principal과 bootstrap account principal을 함께 다루는 공통 타입 */
public sealed interface AuthenticatedRequestPrincipal
    permits AuthenticatedAccountPrincipal, AuthenticatedUserPrincipal {

  String subject();
}
