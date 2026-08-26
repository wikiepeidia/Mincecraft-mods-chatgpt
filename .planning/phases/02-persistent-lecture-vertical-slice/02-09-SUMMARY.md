---
phase: 02-persistent-lecture-vertical-slice
plan: 09
subsystem: lecture-combat-domain
tags: [fabric-26.2, pure-state-machine, deterministic-seeds, combat-geometry, damage-admission]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 04
    provides: Persist-before-effect CampaignService transaction boundary
  - phase: 02-persistent-lecture-vertical-slice
    plan: 05
    provides: Owner, encounter, Professor, and runtime lifecycle identity
  - phase: 02-persistent-lecture-vertical-slice
    plan: 06
    provides: Exact immutable 17x17 boundary and 15x15 combat geometry
  - phase: 02-persistent-lecture-vertical-slice
    plans: [07, 08]
    provides: State-first Retake identity, reconciliation, and delivery behavior
provides:
  - Pure deterministic Slide Deck, Surprise Quiz, and Attendance Check state machine
  - Exact local lane, quiz-pad, and attendance-ring containment
  - Owner/current-encounter/current-window Professor damage clamped at 80/40/0
affects: [02-10, lecture-encounter-manager, professor-presentation, lecture-gametests]

actuals:
  tokens: 16213
  tasks: 2
  commits: 5

tech-stack:
  added: []
  patterns:
    - Immutable pure-domain transitions with explicit server facts and bounded intents
    - Stable mixed seeds derived only from encounter UUID, attempt, act, cycle, and quiz index
    - Campaign transaction validation composed with pure damage-floor admission

key-files:
  created:
    - src/main/java/dev/developershell/lecture/LectureAct.java
    - src/main/java/dev/developershell/lecture/LectureStateMachine.java
    - src/test/java/dev/developershell/lecture/LectureStateMachineTest.java
  modified:
    - src/main/java/dev/developershell/lecture/LectureRules.java
    - src/main/java/dev/developershell/lecture/LectureGeometry.java
    - src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java
    - src/test/java/dev/developershell/lecture/LectureGeometryTest.java

key-decisions:
  - "Preserve the existing seven-argument LectureRules construction surface while exposing the exact Standard 120 health, 100/160/120 wind-ups, 80-tick windows, four-damage consequences, and three-add bound."
  - "Freeze quiz pads as non-overlapping 3x3 half-open local zones centered at forward offset 9 and right offsets -5/0/5; reduced effects never changes containment."
  - "Use a 60-tick recovery, matching the existing three-second recovery copy, without changing any hard 100/160/120/80 timing contract."
  - "A third Attendance absence emits detention once and remains nonlethal; the player must still become PRESENT before the final vulnerability window opens."
  - "Retain CampaignService as the only victory/reward transaction; the Professor adds only manager-window checks and pure 80/40/0 clamping."

patterns-established:
  - "Resolve boundary: WIND_UP records a target, RESOLVE records the server observation, then VULNERABLE or RECOVERY owns a bounded deadline."
  - "Threshold boundary: matching damage is clamped to one act floor, closes that window at the floor, and never grants entity loot."

requirements-completed: [FND-07, LECT-01, LECT-02]

coverage:
  - id: D1
    description: Deterministic three-act timings, choices, retries, consequences, and Standard 120/80/40/0 progression
    requirement: FND-07
    verification:
      - kind: unit
        ref: src/test/java/dev/developershell/lecture/LectureStateMachineTest.java#13 deterministic combat-domain tests
        status: pass
    human_judgment: false
  - id: D2
    description: Exact lane, quiz-pad, and attendance-ring server containment with reduced-effects invariance
    requirement: LECT-01
    verification:
      - kind: unit
        ref: src/test/java/dev/developershell/lecture/LectureGeometryTest.java#7 exact geometry tests
        status: pass
    human_judgment: false
  - id: D3
    description: Professor damage requires the owner, durable active encounter, live manager participant, and current window and cannot skip an act floor
    requirement: LECT-01
    verification:
      - kind: unit
        ref: src/test/java/dev/developershell/lecture/LectureStateMachineTest.java#owner-stale-window-and-floor matrix
        status: pass
      - kind: other
        ref: pinned Java 25 compileJava plus forbidden reward/client/network/random source audit
        status: pass
    human_judgment: false
  - id: D4
    description: Final threshold emits one pure victory intent while entity-level completion remains reward-free and delegates to CampaignService
    requirement: LECT-02
    verification:
      - kind: unit
        ref: src/test/java/dev/developershell/lecture/LectureStateMachineTest.java#finalMatchingThresholdEmitsVictoryExactlyOnce
        status: pass
      - kind: other
        ref: ProfessorInfiniteSlidesEntity no-loot/reward-surface source audit
        status: pass
    human_judgment: false

duration: 16min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 09: Deterministic Three-Act Combat Domain Summary

**Pure three-act Lecture combat with explicit reproducible seeds, exact 100/160/120 wind-ups, bounded consequences, and owner-scoped 80/40/0 damage windows**

## Performance

