package io.akka.coroot.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import io.akka.coroot.domain.Application;
import io.akka.coroot.domain.ApplicationId;
import io.akka.coroot.domain.Attribution;
import io.akka.coroot.domain.AvailabilityIndicator;
import io.akka.coroot.domain.Cause;
import io.akka.coroot.domain.Check;
import io.akka.coroot.domain.CheckId;
import io.akka.coroot.domain.Connection;
import io.akka.coroot.domain.LatencyIndicator;
import io.akka.coroot.domain.Report;
import io.akka.coroot.domain.ServiceMap;
import io.akka.coroot.domain.Status;
import io.akka.coroot.domain.World;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The reachable surface: one world in, the attribution table and the service map out.
 * The capability is a pure decision over a snapshot, so it needs no stored state and the
 * endpoint holds none — the whole snapshot arrives in the request body, the way coroot
 * assembles it from its metric cache before rendering either view.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/attribution")
public class AttributionEndpoint {

  public record CheckRequest(
      String id, String status, Float value, Long count, Long desired, Integer distinctItems) {}

  public record ReportRequest(String name, List<CheckRequest> checks) {}

  public record IndicatorRequest(Double totalRequests, Double failedRequests, Double objectiveLatencySeconds) {}

  public record ApplicationRequest(
      String id,
      String category,
      List<ReportRequest> reports,
      List<IndicatorRequest> availability,
      List<IndicatorRequest> latency,
      String typeStatus,
      Boolean hasTypeReport) {}

  public record ConnectionRequest(
      String from,
      String to,
      Double successfulConnections,
      Double activeConnections,
      Double failedConnections,
      Boolean roundTripTimeMeasured,
      Boolean roundTripTimeSilent,
      Double requestsPerSecond,
      Double latencySeconds,
      Double bytesSent,
      Double bytesReceived) {}

  public record WorldRequest(List<ApplicationRequest> applications, List<ConnectionRequest> connections) {}

  public record ColumnResponse(String status, String value) {}

  public record RowResponse(String id, String category, String status, Map<String, ColumnResponse> causes) {}

  public record EdgeResponse(String to, String status, String reason, double weight, List<String> stats) {}

  public record NodeResponse(
      String id, String category, String status, List<EdgeResponse> upstreams, List<String> downstreams) {}

  public record AnalysisResponse(List<RowResponse> applications, List<NodeResponse> map) {}

  @Post("/analyze")
  public AnalysisResponse analyze(WorldRequest request) {
    var world = toWorld(request);

    var rows = new ArrayList<RowResponse>();
    for (var row : Attribution.attribute(world)) {
      var causes = new LinkedHashMap<String, ColumnResponse>();
      for (var cause : Cause.values()) {
        var column = row.column(cause);
        causes.put(cause.wire(), new ColumnResponse(column.status().wire(), column.value()));
      }
      rows.add(new RowResponse(row.id().toString(), row.category(), row.status().wire(), causes));
    }

    var nodes = new ArrayList<NodeResponse>();
    for (var node : ServiceMap.render(world).nodes()) {
      var upstreams = new ArrayList<EdgeResponse>();
      for (var edge : node.upstreams()) {
        upstreams.add(
            new EdgeResponse(
                edge.to().toString(), edge.status().wire(), edge.reason(), edge.weight(), edge.stats()));
      }
      nodes.add(
          new NodeResponse(
              node.id().toString(),
              node.category(),
              node.status().wire(),
              upstreams,
              node.downstreams().stream().map(ApplicationId::toString).toList()));
    }
    return new AnalysisResponse(rows, nodes);
  }

  private static World toWorld(WorldRequest request) {
    var applications = new ArrayList<Application>();
    for (var a : orEmpty(request.applications())) {
      var reports = new ArrayList<Report>();
      for (var r : orEmpty(a.reports())) {
        var checks = new ArrayList<Check>();
        for (var c : orEmpty(r.checks())) {
          checks.add(
              new Check(
                  CheckId.fromWire(c.id()),
                  Status.fromWire(c.status()),
                  or(c.value(), 0f),
                  or(c.count(), 0L),
                  or(c.desired(), 0L),
                  or(c.distinctItems(), 0)));
        }
        reports.add(new Report(r.name(), checks));
      }
      var availability = new ArrayList<AvailabilityIndicator>();
      for (var i : orEmpty(a.availability())) {
        availability.add(new AvailabilityIndicator(or(i.totalRequests(), 0d), or(i.failedRequests(), 0d)));
      }
      var latency = new ArrayList<LatencyIndicator>();
      for (var i : orEmpty(a.latency())) {
        latency.add(new LatencyIndicator(or(i.objectiveLatencySeconds(), 0d)));
      }
      applications.add(
          new Application(
              ApplicationId.parse(a.id()),
              a.category() == null ? io.akka.coroot.domain.Category.DEFAULT : a.category(),
              reports,
              availability,
              latency,
              Status.fromWire(a.typeStatus()),
              Boolean.TRUE.equals(a.hasTypeReport())));
    }

    var connections = new ArrayList<Connection>();
    for (var c : orEmpty(request.connections())) {
      connections.add(
          new Connection(
              ApplicationId.parse(c.from()),
              ApplicationId.parse(c.to()),
              or(c.successfulConnections(), 0d),
              or(c.activeConnections(), 0d),
              or(c.failedConnections(), 0d),
              !Boolean.FALSE.equals(c.roundTripTimeMeasured()),
              Boolean.TRUE.equals(c.roundTripTimeSilent()),
              or(c.requestsPerSecond(), 0d),
              or(c.latencySeconds(), 0d),
              or(c.bytesSent(), 0d),
              or(c.bytesReceived(), 0d)));
    }
    return new World(applications, connections);
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static <T> T or(T value, T fallback) {
    return value == null ? fallback : value;
  }
}
