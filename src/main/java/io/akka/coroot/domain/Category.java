package io.akka.coroot.domain;

/** Application categories. Only two of them carry a rule; the rest are opaque labels. */
public final class Category {

  public static final String DEFAULT = "application";
  public static final String MONITORING = "monitoring";

  private Category() {}

  public static boolean isDefault(String c) {
    return DEFAULT.equals(c);
  }

  public static boolean isMonitoring(String c) {
    return MONITORING.equals(c);
  }
}