- **Duration:** 16 min
- **Started:** 2026-08-26T21:08:03Z
- **Completed:** 2026-08-26T21:23:41Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Added a pure immutable state machine for Slide Deck, Surprise Quiz, and Attendance Check, including wind-up, resolve, vulnerability, recovery, and complete states.
- Made every apparent random choice reproducible from explicit encounter/attempt/act/cycle/quiz inputs and retained the seed in assertion failures.
- Extended Plan 06 geometry additively with exact, non-overlapping lane and quiz zones plus radius-2.5 Attendance containment.
- Bounded misses, Homework output, absence count, one-time nonlethal detention, timers, transition intent counts, and 120/80/40/0 boss health progression.
- Kept Professor victory and rewards behind the existing CampaignService transaction while adding manager-window admission and floor clamping.

## Task Commits

Each task followed RED then GREEN and was committed atomically:

1. **Task 1 RED: Specify deterministic act timing, choices, and consequences** — `fe45221` (`test`)
2. **Task 1 GREEN: Implement the deterministic three-act combat domain** — `3bbf0c9` (`feat`)
3. **Task 2 RED: Specify owner/current-window Professor damage admission** — `5837c10` (`test`)
4. **Task 2 GREEN: Gate and clamp Professor damage without reward authority** — `9ddd879` (`feat`)

**Plan metadata:** committed separately after tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/lecture/LectureAct.java` — Stable act order, exact Standard wind-ups, and 80/40/0 floors.
- `src/main/java/dev/developershell/lecture/LectureStateMachine.java` — Pure state/input/output model, bounded intents, seed mixer, and damage admission.
- `src/main/java/dev/developershell/lecture/LectureRules.java` — Backwards-compatible Standard combat bounds on the existing seven-argument rules surface.
- `src/main/java/dev/developershell/lecture/LectureGeometry.java` — Additive local containment for lanes, pads, and rings without changing Plan 06 layout APIs.
- `src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java` — 120-health binding and owner/encounter/window/floor damage gate retaining CampaignService victory.
- `src/test/java/dev/developershell/lecture/LectureGeometryTest.java` — Exact containment and boundary coverage.
- `src/test/java/dev/developershell/lecture/LectureStateMachineTest.java` — Timing, seed, consequence, absence, threshold, rejection, and once-only victory matrix.

## Decisions Made

- Quiz pads use 3x3 local zones centered on the already-frozen A/B/C anchors. Half-open maximum edges prevent overlap and make no-answer space explicit.
- Recovery lasts 60 ticks because the UI contract already promises a three-second restored-state message; hard act/window timings remain exactly 100/160/120/80.
- Slide impact always reaches a window, Quiz requires a correct answer, and Attendance requires presence. Third absence detains once but does not manufacture final-window success.
- Standard combat tuning is exposed without changing the seven-argument `LectureRules` constructor/equality contract used by existing runtime/config tests.
- Damage admission composes, rather than replaces, existing server-thread, participant, owner, level, writable-schema, and durable active-encounter validation.

## Deviations from Plan

None - plan executed exactly as written within its pure-domain/entity scope.

## Issues Encountered

- The current `DevelopersHellRuntime.create` retains the older seven-value projection and does not yet pass `professorHealth`, `missDamage`, `maxAdds`, Quiz timing, or Attendance timing from `DevHellConfig.LectureTuning`. Per ownership boundaries, this remains an explicit Plan 02-10 integration follow-up and is not claimed resolved here.
- The retained `FoundationGameTests` tracer still expects a single lethal hit in the first vulnerability window. The new required floor clamping intentionally supersedes that expectation; Plan 02-10 must replace it with the real three-act GameTest. No persistent GameTest or human client UAT was run or claimed in this plan.

## Verification

- Pinned Temurin `25.0.4+7`: `LectureGeometryTest` — 7/7 passed.
- Pinned Temurin `25.0.4+7`: `LectureStateMachineTest` — 13/13 passed.
- `compileJava` and `compileClientJava` passed against Fabric Loom `1.17.19` offline.
- Pure-domain scan found no Minecraft/Fabric, filesystem, network, wall-clock, or unseeded-random dependency in `LectureAct` or `LectureStateMachine`.
- Professor scan found no entity reward, client import, network/OpenAI, HTTP, or unseeded-random surface.
- Stub scan found only deliberate null-state invariants and persisted-identity guards; no UI/data placeholder or incomplete behavior was introduced.

## TDD Gate Compliance

- RED commit `fe45221` precedes GREEN commit `3bbf0c9` for Task 1.
- RED commit `5837c10` precedes GREEN commit `9ddd879` for Task 2.

## User Setup Required

None - the combat domain is offline, local, and requires no account, key, service, or runtime download.

## Next Phase Readiness

- Plan 02-10 can project this machine into the encounter manager, translate bounded intents into server effects/presentation, and replace the old one-hit tracer GameTest.
- Plan 02-10 must also map the five currently dropped combat tuning fields into runtime rules before claiming configured non-Standard integration.
- Human gameplay/readability UAT remains intentionally unclaimed until the later complete client loop is ready.

## Self-Check: PASSED

- All seven plan-owned files exist.
- RED/GREEN commits `fe45221`, `3bbf0c9`, `5837c10`, and `9ddd879` are present.
- Coverage metadata parsed with four fully automated passing deliverables.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
