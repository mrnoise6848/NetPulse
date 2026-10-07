# NetPulse Diagnostic Engine

The engine (`domain/engine/DiagnosticEngine.kt`) is a **pure, deterministic
rule engine**: the same evidence always produces the same findings, summary,
and score. There is no machine learning and no randomness.

## Finding structure

Every finding contains:

- **Finding** — short title (e.g. "High DNS latency")
- **Evidence** — the measured values that triggered the rule
- **Possible cause** — hedged interpretation

Language policy: uses *possible*, *likely*, *suggests*, *detected*,
*could not verify*. Avoids *definitely*, *guaranteed*, *your router is
broken* unless evidence genuinely proves it (it usually cannot).

## Rules (per layer)

| Layer | Trigger | Severity |
|---|---|---|
| Connection | No active network | ERROR (rest of pipeline is skipped) |
| Gateway | Not reachable via ICMP or TCP fallback | ERROR |
| Gateway | Local latency High/Very High | WARNING |
| DNS | System resolution fails and no configured resolver answers | ERROR |
| DNS | System path fails but a direct resolver responds | WARNING (config issue suggested) |
| DNS | Resolution works but latency > 200 ms | WARNING |
| Internet | 0 of 3 endpoints respond | ERROR |
| Internet | Some endpoints respond, some do not | WARNING |
| HTTPS | All probes fail | ERROR (failure class shapes the wording) |
| HTTPS | Intermittent failures | WARNING |
| Latency | Any layer in High/Very High | WARNING |
| Reliability | All requests timed out, or success rate < 90 % | WARNING |

Step statuses (`✓ / ⚠ / ✕ / – / …`) derive from severities; steps that
cannot run on a given network are marked *unavailable*, never failed.

## Health score formula

Total is always out of 100. Unverifiable components receive a **neutral
partial credit**, not a perfect score or a penalty.

| Component | Max | Scoring |
|---|---|---|
| Connectivity | 25 | validated=25, connected-unvalidated=15, none=0 |
| Gateway | 15 | reachable & fair-or-better=15, reachable=10, unreachable=0, not probeable (mobile/unavailable)=10 |
| DNS | 20 | good latency=20, fair=16, high=10, very high=4; system fails but a resolver answers=8; total failure=0; unavailable=10 |
| Latency | 20 | internet grade: Excellent=20, Good=17, Fair=12, High=6, Very High=0; all probes failed=0; never ran=10 |
| HTTPS reliability | 20 | ≥95 %=20, ≥80 %=14, ≥50 %=8, <50 %=4; all timeouts=0; unavailable=10 |

The score is a **diagnostic summary**, not an internet quality standard.
The UI shows each component's contribution so the number is explainable.
