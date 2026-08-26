---
phase: 02-persistent-lecture-vertical-slice
plan: 17
subsystem: persistent-rewards
tags: [fabric-26.2, java-25, gametest, exactly-once, owner-bound-items, tdd]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 11
    provides: Persist-before-effects victory transition with durable PASSED, Sheet entitlement, and Remote ledger
  - phase: 02-persistent-lecture-vertical-slice
    plan: 10
    provides: Real three-act 80/40/0 final damage window owned by LectureEncounterManager
  - phase: 02-persistent-lecture-vertical-slice
    plan: 07
    provides: Inventory-first owner-targeted fallback and matching-Desk Retake interaction patterns
provides:
  - Exactly-once first-victory projection into one Attendance Sheet and one Infinite Slides Remote
  - Owner-and-recovery-sequence-bound Attendance Sheet with matching-Desk loss recovery
  - Manager-only accepted victory handoff with inert entity-death and compatibility reward paths
  - Real production-path GameTests for replay, fallback, recovery, and terminal races
affects: [02-18, remote-item, reward-service, lecture-lifecycle, campaign-persistence]

actuals:
  tokens: 17212
  tasks: 1
  commits: 3

tech-stack:
  added: []
  patterns:
    - Persisted accepted transition is validated against the current saved state before any physical reward or message
    - Recoverable artifacts carry owner UUID plus a monotonic persisted generation, never progression authority
    - Inventory failure falls back to an owner-targeted ItemEntity at the saved retry position

key-files:
  created:
    - src/main/java/dev/developershell/item/AttendanceSheetItem.java
    - src/main/java/dev/developershell/lecture/RewardService.java
  modified:
    - src/main/java/dev/developershell/lecture/LectureEncounterManager.java
    - src/main/java/dev/developershell/server/DeskInteraction.java
    - src/main/java/dev/developershell/registry/ModItems.java
    - src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java
    - src/main/java/dev/developershell/campaign/CampaignService.java
    - src/gametest/java/dev/developershell/gametest/RewardGameTests.java
    - src/gametest/resources/fabric.mod.json
    - src/main/resources/assets/developers_hell/lang/en_us.json

key-decisions:
  - "Only LectureEncounterManager may turn admitted final-window damage into commitVictory and pass its accepted matching transition to RewardService."
  - "Keep CampaignService.victory callable for source compatibility but deprecate it as a false-returning no-op with no persistence or effects."
  - "Bind every Attendance Sheet to owner UUID and sheetRecoverySequence; increment that persisted generation before restoring a missing Sheet."
  - "Preserve Retake priority at the Desk and reuse the existing campaign schema rather than adding a physical-item ledger."

patterns-established:
  - "Reward handoff: manager damage -> accepted persisted commitVictory -> validated RewardService projection -> runtime cleanup."
  - "Artifact recovery: exact PASSED entitlement plus owner, dimension, Desk position/facing, missing current binding, then persisted generation advance before materialization."

requirements-completed: [FND-06, FND-07, LECT-02]

coverage:
  - id: D1
    description: A real three-act final damage window grants exactly one Sheet and one Remote only after persisted victory
    requirement: FND-06
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/RewardGameTests.java#realEncounterVictoryReconcilesFirstReward
        status: pass
    human_judgment: false
  - id: D2
    description: Full inventory uses owner-targeted fallbacks while stale manager callbacks and replay create no duplicate rewards or messages
    requirement: FND-07
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/RewardGameTests.java#fullInventoryVictoryUsesOwnerTargetedFallbackWithoutReplay
        status: pass
    human_judgment: false
  - id: D3
    description: PASSED matching-Desk recovery restores only a missing current Sheet and preserves progression, Remote, and Retake fields
    requirement: LECT-02
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/RewardGameTests.java#matchingDeskRecoversOnlyMissingSheetWithoutRewardReplay
        status: pass
    human_judgment: false
  - id: D4
    description: Direct entity death and the legacy compatibility wrapper cannot persist victory, grant items, or present success
    requirement: FND-07
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/RewardGameTests.java#directDeathAndCompatibilityVictoryCannotBypassManager
        status: pass
    human_judgment: false

