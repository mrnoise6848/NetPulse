# 003 — Local-first diagnostics

**Status:** Accepted

NetPulse is a diagnostic assistant: it inspects and explains, never controls.
No analytics, no uploads, no server of its own. The only persisted data is a
minimal summary history in the app's private storage, deletable by the user.
The app can *open* Android network settings but never modifies any setting
itself.
