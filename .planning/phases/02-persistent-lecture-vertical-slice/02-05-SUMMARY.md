---
phase: 02-persistent-lecture-vertical-slice
plan: 05
subsystem: campaign-lifecycle
tags: [fabric-26.2, saved-data, lifecycle-events, orphan-rejection, gametest]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 04
    provides: Closed campaign events, pure reducer, and persist-before-effect service seam
  - phase: 02-persistent-lecture-vertical-slice
    plan: 16
    provides: Retained survival Contract-to-Professor tracer used as the lifecycle regression baseline
provides:
  - Real Fabric death, respawn, join, leave, entity-load/unload, and server-stopping callback adapters
  - State-first bounded runtime cleanup with exact owner, encounter, Professor, and attempt matching
  - Safe reload normalization, stale Professor rejection, and a shared in-process server-stop handler
  - Stable sanitized shutdown marker for later bounded dedicated-server evidence
affects: [phase-2-retry, phase-2-rewards, phase-2-verification, dedicated-server-smoke]

actuals:
  tokens: 14832
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns: [typed Fabric callback adapter, persist-before-cleanup effects, bounded runtime ownership, exact persisted identity gate]

key-files:
  created:
    - src/main/java/dev/developershell/server/CampaignLifecycle.java
    - src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java
  modified:
    - src/main/java/dev/developershell/server/DevelopersHellRuntime.java
    - src/main/java/dev/developershell/lecture/LectureEncounterManager.java
    - src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java
    - src/main/java/dev/developershell/DevelopersHell.java
    - src/main/resources/assets/developers_hell/lang/en_us.json
    - src/gametest/resources/fabric.mod.json

key-decisions:
  - "Translate Fabric callbacks into closed CampaignEvent values and retain CampaignService.apply as the only durable mutation path."
  - "Require exact owner, encounter, Professor UUID, and attempt identity before accepting runtime teardown; stale runtime cleanup may remove only that exact bounded runtime."
  - "Normalize any disk-loaded matching Professor to RETAKE_READY and reject its load so no in-flight cast or partial combat presentation resumes."
  - "Expose one server-wide onServerStopping handler used unchanged by both GameTest and the real SERVER_STOPPING callback; reserve bounded production-stop observation for Plan 13."

patterns-established:
  - "State-first lifecycle convergence: accepted transition and dirty SavedData precede boss-bar closure, cast cancellation, and owned-entity discard."
  - "Bounded cleanup: the encounter manager inspects its runtime registry and owned entity set only; no world-wide entity scan or arena mutation participates."
  - "Startup quarantine: persisted Professor identity is admission data, not authority to resume combat."

requirements-completed: [FND-06, FND-07, LECT-02]

coverage:
  - id: D1
    description: "Death, escape, timeout, dimension change, disconnect, abort, and entity unload converge once to durable RETAKE_READY before presentation cleanup."
    requirement: FND-06
    verification:
      - kind: e2e
        ref: "LectureLifecycleGameTests#deathEscapeTimeoutDimensionDisconnectAbortAndUnloadConvergeExactlyOnce"
        status: pass
    human_judgment: false
  - id: D2
    description: "Join/reload normalization rejects incomplete, stale, and matching disk-loaded Professors while preserving campaign ledgers and preventing queued combat from resuming."
    requirement: LECT-02
    verification:
      - kind: e2e
        ref: "LectureLifecycleGameTests#reloadJoinRejectsOrphanAndCancelsEveryQueuedImpact"
        status: pass
    human_judgment: false
  - id: D3
    description: "The in-process server-stop handler processes every bounded active runtime once and duplicate calls are no-ops without terminating GameTest."
    requirement: FND-07
    verification:
      - kind: e2e
        ref: "LectureLifecycleGameTests#serverStopHandlerConvergesExactlyOnceWithoutStoppingGameTestServer"
        status: pass
    human_judgment: false
  - id: D4
    description: "Production bootstrap registers SERVER_STOPPING and emits a sanitized completion marker after the shared handler returns."
    requirement: FND-06
    verification:
      - kind: integration
        ref: "CampaignLifecycle.register source assertion plus clean GameTest shutdown callback log"
        status: pass
    human_judgment: false

