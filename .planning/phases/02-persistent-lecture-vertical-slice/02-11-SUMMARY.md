---
phase: 02-persistent-lecture-vertical-slice
plan: 11
subsystem: campaign-transactions
tags: [fabric-26.2, java-25, pure-reducer, exactly-once, tdd]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 04
    provides: Closed campaign events, monotonic persisted state, and the persist-before-effects service
  - phase: 02-persistent-lecture-vertical-slice
    plan: 05
    provides: Typed lifecycle terminal events and state-first encounter cleanup
  - phase: 02-persistent-lecture-vertical-slice
    plan: 07
    provides: Owner-and-failed-encounter Retake identity plus reservation crash-window invariants
  - phase: 02-persistent-lecture-vertical-slice
    plan: 10
    provides: Real three-act 80/40/0 victory seam and presentation cleanup
provides:
  - One closed encounter-terminal event family governed by one owner-and-encounter first-winner gate
  - Transition-returning CampaignService.commitVictory seam for accepted reward reconciliation
  - Exhaustive pure tests for every failure/reload versus victory order and reconciliation replay
affects: [02-17, reward-service, lecture-lifecycle, campaign-persistence]

actuals:
  tokens: 4486
  tasks: 1
  commits: 3

tech-stack:
  added: []
  patterns:
    - The persisted ACTIVE encounter reference is the sole terminal latch; no parallel terminal ledger is added
    - Every encounter terminal passes one common reducer admission gate before producing state or intents
    - Physical reward adapters consume the complete accepted CampaignTransition after persistence

key-files:
  created: []
  modified:
    - src/main/java/dev/developershell/campaign/CampaignEvent.java
    - src/main/java/dev/developershell/campaign/CampaignReducer.java
    - src/main/java/dev/developershell/campaign/CampaignService.java
    - src/test/java/dev/developershell/campaign/CampaignReducerTest.java

key-decisions:
  - "Use the existing ACTIVE encounter reference as the durable first-terminal-wins latch; do not add a schema field or alter Retake identity."
  - "Expose CampaignService.commitVictory as the transition-returning persisted boundary while retaining the three-argument boolean victory wrapper until Plan 02-17 migrates physical rewards."
  - "Treat Terminal, NormalizeReload, and Victory as one sealed EncounterTerminal family so future terminal variants cannot bypass the common owner-and-encounter gate."

patterns-established:
  - "Terminal arbitration: validate owner globally, validate the exact ACTIVE encounter once, then dispatch the sealed terminal subtype."
  - "Reward handoff: PASSED, Sheet entitlement, and Remote ledger are dirty before cleanup/reward/presentation intents reach a caller."

requirements-completed: [FND-06, FND-07, LECT-02]

coverage:
  - id: D1
    description: Victory races every failure reason and reload in both orders, with only the first matching terminal producing state and intents
    requirement: FND-06
    verification:
      - kind: unit
        ref: src/test/java/dev/developershell/campaign/CampaignReducerTest.java#everyFailureAndReloadRaceVictoryWithTheFirstPersistedTerminalWinning
        status: pass
    human_judgment: false
  - id: D2
    description: Victory state and reward ledgers persist before cleanup/reward effects, while reentrant cleanup and replay produce no effects
    requirement: FND-07
    verification:
      - kind: unit
        ref: src/test/java/dev/developershell/campaign/CampaignReducerTest.java#servicePersistsVictoryBeforeEffectsAndSuppressesLateReconciliation
        status: pass
    human_judgment: false
  - id: D3
    description: Stale Retake projection, repeated Sheet recovery, and wrong-owner reconciliation cannot duplicate state or presentation intents
    requirement: LECT-02
    verification:
      - kind: unit
        ref: src/test/java/dev/developershell/campaign/CampaignReducerTest.java#servicePersistsVictoryBeforeEffectsAndSuppressesLateReconciliation
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 11: Exactly-Once Victory and Sheet Recovery Summary

**One persisted encounter-terminal gate now decides every victory/failure race and exposes the accepted reward transition without weakening Retake or three-act invariants**

## Performance

- **Duration:** 9 min
- **Started:** 2026-08-26T22:01:55Z
- **Completed:** 2026-08-26T22:10:18Z
- **Tasks:** 1
- **Files modified:** 4

