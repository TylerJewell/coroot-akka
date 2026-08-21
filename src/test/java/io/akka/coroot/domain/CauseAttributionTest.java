package io.akka.coroot.domain;

import static io.akka.coroot.domain.Worlds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 section 3.2 — which check writes which column, and under what gate. */
class CauseAttributionTest {

  private AttributionRow rowFor(World w, String name) {
    return Attribution.attribute(w).stream()
        .filter(r -> r.id().equals(id(name)))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " is not in the table"));
  }

  @Test
  void nodeCpuCountsOnlyForAnApplicationBreachingItsObjective() {
    var breaching =
        serving(
            app(
                "breaching",
                slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)),
                report(Report.CPU, check(CheckId.CPU_NODE, Status.WARNING, 91))),
            100,
            5);
    var meeting =
        serving(
            app(
                "meeting",
                slo(check(CheckId.SLO_AVAILABILITY, Status.OK)),
                report(Report.INSTANCES, new Check(CheckId.INSTANCE_AVAILABILITY, Status.OK, 0, 2, 2, 0)),
                report(Report.CPU, check(CheckId.CPU_NODE, Status.WARNING, 91))),
            100,
            0);
    var w = world(List.of(breaching, meeting));

    assertThat(rowFor(w, "breaching").column(Cause.CPU))
        .isEqualTo(new Column(Status.WARNING, "shortage"));
    assertThat(rowFor(w, "meeting").column(Cause.CPU)).isEqualTo(Column.absent());
  }

  @Test
  void containerCpuIsNotGatedOnTheObjective() {
    var meeting =
        serving(
            app(
                "meeting",
                slo(check(CheckId.SLO_AVAILABILITY, Status.OK)),
                report(Report.CPU, check(CheckId.CPU_CONTAINER, Status.WARNING, 91))),
            100,
            0);

    assertThat(rowFor(world(List.of(meeting)), "meeting").column(Cause.CPU))
        .isEqualTo(new Column(Status.WARNING, "shortage"));
  }

  @Test
  void theObjectiveVerdictIsDecidedBeforeAnyColumnIsWritten() {
    // Same facts, the CPU report listed first. SPEC-001 section 3.1 and 4.1: the answer
    // must not depend on the order the reports happen to be in.
    var cpuFirst =
        serving(
            app(
                "app",
                report(Report.CPU, check(CheckId.CPU_NODE, Status.WARNING, 91)),
                slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL))),
            100,
            5);
    var sloFirst =
        serving(
            app(
                "app",
                slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)),
                report(Report.CPU, check(CheckId.CPU_NODE, Status.WARNING, 91))),
            100,
            5);

    assertThat(rowFor(world(List.of(cpuFirst)), "app").column(Cause.CPU))
        .isEqualTo(rowFor(world(List.of(sloFirst)), "app").column(Cause.CPU))
        .isEqualTo(new Column(Status.WARNING, "shortage"));
  }

  @Test
  void aWarningObjectiveCheckIsReportedAsCriticalErrors() {
    var a = serving(app("a", slo(check(CheckId.SLO_AVAILABILITY, Status.WARNING))), 1000, 4);

    assertThat(rowFor(world(List.of(a)), "a").column(Cause.ERRORS))
        .isEqualTo(new Column(Status.CRITICAL, "<1%"));
  }

  @Test
  void aFailingObjectiveCheckWithoutAnIndicatorAttributesNothing() {
    var a = app("a", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)));

    assertThat(Attribution.attribute(world(List.of(a)))).isEmpty();
  }

  @Test
  void networkLatencyIsNotACauseForAnApplicationMeetingItsObjective() {
    var meeting =
        serving(
            app(
                "meeting",
                slo(check(CheckId.SLO_AVAILABILITY, Status.OK)),
                report(Report.NETWORK, check(CheckId.NETWORK_RTT, Status.WARNING, 0.02f))),
            1000,
            0);
    var breaching =
        serving(
            app(
                "breaching",
                slo(check(CheckId.SLO_AVAILABILITY, Status.WARNING)),
                report(Report.NETWORK, check(CheckId.NETWORK_RTT, Status.WARNING, 0.02f))),
            1000,
            4);
    var w = world(List.of(meeting, breaching));

    assertThat(rowFor(w, "meeting").column(Cause.NETWORK)).isEqualTo(new Column(Status.OK, "20ms"));
    assertThat(rowFor(w, "breaching").column(Cause.NETWORK))
        .isEqualTo(new Column(Status.WARNING, "20ms"));
  }

  @Test
  void logErrorsNeverRaiseTheColumnAboveInfo() {
    var a = app("a", report(Report.LOGS, new Check(CheckId.LOG_ERRORS, Status.CRITICAL, 0, 0, 0, 3)));

    var row = rowFor(world(List.of(a)), "a");
    assertThat(row.column(Cause.LOGS)).isEqualTo(new Column(Status.INFO, "3 unique errors"));
    assertThat(row.status()).isEqualTo(Status.INFO);
  }

  @Test
  void outOfMemoryOutranksAMemoryLeak() {
    var a =
        app(
            "a",
            report(
                Report.MEMORY,
                check(CheckId.MEMORY_OOM, Status.WARNING),
                check(CheckId.MEMORY_LEAK_PERCENT, Status.WARNING, 20)));

    assertThat(rowFor(world(List.of(a)), "a").column(Cause.MEMORY))
        .isEqualTo(new Column(Status.WARNING, "OOM"));
  }

  @Test
  void aMemoryLeakOnItsOwnIsReported() {
    var a = app("a", report(Report.MEMORY, check(CheckId.MEMORY_LEAK_PERCENT, Status.WARNING, 20)));

    assertThat(rowFor(world(List.of(a)), "a").column(Cause.MEMORY))
        .isEqualTo(new Column(Status.WARNING, "leak"));
  }

  @Test
  void instanceCountsAreCarriedThroughVerbatim() {
    var a =
        app(
            "a",
            report(
                Report.INSTANCES,
                new Check(CheckId.INSTANCE_AVAILABILITY, Status.WARNING, 0, 2, 3, 0),
                new Check(CheckId.INSTANCE_RESTARTS, Status.WARNING, 0, 7, 0, 0)));

    var row = rowFor(world(List.of(a)), "a");
    assertThat(row.column(Cause.INSTANCES)).isEqualTo(new Column(Status.WARNING, "2/3"));
    assertThat(row.column(Cause.RESTARTS)).isEqualTo(new Column(Status.WARNING, "7"));
  }

  @Test
  void anUnnamedCheckAttributesNothing() {
    var a =
        app(
            "a",
            report(Report.INSTANCES, new Check(CheckId.INSTANCE_AVAILABILITY, Status.WARNING, 0, 1, 1, 0)),
            report("Postgres", check(CheckId.OTHER, Status.CRITICAL)));

    assertThat(rowFor(world(List.of(a)), "a").status()).isEqualTo(Status.WARNING);
  }
}
