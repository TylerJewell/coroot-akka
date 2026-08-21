package io.akka.coroot;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.coroot.api.AttributionEndpoint.AnalysisResponse;
import io.akka.coroot.api.AttributionEndpoint.ApplicationRequest;
import io.akka.coroot.api.AttributionEndpoint.CheckRequest;
import io.akka.coroot.api.AttributionEndpoint.ConnectionRequest;
import io.akka.coroot.api.AttributionEndpoint.IndicatorRequest;
import io.akka.coroot.api.AttributionEndpoint.ReportRequest;
import io.akka.coroot.api.AttributionEndpoint.WorldRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The capability reached the way a caller reaches it, against a started runtime — a check
 * that the port has a surface outside its own unit tests at all.
 */
public class AttributionEndpointIntegrationTest extends TestKitSupport {

  private static ApplicationRequest app(
      String name, String category, List<ReportRequest> reports, List<IndicatorRequest> availability) {
    return new ApplicationRequest(
        ":default:Deployment:" + name, category, reports, availability, List.of(), null, false);
  }

  @Test
  public void aWholeWorldIsAttributedOverHttp() {
    var breaching =
        app(
            "frontend",
            "application",
            List.of(
                new ReportRequest("SLO", List.of(new CheckRequest("SLO_AVAILABILITY", "critical", null, null, null, null))),
                new ReportRequest("CPU", List.of(new CheckRequest("CPU_NODE", "warning", 91f, null, null, null)))),
            List.of(new IndicatorRequest(100d, 5d, null)));
    var dependency =
        app(
            "cart",
            "application",
            List.of(
                new ReportRequest("SLO", List.of(new CheckRequest("SLO_AVAILABILITY", "critical", null, null, null, null)))),
            List.of());
    var world =
        new WorldRequest(
            List.of(breaching, dependency),
            List.of(
                new ConnectionRequest(
                    ":default:Deployment:frontend",
                    ":default:Deployment:cart",
                    5d, 0d, 0d, true, false, 10d, 0.01d, 1000d, 2000d)));

    var response =
        httpClient
            .POST("/attribution/analyze")
            .withRequestBody(world)
            .responseBodyAs(AnalysisResponse.class)
            .invoke()
            .body();

    assertThat(response.applications()).hasSize(1);
    var row = response.applications().getFirst();
    assertThat(row.id()).isEqualTo(":default:Deployment:frontend");
    assertThat(row.status()).isEqualTo("critical");
    assertThat(row.causes().get("cpu").status()).isEqualTo("warning");
    assertThat(row.causes().get("cpu").value()).isEqualTo("shortage");
    assertThat(row.causes().get("upstreams").value()).isEqualTo("0/1");
    assertThat(row.causes().get("upstreams").status()).isEqualTo("warning");

    assertThat(response.map()).hasSize(2);
    assertThat(response.map().getFirst().id()).isEqualTo(":default:Deployment:cart");
    var edge = response.map().getLast().upstreams().getFirst();
    assertThat(edge.to()).isEqualTo(":default:Deployment:cart");
    assertThat(edge.status()).isEqualTo("ok");
  }

  @Test
  public void theSameWorldPostedTwiceComesBackIdentical() {
    var world =
        new WorldRequest(
            List.of(
                app("zulu", "application", List.of(), List.of()),
                app("alpha", "application", List.of(), List.of()),
                app("mike", "application", List.of(), List.of())),
            List.of(
                new ConnectionRequest(":default:Deployment:zulu", ":default:Deployment:alpha", 5d, 0d, 0d, true, false, 1d, 0.01d, 0d, 0d),
                new ConnectionRequest(":default:Deployment:mike", ":default:Deployment:alpha", 5d, 0d, 0d, true, false, 1d, 0.01d, 0d, 0d)));

    var first = post(world);
    for (int i = 0; i < 5; i++) {
      assertThat(post(world)).isEqualTo(first);
    }
    assertThat(first.map().stream().map(n -> n.id().substring(n.id().lastIndexOf(':') + 1)))
        .containsExactly("alpha", "mike", "zulu");
  }

  private AnalysisResponse post(WorldRequest world) {
    return httpClient
        .POST("/attribution/analyze")
        .withRequestBody(world)
        .responseBodyAs(AnalysisResponse.class)
        .invoke()
        .body();
  }
}
