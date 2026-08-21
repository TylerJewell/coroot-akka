package io.akka.coroot.domain;

/**
 * Severity, least to greatest. The ordering is load-bearing: nearly every rule in SPEC-001
 * is a maximum or a threshold over it, and UNKNOWN being least is what makes an absent
 * signal raise nothing.
 */
public enum Status {
  UNKNOWN,
  OK,
  INFO,
  WARNING,
  CRITICAL;

  public boolean atLeast(Status other) {
    return compareTo(other) >= 0;
  }

  public Status max(Status other) {
    return compareTo(other) >= 0 ? this : other;
  }

  public String wire() {
    return name().toLowerCase();
  }

  public static Status fromWire(String s) {
    if (s == null) return UNKNOWN;
    return switch (s) {
      case "ok" -> OK;
      case "info" -> INFO;
      case "warning" -> WARNING;
      case "critical" -> CRITICAL;
      default -> UNKNOWN;
    };
  }
}
