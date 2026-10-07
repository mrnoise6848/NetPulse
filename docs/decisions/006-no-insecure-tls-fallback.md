# 006 — No insecure TLS fallback

**Status:** Accepted

HTTPS probes use `HttpsURLConnection` with platform-default certificate
verification. There is no trust-all manager, no hostname-verifier override,
no retry-without-TLS, and no "ignore certificate errors" mode anywhere.
A TLS failure is reported as a *finding* (possible interception or wrong
system clock), never worked around.
