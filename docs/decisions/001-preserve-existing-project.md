# 001 — Preserve the existing project

**Status:** Accepted

The repository was an Android Studio Compose template (single `:app` module,
Gradle 9.8.0, AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 29). NetPulse
was built **on top of** this baseline: no version changes, no new Gradle
modules, application ID untouched, existing theme reused.

One mechanical correction was made: the wrapper's recorded
`distributionSha256Sum` did not match the official gradle-9.8.0-bin checksum
and blocked every build; it was corrected to the official value. The wrapper
version itself is unchanged.
