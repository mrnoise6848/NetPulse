# NetPulse — Product Specification & Agent Instructions

## 1. CRITICAL RULES

You are continuing development of an **existing Android project**.

Do not rebuild the project from scratch.

The current project source, architecture, Gradle configuration, dependency versions, Android configuration, and build setup are already considered valid.

### NON-NEGOTIABLE RULES

1. **Do NOT recreate the project from scratch.**
2. **Do NOT change the existing Gradle version.**
3. **Do NOT change the Gradle Wrapper version.**
4. **Do NOT change the Android Gradle Plugin version.**
5. **Do NOT change the Kotlin version.**
6. **Do NOT change the Java/JDK version.**
7. **Do NOT change existing Compose/plugin versions unless the current project genuinely cannot implement a required feature.**
8. **Do NOT upgrade or downgrade dependencies just because newer versions exist.**
9. **Do NOT change package/application IDs.**
10. **Do NOT replace the current architecture unnecessarily.**
11. **Do NOT introduce unnecessary Gradle modules.**
12. **Do NOT perform broad refactors unrelated to NetPulse.**
13. **Do NOT remove working functionality from the existing project.**
14. **Do NOT silently replace working implementations.**
15. **Do NOT run tests until ALL implementation phases are complete.**
16. You may inspect and update tests during implementation, but **do not execute test suites until the final phase**.
17. After all features are implemented, perform one complete verification/test pass.
18. Prefer reuse over reinvention.
19. If an appropriate open-source library or mature Android implementation exists, **you are explicitly allowed to use it**.
20. You may download, inspect, evaluate, and integrate required open-source libraries, sample implementations, protocol implementations, or reference projects when useful.
21. Before using external code, verify its license and compatibility with this project.
22. Do not copy large amounts of code blindly from another repository.
23. Prefer mature libraries over implementing complex networking functionality from scratch.
24. If an external dependency solves the problem reliably, use it instead of unnecessarily reinventing the implementation.
25. Do not add a dependency only because it is popular; evaluate whether it genuinely reduces complexity or improves correctness.

The objective is:

> **Build NetPulse on top of the existing project with maximum reliability and minimum unnecessary reinvention.**

---

# 2. PRODUCT

## App Name

**NetPulse**

## Product Definition

NetPulse is an Android network diagnostics utility that helps users understand:

> **Why is my internet connection slow, unreliable, or not working?**

It should not merely display a generic internet speed number.

It should perform a structured diagnostic pipeline and identify where a problem is most likely occurring.

---

# 3. CORE VALUE PROPOSITION

Instead of:

> "Your internet speed is 12 Mbps."

NetPulse should answer:

> "Your Wi-Fi connection is healthy, but DNS response time is unusually high."

or:

> "You are connected to Wi-Fi, but the gateway is unreachable."

or:

> "DNS works, but HTTPS connections are failing."

The application must distinguish between different failure layers.

---

# 4. MAIN DIAGNOSTIC PIPELINE

The conceptual flow:

```text
Network State
      ↓
Transport / Link
      ↓
Local IP
      ↓
Gateway
      ↓
DNS
      ↓
Internet Reachability
      ↓
HTTPS
      ↓
Latency
      ↓
Packet / Request Reliability
      ↓
Diagnostic Result
```

Not every device/network exposes every metric.

When a metric is unavailable, clearly mark it as:

```text
Unavailable on this device
```

Do not invent values.

---

# 5. IMPORTANT PRODUCT PRINCIPLE

NetPulse is a **diagnostic assistant**, not a network control app.

It should inspect and explain.

It must not:

* modify router configuration
* change system DNS automatically
* enable VPNs automatically
* change Wi-Fi settings automatically
* disable network security features
* manipulate other applications' traffic

Where appropriate, provide a button to open Android network settings.

---

# 6. PHASE 1 — EXISTING PROJECT INSPECTION

Before implementing anything:

Inspect:

* source tree
* Gradle configuration
* Gradle Wrapper
* AGP
* Kotlin
* JDK
* compileSdk
* targetSdk
* minSdk
* Compose setup
* dependencies
* architecture
* navigation
* existing UI
* themes
* utilities
* tests
* build variants

Treat the current configuration as the baseline.

Create or update:

```text
docs/architecture.md
```

Document:

* current project architecture
* current dependencies
* where NetPulse functionality will be integrated
* which existing components can be reused

Do not change foundational versions.

---

# 7. OPEN-SOURCE / EXISTING IMPLEMENTATION REUSE

You are explicitly encouraged to reuse mature existing solutions.

Before implementing complex networking functionality from scratch, search for suitable:

* AndroidX APIs
* Kotlin libraries
* networking libraries
* DNS libraries
* ICMP/ping alternatives where permitted
* HTTP clients
* network monitoring libraries
* connectivity APIs
* open-source diagnostic implementations
* Android sample projects
* protocol implementations

