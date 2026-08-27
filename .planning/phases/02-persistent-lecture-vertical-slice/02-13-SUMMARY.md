---
phase: 02-persistent-lecture-vertical-slice
plan: 13
subsystem: testing
tags: [powershell, fabric, gametest, dedicated-server, evidence, sha256]

requires:
  - phase: 01-java-25-and-fabric-26-2-foundation
    provides: Checksum-bound Temurin 25, pinned Fabric tuple, foundation audit, and ordinary-JAR conventions
  - phase: 02-persistent-lecture-vertical-slice
    provides: Complete persistent Lecture implementation and SERVER_STOPPING cleanup marker
provides:
  - Fresh pinned-JDK offline build/unit/GameTest/dependency/archive/server evidence transaction
  - Exact Phase 2 source/build/dist JAR identity at SHA-256 3e691776e6bb0f1371eedb341cc5874fc107bd254769e8bc0abb5fffb783907c
  - Reconciled nine-row Nyquist matrix and seven honest direct-client backstops
affects: [phase-03, release-verification, client-uat, distribution]

actuals:
  tokens: 23159
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns: [exact PID and start-time ownership, fail-closed audit adjudication, promote-after-gates, automated-versus-observed evidence split]

key-files:
  created:
    - .planning/phases/02-persistent-lecture-vertical-slice/02-LECTURE-EVIDENCE.md
  modified:
    - scripts/verify-lecture.ps1
    - .planning/phases/02-persistent-lecture-vertical-slice/02-VALIDATION.md
    - README.md
    - dist/developers-hell-0.1.0.jar

key-decisions:
  - "Capture only descendants whose start ticks are not older than the validated parent, preventing stale ParentProcessId PID reuse from annexing unrelated processes."
  - "Preserve raw foundation audit exit 1 and adjudicate only the pinned ConfigIssue.sanitizeRejectedValue denylist literal while independently scanning operational source/archive surfaces."
  - "Accept sends_telemetry_event only when parsed JSON proves the one exact boolean false opt-out."
  - "Promote dist atomically only after fresh build, tests, audits, archive contract, ordered real-server shutdown, and zero owned residue pass."

patterns-established:
  - "Process ownership: bind root and descendants by PID, UTC start ticks, executable, parent edge, and command anchors; cleanup only revalidated captured identities."
  - "Evidence honesty: automated rows may pass from machine gates, while all rendering/readability/playability judgments remain separate PENDING rows."

requirements-completed: [FND-05, FND-06, FND-07, CAMP-01, CAMP-02, LECT-01, LECT-02]

coverage:
  - id: D1
    description: Fresh ordinary Phase 2 JAR with equal source, build, and distribution hashes after complete automated gates
    requirement: FND-07
    verification:
      - kind: e2e
        ref: scripts/verify-lecture.ps1 -Verify and -ValidateEvidence
        status: pass
    human_judgment: false
  - id: D2
    description: Nine automated validation rows mapped to green unit, GameTest, archive, and lifecycle evidence
    requirement: FND-07
    verification:
      - kind: integration
        ref: .planning/phases/02-persistent-lecture-vertical-slice/02-LECTURE-EVIDENCE.md
        status: pass
    human_judgment: false
  - id: D3
    description: Seven client rendering, readability, accessibility, motion, model, and Remote-overlay backstops
    requirement: LECT-01
    verification: []
    human_judgment: true
    rationale: Native client rendering and playability require a visible isolated-client run; no client was launched during this plan.

duration: 72min
completed: 2026-08-27
status: complete
---

# Phase 02 Plan 13: Reproducible Evidence and Honest Client Backstops Summary

**Pinned-JDK Phase 2 proof with 75 unit tests, 31 GameTest anchors, ordered real-server cleanup, equal artifact hashes, and seven explicit client-UAT backstops**

## Performance

- **Duration:** 72 min
- **Started:** 2026-08-26T23:40:40Z
- **Completed:** 2026-08-27T00:52:14Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Built and self-tested a PowerShell 5.1/7 verifier that fails closed on path, artifact, archive, evidence, process, shutdown, telemetry-opt-out, and atomic-promotion mutations.
- Ran one fresh checksum-bound Java 25 offline transaction: 75 unit tests, 31 GameTest anchors, exact dependency audit, source/archive gates, and a real production server clean stop.
- Required `FIRST_TICK_READY -> STOPPING_CLEANUP_COMPLETE -> Stopping server -> All dimensions are saved`, captured seven owned processes under server root PID 9644, and proved zero residue.
- Promoted only the inspected candidate; source/build/dist now equal SHA-256 `3e691776e6bb0f1371eedb341cc5874fc107bd254769e8bc0abb5fffb783907c`.
- Reconciled all nine automated validation rows as PASS while retaining exactly seven visible-client judgments as PENDING.

## Task Commits

Each task was committed atomically:

1. **Task 1: Build a self-checking fresh-artifact and real server-stop verifier** - `c2f59ce` (feat)
2. **Task 2: Reconcile all nine validation rows and preserve seven observed-only judgments** - `6e27e46` (docs)

## Files Created/Modified

