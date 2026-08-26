---
phase: 02-persistent-lecture-vertical-slice
plan: 07
subsystem: retake-authority
tags: [fabric-26.2, saved-data, reducer, retake, exactly-once, state-first]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 04
    provides: Closed campaign events, pure reducer, versioned SavedData, and persist-before-effect CampaignService
  - phase: 02-persistent-lecture-vertical-slice
    plan: 05
    provides: Exact encounter lifecycle identity and state-first terminal cleanup
  - phase: 02-persistent-lecture-vertical-slice
    plan: 06
    provides: Immutable accepted arena geometry and atomic Contract START behavior
provides:
  - One durable owner plus failed-encounter Retake entitlement independent of attempt counters or physical items
  - Schema-v1-compatible optional failed-encounter and fallback-reservation UUID persistence
  - Reserve, materialize, record, loss, recovery, and retry fencing through one RetakeService
  - Runtime-start compensation and state-first cleanup for inventory, reserved, and committed fallback projections
affects: [phase-2-item-interactions, phase-2-lecture-retry, phase-2-persistence, phase-2-verification]

actuals:
  tokens: 16379
  tasks: 2
  commits: 10

tech-stack:
  added: []
  patterns: [encounter-bound durable identity, reserve-materialize-record, lossy physical projection, commit-before-consume, compensating terminal]

key-files:
  created:
    - src/main/java/dev/developershell/lecture/RetakeService.java
  modified:
    - src/main/java/dev/developershell/campaign/PlayerCampaignState.java
    - src/main/java/dev/developershell/campaign/CampaignEvent.java
    - src/main/java/dev/developershell/campaign/CampaignReducer.java
    - src/main/java/dev/developershell/campaign/CampaignSavedData.java
    - src/main/java/dev/developershell/campaign/CampaignService.java
    - src/test/java/dev/developershell/campaign/CampaignReducerTest.java
    - src/test/java/dev/developershell/campaign/CampaignCodecTest.java
    - src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java

key-decisions:
  - "Define Retake identity as owner UUID plus the exact failed encounter UUID; attemptCount remains monotonic progression data, not identity authority."
  - "Persist reservation and materialized fallback UUIDs as mutually exclusive optional schema-v1 fields, retaining existing keys and read-only future-schema handling."
  - "Treat inventory Forms and fallback entities as lossy projections; every physical mutation follows already-persisted authority through RetakeService."
  - "On retry runtime-start failure, persist a compensating ABORT for the new encounter, replace the old physical projection, and leave one new recoverable entitlement."

patterns-established:
  - "Encounter-bound entitlement: terminal/reload records one RetakeKey(owner, failedEncounter), while replay and stale keys are no-ops."
  - "Three-step fallback transaction: reserve UUID in SavedData, materialize only from the accepted intent, then record the entity UUID."
  - "Commit-before-consume retry: keyed START and runtime intent occur before Form consumption or fallback discard; rejected START has zero physical effect."

requirements-completed: [FND-06, FND-07, LECT-02]

coverage:
  - id: D1
    description: "Every terminal/reload ordering creates one durable encounter-bound Retake entitlement, with schema-v1 reload and future-schema safety."
    requirement: FND-06
    verification:
      - kind: unit
        ref: "CampaignReducerTest terminal/reload replay matrix and CampaignCodecTest identity/reservation round trips"
        status: pass
    human_judgment: false
  - id: D2
    description: "Full inventory, materialization failure, entity loss, duplicate projection, stale key, changed-state, rejected retry, and runtime-start failure remain exactly-once and recoverable."
    requirement: FND-07
    verification:
      - kind: unit
        ref: "CampaignReducerTest RetakeService port and ordering cases"
        status: pass
      - kind: e2e
        ref: "Clean Fabric server GameTest run: all 16 required tests passed"
        status: pass
    human_judgment: false
  - id: D3
    description: "Lecture failure and cleanup preserve one recoverable retry right, and accepted keyed retries persist a new attempt before physical cleanup."
    requirement: LECT-02
    verification:
      - kind: e2e
        ref: "LectureLifecycleGameTests initial Contract plus keyed RetakeService retry path"
        status: pass
    human_judgment: false

