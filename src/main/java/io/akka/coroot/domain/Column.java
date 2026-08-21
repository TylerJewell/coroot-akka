package io.akka.coroot.domain;

/** One cause column: a severity and the short human-readable value shown beside it. */
public record Column(Status status, String value) {

  private static final Column ABSENT = new Column(Status.UNKNOWN, "");

  public static Column absent() {
    return ABSENT;
  }

  public Column withStatus(Status s) {
    return new Column(s, value);
  }

  public Column withValue(String v) {
    return new Column(status, v);
  }
}
