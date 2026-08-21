package io.akka.coroot.domain;

import static io.akka.coroot.domain.Worlds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 sections 3.4 and 3.5 — rolling the columns up, and who is on the table at all. */
class RollupTest {

  private static Application typed(Application a, Status typeStatus, boolean hasReport) {
    return new Application(
        a.id(), a.category(), a.reports(), a.availability(), a.latency(), typeStatus, hasReport);
  }

  @Test
  void theStatusIsTheHighestColumn() {
    var a =
        app(
            "a",
            report(Report.LOGS, new Check(CheckId.LOG_ERRORS, Status.CRITICAL, 0, 0, 0, 2)),
            report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.WARNING)));

    assertThat(Attribution.attribute(world(List.of(a))).getFirst().status()).isEqualTo(Status.WARNING);
  }

  @Test
  void anApplicationWithNothingAttributedIsNotOnTheTable() {
    var a = app("a", report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.OK)));

    assertThat(Attribution.attribute(world(List.of(a)))).isEmpty();
  }

  @Test
  void theTypeReportCanRaiseTheStatusButNotLowerIt() {
    var raised =
        typed(app("raised", report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.WARNING))), Status.CRITICAL, true);
    var notLowered =
        typed(
            app("notlowered", report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.WARNING))), Status.OK, true);
    var w = world(List.of(raised, notLowered));

    var rows = Attribution.attribute(w);
    assertThat(rows.stream().filter(r -> r.id().equals(id("raised"))).findFirst().orElseThrow().status())
        .isEqualTo(Status.CRITICAL);
    assertThat(rows.stream().filter(r -> r.id().equals(id("notlowered"))).findFirst().orElseThrow().status())
        .isEqualTo(Status.WARNING);
  }

  @Test
  void anOkApplicationWithAnUndecidedTypeReportBecomesUndecided() {
    var a =
        typed(
            app("a", report(Report.STORAGE, check(CheckId.STORAGE_SPACE, Status.OK, 12))),
            Status.UNKNOWN,
            true);

    assertThat(Attribution.attribute(world(List.of(a))).getFirst().status()).isEqualTo(Status.UNKNOWN);
  }

  @Test
  void theTypeReportIsAppliedAfterTheDropTestNotBefore() {
    // Every column undecided, so the application is dropped, and a critical type status
    // does not rescue it (SPEC-001 section 3.5).
    var a = typed(app("a"), Status.CRITICAL, true);

    assertThat(Attribution.attribute(world(List.of(a)))).isEmpty();
  }
}
