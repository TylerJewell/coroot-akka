package io.akka.coroot.domain;

import java.util.List;

/**
 * One application in the world. {@code typeStatus} is the verdict of the report specific
 * to what the application is (a database, a runtime); it is an input here rather than
 * something derived, because deriving it needs the measurement pipeline SPEC-001 puts out
 * of scope.
 */
public record Application(
    ApplicationId id,
    String category,
    List<Report> reports,
    List<AvailabilityIndicator> availability,
    List<LatencyIndicator> latency,
    Status typeStatus,
    boolean hasTypeReport) {}
