# NetPulse Architecture

## Baseline (existing project — do not change)

| Item | Value |
|---|---|
| Gradle Wrapper | 9.8.0 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.2.10 (via `org.jetbrains.kotlin.plugin.compose`) |
| Java / JDK target | 11 (`source/targetCompatibility`) |
| compileSdk | 37 (`compileSdk { version = release(37) }`) |
| minSdk | 29 |
| targetSdk | 37 |
| applicationId / namespace | `com.noise.netpulse` |
| Compose BOM | 2026.09.00 (Material3, UI, graphics, tooling) |
| Activity Compose | 1.13.0 |
| Lifecycle Runtime KTX | 2.11.0 |
| Core KTX | 1.19.1 |

### Existing structure

- Single Gradle module `:app` (no extra modules will be introduced).
- Template app: `MainActivity` with `enableEdgeToEdge`, `Scaffold`, Compose `setContent`.
- `ui/theme/` — Material3 theme (`NetPulseTheme`, dynamic color on Android 12+), `Color.kt`, `Type.kt`, `Theme.kt`.
- No navigation library, no ViewModel-compose artifact, no networking stack, no data layer yet.
- Manifest has no network permissions yet.
- Tests: template `ExampleInstrumentedTest` only.

### Reusable existing components

- `NetPulseTheme` and theme colors — reused unchanged for all new screens.
- `MainActivity` — extended as the single activity host; screens are Compose-only.
- Version catalog `gradle/libs.versions.toml` — extended with a few lifecycle/dependency additions only where justified.

## NetPulse integration plan

All NetPulse code lives under `com.noise.netpulse` in these packages:

```
com.noise.netpulse
├── MainActivity            (single activity, hosts Compose UI + ViewModel)
├── data
│   ├── network             NetworkStateDataSource, NetworkInfoProvider,
│   │                       ConnectivityMonitor, GatewayChecker, DnsChecker,
│   │                       InternetChecker, HttpsChecker
│   └── history             DiagnosticRepository (local JSON file storage)
├── domain
│   ├── model               NetworkSnapshot, GatewayResult, DnsResult, InternetResult,
│   │                       HttpsResult, LatencyResult, ReliabilityResult,
│   │                       DiagnosticFinding, DiagnosticReport, HealthScore
│   ├── engine              DiagnosticEngine (deterministic rule engine),
│   │                       LatencyAnalyzer, ReliabilityAnalyzer
│   └── scoring             HealthScoreCalculator (documented deterministic formula)
└── ui
    ├── screen              MainScreen, DetailScreen, HistoryScreen
    ├── components          shared cards/rows
    └── DiagnosticViewModel (state holder; lifecycle-viewmodel-compose)
```

Layering follows the existing template style extended with `Presentation / Domain / Data`:

- **Data layer** talks only to Android platform APIs (`ConnectivityManager`, `NetworkCallback`, `LinkProperties`, `NetworkCapabilities`, `WifiManager` DHCP info, `java.net`, plain HTTPS via `HttpURLConnection`) and local file storage.
- **Domain layer** is pure Kotlin: deterministic diagnostic rules, latency classification, reliability metrics, health score. No Android imports.
- **Presentation layer** is Compose Material3 + one ViewModel; navigation is a simple in-ViewModel screen state (no navigation dependency needed for three screens).

### Dependencies added (justified, no version churn)

- `androidx.lifecycle:lifecycle-viewmodel-compose` (uses the already-present lifecycle 2.11.0) — needed for a properly scoped ViewModel from Compose.
- `kotlinx-coroutines-android` already arrives transitively via `lifecycle-runtime-ktx`; used directly for the diagnostic pipeline (`Dispatchers.IO`, cancellation).
- No OkHttp / Retrofit / dnsjava / Room: `HttpURLConnection` covers lightweight HTTPS probes, a small RFC-1035 UDP DNS query covers direct resolver testing, and a JSON file covers history. See `docs/decisions/`.

### Permissions

- `android.permission.INTERNET` — perform the probes.
- `android.permission.ACCESS_NETWORK_STATE` — read connectivity state, capabilities, LinkProperties.

SSID/location-protected Wi-Fi metadata is deliberately **not** used (privacy principle).

### Design decisions

See `docs/decisions/` for the recorded decisions (preserve project, Android network APIs, local-first, deterministic engine, external-library reuse, no insecure TLS fallback).
