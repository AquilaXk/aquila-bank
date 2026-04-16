package com.aquilabank.domain.auth.port;

/** password hash 알고리즘을 domain 밖으로 분리하는 port */
public interface PasswordHashPort {

  boolean matches(String rawPassword, String passwordHash);
}
