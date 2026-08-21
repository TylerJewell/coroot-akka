package io.akka.coroot.domain;

/** The latency at the objective quantile, already computed from the histogram. */
public record LatencyIndicator(double objectiveLatencySeconds) {}
