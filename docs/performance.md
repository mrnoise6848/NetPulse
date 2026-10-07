# NetPulse Performance

## Target

A normal full diagnostic completes in **under 10 seconds** on a healthy
connection:

| Step | Budget |
|---|---|
| Gateway probe | ≤ 2 s per method attempt, ports tried sequentially (53, 80, 443) |
| DNS | ≤ 2 s per resolver, one hostname, sequential |
| Internet / HTTPS | 5 s connect + read timeout per endpoint, 3 endpoints |
| Reliability | 10 sequential HTTPS probes, typically ≪ 1 s each on a healthy link |

Each checker uses **bounded timeouts everywhere**; the pipeline checks for
cancellation between probes so a cancel takes effect within seconds at most.

## Rules the implementation follows

- No fake progress timers: the UI progress line names the step that is
  actually executing.
- No blocking on the main thread — all probes run on `Dispatchers.IO`.
- Bounded body reads (≤ 4 KiB) and `generate_204`-style endpoints; no large
  downloads.
- Sequential, fixed-count requests (no unbounded coroutine creation, no
  polling loops).
- Live network state comes from one registered callback, not polling.
