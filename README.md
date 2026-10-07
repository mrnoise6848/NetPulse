# NetPulse

Why is my internet slow or unreliable?

NetPulse runs a local network diagnostic and shows where the problem is most
likely occurring — instead of a single generic "speed" number.

## Problem

"Your internet speed is 12 Mbps" tells you nothing when the actual problem
is a slow DNS resolver, an unreachable router, or TLS failures. Generic
speed-test apps cannot distinguish between these failure layers.

## Solution

NetPulse runs a structured pipeline over the live network and reports
per-layer results with evidence:

```
Network State → Gateway → DNS → Internet → HTTPS → Latency → Reliability
```

Example outputs:

- "Your Wi-Fi connection is healthy, but DNS response time is unusually high."
- "You are connected to Wi-Fi, but the gateway could not be reached."
- "Local connectivity looks healthy. External HTTPS requests are failing."

## Features

- Live network card: type (Wi-Fi / Mobile / Ethernet / VPN), state, local IP,
  gateway, DNS servers, expandable advanced details
- Full diagnostic: gateway reachability (ICMP with TCP fallback), DNS
  (system path + direct per-resolver queries), internet reachability over
  three independent endpoints, real HTTPS diagnostics with classified
  failures
- Latency grades, application-level request reliability with success rate,
  timeouts and jitter
- Deterministic diagnostic engine: findings with evidence and hedged
  possible causes — never false certainty
- Explainable health score (per-component breakdown, documented formula)
- Local, deletable history
- Cancellable runs; aborted automatically when the network changes mid-run
- Metrics that cannot be measured on a device are marked
  "Unavailable on this device" — never invented

## Screenshots

*(Add screenshots here after building on a device.)*

## Architecture

Single `:app` module, Presentation / Domain / Data:

- `data/network` — platform API data sources and checkers
  (`ConnectivityMonitor`, `GatewayChecker`, `DnsChecker`, `HttpsChecker`)
- `data/history` — local JSON summary storage
- `domain/engine` — pure, deterministic `DiagnosticEngine`,
  `LatencyAnalyzer`, `ReliabilityAnalyzer`
- `domain/scoring` — `HealthScoreCalculator`
- `ui` — Compose Material3 screens + one `DiagnosticViewModel`

See [docs/architecture.md](docs/architecture.md) for details.

## Diagnostic Pipeline

See [docs/network-diagnostics.md](docs/network-diagnostics.md) for every
measurement method (including how ICMP/TCP gateway probing and direct DNS
queries work) and the latency thresholds.

## Privacy

**Local-first.** Nothing is uploaded — no IPs, gateway, DNS configuration,
SSID, or results. No analytics. TLS verification is never disabled. See
[docs/privacy.md](docs/privacy.md).

## Performance

A normal diagnostic targets **< 10 seconds** with bounded timeouts, tiny
requests (≤ 4 KiB reads), real per-step progress, and working cancellation.
See [docs/performance.md](docs/performance.md).

## Limitations

- ICMP is often unavailable to unprivileged apps; gateway probes fall back
  to TCP connects and the method used is always shown.
- Mobile networks do not expose a probeable gateway (shown as unavailable).
- Gateway on non-Wi-Fi networks may be a documented first-host-of-prefix
  heuristic, labelled as derived.
- Some networks filter probe endpoints; a partial result is reported as
  such rather than hidden.
- Reliability is application-level request success, not packet loss.

## Testing

Domain logic (engine, scoring, analyzers, DNS query parser) is covered by
JVM unit tests; instrumented template tests remain. Run:

```text
./gradlew testDebugUnitTest
```

## Roadmap

- Continuous background monitoring of network quality trends
- More probe endpoints and per-endpoint weighting
- Export/sharing of diagnostic reports
- IPv6-aware gateway and DNS diagnostics
