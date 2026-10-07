# 005 — External library reuse

**Status:** Accepted

Only one artifact was added: `androidx.lifecycle:lifecycle-viewmodel-compose`
(pinned to the already-present lifecycle 2.11.0), needed to scope the state
holder properly in Compose. Coroutines arrive transitively via
`lifecycle-runtime-ktx`.

Deliberately **not** added: OkHttp/Retrofit (`HttpURLConnection` covers tiny
HTTPS probes with fewer moving parts), dnsjava (a ~90-line RFC-1035 UDP
A-query does exactly what is needed and adds no transitive dependency
surface), Room (a single JSON file covers summary history). This follows the
"smallest reliable set" rule: nothing here reinvents a complex protocol, and
no external networking code needed license review.
