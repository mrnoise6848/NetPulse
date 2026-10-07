# NetPulse Privacy

Diagnostic reports and history stay on the device. DNS/HTTPS probes communicate
with external services, which observe requests and the public source IP.

## Never uploaded

- Local IP, interface names, or link addresses
- Gateway address
- DNS configuration
- SSID or location-protected Wi-Fi metadata (deliberately never requested)
- Network history or diagnostic results
- No diagnostic report payload is sent to a NetPulse service

There is no analytics SDK, no remote endpoint of NetPulse's own, and no
network request other than the user-triggered diagnostic probes.

## What is stored

Only `diagnostic_history.json` in the app's private `filesDir`:
timestamp, health score, summary sentence, network type/state, duration.
Per-entry or full deletion is available in the History screen. Uninstalling
removes everything.

## Security

- TLS certificate verification is always at platform defaults; there is no
  trust-all, self-signed, or insecure fallback anywhere in the code.
- No credentials are stored or requested.
- The app only inspects its own diagnostic probes — never other apps' traffic.
- Diagnostic probes go to well-known public endpoints; no NetPulse server
  exists.
