package io.akka.coroot.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The attribution table — SPEC-001 section 3. Given check verdicts and traffic, decide which
 * named cause each application's trouble belongs to. Nothing here is inferred: every rule is
 * a threshold, a gate or a maximum over the severity ordering.
 */
public final class Attribution {

  private Attribution() {}

  /**
   * Whether the application is failing its own service level objective — the one verdict the
   * shortage, network and dependency gates all read.
   *
   * <p>A failing objective check is not enough on its own: the check has to be backed by an
   * indicator, because an application with no measured requests has no objective to fail.
   */
  public static boolean breachingObjective(Application app) {
    for (var report : app.reports()) {
      if (!report.isObjective()) continue;
      for (var check : report.checks()) {
        if (!check.status().atLeast(Status.WARNING)) continue;
        if (check.id() == CheckId.SLO_AVAILABILITY && !app.availability().isEmpty()) return true;
        if (check.id() == CheckId.SLO_LATENCY && !app.latency().isEmpty()) return true;
      }
    }
    return false;
  }

  public static List<AttributionRow> attribute(World world) {
    var byId = new HashMap<ApplicationId, Application>();
    for (var app : world.applications()) byId.put(app.id(), app);

    // Indexed once rather than rescanned per application: the dependency rule asks about
    // one caller at a time, and a linear scan there is quadratic in the size of the world.
    var outgoing = new HashMap<ApplicationId, List<Connection>>();
    for (var connection : world.connections()) {
      outgoing.computeIfAbsent(connection.from(), k -> new ArrayList<>()).add(connection);
    }
    // One dependency is typically called by many applications; its verdict does not change.
    var verdicts = new HashMap<ApplicationId, Optional<Boolean>>();

    var rows = new ArrayList<AttributionRow>();
    for (var app : world.applications()) {
      var columns = new EnumMap<Cause, Column>(Cause.class);
      boolean breaching = breachingObjective(app);

      for (var report : app.reports()) {
        for (var check : report.checks()) {
          apply(app, check, breaching, columns);
        }
      }
      dependencies(outgoing, verdicts, byId, app, breaching, columns);

      var rolled = Status.UNKNOWN;
      for (var column : columns.values()) rolled = rolled.max(column.status());
      if (rolled == Status.UNKNOWN) continue;

      rolled = rolled.max(app.typeStatus());
      if (rolled == Status.OK && app.typeStatus() == Status.UNKNOWN && app.hasTypeReport()) {
        rolled = Status.UNKNOWN;
      }
      rows.add(new AttributionRow(app.id(), app.category(), columns, rolled));
    }

    rows.sort(ordering());
    return rows;
  }

  /** Healthy last unconditionally, then worst first, then by name. */
  static Comparator<AttributionRow> ordering() {
    return (a, b) -> {
      if (a.status() == b.status()) return a.id().name().compareTo(b.id().name());
      if (a.status() == Status.OK) return 1;
      if (b.status() == Status.OK) return -1;
      return b.status().compareTo(a.status());
    };
  }

