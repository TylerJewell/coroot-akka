package io.akka.coroot.domain;

/** Cluster, namespace, kind and name — unique across a world, and the map's sort key. */
public record ApplicationId(String cluster, String namespace, String kind, String name)
    implements Comparable<ApplicationId> {

  public static ApplicationId parse(String s) {
    var parts = s.split(":", -1);
    if (parts.length == 4) return new ApplicationId(parts[0], parts[1], parts[2], parts[3]);
    if (parts.length == 3) return new ApplicationId("", parts[0], parts[1], parts[2]);
    throw new IllegalArgumentException("not an application id: " + s);
  }

  @Override
  public String toString() {
    return cluster + ":" + namespace + ":" + kind + ":" + name;
  }

  @Override
  public int compareTo(ApplicationId o) {
    return toString().compareTo(o.toString());
  }
}
