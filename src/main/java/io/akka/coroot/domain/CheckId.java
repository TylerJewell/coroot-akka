package io.akka.coroot.domain;

/**
 * The checks that attribute to a column. OTHER stands for every other check the auditor
 * produces: SPEC-001 section 3.2 requires those to attribute nothing.
 */
public enum CheckId {
  SLO_AVAILABILITY,
  SLO_LATENCY,
  CPU_NODE,
  CPU_CONTAINER,
  MEMORY_OOM,
  MEMORY_LEAK_PERCENT,
  STORAGE_IO_LOAD,
  STORAGE_SPACE,
  NETWORK_RTT,
  NETWORK_CONNECTIVITY,
  NETWORK_TCP_CONNECTIONS,
  DNS_LATENCY,
  DNS_SERVER_ERRORS,
  DNS_NXDOMAIN_ERRORS,
  INSTANCE_AVAILABILITY,
  INSTANCE_RESTARTS,
  LOG_ERRORS,
  OTHER;

  public static CheckId fromWire(String s) {
    if (s == null) return OTHER;
    try {
      return valueOf(s);
    } catch (IllegalArgumentException e) {
      return OTHER;
    }
  }
}
