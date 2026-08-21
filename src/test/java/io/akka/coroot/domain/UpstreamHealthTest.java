package io.akka.coroot.domain;

import static io.akka.coroot.domain.Worlds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 section 3.3 — how dependency health is counted and when it is blamed. */
class UpstreamHealthTest {

  private AttributionRow rowFor(World w, String name) {
    return Attribution.attribute(w).stream()
        .filter(r -> r.id().equals(id(name)))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " is not in the table"));
  }

  private World twoCallers() {
    var meeting = serving(app("meeting", slo(check(CheckId.SLO_AVAILABILITY, Status.OK))), 100, 0);
    var breaching =
        serving(app("breaching", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), 100, 9);
    var failing = app("failing", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)));
    var healthy = app("healthy", slo(check(CheckId.SLO_AVAILABILITY, Status.OK)));
    return world(
        List.of(meeting, breaching, failing, healthy),
        calls("meeting", "failing"),
        calls("meeting", "healthy"),
        calls("breaching", "failing"),
        calls("breaching", "healthy"));
  }

  @Test
  void anUnhealthyDependencyIsBlamedOnlyWhenTheCallerIsItselfBreaching() {
    var w = twoCallers();

    assertThat(rowFor(w, "meeting").column(Cause.UPSTREAMS)).isEqualTo(new Column(Status.OK, "1/2"));
    assertThat(rowFor(w, "breaching").column(Cause.UPSTREAMS))
        .isEqualTo(new Column(Status.WARNING, "1/2"));
  }

  @Test
  void aDependencyInTheMonitoringCategoryIsNotCounted() {
    var breaching =
        serving(app("breaching", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), 100, 9);
    var failing = app("failing", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)));
    var healthy = app("healthy", slo(check(CheckId.SLO_AVAILABILITY, Status.OK)));
    var prometheus =
        withCategory(app("prom", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), Category.MONITORING);
    var w =
        world(
            List.of(breaching, failing, healthy, prometheus),
            calls("breaching", "failing"),
            calls("breaching", "healthy"),
            calls("breaching", "prom"));

    assertThat(rowFor(w, "breaching").column(Cause.UPSTREAMS))
        .isEqualTo(new Column(Status.WARNING, "1/2"));
  }

  @Test
  void aMonitoringCallerDoesCountItsMonitoringDependencies() {
    var caller =
        withCategory(
            serving(app("grafana", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), 100, 9),
            Category.MONITORING);
    var prometheus =
        withCategory(app("prom", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), Category.MONITORING);
    var w = world(List.of(caller, prometheus), calls("grafana", "prom"));

    assertThat(rowFor(w, "grafana").column(Cause.UPSTREAMS))
        .isEqualTo(new Column(Status.WARNING, "0/1"));
  }

  @Test
  void aDependencyWithNoDecidedObjectiveCheckIsNotCountedAtAll() {
    var caller =
        serving(app("caller", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), 100, 9);
    var silent = app("silent", slo(check(CheckId.SLO_AVAILABILITY, Status.UNKNOWN)));
    var w = world(List.of(caller, silent), calls("caller", "silent"));

    assertThat(rowFor(w, "caller").column(Cause.UPSTREAMS)).isEqualTo(Column.absent());
  }

  @Test
  void onlyChecksInTheObjectiveReportCount() {
    var caller =
        serving(app("caller", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), 100, 9);
    var noisy =
        app(
            "noisy",
            slo(check(CheckId.SLO_AVAILABILITY, Status.OK)),
            report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.CRITICAL)));
    var w = world(List.of(caller, noisy), calls("caller", "noisy"));

    assertThat(rowFor(w, "caller").column(Cause.UPSTREAMS)).isEqualTo(new Column(Status.OK, "1/1"));
  }

  @Test
  void anApplicationDoesNotCountItself() {
    var caller =
        serving(app("caller", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))), 100, 9);
    var w = world(List.of(caller), calls("caller", "caller"));

    assertThat(rowFor(w, "caller").column(Cause.UPSTREAMS)).isEqualTo(Column.absent());
  }
}