duration: 24min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 17: Physical First Reward and Sheet Recovery Summary

**Persisted Lecture victory now projects exactly once into an owner-bound Attendance Sheet and Infinite Slides Remote, with generation-safe Sheet recovery at the saved Desk**

## Performance

- **Duration:** 24 min
- **Started:** 2026-08-26T22:14:09Z
- **Completed:** 2026-08-26T22:37:16Z
- **Tasks:** 1
- **Files modified:** 10

## Accomplishments

- Routed the real Slide Deck -> Surprise Quiz -> Attendance final damage window through `LectureEncounterManager`, `CampaignService.commitVictory`, and `RewardService`, in that persisted-first order.
- Materialized one non-stackable owner-bound Attendance Sheet and one Infinite Slides Remote inventory-first, with owner-targeted retry-position fallbacks for a full inventory and one truthful victory message only after both succeed.
- Added matching-owner/matching-Desk Sheet recovery that advances the existing durable recovery sequence, rejects an existing current representation, and never reissues the Remote or changes progression/Retake state.
- Deauthorized both legacy direct paths: entity death is inert and the callable deprecated `CampaignService.victory(...)` compatibility wrapper always returns false without state, effects, items, or presentation.

## Task Commits

1. **Task 1 RED: Specify reward reconciliation and bypass rejection** — `f32c97a` (test)
2. **Task 1 GREEN: Reconcile first rewards and missing Sheet recovery** — `2b12332` (feat)