Examples of technologies that may be evaluated where appropriate:

* ConnectivityManager
* NetworkCallback
* LinkProperties
* NetworkCapabilities
* WifiManager / appropriate modern Wi-Fi APIs
* OkHttp
* Kotlin coroutines
* AndroidX lifecycle
* WorkManager only when actually required

Do not assume every library is necessary.

Use the smallest reliable set.

### External project rules

For every externally sourced dependency or implementation:

1. Verify its license.
2. Verify Android compatibility.
3. Verify maintenance / maturity where relevant.
4. Verify that the functionality is appropriate for the product.
5. Avoid importing unrelated functionality.
6. Document important external dependencies in the architecture documentation.

Do not violate licenses.

---

# 8. PHASE 2 — NETWORK DISCOVERY

Detect the current active network.

Display:

```text
Network
Wi-Fi

Connected
```

or:

```text
Network
Mobile Data

Connected
```

Possible states:

* Wi-Fi
* Mobile
* Ethernet
* VPN
* No active network
* Unknown

Use modern Android APIs.

Avoid deprecated APIs when a suitable current API exists.

---

# 9. PHASE 3 — LOCAL NETWORK INFORMATION

Where supported, show:

* local IP
* interface information
* gateway
* DNS servers
* network type
* link properties
* transport capabilities

Example:

```text
Connection

Wi-Fi
Connected

Local IP
192.168.1.24

Gateway
192.168.1.1

DNS
1.1.1.1
8.8.8.8
```

Do not expose irrelevant low-level information to the default UI.

Advanced details may be expandable.

---

# 10. PHASE 4 — GATEWAY DIAGNOSTICS

Determine whether the local gateway is reachable.

Measure latency where technically and legally appropriate using an available mechanism.

Do not assume ICMP ping is universally available.

If ICMP is unavailable or blocked, use an appropriate TCP or other reachability technique.

The diagnostic engine should explain the method used.

Example:

```text
Gateway

192.168.1.1
Reachable

Latency
3 ms
```

Failure example:

```text
Gateway

192.168.1.1
Not reachable

Possible problem:
Local network / router connectivity
```

Do not claim certainty when the evidence only suggests a possibility.

---

# 11. PHASE 5 — DNS DIAGNOSTICS

Test DNS resolution.

Use real DNS queries or appropriate hostname resolution.

Measure:

* success/failure
* response latency
* resolution result

Example:

```text
DNS

1.1.1.1

✓ Resolution works
Latency: 22 ms
```

Failure:

```text
DNS

✕ Resolution failed

Possible problem:
DNS service unavailable
```

Do not claim that the DNS provider is definitively the cause unless evidence supports it.

---

# 12. PHASE 6 — INTERNET REACHABILITY

Test external reachability using reliable HTTPS endpoints.

Avoid relying on a single third-party endpoint.

Prefer a small set of stable endpoints.

Possible strategy:

```text
Endpoint A
Endpoint B
Endpoint C
```

Measure:

* connection success
* response latency
* timeout
* failure rate

Do not download large files just to determine connectivity.

Use lightweight requests.

---

# 13. PHASE 7 — HTTPS DIAGNOSTICS

Test real HTTPS connectivity.

Differentiate:

```text
Network connected
DNS works
HTTPS fails
```

from:

```text
Network connected
DNS works
HTTPS works
```

Handle:

* timeout
* TLS failure
* connection failure
* HTTP response failure
* DNS failure

Do not disable certificate verification.

Do not implement insecure TLS fallbacks.

---

# 14. PHASE 8 — LATENCY ANALYSIS

Measure latency using appropriate network methods.

The UI should distinguish:

```text
Excellent
Good
Fair
High
Very High
```

The thresholds must be documented and should not be presented as universal truths.

Example:

```text
Latency

Gateway
3 ms

DNS
24 ms

Internet
61 ms
```

---

# 15. PHASE 9 — RELIABILITY ANALYSIS

Perform lightweight repeated requests when necessary.

Measure:

* successful requests
* failed requests
* timeout count
* approximate packet/request loss at the application level
* consistency / jitter where meaningful

Do not call this "packet loss" if the test is actually HTTP request failure.

Use accurate terminology.

Example:

```text
Reliability

Requests
20

Successful
19

Failed
1

Success rate
95%
```

---

# 16. PHASE 10 — DIAGNOSTIC ENGINE

This is the core feature.

Create a deterministic diagnostic engine that combines measured evidence.

Example:

```text
Diagnostic Result

Internet connection is available,
but DNS response time is unusually high.
```

Or:

```text
Diagnostic Result

Your device is connected to Wi-Fi,
but the local gateway could not be reached.
```

Or:

