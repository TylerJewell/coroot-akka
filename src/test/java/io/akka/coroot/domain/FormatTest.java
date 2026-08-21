package io.akka.coroot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The value strings, at the midpoints where two rounding rules disagree. */
class FormatTest {

  @Test
  void figuresRoundHalfToEven() {
    assertThat(Format.number(6.5)).isEqualTo("6");
    assertThat(Format.number(7.5)).isEqualTo("8");
    assertThat(Format.percent(12.5)).isEqualTo("12%");
    assertThat(Format.percent(13.5)).isEqualTo("14%");
  }

  @Test
  void byteCountsRoundHalfAwayFromZero() {
    assertThat(Format.bytes(2500)).isEqualTo("3kB");
    assertThat(Format.bytes(1000)).isEqualTo("1kB");
    assertThat(Format.bytes(2000000)).isEqualTo("2MB");
  }

  @Test
  void smallerFiguresKeepMoreDigits() {
    assertThat(Format.number(0.5)).isEqualTo("0.5");
    assertThat(Format.number(0.05)).isEqualTo("0.05");
    assertThat(Format.number(0.005)).isEqualTo("0.005");
    assertThat(Format.number(0)).isEqualTo("0");
  }

  @Test
  void latencySwitchesUnitAtOneSecond() {
    assertThat(Format.latency(0.00005)).isEqualTo("<0.1ms");
    assertThat(Format.latency(0.02)).isEqualTo("20ms");
    assertThat(Format.latency(1.5)).isEqualTo("2s");
  }

  @Test
  void anythingUnderOnePercentCollapses() {
    assertThat(Format.percent(0.4)).isEqualTo("<1%");
    assertThat(Format.percent(1)).isEqualTo("1%");
  }
}