duration: 22min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 05: Lifecycle Convergence and Orphan Rejection Summary

**Every lecture interruption now enters durable safe Retake state before bounded combat cleanup, while disk-loaded or stale Professors are rejected and shutdown uses the same tested handler as the real Fabric callback.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-26T19:05:03Z
- **Completed:** 2026-08-26T19:26:10Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Connected current Fabric death, respawn, join, leave, entity-load/unload, and server-stop events to closed campaign events without bypassing the persisted reducer/service boundary.
- Made escape, timeout, missing-player, dimension change, and missing-Professor detection inspect only bounded active runtimes, then clean only after an accepted durable transition.
- Rejected incomplete and stale persisted Professors, normalized exact disk matches to Retake instead of resuming combat, and added localized failure/reload/Retake feedback.
- Added a seven-path lifecycle convergence matrix plus reload/orphan and in-process shutdown GameTests, including duplicates and forged encounter/Professor identities.

## Requirements (Copied Verbatim)

- **FND-06**: Versioned campaign, encounter, reward, and module state survives save, quit, reload, death, and chunk unload without duplication or regression.
- **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.
- **LECT-02**: Defeating Professor Infinite Slides grants the Attendance Sheet and a cooldown-limited Infinite Slides Remote after a fight that supports failure, cleanup, retry, and mid-campaign persistence.

## Task Commits

Each TDD task was committed at its RED and GREEN gates:

