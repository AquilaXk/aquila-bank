package com.aquilabank.global.ops;

public record T3MicroSaturationDecision(
    boolean allowed, int retryAfterSeconds, T3MicroSaturationSnapshot snapshot) {

  public static T3MicroSaturationDecision allowed(T3MicroSaturationSnapshot snapshot) {
    return new T3MicroSaturationDecision(true, 0, snapshot);
  }

  public static T3MicroSaturationDecision rejected(
      int retryAfterSeconds, T3MicroSaturationSnapshot snapshot) {
    return new T3MicroSaturationDecision(false, retryAfterSeconds, snapshot);
  }
}
