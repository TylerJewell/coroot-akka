# coroot-akka

Works out which of twelve named things is to blame when a service in a cluster is unhealthy, and draws the map of what calls what.

A port of [coroot/coroot](https://github.com/coroot/coroot) onto **Akka**, built with **Akka Specify**.

---

## Where it came from

coroot watches a cluster, collects measurements from every service in it, and shows an
operator what is wrong. It was ported to derive a specification format precise enough to
regenerate a system on a different stack — the port is the vehicle, the specification is the
deliverable.

coroot is sold on finding root causes, and it has a button that does exactly that by sending
your cluster to a service the company runs. What it also has, in the open part, is a much
smaller thing that most people take for the same feature: a fixed set of rules that decides,
for each service, which named thing to blame. No model, no learning, no guessing — a few
hundred lines of arithmetic. That is what this port rebuilds.

The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness) under
`coroot-port/`.

---

## coroot/coroot → this port

📉 533 Go lines → **667 Java lines**<br>
📁 9 files → **18 files**<br>
⚡ 1,277,040 → **1,452,561** nanoseconds per answer, two hundred services<br>
⚡ 11,846 → **4,158** nanoseconds per answer, four services<br>
🎯 7 of 7 comparison runs identical → **7 of 7**<br>
🔁 88 different answers to 120 identical requests → **1**<br>
🧪 0 tests over this code → **50**

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/coroot-port/bench/REPORT.md).

---

## What it took to build

⏱️ **1.0 hours** from the first command to the published repository, **1.0** of them active<br>
💬 **304** exchanges with the model<br>
✍️ **238,663** tokens written by the model, **61,152,078** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **50** tests

```bash
python toolkit/tokens.py --port coroot    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

You send it everything known about a cluster at one moment: a list of services, the health
checks that have already been run on each of them, and the traffic between them. It sends
back two things — a table saying what is wrong with each service and which named thing is
being blamed, and a map of which service calls which, with a verdict on every arrow.

From the specification:

- **A shortage is only blamed when the service is actually failing.** A machine running hot
  under a service that is still serving every request is not the reason for anything, so it
  is not reported as one.
- **A slow or failing thing you depend on is only blamed when you are failing too.** The
  count of healthy dependencies is always shown; the blame is not.
- **The severity reported is not the severity that was measured.** A mild warning about
  failed requests is reported as serious, because failing requests is what the service is
  for. Errors in the logs are never reported as more than a note, because most of them
  are noise.
- **A verdict on an arrow comes from the traffic on that arrow.** Not from the health of
  either end: a healthy service calling a broken one has a healthy arrow, and the broken
  one is marked broken where it sits.
- **A database that everything calls, and that calls nothing, takes on the label of its
  callers** — but only if they all agree on one label.

---

## Design decisions

**One question, one answer.** The whole picture of the cluster arrives in a single message
and the reply is computed from it and nothing else. Nothing is stored between messages, so
two identical questions always get identical answers and there is no state to get out of
step.

**Decide the verdict first.** Whether a service is meeting its promise is worked out before
anything else is examined, instead of while the checks are being walked. The answer then
cannot depend on the order the checks happened to be listed in.

**A fixed order for the map.** Services come back sorted by name every time. Two identical
requests give byte-for-byte identical replies, which is what lets a stored reply be compared
against a fresh one.

**Blame is a name, not a number.** Every finding lands in one of twelve named buckets rather
than a score. An operator reads the word and knows where to look, and two people reading the
same output cannot disagree about what it says.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/coroot-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Send it a cluster**, with the example below.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9034**.

### Ask it something

```bash
curl -s -X POST http://localhost:9034/attribution/analyze \
  -H 'Content-Type: application/json' \
  -d '{
    "applications": [
      {"id": ":default:Deployment:frontend",
       "reports": [
         {"name": "SLO", "checks": [{"id": "SLO_AVAILABILITY", "status": "critical"}]},
         {"name": "CPU", "checks": [{"id": "CPU_NODE", "status": "warning", "value": 91}]}],
       "availability": [{"totalRequests": 100, "failedRequests": 5}]},
      {"id": ":default:Deployment:cart",
       "reports": [{"name": "SLO", "checks": [{"id": "SLO_AVAILABILITY", "status": "critical"}]}]}
    ],
    "connections": [
      {"from": ":default:Deployment:frontend", "to": ":default:Deployment:cart",
       "successfulConnections": 5, "requestsPerSecond": 10, "latencySeconds": 0.01}
    ]
  }'
```

`frontend` comes back blamed on both the machine it runs on and the thing it calls;
`cart` is not on the table at all, because nothing measured about it says anything.

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | — | The service reads no configuration. The port it listens on is set in `src/main/resources/application.conf`. |

---

## Where it differs from coroot/coroot

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **The order services come back in.** coroot returns them in whatever order its internal
  table happens to hand them over, and 120 identical requests produced 88 different orders
  when measured. This port sorts them by name, so that two identical requests give identical
  replies and a stored reply can be compared with a fresh one. coroot never has to say what
  the order is, because the browser reading it lays the map out itself.
- **When the promise-kept verdict is read.** coroot works out whether a service is meeting
  its promise while it walks that service's checks, so a check examined before the promise
  is reached sees the answer as "no". Its own ordering means this never happens in practice.
  This port works the verdict out first, which gives the same answer on every input coroot
  can produce and a defined one on inputs it cannot.
- **Rounding of halves.** Java rounds a half away from zero and Go rounds it to the nearest
  even number, so a disk load of 6.5 came back as 7 here and 6 there. This port rounds the
  way coroot does, byte counts excepted, which coroot itself rounds the other way.
- **Where the health checks come from.** coroot runs them, from measurements it collects out
  of the cluster. This port is told the results. The 4,300 lines that do the collecting are
  not rebuilt here.
- **What the service is running.** coroot works out whether something is a database, a Java
  program or a web server, and folds a verdict about that into the overall answer. This port
  accepts that verdict as part of the question instead of working it out.
- **The two extra reports on a map label.** coroot folds four kinds of report into the label
  it puts on each box; this port folds in two of them, and the two left out are about
  databases it does not know how to inspect.
- **The button that sends your cluster away.** coroot has one, and it posts to a service the
  company runs. This port has nothing corresponding to it and never contacts anything.
- **Anything relying on the exact text of a message.** coroot writes a sentence on every
  check explaining what it found. This port carries the short value shown beside each blame
  — `shortage`, `2/3`, `20ms` — and those match character for character across every
  comparison run. The longer sentences are `not checked`.
- **Very large clusters.** Both sides were compared up to two hundred services and six
  hundred connections and agreed exactly. Past that, `not checked`.

---

## Licence

coroot is Apache 2.0, © Coroot, Inc. This port reimplements the behaviour on a different
stack without copied source; see `ACKNOWLEDGEMENTS.md`.
