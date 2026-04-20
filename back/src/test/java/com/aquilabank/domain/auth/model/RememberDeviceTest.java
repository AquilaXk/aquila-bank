package com.aquilabank.domain.auth.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RememberDeviceTest {

  @Test
  void createsValidRememberDevice() {
    RememberDevice device =
        new RememberDevice(
            1L,
            7L,
            "a".repeat(64),
            RememberDeviceStatus.ACTIVE,
            "Windows / Chrome",
            Instant.parse("2026-04-20T11:00:00Z"),
            Instant.parse("2026-05-20T11:00:00Z"),
            Instant.parse("2026-04-20T11:00:00Z"));

    assertThat(device.userId()).isEqualTo(7L);
    assertThat(device.deviceStatus()).isEqualTo(RememberDeviceStatus.ACTIVE);
  }

  @Test
  void rejectsInvalidIdentifiersAndBlankValues() {
    Instant now = Instant.parse("2026-04-20T11:00:00Z");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RememberDevice(
                0L,
                7L,
                "a".repeat(64),
                RememberDeviceStatus.ACTIVE,
                "Windows / Chrome",
                now,
                now.plusSeconds(60),
                now));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RememberDevice(
                1L, 7L, " ", RememberDeviceStatus.ACTIVE, "Windows / Chrome", now, now, now));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RememberDevice(
                1L, 7L, "a".repeat(64), RememberDeviceStatus.ACTIVE, " ", now, now, now));
  }
}
