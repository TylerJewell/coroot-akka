# Acknowledgements

## coroot/coroot

This project reimplements a slice of [coroot](https://github.com/coroot/coroot) — the rules
that decide which named thing to blame when a service is unhealthy, and the rules that draw
the map of what calls what.

coroot is © Coroot, Inc. and contributors, licensed under the Apache License 2.0. A copy of
that licence, as it stands in the coroot repository, is in `LICENSE-coroot`.

**No coroot source was copied into this project.** The behaviour was established by running
coroot's own `api/views/overview.Render` against constructed inputs and recording what came
back; that record is the question log in the harness repository, and the specification was
written from it before any code here existed. Names of coroot's own concepts are used where
a different name would make the two harder to compare — the check identifiers, the twelve
column names, the four severities and the wording of the short values beside each blame.

The comparison in `README.md` and the harness `bench/REPORT.md` was measured against coroot
at the commit cloned on 2026-08-21.

## Akka

Built on the [Akka SDK](https://doc.akka.io/), © Lightbend, Inc.
