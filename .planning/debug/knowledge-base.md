# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## foundation-audit-exit-1 — Full verification failed on stale mixin policy and transient blank process identity
- **Date:** 2026-08-27
- **Error patterns:** Foundation audit returned non-zero exit 1, Cannot bind argument to parameter LiteralPath because it is an empty string, verifier exited before publication
- **Root cause(s):** The Phase 1 archive audit blanket-rejected later reviewed reward mixins; a short-lived owned audit process could also yield a blank root ExecutablePath that Get-OwnedProcessTree passed directly to Resolve-Path without bounded exact-identity re-observation
- **Fix:** Replaced the blanket mixin prohibition with an exact approved artifact/config contract, then added bounded PID/start-time/canonical-executable root observation and deterministic verifier SelfCheck boundaries
- **Files changed:** scripts/audit-foundation.ps1, scripts/verify-lecture.ps1
- **Why not caught:** The full -Verify gate eventually caught both defects, but the mixin integration did not run an exact archive-contract regression and verifier SelfCheck lacked blank-first/persistently-blank root observation cases
- **Recurrence guard:** Exact mixin artifact/config checks in scripts/audit-foundation.ps1 plus blank/incomplete/mismatched/valid/exited owned-root cases in scripts/verify-lecture.ps1 -SelfCheck; both passed the authoritative clean -Verify on f68a8a404c5e1318c2c860cff08e03951b715b4b
---