**Plan metadata:** committed separately after tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/item/AttendanceSheetItem.java` — Non-stackable translated proof item with fail-closed owner UUID and recovery-sequence binding.
- `src/main/java/dev/developershell/lecture/RewardService.java` — Accepted-transition validation, inventory/fallback materialization, Sheet recovery, and stale entity-load fencing.
- `src/main/java/dev/developershell/lecture/LectureEncounterManager.java` — Immediate manager-owned damage consumption and sole accepted victory-to-reward handoff.
- `src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java` — Admitted hits notify the manager; direct death can no longer commit or grant.
- `src/main/java/dev/developershell/campaign/CampaignService.java` — Deprecated compatibility victory wrapper reduced to a safe false-returning no-op.
- `src/main/java/dev/developershell/server/DeskInteraction.java` — Retake-first routing plus PASSED matching-Desk Sheet recovery.
- `src/main/java/dev/developershell/registry/ModItems.java` — Stable Attendance Sheet key now registers the custom non-stackable item.
- `src/main/resources/assets/developers_hell/lang/en_us.json` — Exact victory, recovery, no-op, already-present, and proof tooltip copy.
- `src/gametest/java/dev/developershell/gametest/RewardGameTests.java` — Four real transformed-runtime acceptance tests covering grant, fallback, loss, replay, and bypasses.
- `src/gametest/resources/fabric.mod.json` — Registers the reward GameTest suite.

## Decisions Made

- The campaign record remains the sole authority. Physical items contain only identity/generation metadata and cannot confer PASSED, Remote issuance, or Retake rights.
- The existing `sheetRecoverySequence` is sufficient to invalidate stale Sheet entities and serialize recovery; no schema change or parallel item ledger was introduced.
- The manager consumes each admitted hit synchronously so the final lethal health change cannot invoke entity-owned death logic before the accepted persisted transition.
- Victory presentation is a single localized result containing both exact accepted facts; rejected and replayed transitions dispatch no message.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Resolved carried direct reward authority from Plan 02-11**
- **Found during:** Task 1 production-path tracing
- **Issue:** `ProfessorInfiniteSlidesEntity.die()` still invoked the compatibility victory path, and that wrapper could persist victory and grant items outside the manager-to-service contract.
- **Fix:** Used the explicitly approved narrow ownership expansion to make entity death inert and preserve `CampaignService.victory(...)` as a deprecated safe false/no-op. The manager is now the sole production `commitVictory` caller that reaches `RewardService`.
- **Files modified:** `ProfessorInfiniteSlidesEntity.java`, `CampaignService.java`, `LectureEncounterManager.java`, `deferred-items.md`
- **Verification:** `directDeathAndCompatibilityVictoryCannotBypassManager` passes; source scan finds no production call to `CampaignService.victory(...)` and only the manager calls `RewardService.reconcileVictory(...)`.
- **Committed in:** `2b12332` (implementation; tracking resolution in metadata commit)

**2. [Rule 1 - Bug] Made nullable campaign-field assertions null-safe**
- **Found during:** Task 1 first clean transformed GameTest run
- **Issue:** Minecraft 26.2 `GameTestHelper.assertValueEqual` dereferenced the nullable expected active/Retake references, causing an assertion-helper NPE after recovery behavior had succeeded.
- **Fix:** Compared the four intentionally nullable preserved fields with `Objects.equals` while retaining exact field-by-field recovery invariants.
- **Files modified:** `RewardGameTests.java`
- **Verification:** The rerun completed all 29 required GameTests with zero failures.
- **Committed in:** `2b12332`

---

**Total deviations:** 2 auto-fixed (1 missing critical functionality, 1 test bug)
**Impact on plan:** Both changes enforce the specified single-authority reward boundary and its proof without schema or feature scope expansion.

## Issues Encountered

- The intentional RED compile failed only on the absent `AttendanceSheetItem` binding and manager damage callback contracts.
- The first clean transformed run passed 28/29 tests and exposed only the nullable assertion-helper bug above; the exact rerun passed all 29.

## TDD Gate Compliance

- RED commit `f32c97a` captured four production-path GameTests and failed compilation with 19 expected missing-contract errors.
- GREEN commit `2b12332` supplied the minimum item, service, manager, Desk, localization, and bypass-deauthorization implementation.
- Commit order is RED then GREEN; no refactor commit was required.

## Verification

- Project-pinned Eclipse Temurin `25.0.4+7` and Loom `1.17.19` were used with toolchain auto-detection/download disabled.
- Exact offline gate passed from `clean`: `test runGameTest auditDirectDependencies build --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle`.
- All 29 required persistent GameTests passed, including all four new reward tests and the existing three-act, lifecycle, Retake, and geometry suites.
- Dependency audit remained on Minecraft `26.2`, Fabric Loader `0.19.3`, and Fabric API `0.158.0+26.2`; the full build passed.
- Stub scan found no TODO, FIXME, placeholder, UI-flowing empty value, skipped test, or unrun verification in the changed implementation/tests.
- No visible client, distribution replacement/publication, or human UAT was run or claimed.

## Known Stubs

None.

## User Setup Required

None - rewards and recovery are offline, world-local, and require no account, API, credential, telemetry, dependency, or external service.

## Next Phase Readiness

- Plan 02-18 can implement Infinite Slides Remote behavior against the already-persisted `remoteIssued` ledger and one physical first grant.
- The carried Plan 02-11 direct-path issue is resolved; the phase deferred-items ledger records that resolution.
- Three-act 80/40/0 thresholds, lifecycle first-terminal-wins ordering, Retake identity/priority, campaign schema 1, and arena geometry remain covered by the passing full GameTest suite.

## Self-Check: PASSED

- All ten created/modified implementation, resource, and GameTest files plus this Summary exist.
- RED/GREEN commits `f32c97a` and `2b12332` are present in repository history.
- Source assertions confirm the manager-to-service handoff, inert entity death path, deprecated false-returning compatibility wrapper, and valid localization JSON.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
