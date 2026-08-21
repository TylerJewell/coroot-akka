package io.akka.coroot.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The value strings shown beside a column or on a map edge.
 *
 * <p>Rounding is half-to-even throughout, which is what the original produces: a disk load
 * of 6.5 reads as {@code 6}, not {@code 7}. Java's own {@code String.format} rounds half
 * away from zero and disagrees on exactly those midpoints.
 */
public final class Format {

  private Format() {}

  private static String fixed(double v, int decimals) {
    return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_EVEN).toPlainString();
  }

  /** Fewer significant digits the larger the number, and an empty string for nothing. */
  public static String number(double v) {
    if (Double.isNaN(v)) return "";
    if (v == 0) return "0";
    if (v >= 1) return fixed(v, 0);
    if (v >= 0.1) return fixed(v, 1);
    if (v >= 0.01) return fixed(v, 2);
    return fixed(v, 3);
  }

  public static String latency(double seconds) {
    if (seconds < 0.0001) return "<0.1ms";
    if (seconds < 1) return number(seconds * 1000) + "ms";
    return number(seconds) + "s";
  }

  /** Anything under one percent collapses, because the exact figure is not the point there. */
  public static String percent(double v) {
    if (v < 1) return "<1%";
    return fixed(v, 0) + "%";
  }

  private static final String[] UNITS = {"B", "kB", "MB", "GB", "TB", "PB", "EB"};

  public static String bytes(double v) {
    int e = (int) Math.floor(Math.log(v) / Math.log(1000.0));
    if (e < 0 || e > UNITS.length - 1) return fixed(v, 0);
    // Byte counts round half away from zero, unlike every other figure here: the original
    // rounds the scaled value to a whole number before formatting it.
    return Long.toString(Math.round(v / Math.pow(1000.0, e))) + UNITS[e];
  }
}
