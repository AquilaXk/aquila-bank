package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** provider destination을 login 식별자와 분리해 보관하는 verified contact view */
public record VerifiedContact(
    long userId,
    VerifiedContactChannel channel,
    String providerDestination,
    Instant verifiedAt,
    Instant createdAt,
    Instant updatedAt) {

  public VerifiedContact {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (providerDestination == null || providerDestination.isBlank()) {
      throw new IllegalArgumentException("providerDestination must not be blank");
    }
    if (providerDestination.length() > 255) {
      throw new IllegalArgumentException("providerDestination must be 255 characters or less");
    }
    if (verifiedAt == null) {
      throw new IllegalArgumentException("verifiedAt must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt must not be null");
    }
  }
}