## Accomplishments

- Grouped failure, reload normalization, and victory into one sealed `EncounterTerminal` family and one exact-owner/exact-encounter reducer gate.
- Added `CampaignService.commitVictory(...)`, which returns the full accepted/no-op transition after durable PASSED, Attendance Sheet entitlement, and Remote ledger persistence; the current boolean wrapper remains compatible for the live three-act path.
- Proved all eight terminal reasons plus reload against victory in both orders, reentrant unload during cleanup, repeated victory, stale Retake projection, Sheet recovery replay, and wrong-owner reconciliation.

## Task Commits

1. **Task 1 RED: Specify every terminal/reward ordering** — `90d87cb` (test)
2. **Task 1 GREEN: Expose the persisted victory result and common terminal gate** — `f11be5a` (feat)

**Plan metadata:** committed separately after tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/campaign/CampaignEvent.java` — Sealed encounter-terminal subtype shared by failure, reload, and victory.
- `src/main/java/dev/developershell/campaign/CampaignReducer.java` — Single first-winner admission gate before subtype-specific persisted outcomes and intents.
- `src/main/java/dev/developershell/campaign/CampaignService.java` — Transition-returning victory commit boundary plus compatibility physical projection wrapper.
- `src/test/java/dev/developershell/campaign/CampaignReducerTest.java` — Exhaustive two-order terminal matrix and service/reconciliation replay proofs.

## Decisions Made

- No new `PlayerCampaignState` field or schema change was needed. `ACTIVE + activeEncounterRef` is already the durable latch: the first accepted terminal clears it, so every later terminal observes `no_active_encounter` and carries no intents.
- Retake state remains owner UUID plus failed encounter UUID. Reservation/materialized UUID mutual exclusion, reservation-before-materialization, commit-before-consume retry, and runtime-failure compensation are untouched.
- `commitVictory` is the Plan 02-17 handoff. The existing three-argument `victory` method deliberately remains a compatibility wrapper so the current complete 80/40/0 encounter and its GameTests continue to compile until physical reward ownership migrates.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None in implementation. The TDD RED gate failed on the intentionally missing closed terminal/service result seam, then GREEN passed.

## Deferred Issues

- Plan 02-17 must deauthorize or reroute `ProfessorInfiniteSlidesEntity.die()` so direct entity death cannot consume the compatibility reward path; only the manager's accepted final-window `commitVictory` result may reach `RewardService`. This is recorded in `deferred-items.md` and was outside Plan 02-11 ownership.

## TDD Gate Compliance

- RED commit `90d87cb` failed during test compilation on the missing `EncounterTerminal` and `CampaignService.applyTerminal` contracts.
- GREEN commit `f11be5a` implemented the shared gate and transition-returning victory boundary; all 19 `CampaignReducerTest` cases pass.
- Commit order is RED then GREEN.

## Verification

- Project-pinned Eclipse Temurin `25.0.4+7` confirmed by `java --version` and `javac --version`.
- Exact plan gate passed offline: focused `CampaignReducerTest` plus `compileJava` with Loom `1.17.19` and the pinned resolution init script.
- Full Java unit suite passed after GREEN.
- Stub scan found no TODO, FIXME, placeholder, or UI-flowing empty value in the four changed files; matched `null` values are existing explicit optional-state checks.
- No broad persistent GameTest, visible client, distribution replacement/publication, or human UAT was run or claimed by this pure reducer plan.

## User Setup Required

None - the campaign transaction remains offline and introduces no account, API, credential, telemetry, dependency, or external service.

## Next Phase Readiness

- Plan 02-17 can consume `CampaignService.commitVictory(...)` and reconcile physical rewards only from its accepted persisted `GrantFirstRewards` intent.
- The direct Professor death compatibility call must be migrated or deauthorized during that plan; no blocker remains for Plan 02-11 itself.
- Retake crash-window semantics, campaign schema 1, lifecycle cleanup, and the deterministic three-act 80/40/0 threshold seam remain unchanged.

## Self-Check: PASSED

- All four changed implementation/test files, this Summary, and the phase deferred-items ledger exist.
- RED/GREEN commits `90d87cb` and `f11be5a` are present in repository history.
- Coverage metadata parsed with three fully automated passing deliverables.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
