package io.akka.coroot.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** The dependency graph — SPEC-001 section 3.7. */
public record ServiceMap(List<Node> nodes) {

  public record Edge(ApplicationId to, Status status, String reason, double weight, List<String> stats) {}

  public record Node(
      ApplicationId id, String category, Status status, List<Edge> upstreams, List<ApplicationId> downstreams) {}

  public static ServiceMap render(World world) {
    var byId = new TreeMap<ApplicationId, Application>();
    for (var app : world.applications()) byId.put(app.id(), app);

    // Worst status wins per dependency; the reason travels with the status it belongs to,
    // and the traffic figures come from the last connection seen, as in the original.
    var edges = new TreeMap<ApplicationId, Map<ApplicationId, Merged>>();
    var callers = new TreeMap<ApplicationId, TreeSet<ApplicationId>>();
    for (var connection : world.connections()) {
      if (connection.from().equals(connection.to())) continue;
      if (!byId.containsKey(connection.from()) || !byId.containsKey(connection.to())) continue;
      var merged =
          edges
              .computeIfAbsent(connection.from(), k -> new TreeMap<>())
              .computeIfAbsent(connection.to(), k -> new Merged());
      merged.absorb(connection);
      callers.computeIfAbsent(connection.to(), k -> new TreeSet<>()).add(connection.from());
    }

    var categories = new HashMap<ApplicationId, String>();
    for (var app : byId.values()) categories.put(app.id(), app.category());

    for (var app : byId.values()) {
      boolean isSink = !edges.containsKey(app.id()) && callers.containsKey(app.id());
      if (!isSink || !Category.isDefault(app.category())) continue;
      var callerCategories = new TreeSet<String>();
      for (var caller : callers.get(app.id())) callerCategories.add(categories.get(caller));
      if (callerCategories.size() == 1) categories.put(app.id(), callerCategories.first());
    }

    var nodes = new ArrayList<Node>();
    for (var app : byId.values()) {
      var outgoing = edges.getOrDefault(app.id(), Map.of());
      var incoming = callers.getOrDefault(app.id(), new TreeSet<>());
      if (outgoing.isEmpty() && incoming.isEmpty()) continue;

      var upstreams = new ArrayList<Edge>();
      for (var entry : outgoing.entrySet()) {
        upstreams.add(entry.getValue().toEdge(entry.getKey()));
      }
      nodes.add(
          new Node(
              app.id(),
              categories.get(app.id()),
              nodeStatus(app),
              upstreams,
              new ArrayList<>(incoming)));
    }
    return new ServiceMap(nodes);
  }

  /**
   * The status shown on a node is the objective and instance verdict of the application
   * itself, not a roll-up of the attribution columns and not anything about its edges.
   */
  static Status nodeStatus(Application app) {
    var status = Status.UNKNOWN;
    for (var report : app.reports()) {
      if (!Report.SLO.equals(report.name()) && !Report.INSTANCES.equals(report.name())) continue;
      for (var check : report.checks()) status = status.max(check.status());
    }
    return status;
  }

  private static final class Merged {
    private Status status = Status.UNKNOWN;
    private String reason = "";
    private Connection last;

    void absorb(Connection c) {
      var s = c.status();
      if (s.compareTo(status) >= 0) {
        status = s;
        reason = c.reason();
      }
      last = c;
    }

    Edge toEdge(ApplicationId to) {
      return new Edge(to, status, reason, last.requestsPerSecond(), stats(last, reason));
    }
  }

  static List<String> stats(Connection c, String reason) {
    if (!reason.isEmpty()) return List.of("⚠️ " + reason);
    var out = new ArrayList<String>();
    var line = "📈 " + Format.number(c.requestsPerSecond()) + " rps";
    line += " ⏱️ " + Format.latency(c.latencySeconds());
    out.add(line);
    if (c.bytesSent() > 0 && c.bytesReceived() > 0) {
      out.add("↑" + Format.bytes(c.bytesSent()) + "/s ↓" + Format.bytes(c.bytesReceived()) + "/s");
    }
    return out;
  }
}