duration: 32min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 07: Exactly-One Retake Authority Summary

**Each failed encounter now owns one durable, reload-safe Retake right, while one state-first service reconciles inventory/fallback loss and persists a keyed retry before consuming anything physical.**

## Performance

- **Duration:** 32 min
- **Started:** 2026-08-26T20:03:26Z
- **Completed:** 2026-08-26T20:34:51Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Bound each Retake entitlement to the owner and exact failed encounter UUID, preventing attempt-counter inference, replay, wrong-owner use, and stale representation callbacks from becoming authority.
- Added mutually exclusive reservation/materialized fallback fields without changing schema version 1 or reinterpreting existing keys; old saves receive a deterministic safe backfill and future schemas remain read-only.
- Implemented one `RetakeService` for reconciliation, manual recovery, and retry. It prefers an existing Form, repairs reserved/committed fallback crash windows, issues one replacement, and cleans changed-state materialization races.
- Preserved Plan 02-06's immutable accepted arena input and CampaignService persistence ordering. A retry START must match its old desk/key, reaches runtime only after persistence, and consumes/discards its old projection last.
- Preserved Plan 02-05 lifecycle behavior by updating its persistent GameTest helper to use a real keyed RetakeService retry after the initial atomic Contract start.

## Requirements (Copied Verbatim)

- **FND-06**: Versioned campaign, encounter, reward, and module state survives save, quit, reload, death, and chunk unload without duplication or regression.
- **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.
- **LECT-02**: Defeating Professor Infinite Slides grants the Attendance Sheet and a cooldown-limited Infinite Slides Remote after a fight that supports failure, cleanup, retry, and mid-campaign persistence.

## Task Commits

