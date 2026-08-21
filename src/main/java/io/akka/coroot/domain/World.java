package io.akka.coroot.domain;

import java.util.List;

/** One snapshot: the applications, and the traffic between them. */
public record World(List<Application> applications, List<Connection> connections) {}
