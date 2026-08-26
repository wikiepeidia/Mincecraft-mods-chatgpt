---
phase: 02-persistent-lecture-vertical-slice
plan: 10
subsystem: lecture-boss-runtime
tags: [fabric-26.2, gametest, server-boss-event, owner-scoped-entity, accessibility-cues]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 05
    provides: Owner, encounter, Professor, campaign transaction, and terminal cleanup identity
  - phase: 02-persistent-lecture-vertical-slice
    plan: 08
    provides: Retake materialization and lifecycle reconciliation invariants
  - phase: 02-persistent-lecture-vertical-slice
    plan: 09
    provides: Pure deterministic three-act outputs, geometry, and 80/40/0 damage admission
provides:
  - Server-authoritative three-act Professor Infinite Slides encounter
  - Participant-only bounded presentation with semantic shapes, translated text, sounds, and particles
  - One owner-and-encounter-scoped no-loot Homework add with bounded lifetime and cleanup
  - Real GameTest coverage for all acts, rewards, ownership, persistence, reduced effects, and cleanup
affects: [lecture-uat, campaign-vertical-slice, final-packaging, future-boss-presentations]

actuals:
  tokens: 24408
  tasks: 2
  commits: 5

tech-stack:
  added: []
  patterns:
    - Pure state outputs are persisted into runtime state before server effects and presentation are materialized
    - Presentation is owner-only, transition-deduplicated, semantically redundant, bounded, and cleanup-atomic
    - Ephemeral helper entities prove owner and encounter identity and fail closed after disk load

key-files:
  created:
    - src/main/java/dev/developershell/entity/HomeworkAddEntity.java
    - src/main/java/dev/developershell/lecture/LecturePresentation.java
    - src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java
  modified:
    - src/main/java/dev/developershell/registry/ModEntities.java
    - src/main/java/dev/developershell/lecture/LectureEncounterManager.java
    - src/main/java/dev/developershell/lecture/LectureRules.java
    - src/main/java/dev/developershell/server/DevelopersHellRuntime.java
    - src/client/java/dev/developershell/client/DevelopersHellClient.java
    - src/main/resources/assets/developers_hell/lang/en_us.json
    - src/gametest/resources/fabric.mod.json
    - src/gametest/java/dev/developershell/gametest/FoundationGameTests.java
    - src/test/java/dev/developershell/config/DevHellConfigTest.java

key-decisions:
  - "Keep combat authority in LectureStateMachine and LectureEncounterManager; LecturePresentation consumes recorded state and can never decide an outcome."
  - "Use explicit translated target names plus diamond/X, square/circle/diamond, and ring/center identities so no harmful mechanic depends on color, particles, sound, or transient text alone."
  - "Preserve the seven-argument LectureRules constructor and equality contract while a configured factory stores all five formerly dropped combat values and the runtime forwards reduced-effects mode."
  - "Treat each vulnerability opening as a fresh deterministic admission boundary by clearing only the Professor's stale prior-hit i-frame, without changing the 80/40/0 floors."

patterns-established:
  - "Presentation boundary: one owner-only boss bar plus transition/whole-second text, capped sounds, and capped targeted server particles."
  - "Helper boundary: at most one live Homework add, exact owner/encounter targeting, no loot, a 400-tick lifetime, and immediate terminal cleanup."
  - "Accessibility boundary: reduced effects changes only density/cadence; collision, target, timing, semantic shapes, text, and sound remain identical."

requirements-completed: [FND-07, LECT-01, LECT-02]

coverage:
  - id: D1
    description: Wrong or unanswered quizzes create at most one bounded, no-loot Homework add that targets only its active owner and is removed on stale load or cleanup
    requirement: LECT-02
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java#homeworkAddRegistryIdentityAndOrphanGuardAreStable
        status: pass
      - kind: e2e
        ref: src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java#wrongQuizSpawnsOneOwnerScopedCleanupOwnedHomeworkAdd
        status: pass
    human_judgment: false
  - id: D2
    description: Slide Deck, Surprise Quiz, and Attendance Check resolve from server position and owner damage, enforce 80/40/0 floors, and commit Sheet and Remote exactly once
    requirement: LECT-01
    verification:
      - kind: e2e
        ref: src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java#threeActsUseRedundantCuesOwnerDamageAndAtomicVictoryCleanup
        status: pass
      - kind: other
        ref: pinned Temurin 25.0.4+7 clean test runGameTest auditDirectDependencies build
        status: pass
    human_judgment: false
  - id: D3
    description: Participant-only presentation supplies translated act/action/chat copy, distinct bounded vanilla cues, semantic shapes, reduced-effects invariance, and atomic cleanup
    requirement: FND-07
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java#reducedEffectsKeepSemanticShapesWithLowerBoundedCadence
        status: pass
      - kind: e2e
        ref: src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java#threeActsUseRedundantCuesOwnerDamageAndAtomicVictoryCleanup
        status: pass
    human_judgment: false
  - id: D4
    description: In-game readability, particle placement, cue legibility, and comedy timing of the complete Professor fight
    requirement: LECT-01
    verification:
      - kind: e2e
        ref: server GameTests prove semantic targets, cadence, caps, and cleanup without launching a client
        status: pass
    human_judgment: true
    rationale: Visual legibility and comedy timing require later in-game human judgment; no visible client or human UAT was run or claimed during this plan.

