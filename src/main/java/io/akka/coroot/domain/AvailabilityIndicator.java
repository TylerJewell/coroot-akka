package io.akka.coroot.domain;

/** Request counts over the window the attribution is being computed for. */
public record AvailabilityIndicator(double totalRequests, double failedRequests) {}
