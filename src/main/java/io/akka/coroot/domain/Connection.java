package io.akka.coroot.domain;

/**
 * Traffic from one application to another. Round-trip time is carried as two flags rather
 * than a series: whether it was ever measured, and whether the measurements have stopped
 * arriving. Those are different states, and only the second is a fault.
 */
public record Connection(
    ApplicationId from,
    ApplicationId to,
    double successfulConnections,
    double activeConnections,
    double failedConnections,
    boolean roundTripTimeMeasured,
    boolean roundTripTimeSilent,
    double requestsPerSecond,
    double latencySeconds,
    double bytesSent,
    double bytesReceived) {

  public boolean isActive() {
    return successfulConnections > 0 || activeConnections > 0 || failedConnections > 0;
  }

  public boolean hasConnectivityIssues() {
    return isActive() && roundTripTimeMeasured && roundTripTimeSilent;
  }

  public boolean hasFailedConnectionAttempts() {
    return isActive() && failedConnections > 0;
  }

  /** Severity and reason, decided by the connection alone — never by either endpoint. */
  public Status status() {
    if (!isActive()) return Status.UNKNOWN;
    if (hasConnectivityIssues() || hasFailedConnectionAttempts()) return Status.CRITICAL;
    return Status.OK;
  }

  public String reason() {
    if (!isActive()) return "";
    if (hasConnectivityIssues()) return "connectivity issues";
    if (hasFailedConnectionAttempts()) return "failed connections";
    return "";
  }
}