duration: 29min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 10: Three-Act Presentation and Homework Add Summary

**A real server-authoritative Professor fight with three deterministic spatial acts, owner-only semantic cues, one bounded Homework add, and exactly-once victory cleanup**

## Performance

- **Duration:** 29 min
- **Started:** 2026-08-26T21:28:39Z
- **Completed:** 2026-08-26T21:57:48Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments

- Registered `developers_hell:homework_add` as an unconditional no-loot Zombie-derived entity with exact owner/encounter persistence, owner-only targeting, bounded combat values, one-active-add cap, 400-tick lifetime, and terminal cleanup.
- Replaced the retained Slide-only runtime with the real deterministic Slide Deck, Surprise Quiz, and Attendance Check state flow, including server position resolution, explicit vulnerability windows, 80/40/0 floors, nonlethal consequences, and exactly-once Sheet/Remote victory.
- Added one owner-only `ServerBossEvent` and redundant translated action/chat, semantic non-color shapes, distinct vanilla sounds, and targeted particles with whole-second/transition dedupe and encounter caps.
- Proved normal/reduced-effects semantic parity, wrong-owner rejection, save/load identity, stale helper rejection, add caps, failure behavior, reward replay safety, and atomic presentation/entity cleanup in the live transformed server runtime.
- Projected every accepted Lecture combat setting and reduced-effects mode into immutable session rules without changing Standard defaults or the established seven-value equality surface.

## Task Commits

Each TDD task was committed atomically:

1. **Task 1 RED: Specify the Homework add contract** - `e1329c7` (test)
2. **Task 1 GREEN: Add owner-scoped Homework entity and manager ownership** - `1cf6341` (feat)
3. **Task 2 RED: Specify the real three-act presentation and runtime config projection** - `1f4bc6e` (test)
4. **Task 2 GREEN: Present and execute the complete three-act lecture** - `2ec4347` (feat)

