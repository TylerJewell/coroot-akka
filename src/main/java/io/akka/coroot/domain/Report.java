package io.akka.coroot.domain;

import java.util.List;

/** A group of checks. Only the objective report is consulted by name. */
public record Report(String name, List<Check> checks) {

  public static final String SLO = "SLO";
  public static final String CPU = "CPU";
  public static final String MEMORY = "Memory";
  public static final String STORAGE = "Storage";
  public static final String NETWORK = "Net";
  public static final String DNS = "DNS";
  public static final String LOGS = "Logs";
  public static final String INSTANCES = "Instances";

  public boolean isObjective() {
    return SLO.equals(name);
  }
}