1. **Task 1 RED — Specify encounter-bound identity and replay/loss ordering** - `96ac972` (test)
2. **Task 1 RED refinement — Correct monotonic retry assertion** - `ed0bd20` (test)
3. **Task 1 RED refinement — Remove attemptCount from durable Retake identity** - `a528edc` (test)
4. **Task 1 GREEN — Bind Retakes to failed encounter UUIDs** - `608ae78` (feat)
5. **Task 2 RED — Specify state-first RetakeService ordering** - `e95e562` (test)
6. **Task 2 GREEN — Add the single reconciliation/retry service** - `cbc9489` (feat)
7. **Rule 3 integration — Route lifecycle retries through RetakeService** - `350a0cc` (test)
8. **Rule 1 RED — Cover runtime and fallback crash windows** - `693eab1` (test)
9. **Rule 1 GREEN — Compensate and clean crash windows** - `3b9397e` (fix)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/lecture/RetakeService.java` - Sole reconciliation/recovery/retry adapter with bounded physical and runtime ports.
- `src/main/java/dev/developershell/campaign/PlayerCampaignState.java` - Durable Retake key, reservation/materialized invariants, and legacy identity backfill.
- `src/main/java/dev/developershell/campaign/CampaignEvent.java` - Keyed retry/reconciliation/fallback events and reserve/commit/failure/loss operations.
- `src/main/java/dev/developershell/campaign/CampaignReducer.java` - Replay-safe terminal entitlement, fallback transitions, and atomic keyed retry START.
- `src/main/java/dev/developershell/campaign/CampaignSavedData.java` - Optional schema-v1 failed-encounter and reservation UUID codec fields.
- `src/main/java/dev/developershell/campaign/CampaignService.java` - Read-only state snapshot for the production Retake adapter; existing Contract transaction remains unchanged.
- `src/test/java/dev/developershell/campaign/CampaignReducerTest.java` - Reducer and service-port ordering, loss, duplication, stale-state, and compensation cases.
- `src/test/java/dev/developershell/campaign/CampaignCodecTest.java` - Legacy backfill plus non-null reservation round-trip/reload proof.
- `src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java` - Initial Contract coverage and production keyed retries using invocation-isolated owners.

## Decisions Made

- Kept the durable identity exactly owner plus failed encounter UUID. `attemptCount` is still validated and incremented monotonically, but never manufactures identity for new writes.
- Kept schema version 1 compatible by adding optional `retake_encounter_uuid` and `retake_fallback_reservation_uuid`; `retake_fallback_entity_uuid` retains its original committed/materialized meaning.
- Made reservation and committed entity UUIDs mutually exclusive. A crash after reservation or materialization is repaired from saved authority without issuing a second logical right.
- Required a fresh immutable `ArenaValidationResult.Accepted` for retries and let the reducer enforce the same saved desk/facing and exact Retake key.
- Added synchronous runtime success reporting to the bounded service port. Failed runtime start is compensated with a keyed terminal transition before old physical state is cleared and the replacement is reconciled.

## Automated Evidence

- Pinned Temurin `25.0.4+7`, Loom `1.17.19`, offline final gate `clean test runGameTest auditDirectDependencies build` - PASS.
- Java unit suite - `54` tests, `0` failures, `0` errors, `0` skipped - PASS.
- Fabric server GameTests - `16 GAME TESTS COMPLETE`; `All 16 required tests passed` - PASS.
- Direct dependency audit retained the approved five declarations, Loom `1.17.19`, injection baseline `145`, and SHA-256 baseline `a3fef1ae...` - PASS.
- Runtime construction scan found Retake reconciliation/fallback event construction only in `RetakeService` and the closed event/reducer definitions - PASS.
- Independent read-only re-review confirmed runtime compensation, reservation cleanup, changed-state discard, exact legacy identity, and reservation codec coverage with no remaining blocker - PASS.
- Stub/TODO/FIXME/skip scan, tracked-deletion check, common/client boundary scan, and offline/runtime-network constraints - PASS.
- No visible client was launched, no `dist` artifact was published or replaced, and no human UAT is claimed.

## Threat Mitigations

- **T-02-RETAKE-01:** All service and reducer operations carry the exact owner plus failed-encounter key; wrong owners, old encounter keys, changed desks, and stale entity callbacks are no-ops.
- **T-02-RETAKE-02:** The fallback path persists reservation before materialization and records completion afterward; retry persists START before runtime and physical consumption, with compensation on runtime failure.
- **T-02-RETAKE-03:** Missing inventory/fallback projections never erase authority. Loss clears only the matching reference and then permits one bounded replacement.
- No endpoint, dependency, client authority, runtime network, credential, external file access, or unplanned trust boundary was added.

## TDD Gate Compliance

- Task 1 RED commits `96ac972`, `ed0bd20`, and `a528edc` failed on the absent exact identity/reservation/keyed retry behavior before GREEN commit `608ae78`; the focused reducer and codec gate then passed.
- Task 2 RED commit `e95e562` failed because `RetakeService` did not exist before GREEN commit `cbc9489`; focused ordering tests and the exact service/audit scan then passed.
- The first broader clean GameTest run exposed the retained lifecycle helper's obsolete Contract retry. Commit `350a0cc` moved retries through the service while retaining initial Contract coverage.
- Crash-window RED commit `693eab1` failed on missing runtime-success, reservation cleanup, and changed-state behavior before fix `3b9397e`; the final clean 54-unit/16-GameTest gate passed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Persisted the exact Retake identity and fallback reservation**
- **Found during:** Task 1 RED/architecture proof
- **Issue:** The plan-listed state fields could not survive reload with owner plus failed-encounter identity or distinguish a reserved fallback from a committed/materialized entity. Inferring identity from `attemptCount` would violate the threat model.
- **Fix:** With bounded orchestrator approval, added optional schema-v1 fields with safe legacy defaults, retained all existing keys, enforced reservation/entity mutual exclusion, and added exact codec tests.
- **Files modified:** `src/main/java/dev/developershell/campaign/CampaignSavedData.java`, `src/test/java/dev/developershell/campaign/CampaignCodecTest.java`
- **Verification:** Focused codec tests and final clean full gate.
- **Committed in:** `96ac972`, `608ae78`, `693eab1`

**2. [Rule 3 - Blocking] Updated the retained lifecycle GameTest retry contract**
- **Found during:** First broader clean gate after Task 2 GREEN
- **Issue:** Two lifecycle tests still used a Cursed Contract after terminal failure. The new keyed reducer correctly rejected that service bypass, and persistent fixed owner UUIDs also retained old-desk state between GameTest runs.
- **Fix:** With bounded orchestrator approval, kept each initial attempt on the atomic Contract path, routed subsequent attempts through `RetakeService`, and derived deterministic invocation-isolated owner UUIDs for persistent GameTest runs.
- **Files modified:** `src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java`
- **Verification:** Rerun from `clean`; all 16 required GameTests passed twice, including the final full gate.
- **Committed in:** `350a0cc`, `693eab1`

**3. [Rule 1 - Bug] Closed retry runtime and fallback crash windows**
- **Found during:** Independent post-GREEN read-only review
- **Issue:** Runtime-start failure could leave ACTIVE state after consuming the Retake; a materialized reservation was not counted during retry; and a rejected post-spawn commit could leave an untracked entity after state changed.
- **Fix:** Added runtime success/compensation, unified state-first clearing of inventory/reserved/committed projections, and discard of materialized entities only when no durable reference remains.
- **Files modified:** `src/main/java/dev/developershell/lecture/RetakeService.java`, `src/test/java/dev/developershell/campaign/CampaignReducerTest.java`, `src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java`
- **Verification:** Fail-first crash-window tests, independent re-review, and final clean full gate.
- **Committed in:** `693eab1`, `3b9397e`

---

**Total deviations:** 3 auto-fixed (1 missing critical persistence extension, 1 blocking retained-test update, 1 crash-window bug fix).
**Impact on plan:** All changes enforce the planned exactly-one/state-first contract; no feature, dependency, runtime service, or client surface was added. Broken-window entries 10-12 are resolved.

## Known Stubs

None. `RepresentationPort` is the explicit bounded contract requested by this plan; concrete inventory/entity adapters intentionally arrive in the later interaction plan and do not block this plan's authority/ordering goal.

## Issues Encountered

- The first broad GameTest gate failed 2/16 because the retained lifecycle helper bypassed keyed retries; deviation 2 corrected the test contract without loosening production validation.
- GameTest startup emitted the existing transient warnings for absent `server.properties`, `eula.txt`, and client resource output, then loaded normally and passed all required tests.
- The build repeated the pre-existing deprecation note in `CursedInternshipContractItem`; this plan did not modify that API call.

## User Setup Required

None - Retake authority and reconciliation are local/offline and require no account, credential, external service, runtime network, visible client launch, or manual UAT.

## Next Phase Readiness

- Item/entity interaction work can implement the bounded `RepresentationPort` without duplicating state authority, reconciliation, or retry ordering.
- Lifecycle, desk, and command callers have one keyed service seam for recovery and retry.
- No open broken-window entry, stub, skipped test, unrun verification, or implementation blocker remains; visual/player UAT and distribution remain explicitly outside this plan.

## Self-Check: PASSED

- All nine declared implementation/test files and this canonical summary exist on disk.
- All nine RED/GREEN/integration/fix commits exist in repository history with no tracked-file deletion.
- Requirement coverage, actuals, TDD compliance, threat evidence, verification results, `status: complete`, and three resolved deviation-ledger entries are present.
- No stub, TODO, FIXME, skipped test, unrun verification, unknown coverage, open broken-window entry, or deferred blocker remains.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