**Plan metadata:** committed separately after tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/entity/HomeworkAddEntity.java` - Owner/encounter identity, owner-only attack guards, bounded attributes, persistence, and orphan rejection.
- `src/main/java/dev/developershell/lecture/LecturePresentation.java` - Owner-only boss bar, translated text, semantic geometry, bounded sounds/particles, reduced-effects cadence, snapshots, and atomic close.
- `src/main/java/dev/developershell/lecture/LectureEncounterManager.java` - Absolute-tick state-machine bridge, local-position projection, consequence/add materialization, presentation wiring, health synchronization, and cleanup.
- `src/main/java/dev/developershell/lecture/LectureRules.java` - Compatibility-preserving complete runtime combat tuning.
- `src/main/java/dev/developershell/registry/ModEntities.java` - Stable Homework entity type and default attributes.
- `src/main/java/dev/developershell/server/DevelopersHellRuntime.java` - Complete Lecture tuning and reduced-effects projection.
- `src/client/java/dev/developershell/client/DevelopersHellClient.java` - Client-only vanilla Zombie renderer registration.
- `src/main/resources/assets/developers_hell/lang/en_us.json` - Act, countdown, result, target, recovery, and window translations.
- `src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java` - Four transformed-runtime tests for entity identity, quiz ownership, full three-act victory, and accessibility invariance.
- `src/gametest/resources/fabric.mod.json` - Explicit Lecture GameTest entrypoint metadata.
- `src/gametest/java/dev/developershell/gametest/FoundationGameTests.java` - Retired the obsolete one-window immediate-victory registration superseded by real three-act coverage.
- `src/test/java/dev/developershell/config/DevHellConfigTest.java` - Focused assertions for all formerly dropped combat values and reduced-effects propagation.

## Decisions Made

- Presentation remains a one-way projection of server state. Client rendering, particles, sounds, and text never select a target, answer, result, damage window, or reward.
- Every spatial mechanic has persistent semantic identity in translated text and snapshot geometry in addition to particles and sound; reduced effects retains those identities and all server collision/timing.
- Homework is deliberately ephemeral and non-authoritative: it can damage only the bound owner, cannot carry progress or loot, and cannot resume from disk without the exact active runtime.
- CampaignService remains the only reward transaction. Professor death and the state-machine victory intent converge on its replay-safe owner/encounter validation.
- The five carried combat settings are stored in a compatibility-preserving `LectureRules.configured` instance; the original seven-argument constructor, Standard values, accessors, equality, and hash semantics remain stable.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Projected all accepted Lecture combat settings into runtime rules**
- **Found during:** Task 2 GREEN (carried Plan 02-09 follow-up)
- **Issue:** `professorHealth`, `missDamage`, `maxAdds`, Quiz timing, Attendance timing, and reduced-effects mode were accepted by config but dropped before manager initialization.
- **Fix:** Added a validated configured-rules factory, retained the seven-argument construction/equality surface and identical Standard defaults, and forwarded reduced effects through the runtime adapter.
- **Files modified:** `LectureRules.java`, `DevelopersHellRuntime.java`, `DevHellConfigTest.java`
- **Verification:** Focused runtime-composition assertions and the full unit/GameTest/build gate pass.
- **Committed in:** `1f4bc6e` (tests), `2ec4347` (implementation)

**2. [Rule 3 - Blocking] Retired the obsolete one-window immediate-victory GameTest registration**
- **Found during:** Task 1 GREEN (carried Plan 02-09 follow-up)
- **Issue:** The Foundation tracer expected one hit in the first window to finish the encounter, contradicting the required 80/40/0 three-act floors.
- **Fix:** Removed it from registered GameTests and replaced its encounter/reward assertions with the real all-three-act suite.
- **Files modified:** `FoundationGameTests.java`, `LectureBossGameTests.java`
- **Verification:** The clean broad gate discovers 25 tests, and all 25 pass with the real three-act victory path.
- **Committed in:** `1cf6341`, `1f4bc6e`

**3. [Rule 1 - Bug] Made direct lecture consequences immune to unrelated player join i-frames**
- **Found during:** Task 2 clean GameTest GREEN gate
- **Issue:** Vanilla `hurtServer` could reject a Slide miss or detention during the connection/spawn invulnerability interval, silently dropping a server-approved nonlethal consequence.
- **Fix:** Materialized the already-bounded `DirectDamage` intent as an explicit server health delta clamped to one heart.
- **Files modified:** `LectureEncounterManager.java`
- **Verification:** Unsafe Slide and third-absence detention assertions pass without becoming lethal.
- **Committed in:** `2ec4347`

**4. [Rule 1 - Bug] Isolated new act windows from stale prior-hit i-frames**
- **Found during:** Task 2 clean GameTest GREEN gate
- **Issue:** The deterministic absolute-time seam advanced through recovery without real entity ticks, leaving the prior accepted hit's vanilla i-frame active and rejecting the next act's valid owner hit.
- **Fix:** Clear only the Professor's stale i-frame when a new vulnerability window opens; owner/encounter/window admission and 80/40/0 floors remain unchanged.
- **Files modified:** `LectureEncounterManager.java`
- **Verification:** All three owner windows, wrong-owner rejection, exact floors, and final victory pass in the transformed server runtime.
- **Committed in:** `2ec4347`

---

**Total deviations:** 4 auto-fixed (2 bugs, 1 missing critical integration, 1 blocking carried regression)
**Impact on plan:** All changes are narrowly required for deterministic server authority and the clean broad gate. No lifecycle, Retake, geometry, threshold, distribution, or external-service scope expanded.

## Issues Encountered

- The first clean GameTest run exposed player join i-frames swallowing direct consequences; the second exposed a stale Professor i-frame across the deterministic time seam. Both were fixed at the server materialization/window boundaries, followed by a fresh clean passing gate.
- Context7 was unavailable in this environment. Exact 26.2 APIs were confirmed from the resolved Minecraft/Fabric sources and compilation; no dependency or version changed.

## TDD Gate Compliance

- Task 1 RED `e1329c7` failed at GameTest compilation on the intentionally missing Homework entity/manager contract before GREEN `1cf6341` passed all 23 then-registered GameTests.
- Task 2 RED `1f4bc6e` failed at test compilation on the intentionally missing reduced-effects/presentation surfaces before GREEN `2ec4347` passed compilation and all 25 registered GameTests.
- Commit order is RED then GREEN for both behavior-adding tasks.

## Verification

- Project-pinned Eclipse Temurin `25.0.4+7` confirmed by `java --version`.
- Offline clean gate passed: `clean test runGameTest auditDirectDependencies build` with Loom `1.17.19` and the pinned resolution init script.
- All 25 required server GameTests passed, including the real complete three-act path.
- Common-source scan found no `net.minecraft.client` import; renderer registration remains under `src/client`.
- `build/libs/developers-hell-0.1.0.jar` contains 170 entries and no `gametest` or `LectureBossGameTests` output.
- No visible client was launched, no distributable was copied/published, and no human UAT is claimed.

## User Setup Required

None - gameplay is offline and requires no account, API, credential, telemetry, or external service.

## Next Phase Readiness

- The complete persistent Lecture vertical slice is mechanically verified and ready for phase verification/package gates.
- Human in-game judgment remains appropriate for visual readability, particle placement, cue legibility, and comedy timing; this summary routes that work explicitly without claiming it occurred.
- Retake, campaign persistence, arena geometry, lifecycle identity, reward replay safety, and hard 80/40/0 thresholds remain intact.

## Self-Check: PASSED

- All three created implementation/test files and this canonical Summary exist.
- RED/GREEN commits `e1329c7`, `1cf6341`, `1f4bc6e`, and `2ec4347` exist in repository history.
- The latest project-pinned clean broad gate passed, and frontmatter records all plan requirements, actuals, and `status: complete`.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