  private static void apply(Application app, Check check, boolean breaching, Map<Cause, Column> columns) {
    switch (check.id()) {
      case SLO_AVAILABILITY -> {
        if (app.availability().isEmpty()) return;
        var sli = app.availability().getFirst();
        var column = column(columns, Cause.ERRORS);
        if (check.status().atLeast(Status.WARNING)) column = column.withStatus(Status.CRITICAL);
        if (sli.totalRequests() > 0 && sli.failedRequests() > 0) {
          column = column.withValue(Format.percent(sli.failedRequests() * 100 / sli.totalRequests()));
        }
        columns.put(Cause.ERRORS, column);
      }
      case SLO_LATENCY -> {
        if (app.latency().isEmpty()) return;
        var sli = app.latency().getFirst();
        var column = column(columns, Cause.LATENCY);
        if (check.status().atLeast(Status.WARNING)) column = column.withStatus(Status.CRITICAL);
        if (sli.objectiveLatencySeconds() > 0) {
          column = column.withValue(Format.latency(sli.objectiveLatencySeconds()));
        }
        columns.put(Cause.LATENCY, column);
      }
      case INSTANCE_AVAILABILITY -> {
        var value = check.desired() > 0 ? check.count() + "/" + check.desired() : "";
        columns.put(Cause.INSTANCES, new Column(check.status(), value));
      }
      case INSTANCE_RESTARTS -> {
        var value = check.count() > 0 ? String.valueOf(check.count()) : "";
        columns.put(Cause.RESTARTS, new Column(check.status(), value));
      }
      case CPU_NODE -> {
        if (check.status().atLeast(Status.WARNING) && breaching) {
          columns.put(Cause.CPU, new Column(Status.WARNING, "shortage"));
        }
      }
      case CPU_CONTAINER -> {
        if (check.status().atLeast(Status.WARNING)) {
          columns.put(Cause.CPU, new Column(Status.WARNING, "shortage"));
        }
      }
      case MEMORY_OOM -> {
        if (check.status().atLeast(Status.WARNING)) {
          columns.put(Cause.MEMORY, new Column(Status.WARNING, "OOM"));
        }
      }
      case MEMORY_LEAK_PERCENT -> {
        if (check.status().atLeast(Status.WARNING)
            && !column(columns, Cause.MEMORY).status().atLeast(Status.WARNING)) {
          columns.put(Cause.MEMORY, new Column(Status.WARNING, "leak"));
        }
      }
      case STORAGE_IO_LOAD -> {
        var column = column(columns, Cause.DISK_IO_LOAD);
        if (check.status() != Status.UNKNOWN) column = column.withStatus(check.status());
        if (check.value() > 0) column = column.withValue(Format.number(check.value()));
        columns.put(Cause.DISK_IO_LOAD, column);
      }
      case STORAGE_SPACE -> {
        var column = column(columns, Cause.DISK_USAGE).withStatus(check.status());
        if (check.value() > 0) column = column.withValue(Format.percent(check.value()));
        columns.put(Cause.DISK_USAGE, column);
      }
      case NETWORK_RTT -> {
        var column = column(columns, Cause.NETWORK);
        if (check.status() != Status.UNKNOWN) {
          column = column.withStatus(breaching ? check.status() : Status.OK);
        }
        if (check.value() > 0) column = column.withValue(Format.latency(check.value()));
        columns.put(Cause.NETWORK, column);
      }
      case NETWORK_CONNECTIVITY -> {
        if (check.status().atLeast(Status.WARNING)) {
          columns.put(Cause.NETWORK, new Column(check.status(), "packet loss"));
        }
      }
      case NETWORK_TCP_CONNECTIONS -> {
        if (check.status().atLeast(Status.WARNING)) {
          columns.put(Cause.NETWORK, new Column(check.status(), "failed conns"));
        }
      }
      case DNS_LATENCY -> {
        if (check.status().atLeast(Status.WARNING)) {
          var value = check.value() > 0 ? Format.latency(check.value()) : column(columns, Cause.DNS).value();
          columns.put(Cause.DNS, new Column(check.status(), value));
        }
      }
      case DNS_SERVER_ERRORS, DNS_NXDOMAIN_ERRORS -> {
        if (check.status().atLeast(Status.WARNING)) {
          columns.put(Cause.DNS, new Column(check.status(), "errors"));
        }
      }
      case LOG_ERRORS -> {
        var column = column(columns, Cause.LOGS);
        if (check.distinctItems() > 0) {
          column =
              column.withValue(
                  check.distinctItems() + " unique error" + (check.distinctItems() == 1 ? "" : "s"));
        }
        if (check.status().atLeast(Status.WARNING)) column = column.withStatus(Status.INFO);
        columns.put(Cause.LOGS, column);
      }
      case OTHER -> {}
    }
  }

  private static Column column(Map<Cause, Column> columns, Cause cause) {
    return columns.getOrDefault(cause, Column.absent());
  }

  private static void dependencies(
      Map<ApplicationId, List<Connection>> outgoing,
      Map<ApplicationId, Optional<Boolean>> verdicts,
      Map<ApplicationId, Application> byId,
      Application app,
      boolean breaching,
      Map<Cause, Column> columns) {

    var counted = new LinkedHashSet<ApplicationId>();
    var unhealthy = new LinkedHashSet<ApplicationId>();
    for (var connection : outgoing.getOrDefault(app.id(), List.of())) {
      if (connection.to().equals(app.id())) continue;
      var dependency = byId.get(connection.to());
      if (dependency == null) continue;
      if (!Category.isMonitoring(app.category()) && Category.isMonitoring(dependency.category())) continue;

      var verdict = verdicts.computeIfAbsent(dependency.id(), k -> objectiveVerdict(dependency));
      if (verdict.isEmpty()) continue;
      counted.add(dependency.id());
      if (verdict.get()) unhealthy.add(dependency.id());
    }
    if (counted.isEmpty()) return;

    int healthy = counted.size() - unhealthy.size();
    var status = (!unhealthy.isEmpty() && breaching) ? Status.WARNING : Status.OK;
    columns.put(Cause.UPSTREAMS, new Column(status, healthy + "/" + counted.size()));
  }

  /**
   * Empty when the dependency has no decided objective check at all, which is the difference
   * between "healthy" and "not counted".
   */
  private static Optional<Boolean> objectiveVerdict(Application dependency) {
    Boolean verdict = null;
    for (var report : dependency.reports()) {
      if (!report.isObjective()) continue;
      for (var check : report.checks()) {
        if (check.status() == Status.UNKNOWN) continue;
        verdict = (verdict != null && verdict) || check.status().atLeast(Status.WARNING);
      }
    }
    return Optional.ofNullable(verdict);
  }
}