1. **RED — Specify live-exit, reload, duplicate, and orphan convergence** - `6980fd2` (test)
2. **GREEN — Converge lifecycle callbacks through state-first bounded cleanup** - `e217e36` (feat)
3. **RED — Specify the in-process server-stop handler and idempotency** - `a83424d` (test)
4. **GREEN — Register the real stop callback and completion marker** - `6d936e4` (feat)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/server/CampaignLifecycle.java` - One-time Fabric callback registration, exact identity admission, reload normalization, bounded runtime exit, and shared shutdown handler.
- `src/main/java/dev/developershell/server/DevelopersHellRuntime.java` - Lifecycle service adapter that applies transitions durably before cleanup and sends localized safe-Retake feedback.
- `src/main/java/dev/developershell/lecture/LectureEncounterManager.java` - Bounded runtime snapshots, owned entity set, live-exit detection, identity-safe idempotent cleanup, and cast/presentation cancellation.
- `src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java` - Transient disk-load provenance used by defensive entity admission.
- `src/main/java/dev/developershell/DevelopersHell.java` - Production lifecycle bootstrap before lecture manager initialization.
- `src/main/resources/assets/developers_hell/lang/en_us.json` - Localized exit, reload, and Retake copy.
- `src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java` - Full convergence, replay, forged identity, orphan, reload, and shutdown-handler proof.
- `src/gametest/resources/fabric.mod.json` - GameTest entrypoint registration.

## Decisions Made

- Kept all callback adapters translation-only: they submit `CampaignEvent` values, while `CampaignService.apply` remains the sole state mutation and effect-ordering boundary.
- Matched four identity dimensions for live runtimes and three persisted identity dimensions for disk entities so stale or forged callbacks cannot tear down current presentation.
- Treated any persisted Professor load as restart evidence: exact matches normalize the saved attempt and are rejected from the world; stale or incomplete instances are simply rejected/discarded.
- Used one server-wide handler for real shutdown and GameTest. The GameTest calls the handler without stopping its process; Plan 13 remains responsible for bounded production-server stop evidence.

## Verification

- Pinned Temurin `25.0.4+7`, Loom `1.17.19`, and offline Gradle gate `clean test runGameTest auditDirectDependencies build` - PASS.
- Fresh generated server world - `7 GAME TESTS COMPLETE`; `All 7 required tests passed` - PASS.
- Lifecycle source matrix contains death, escape, timeout, dimension, disconnect, abort, unload, orphan, reload, and server-stop cases - PASS.
- Real `ServerLifecycleEvents.SERVER_STOPPING` registration and sanitized `DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE` marker are present; the GameTest runner's ordinary shutdown invoked the callback after all tests - PASS.
- Direct dependency audit retained the approved five declarations and Loom injection baseline `145/a3fef1ae...` - PASS.
- Stub/skip scan, no tracked deletions, no common-side client imports, no runtime network access, and no bed/respawn or arena-block mutation in lifecycle production code - PASS.
- No visible client was launched, no distributable artifact was published or replaced, and no human visual UAT is claimed.

## Threat Mitigations

- **T-02-LIFE-01:** Live exits require exact owner, encounter, Professor UUID, and attempt; entity admission requires exact saved owner, encounter, and Professor UUID.
- **T-02-LIFE-02:** Saved active identity is cleared and marked dirty before cleanup effects, the runtime map is bounded, cleanup removes the map entry before discard, and cleanup-generated unload is stale.
- **T-02-LIFE-03:** The real callback delegates to the tested handler and logs only a constant completion marker after the handler completes; it contains no path, UUID, identity, or private data.
- No new endpoint, dependency, file-access path, schema boundary, client authority, runtime network, or unplanned threat surface was introduced.

## TDD Gate Compliance

- Task 1 RED commit `6980fd2` failed at `compileGametestJava` because lifecycle and runtime-snapshot APIs did not yet exist; GREEN commit `e217e36` made the same clean lifecycle suite pass.
- Task 2 RED commit `a83424d` failed at `compileGametestJava` because `onServerStopping(MinecraftServer)` did not yet exist; GREEN commit `6d936e4` made the full clean unit/GameTest/dependency/build gate pass.
- No refactor commit was needed after either GREEN gate.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Scoped the deterministic timeout seam to one encounter**
- **Found during:** Task 1 GREEN verification
- **Issue:** The first test clock seam advanced every active runtime on the shared GameTest server, which could time out a concurrent retained tracer.
- **Fix:** Required an encounter UUID and advanced only that bounded runtime while production ticking still uses each runtime's real level clock.
- **Files modified:** `LectureEncounterManager.java`, `LectureLifecycleGameTests.java`
- **Commit:** `e217e36`

**2. [Rule 1 - Bug] Isolated the server-wide stop test from concurrent runtime tests**
- **Found during:** Task 2 GREEN verification
- **Issue:** GameTests share one logical server, so the first stop-handler run correctly cleaned both the stop fixture and a delayed Foundation tracer, invalidating that unrelated test.
- **Fix:** Delayed only the stop test until the retained tracer completes, preserving the production handler's required server-wide semantics.
- **Files modified:** `LectureLifecycleGameTests.java`
- **Commit:** `6d936e4`

## Issues Encountered

- Clean GameTest startup emitted the existing warnings for absent transient `server.properties`, `eula.txt`, and client-resource output, then loaded normally and passed every required test.
- The first Task 2 GREEN run exposed the shared-server test collision described above; the next clean full gate passed 7/7 GameTests.

## User Setup Required

None - lifecycle convergence is local, offline, and requires no account, credential, external service, runtime network, or client launch.

## Next Phase Readiness

- Later Retake materialization plans can consume the already-emitted `ReconcileRetake` intent; this plan deliberately establishes durable entitlement and safe player feedback without preempting their inventory/fallback policy.
- Plan 13 can require `DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE` during its bounded `runProductionServer` clean-stop smoke. This plan does not claim that later dedicated-server evidence or human visual UAT.
- Reward and retry plans can rely on exact stale-identity rejection, bounded cleanup ownership, and no resumed cast after reload.

## Self-Check: PASSED

- All eight declared task files and this canonical summary exist on disk.
- RED commits `6980fd2`/`a83424d` and GREEN commits `e217e36`/`6d936e4` exist in repository history with no tracked-file deletion.
- Requirement coverage, actuals, TDD compliance, threat evidence, verification results, `status: complete`, and two resolved deviation-ledger entries are present.
- No stub, TODO, FIXME, skipped test, unrun verification, unknown coverage, open broken-window entry, or deferred blocker remains.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-26*
