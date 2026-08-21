package io.akka.coroot.domain;

/** The twelve columns an application's trouble can be attributed to. */
public enum Cause {
  ERRORS("errors"),
  LATENCY("latency"),
  UPSTREAMS("upstreams"),
  INSTANCES("instances"),
  RESTARTS("restarts"),
  CPU("cpu"),
  MEMORY("memory"),
  DISK_IO_LOAD("disk_io_load"),
  DISK_USAGE("disk_usage"),
  NETWORK("network"),
  DNS("dns"),
  LOGS("logs");

  private final String wire;

  Cause(String wire) {
    this.wire = wire;
  }

  public String wire() {
    return wire;
  }
}