- `scripts/verify-lecture.ps1` - Cross-shell self-check, fresh build/audit/archive/evidence transaction, exact-child server supervisor, and atomic distribution promotion.
- `.planning/phases/02-persistent-lecture-vertical-slice/02-LECTURE-EVIDENCE.md` - Sanitized command exits, test counts, archive/hash/server facts, nine PASS rows, and seven PENDING backstops.
- `.planning/phases/02-persistent-lecture-vertical-slice/02-VALIDATION.md` - Complete Nyquist mapping for all nine automated IDs and seven client-only rows.
- `README.md` - Player campaign/config/retry/reward instructions, exact current hash, verifier commands, and automated-versus-client-UAT distinction.
- `dist/developers-hell-0.1.0.jar` - Exact verified Phase 2 ordinary JAR (Git-ignored handoff artifact).

## Decisions Made

- Rejected stale CIM parent edges by child/parent start-time ordering so PID reuse cannot capture or terminate an unrelated process. The encountered OpenClaw gateway remained untouched.
- Kept the foundation audit's raw exit honest. The only failure is pinned to the single safe `credential` denylist literal in `sanitizeRejectedValue`; any source hash drift or additional finding fails.
- Parsed the vanilla advancement JSON and accepted `sends_telemetry_event` only as boolean `false`; `true` and string `"false"` mutation tests fail.
- Prepared only the fixed ignored production-server profile with loopback, offline mode, query/RCON/status disabled, and no resource-pack URL.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Bound legitimate and stale Windows process-tree shapes**
- **Found during:** Task 1
- **Issue:** Exact-child inspection initially rejected the owned console host, then a stale ParentProcessId collision exposed an unrelated OpenClaw Node process.
- **Fix:** Allowed only exact System32 `conhost.exe`, added parent/child start-tick admission, exact requery for incomplete live CIM identities, and mutation coverage.
- **Files modified:** `scripts/verify-lecture.ps1`
- **Verification:** Both shell self-checks exit 0; full server proof captured seven owned processes; unrelated PIDs remained live.
- **Committed in:** `c2f59ce`

**2. [Rule 1 - Bug] Adjudicated a foundation-audit sanitizer false positive without hiding it**
- **Found during:** Task 1
- **Issue:** The Phase 1 lexical audit flags the word `credential` even though it occurs only in a rejection denylist.
- **Fix:** Required the exact raw finding, path, method context, occurrence count, and pinned source hash; independently scanned operational source/archive surfaces; recorded raw exit 1 and adjudication exit 0.
- **Files modified:** `scripts/verify-lecture.ps1`, `02-LECTURE-EVIDENCE.md`
- **Verification:** Any additional audit failure or source drift fails; current evidence validates.
- **Committed in:** `c2f59ce`

**3. [Rule 1 - Bug] Distinguished vanilla telemetry opt-out data from telemetry code**
- **Found during:** Task 1
- **Issue:** The archive lexical scan rejected the advancement's vanilla `sends_telemetry_event: false` field.
- **Fix:** Parse JSON and permit only one exact boolean false value; reject true, string false, duplicates, invalid JSON, and operational telemetry markers.
- **Files modified:** `scripts/verify-lecture.ps1`
- **Verification:** Cross-shell mutation tests and full archive gate pass.
- **Committed in:** `c2f59ce`

**4. [Rule 3 - Blocking] Made production smoke/profile/log/promotion Windows-safe**
- **Found during:** Task 1
- **Issue:** The ignored server profile lacked EULA/local properties, the live logger denied exclusive reads, and .NET Framework rejected a null File.Replace backup path.
- **Fix:** Wrote only the fixed local smoke profile, read logs with FileShare.ReadWrite, and used PowerShell NullString for atomic replacement with preservation/failure self-checks.
- **Files modified:** `scripts/verify-lecture.ps1`
- **Verification:** Full Verify and ValidateEvidence exit 0; promotion occurred after server clean stop.
- **Committed in:** `c2f59ce`

---

**Total deviations:** 4 auto-fixed (2 bugs, 2 blocking issues)
**Impact on plan:** All fixes enforce the planned fail-closed Windows verifier contract; no gameplay, dependency, source, client, or external-service scope was added.

## Issues Encountered

- Several deliberately fail-closed verifier runs preserved the old Phase 1 distribution hash until the last successful candidate gate. The final promotion changed it only once to the verified Phase 2 hash.
- The raw foundation lexical audit remains exit 1 by design because its sealed rule cannot distinguish a sanitizer denylist literal; the evidence records this explicitly and validates the narrow adjudication.

## Known Stubs

None. The seven PENDING client backstops are unperformed observations, not implementation stubs; they are also represented as human-judgment coverage above.

## User Setup Required

None - no external service, account, API key, credential, telemetry, or network configuration is required.

## Next Phase Readiness

- The complete persistent Lecture vertical slice has one fresh hash-identified ordinary JAR and reproducible automated evidence.
- A visible isolated Fabric 26.2 client session is still required to resolve the seven manual backstops. Until then, do not claim visual readability, model rendering, audio/motion comfort, fun, or exact-client UAT passed.

## Self-Check: PASSED

- All six plan outputs exist, including the ignored distributable.
- RED, GREEN, and reconciliation commits `973ee51`, `c2f59ce`, and `6e27e46` exist.
- Build and distribution SHA-256 values are equal at `3E691776E6BB0F1371EEDB341CC5874FC107BD254769E8BC0ABB5FFFB783907C`.
- The only placeholder wording describes the intentionally accepted vanilla-backed cosmetic baseline; no implementation stub was introduced.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
