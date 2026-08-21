package io.akka.coroot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.coroot.domain.Application;
import io.akka.coroot.domain.ApplicationId;
import io.akka.coroot.domain.Attribution;
import io.akka.coroot.domain.AvailabilityIndicator;
import io.akka.coroot.domain.Category;
import io.akka.coroot.domain.Check;
import io.akka.coroot.domain.CheckId;
import io.akka.coroot.domain.Connection;
import io.akka.coroot.domain.LatencyIndicator;
import io.akka.coroot.domain.Report;
import io.akka.coroot.domain.ServiceMap;
import io.akka.coroot.domain.Status;
import io.akka.coroot.domain.World;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Times the decision itself over the benchmark worlds, in process and warm, and prints one
 * {@code TIMING} line per world for {@code bench/harness.py speed} to read. Timing the HTTP
 * round trip instead would measure the runtime and the JSON codec, which is not what the
 * comparison against the original is about.
 *
 * <p>It is a test rather than a main so that every world in {@code workloads.json} is run on
 * every build: a world the domain cannot handle fails here rather than at benchmark time.
 */
public class BenchTimingTest {

  private static final int ITERATIONS = 2000;

  @Test
  public void everyBenchmarkWorldIsAnsweredAndTimed() throws Exception {
    var mapper = new ObjectMapper();
    var workloads =
        mapper.readTree(getClass().getClassLoader().getResourceAsStream("workloads.json"));

    // Warmed over every world before any of them is timed: the compiler warms once for
    // the process, not once per world, and timing the first world cold measures that.
    var worlds = new ArrayList<World>();
    var names = new ArrayList<String>();
    for (var workload : workloads) {
      if (workload.has("sequence")) continue;
      worlds.add(toWorld(workload));
      names.add(workload.get("name").asText());
    }
    for (int i = 0; i < 200; i++) for (var world : worlds) run(world);

    int answered = 0;
    for (int w = 0; w < worlds.size(); w++) {
      var world = worlds.get(w);
      answered++;

      long start = System.nanoTime();
      for (int i = 0; i < ITERATIONS; i++) run(world);
      long nanosPerOp = (System.nanoTime() - start) / ITERATIONS;

      var rows = Attribution.attribute(world);
      var map = ServiceMap.render(world);
      System.out.printf(
          "TIMING\t%s\t%d\t%d\t%d%n", names.get(w), nanosPerOp, rows.size(), map.nodes().size());
    }
    assertThat(answered).isEqualTo(7);
  }

  private static void run(World world) {
    Attribution.attribute(world);
    ServiceMap.render(world);
  }

  private static ApplicationId id(String name) {
    return new ApplicationId("", "default", "Deployment", name);
  }

  private static World toWorld(JsonNode workload) {
    var applications = new ArrayList<Application>();
    for (var a : workload.get("applications")) {
      var reports = new ArrayList<Report>();
      for (var r : a.path("reports")) {
        var checks = new ArrayList<Check>();
        for (var c : r.get("checks")) {
          checks.add(
              new Check(
                  CheckId.fromWire(c.get("id").asText()),
                  Status.fromWire(c.get("status").asText()),
                  (float) c.path("value").asDouble(0),
                  c.path("count").asLong(0),
                  c.path("desired").asLong(0),
                  c.path("distinctItems").asInt(0)));
        }
        reports.add(new Report(r.get("name").asText(), checks));
      }
      var availability = new ArrayList<AvailabilityIndicator>();
      for (var i : a.path("availability")) {
        availability.add(
            new AvailabilityIndicator(
                i.get("totalRequests").asDouble(), i.get("failedRequests").asDouble()));
      }
      var latency = new ArrayList<LatencyIndicator>();
      for (var i : a.path("latency")) {
        latency.add(new LatencyIndicator(i.get("objectiveLatencySeconds").asDouble()));
      }
      applications.add(
          new Application(
              id(a.get("name").asText()),
              a.path("category").asText(Category.DEFAULT),
              reports,
              availability,
              latency,
              Status.UNKNOWN,
              false));
    }

    var connections = new ArrayList<Connection>();
    for (var c : workload.get("connections")) {
      connections.add(
          new Connection(
              id(c.get("from").asText()),
              id(c.get("to").asText()),
              c.path("successfulConnections").asDouble(0),
              0,
              c.path("failedConnections").asDouble(0),
              true,
              c.path("roundTripTimeSilent").asBoolean(false),
              c.path("requestsPerSecond").asDouble(0),
              c.path("latencySeconds").asDouble(0),
              c.path("bytesSent").asDouble(0),
              c.path("bytesReceived").asDouble(0)));
    }
    return new World(List.copyOf(applications), List.copyOf(connections));
  }
}
