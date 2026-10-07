# NetPulse

**Narrow an Android network failure to the layer that produced the evidence.**

A speed number alone cannot explain why a page fails to load. The device may have a connection while its DNS path is slow, a gateway probe fails, or an HTTPS request encounters a TLS error. NetPulse checks these layers separately so the next troubleshooting step follows an observation rather than a generic “internet is slow” verdict.

It combines live platform network state with gateway, DNS and HTTPS probes, then produces findings with supporting measurements, possible causes and an explainable health-score breakdown. Summaries are stored locally for later review.

## Follow the diagnostic path

```text
Network capabilities and link information
    → gateway reachability (ICMP, then TCP fallback)
    → direct resolver queries + system DNS resolution
    → internet / HTTPS endpoint probes
    → latency and repeated-request reliability
    → evidence-based findings + score + local summary history
```

The useful distinction is between layers, not simply pass/fail:

| Measurement | What it helps investigate | What it cannot prove |
|---|---|---|
| Gateway ICMP/TCP probe | Reachability of the reported or derived first hop | A router rejecting these probes is not proof of a broken connection |
| System DNS versus per-resolver UDP A queries | Differences between the app's resolver path and advertised resolvers | Direct UDP does not reproduce Private DNS or every cached system lookup |
| HTTPS requests to three public endpoints | DNS, timeout, connection, TLS or HTTP-status failures | Failure of a probe endpoint does not prove the entire internet is down |
| Ten sequential HTTPS requests | Request success rate, timeouts, median and jitter | This is application-level reliability, not packet loss or throughput |

Unavailable measurements remain explicit. Mobile networks often expose no probeable gateway; some other gateway values are derived heuristically and labelled as such. Methods and thresholds: [network diagnostics](docs/network-diagnostics.md).

## Turning observations into findings

Android checkers collect results off the main thread. Pure domain analyzers classify latency and reliability; `DiagnosticEngine` maps combinations of results to findings. The score assigns contributions for connectivity, gateway, DNS, latency and HTTPS reliability. Missing observations receive partial credit, so the score is a heuristic summary rather than a universal quality rating.

Source: [DNS checker](app/src/main/java/com/noise/netpulse/data/network/DnsChecker.kt), [diagnostic engine](app/src/main/java/com/noise/netpulse/domain/engine/DiagnosticEngine.kt), [score](app/src/main/java/com/noise/netpulse/domain/scoring/HealthScoreCalculator.kt). The single Compose module separates platform probes, pure analysis, JSON history and ViewModel state. See [architecture](docs/architecture.md).

## Network changes and timing limits

A run records its network identity. A detected mid-run change cancels it rather than presenting mixed-network measurements as one completed diagnosis. Progress identifies the current stage; cancellation is cooperative between probes.

Gateway, direct DNS and HTTPS probes have per-attempt timeout settings. Sequential failed requests can accumulate well beyond ten seconds. The blocking system resolver has no app-specified timeout, so there is **no hard whole-run or cancellation deadline**. [Performance notes](docs/performance.md) describe these limits; no measured device timing report is included.

## Try it on a device

Use Android Studio with the configured SDK/toolchain, or:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Run a diagnostic on Wi-Fi, inspect DNS/HTTPS details and the score contributions, then compare with mobile data. Switch networks during a separate run to exercise interruption. Open History and delete a saved summary.

Screenshots are not included yet. A real report plus expanded DNS/HTTPS details would communicate the workflow better than an empty home screen. Mask identifying network information before publishing captures.

Existing JVM tests cover domain findings, scoring, analyzers and DNS packet parsing:

```bash
./gradlew testDebugUnitTest
```

They do not establish device-specific gateway behavior or real-network accuracy, and were not rerun for this documentation-only change.

## Privacy and scope

There is no NetPulse backend or analytics integration. Reports and history stay local, but diagnosis sends DNS queries and HTTPS probes to external services; those services observe the requests and public source IP. Platform TLS verification remains enabled. The app does not inspect other apps' traffic. See [privacy](docs/privacy.md).

This is an interactive diagnostic, not continuous monitoring or a bandwidth test. Endpoint filtering, VPN routing and platform restrictions can limit conclusions. History stores summaries rather than full probe traces. Background trends, report export and broader IPv6 diagnostics remain future work.
