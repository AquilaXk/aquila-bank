package com.aquilabank.domain.auth.model;

import java.time.Instant;

public record VerifiedContactUpsertCommand(
    long userId, VerifiedContactChannel channel, String providerDestination, Instant verifiedAt) {

  public VerifiedContactUpsertCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (providerDestination == null || providerDestination.isBlank()) {
      throw new IllegalArgumentException("providerDestination must not be blank");
    }
    providerDestination = providerDestination.trim();
    if (providerDestination.length() > 255) {
      throw new IllegalArgumentException("providerDestination must be 255 characters or less");
    }
    if (verifiedAt == null) {
      throw new IllegalArgumentException("verifiedAt must not be null");
    }
  }
}
