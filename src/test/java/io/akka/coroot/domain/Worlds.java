package io.akka.coroot.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builders that keep the tests about the rule under test rather than about record arity. */
final class Worlds {

  private Worlds() {}

  static ApplicationId id(String name) {
    return new ApplicationId("", "default", "Deployment", name);
  }

  static Check check(CheckId id, Status status) {
    return new Check(id, status, 0, 0, 0, 0);
  }

  static Check check(CheckId id, Status status, float value) {
    return new Check(id, status, value, 0, 0, 0);
  }

  static Report slo(Check... checks) {
    return new Report(Report.SLO, Arrays.asList(checks));
  }

  static Report report(String name, Check... checks) {
    return new Report(name, Arrays.asList(checks));
  }

  static Application app(String name, Report... reports) {
    return new Application(
        id(name), Category.DEFAULT, Arrays.asList(reports), List.of(), List.of(), Status.UNKNOWN, false);
  }

  static Application withCategory(Application a, String category) {
    return new Application(
        a.id(), category, a.reports(), a.availability(), a.latency(), a.typeStatus(), a.hasTypeReport());
  }

  static Application serving(Application a, double total, double failed) {
    return new Application(
        a.id(),
        a.category(),
        a.reports(),
        List.of(new AvailabilityIndicator(total, failed)),
        a.latency(),
        a.typeStatus(),
        a.hasTypeReport());
  }

  static Application responding(Application a, double objectiveLatencySeconds) {
    return new Application(
        a.id(),
        a.category(),
        a.reports(),
        a.availability(),
        List.of(new LatencyIndicator(objectiveLatencySeconds)),
        a.typeStatus(),
        a.hasTypeReport());
  }

  /** A live connection with nothing wrong with it. */
  static Connection calls(String from, String to) {
    return new Connection(id(from), id(to), 5, 0, 0, true, false, 10, 0.01, 1000, 2000);
  }

  static World world(List<Application> apps, Connection... connections) {
    return new World(new ArrayList<>(apps), Arrays.asList(connections));
  }
}