```text
Diagnostic Result

Local connectivity looks healthy.
External HTTPS requests are failing.
```

Every result should contain:

```text
Finding
Evidence
Possible Cause
```

Example:

```text
Finding
High DNS latency

Evidence
DNS response: 412 ms

Possible Cause
Slow or overloaded DNS resolver
```

Do not pretend that diagnosis is certainty.

Use language such as:

* likely
* possible
* suggests
* detected
* could not verify

Avoid:

* definitely
* guaranteed
* your router is broken

unless the evidence genuinely proves it.

---

# 17. PHASE 11 — NETWORK HEALTH SCORE

Optionally expose:

```text
Network Health
72 / 100
```

This is a **diagnostic summary**, not an internet quality standard.

The score must be deterministic and explainable.

Example:

```text
Health Score

Connectivity       25/25
DNS                12/20
Latency            17/20
HTTPS reliability  20/20
-------------------------
Total              74/85
```

Or normalize to 100.

Document the formula.

Do not use machine learning for the MVP.

---

# 18. PHASE 12 — MAIN SCREEN

Example:

```text
NetPulse

Network Health
72 / 100

Wi-Fi
Connected

Gateway
✓ 3 ms

DNS
⚠ 184 ms

Internet
✓

HTTPS
✓

[ Run Full Diagnostic ]
```

The main screen should be understandable at a glance.

---

# 19. PHASE 13 — DIAGNOSTIC DETAIL SCREEN

After a diagnostic run:

```text
Network Diagnostic

✓ Connection
✓ Gateway
⚠ DNS
✓ Internet
✓ HTTPS
⚠ Latency
✓ Reliability
```

Selecting an item reveals details.

---

# 20. PHASE 14 — HISTORY

Store recent diagnostic summaries locally.

For example:

```text
Today 13:22
72 / 100
DNS latency high

Today 11:05
91 / 100
Healthy
```

Do not store unnecessary sensitive network information.

Prefer summaries over complete network traces.

The user should be able to delete history.

---

# 21. PHASE 15 — NETWORK CHANGE HANDLING

The application should respond correctly when:

* Wi-Fi disconnects
* Wi-Fi connects
* switching Wi-Fi → mobile
* switching mobile → Wi-Fi
* VPN changes network state
* connectivity is temporarily lost

Avoid stale UI.

Do not assume a network remains valid after a diagnostic starts.

---

# 22. PHASE 16 — ERROR HANDLING

Handle:

* timeout
* DNS failure
* no network
* gateway unavailable
* TLS failure
* HTTP failure
* API restrictions
* unavailable network metadata
* permission limitations
* Android API differences
* network changed during test

A single failed diagnostic step must not crash the full diagnostic.

Example:

```text
DNS test failed,
but the remaining diagnostics completed.
```

---

# 23. PERFORMANCE

The diagnostic should be reasonably fast.

Target:

```text
Normal diagnostic
< 10 seconds
```

Do not create a fake timer.

Progress must represent real work.

Avoid:

* blocking the main thread
* unbounded coroutine creation
* unnecessary network requests
* large downloads
* unnecessary polling

Cancellation must work.

---

# 24. PRIVACY & SECURITY

NetPulse must be local-first.

Do not upload diagnostic results to a server.

Do not upload:

* local IP
* gateway
* DNS configuration
* network history
* SSID
* user-specific application data

Do not collect analytics by default.

Do not log sensitive network information in release builds.

Never disable TLS certificate verification.

Never store credentials.

Never inspect unrelated application traffic.

---

# 25. ARCHITECTURE

Preserve the existing project architecture.

If the existing project uses:

```text
Presentation
Domain
Data
```

extend it.

Suggested responsibilities:

```text
NetworkStateDataSource
NetworkInfoProvider
ConnectivityMonitor
GatewayChecker
DnsChecker
InternetChecker
HttpsChecker
LatencyAnalyzer
ReliabilityAnalyzer
DiagnosticEngine
DiagnosticRepository
```

Possible domain models:

```text
NetworkSnapshot
GatewayResult
DnsResult
InternetResult
HttpsResult
LatencyResult
ReliabilityResult
DiagnosticFinding
DiagnosticReport
HealthScore
```

Do not create excessive abstractions.

---

# 26. CANCELLATION

A running diagnostic must be cancellable.

Example:

```text
Running diagnostic...

DNS check
[Cancel]
```

Cancellation must propagate through the entire diagnostic pipeline.

No leaked coroutines.

No work continuing after cancellation.

---

# 27. TEST DATA / DEMO MODE

Do not create fake production results.

A development-only mock/fake implementation may be used where necessary for architecture or manual development, but it must be clearly separated from production behavior.

Production must use real Android network APIs.

---

# 28. TESTING RULE

## DO NOT RUN TESTS UNTIL ALL PHASES ARE COMPLETE

During implementation:

