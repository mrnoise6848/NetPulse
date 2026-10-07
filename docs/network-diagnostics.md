# NetPulse Network Diagnostics

## Diagnostic pipeline

```
Network State → Transport/Link → Local IP → Gateway → DNS
→ Internet Reachability → HTTPS → Latency → Reliability → Result
```

Every step degrades gracefully: when a metric cannot be measured on a
device or network, the UI shows **"Unavailable on this device"** rather
than an invented value.

## Measurement methods

| Step | Method | Notes |
|---|---|---|
| Network state | `ConnectivityManager` + `NetworkCapabilities` (default network callback) | Wi-Fi / Mobile / Ethernet / VPN; validation state from `NET_CAPABILITY_VALIDATED` |
| Local info | `LinkProperties`, `WifiManager.dhcpInfo` | Transports, interface, MTU, DNS servers, local IP |
| Gateway | 1) ICMP via `InetAddress.isReachable`, 2) TCP connect to ports 53/80/443 | Method actually used is shown in the UI. On mobile networks the gateway is typically not probeable — shown as *Unavailable on this device* |
| DNS | a) system resolver (`InetAddress.getAllByName`), b) direct RFC-1035 UDP A-query to each `LinkProperties` DNS server | The two measurements can disagree; a discrepancy is itself a diagnostic signal |
| Internet reachability | HTTPS GET to 3 independent endpoints returning tiny/empty bodies (`connectivitycheck.gstatic.com/generate_204`, `www.msftconnecttest.com/connecttest.txt`, `cp.cloudflare.com/generate_204`) | Bounded body reads (≤4 KiB); no large downloads |
| HTTPS | Same probes with full platform TLS verification | Failure classes: DNS, timeout, refused, TLS, unexpected status |
| Latency | Best successful endpoint RTT; per-layer classification | See thresholds below |
| Reliability | 10 sequential HTTPS probes; success rate, timeouts, jitter (stddev), median | This is **application-level request reliability**, deliberately not called packet loss |

## Latency thresholds

Thresholds are pragmatic heuristics, **not** universal standards.

| Grade | Gateway / DNS (local) | Internet / HTTPS |
|---|---|---|
| Excellent | ≤ 20 ms | ≤ 50 ms |
| Good | ≤ 50 ms | ≤ 100 ms |
| Fair | ≤ 100 ms | ≤ 200 ms |
| High | ≤ 200 ms | ≤ 400 ms |
| Very High | > 200 ms | > 400 ms |

## Cancellation

The pipeline runs inside a cancellable coroutine job. Cancellation is
checked between probes and ports; all probes use bounded timeouts, so a
cancelled run stops within seconds at most. Cancelling from the UI or a
mid-run network change both go through the same path.
