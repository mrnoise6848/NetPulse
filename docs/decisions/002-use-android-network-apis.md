# 002 — Use Android network APIs

**Status:** Accepted

All connectivity state comes from modern platform APIs:
`ConnectivityManager` / `registerDefaultNetworkCallback` /
`NetworkCapabilities` / `LinkProperties`. No deprecated APIs (e.g.
`NetworkInfo`, `CONNECTIVITY_ACTION` broadcasts) are used. SSID/location
metadata is deliberately not requested: it adds a permission burden and
privacy risk without diagnostic value.
