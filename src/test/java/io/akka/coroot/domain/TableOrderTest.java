package io.akka.coroot.domain;

import static io.akka.coroot.domain.Worlds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 section 3.6 — the order of the attribution table. */
class TableOrderTest {

  private static Application withStatus(String name, Status s) {
    return app(name, report(Report.STORAGE, check(CheckId.STORAGE_SPACE, s, 12)));
  }

  private static List<String> names(World w) {
    return Attribution.attribute(w).stream().map(r -> r.id().name()).toList();
  }

  @Test
  void worstFirstAndTiesByName() {
    var w =
        world(
            List.of(
                withStatus("zulu", Status.WARNING),
                withStatus("alpha", Status.CRITICAL),
                withStatus("bravo", Status.WARNING),
                app("india", report(Report.LOGS, new Check(CheckId.LOG_ERRORS, Status.WARNING, 0, 0, 0, 1)))));

    assertThat(names(w)).containsExactly("alpha", "bravo", "zulu", "india");
  }

  @Test
  void healthyRowsSortLastEvenBelowUndecidedOnes() {
    var undecided =
        new Application(
            id("undecided"),
            Category.DEFAULT,
            List.of(report(Report.STORAGE, check(CheckId.STORAGE_SPACE, Status.OK, 12))),
            List.of(),
            List.of(),
            Status.UNKNOWN,
            true);
    var w = world(List.of(withStatus("aaa", Status.OK), undecided));

    assertThat(names(w)).containsExactly("undecided", "aaa");
  }

  @Test
  void theSameWorldInADifferentInputOrderGivesTheSameTable() {
    var a = withStatus("alpha", Status.CRITICAL);
    var b = withStatus("bravo", Status.WARNING);
    var c = withStatus("charlie", Status.OK);

    assertThat(names(world(List.of(a, b, c)))).isEqualTo(names(world(List.of(c, a, b))));
  }
}
