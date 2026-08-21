package io.akka.coroot.domain;

import static io.akka.coroot.domain.Worlds.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SPEC-001 section 3.1 — the one verdict every gate in section 3.2 and 3.3 reads. */
class SloVerdictTest {

  @Test
  void anyObjectiveCheckAtWarningOrAboveIsABreach() {
    assertThat(Attribution.breachingObjective(serving(app("a", slo(check(CheckId.SLO_AVAILABILITY, Status.WARNING))), 100, 5)))
        .isTrue();
    assertThat(Attribution.breachingObjective(responding(app("a", slo(check(CheckId.SLO_LATENCY, Status.CRITICAL))), 0.4)))
        .isTrue();
  }

  @Test
  void anythingBelowWarningIsNot() {
    assertThat(Attribution.breachingObjective(serving(app("a", slo(check(CheckId.SLO_AVAILABILITY, Status.OK))), 100, 0)))
        .isFalse();
    assertThat(Attribution.breachingObjective(serving(app("a", slo(check(CheckId.SLO_AVAILABILITY, Status.INFO))), 100, 0)))
        .isFalse();
    assertThat(Attribution.breachingObjective(app("a"))).isFalse();
  }

  @Test
  void aFailingObjectiveCheckWithNoIndicatorBehindItIsNotABreach() {
    assertThat(Attribution.breachingObjective(app("a", slo(check(CheckId.SLO_AVAILABILITY, Status.CRITICAL)))))
        .isFalse();
  }

  @Test
  void onlyTheObjectiveReportIsConsulted() {
    assertThat(
            Attribution.breachingObjective(
                app("a", report(Report.MEMORY, check(CheckId.MEMORY_OOM, Status.CRITICAL)))))
        .isFalse();
  }
}
