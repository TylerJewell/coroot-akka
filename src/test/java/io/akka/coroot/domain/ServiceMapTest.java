package io.akka.coroot.domain;

import static io.akka.coroot.domain.Worlds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 section 3.7 and 4.2 — the map, its edge statuses and its order. */
class ServiceMapTest {

  private static ServiceMap.Node node(ServiceMap map, String name) {
    return map.nodes().stream()
        .filter(n -> n.id().equals(id(name)))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " is not on the map"));
  }

  @Test
  void edgeStatusComesFromTheConnectionNotFromEitherEnd() {
    var caller = app("caller", slo(check(CheckId.SLO_AVAILABILITY, Status.OK)));
    var callee = app("callee", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)));
    var w = world(List.of(caller, callee), calls("caller", "callee"));

    var edge = node(ServiceMap.render(w), "caller").upstreams().getFirst();
    assertThat(edge.status()).isEqualTo(Status.OK);
    assertThat(edge.reason()).isEmpty();
  }

  @Test
  void silentRoundTripTimeIsAConnectivityProblem() {
    var w =
        world(
            List.of(app("a"), app("b")),
            new Connection(id("a"), id("b"), 5, 0, 0, true, true, 10, 0.01, 1000, 2000));

    var edge = node(ServiceMap.render(w), "a").upstreams().getFirst();
    assertThat(edge.status()).isEqualTo(Status.CRITICAL);
    assertThat(edge.reason()).isEqualTo("connectivity issues");
    assertThat(edge.stats()).containsExactly("⚠️ connectivity issues");
  }

  @Test
  void failedConnectionAttemptsAreReportedWhenRoundTripTimeIsFine() {
    var w =
        world(
            List.of(app("a"), app("b")),
            new Connection(id("a"), id("b"), 5, 0, 2, true, false, 10, 0.01, 1000, 2000));

    var edge = node(ServiceMap.render(w), "a").upstreams().getFirst();
    assertThat(edge.status()).isEqualTo(Status.CRITICAL);
    assertThat(edge.reason()).isEqualTo("failed connections");
  }

  @Test
  void anIdleConnectionHasNoStatusAtAll() {
    var w =
        world(
            List.of(app("a"), app("b")),
            new Connection(id("a"), id("b"), 0, 0, 0, true, false, 10, 0.01, 1000, 2000));

    assertThat(node(ServiceMap.render(w), "a").upstreams().getFirst().status())
        .isEqualTo(Status.UNKNOWN);
  }

  @Test
  void theWorstOfSeveralConnectionsToTheSameDependencyWins() {
    var w =
        world(
            List.of(app("a"), app("b")),
            new Connection(id("a"), id("b"), 5, 0, 0, true, false, 10, 0.01, 1000, 2000),
            new Connection(id("a"), id("b"), 5, 0, 3, true, false, 4, 0.02, 500, 500));

    var edges = node(ServiceMap.render(w), "a").upstreams();
    assertThat(edges).hasSize(1);
    assertThat(edges.getFirst().status()).isEqualTo(Status.CRITICAL);
    assertThat(edges.getFirst().reason()).isEqualTo("failed connections");
  }

  @Test
  void aSinkAdoptsTheCategoryOfItsCallersOnlyWhenTheyAgree() {
    var w =
        world(
            List.of(
                withCategory(app("a1"), "control-plane"),
                withCategory(app("a2"), "control-plane"),
                withCategory(app("b1"), "control-plane"),
                withCategory(app("b2"), Category.MONITORING),
                withCategory(app("c1"), Category.MONITORING),
                app("agreed"),
                app("disputed"),
                withCategory(app("named"), "control-plane")),
            calls("a1", "agreed"),
            calls("a2", "agreed"),
            calls("b1", "disputed"),
            calls("b2", "disputed"),
            calls("c1", "named"));

    var map = ServiceMap.render(w);
    assertThat(node(map, "agreed").category()).isEqualTo("control-plane");
    assertThat(node(map, "disputed").category()).isEqualTo(Category.DEFAULT);
    assertThat(node(map, "named").category()).isEqualTo("control-plane");
  }

  @Test
  void anApplicationWithNoEdgesIsNotOnTheMap() {
    var w = world(List.of(app("lonely"), app("a"), app("b")), calls("a", "b"));

    assertThat(ServiceMap.render(w).nodes().stream().map(n -> n.id().name()))
        .containsExactly("a", "b");
  }

  @Test
  void nodeOrderIsTotalAndRepeatable() {
    var apps = List.of(app("zulu"), app("alpha"), app("mike"));
    var forward = world(apps, calls("zulu", "alpha"), calls("mike", "alpha"));
    var reversed =
        world(
            List.of(app("mike"), app("zulu"), app("alpha")),
            calls("mike", "alpha"),
            calls("zulu", "alpha"));

    var names = ServiceMap.render(forward).nodes().stream().map(n -> n.id().name()).toList();
    assertThat(names).containsExactly("alpha", "mike", "zulu");
    assertThat(ServiceMap.render(reversed).nodes().stream().map(n -> n.id().name()).toList())
        .isEqualTo(names);
  }

  @Test
  void aDependencyRecordsItsCallers() {
    var w = world(List.of(app("a"), app("b"), app("c")), calls("a", "c"), calls("b", "c"));

    assertThat(node(ServiceMap.render(w), "c").downstreams().stream().map(ApplicationId::name))
        .containsExactly("a", "b");
  }

  @Test
  void aSelfCallIsNotAnEdge() {
    var w = world(List.of(app("a"), app("b")), calls("a", "a"), calls("a", "b"));

    var map = ServiceMap.render(w);
    assertThat(node(map, "a").upstreams()).hasSize(1);
    assertThat(node(map, "a").upstreams().getFirst().to()).isEqualTo(id("b"));
  }

  @Test
  void aNodeCarriesTheVerdictAboutTheApplicationItself() {
    var caller = app("caller", slo(check(CheckId.SLO_AVAILABILITY, Status.OK)));
    var callee =
        app(
            "callee",
            slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)),
            report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.CRITICAL)));
    var w =
        world(
            List.of(caller, callee),
            new Connection(id("caller"), id("callee"), 5, 0, 3, true, false, 1, 0.01, 0, 0));

    var map = ServiceMap.render(w);
    assertThat(node(map, "caller").upstreams().getFirst().status()).isEqualTo(Status.CRITICAL);
    assertThat(node(map, "caller").status()).isEqualTo(Status.OK);
    assertThat(node(map, "callee").status()).isEqualTo(Status.CRITICAL);
  }

  @Test
  void aNodeStatusIgnoresReportsOutsideTheObjectiveAndInstanceOnes() {
    var a = app("a", report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.CRITICAL)));
    var b = app("b", report(Report.INSTANCES, check(CheckId.INSTANCE_AVAILABILITY, Status.WARNING)));
    var w = world(List.of(a, b), calls("a", "b"));

    var map = ServiceMap.render(w);
    assertThat(node(map, "a").status()).isEqualTo(Status.UNKNOWN);
    assertThat(node(map, "b").status()).isEqualTo(Status.WARNING);
  }

  @Test
  void liveEdgesCarryTheirTrafficStats() {
    var w = world(List.of(app("a"), app("b")), calls("a", "b"));

    var edge = node(ServiceMap.render(w), "a").upstreams().getFirst();
    assertThat(edge.weight()).isEqualTo(10.0);
    assertThat(edge.stats())
        .containsExactly("📈 10 rps ⏱️ 10ms", "↑1kB/s ↓2kB/s");
  }
}
