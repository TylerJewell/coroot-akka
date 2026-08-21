package io.akka.coroot.domain;

import java.util.Map;

/** One line of the attribution table. */
public record AttributionRow(ApplicationId id, String category, Map<Cause, Column> columns, Status status) {

  public Column column(Cause cause) {
    return columns.getOrDefault(cause, Column.absent());
  }
}