* do not run unit tests
* do not run instrumentation tests
* do not run UI tests
* do not run benchmark tests
* do not run full Gradle test tasks

You may:

* inspect existing tests
* create/update tests
* review test code statically

But do not execute the tests until all implementation phases are complete.

This project should be implemented phase-by-phase first.

---

# 29. FINAL VERIFICATION — ONLY AFTER ALL PHASES

Once every implementation phase is complete:

Run:

```text
./gradlew assembleDebug
```

Then perform the complete verification suite.

Include:

* unit tests
* repository tests
* networking tests
* diagnostic engine tests
* UI tests
* instrumentation tests where applicable
* lint/static analysis
* build verification

Then perform manual validation.

---

# 30. MANUAL VALIDATION

Verify on a real Android device or emulator:

1. Wi-Fi connected
2. Wi-Fi disconnected
3. Mobile data
4. No connectivity
5. Gateway reachable
6. Gateway unavailable
7. DNS success
8. DNS failure
9. HTTPS success
10. HTTPS failure
11. High latency scenario
12. Temporary network switch
13. Diagnostic cancellation
14. Multiple diagnostic runs
15. History
16. Delete history
17. Large enough history set
18. App backgrounding
19. Process recreation where relevant
20. VPN/network capability differences where available

Do not claim manual validation for scenarios that were not actually tested.

---

# 31. README

README must start with the problem.

Example:

```text
# NetPulse

Why is my internet slow or unreliable?

NetPulse runs a local network diagnostic and
shows where the problem is most likely occurring.
```

Then:

```text
## Problem

## Solution

## Features

## Screenshots

## Architecture

## Diagnostic Pipeline

## Privacy

## Performance

## Limitations

## Testing

## Roadmap
```

Do not claim unsupported functionality.

---

# 32. DOCUMENTATION

Create or update:

```text
docs/architecture.md
docs/network-diagnostics.md
docs/diagnostic-engine.md
docs/privacy.md
docs/performance.md
docs/decisions/
```

Suggested decisions:

```text
001-preserve-existing-project.md
002-use-android-network-apis.md
003-local-first-diagnostics.md
004-deterministic-diagnostic-engine.md
005-external-library-reuse.md
006-no-insecure-tls-fallback.md
```

---

# 33. EXTERNAL LIBRARIES / GITHUB PROJECTS

You may use external open-source solutions when they provide a real advantage.

Examples of acceptable reuse:

* HTTP client
* DNS resolver
* networking utilities
* compatibility helpers
* parsing libraries
* visualization libraries
* mature Android networking components

Before integrating anything:

* inspect the source
* verify license
* verify maintenance quality
* verify compatibility
* verify security
* avoid unnecessary transitive dependencies

Prefer:

> existing reliable implementation

over:

> writing a complex low-level implementation from scratch

But do not turn the application into a collection of random dependencies.

---

# 34. DEFINITION OF DONE

The project is complete when:

* existing Gradle version is unchanged
* existing Gradle Wrapper version is unchanged
* AGP version is unchanged
* Kotlin version is unchanged
* Java/JDK version is unchanged
* existing architecture is preserved
* unnecessary dependencies were not introduced
* mature external solutions were reused where appropriate
* real network information is shown
* gateway diagnostics work
* DNS diagnostics work
* HTTPS diagnostics work
* internet reachability works
* latency analysis works
* reliability analysis works
* deterministic diagnostic engine works
* health score is explainable
* diagnostic history works
* cancellation works
* network changes are handled
* no fake production data exists
* no insecure TLS behavior exists
* no sensitive network data is uploaded
* app remains responsive
* documentation exists
* README exists
* final build succeeds
* final tests pass
* final lint/static analysis passes
* manual validation is completed

---

# 35. FINAL REPORT FORMAT

After the final verification:

## Implemented

* ...

## External Libraries / Open Source Reused

* ...

## Build

* ...

## Tests

* ...

## Static Analysis

* ...

## Manual Validation

* ...

## Performance

* ...

## Privacy / Security

* ...

## Known Limitations

* ...

## Architecture Notes

* ...

## Files Changed

* ...

## Final Status

* Complete / Incomplete

---

# 36. FINAL INSTRUCTION

Build NetPulse on top of the **existing source code**.

Do not rebuild the project.

Do not change foundational versions.

Do not perform unnecessary migrations.

You are explicitly allowed to use and download suitable open-source projects, libraries, examples, and implementations when they improve correctness or reduce unnecessary work.

**Do not reinvent mature networking functionality unnecessarily.**

At the same time, do not blindly copy external code. Verify licenses, compatibility, security, and relevance.

Implement all phases first.

**Do not execute tests until all implementation phases are complete.**

Only after all implementation work is finished, perform the final comprehensive build, test, static-analysis, and manual-validation pass.
