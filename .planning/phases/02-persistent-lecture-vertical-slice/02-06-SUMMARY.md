---
phase: 02-persistent-lecture-vertical-slice
plan: 06
subsystem: arena-validation
tags: [fabric-26.2, gametest, immutable-geometry, typed-rejections, atomic-start]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 03
    provides: Versioned campaign SavedData and exact durable desk, facing, retry, encounter, and Professor identity
  - phase: 02-persistent-lecture-vertical-slice
    plan: 04
    provides: Closed START event, pure reducer, and persist-before-effect CampaignService seam
  - phase: 02-persistent-lecture-vertical-slice
    plan: 05
    provides: Bounded runtime lifecycle ownership and state-first cleanup
  - phase: 02-persistent-lecture-vertical-slice
    plan: 16
    provides: Command-free survival Contract interaction and retained encounter tracer
provides:
  - Exact lectern-relative 17x17 boundary, 15x15 interior, act geometry, and deterministic radius-five retry order
  - One bounded server-side ArenaValidator with typed localized rejection families and spawn-capacity preflight
  - State-first Contract acceptance that consumes the item and spawns the matching Professor only after durable START
  - Padded GameTests proving every rejection is a five-surface no-op and a valid start preserves blocks and vanilla respawn data
affects: [phase-2-retake, phase-2-lecture-acts, phase-2-verification, contract-interaction]

actuals:
  tokens: 16961
  tasks: 2
  commits: 6

tech-stack:
  added: []
  patterns: [immutable accepted geometry, bounded read-only world adapter, typed localized rejection, persist-before-materialize transaction]

key-files:
  created:
    - src/main/java/dev/developershell/lecture/LectureGeometry.java
    - src/main/java/dev/developershell/lecture/ArenaRejection.java
    - src/main/java/dev/developershell/lecture/ArenaValidationResult.java
    - src/main/java/dev/developershell/lecture/ArenaValidator.java
    - src/test/java/dev/developershell/lecture/LectureGeometryTest.java
    - src/gametest/java/dev/developershell/gametest/ContractArenaGameTests.java
  modified:
    - src/main/java/dev/developershell/item/CursedInternshipContractItem.java
    - src/main/java/dev/developershell/campaign/CampaignService.java
    - src/main/java/dev/developershell/server/DevelopersHellRuntime.java
    - src/main/resources/assets/developers_hell/lang/en_us.json
    - src/gametest/resources/fabric.mod.json

key-decisions:
  - "Treat only the 15x15 combat interior as requiring four passable headroom blocks; the one-block 17x17 margin still requires solid, loaded, border-safe support."
  - "Validate a Contract once, then pass immutable ArenaValidationResult.Accepted geometry through the runtime adapter into CampaignService so persistence cannot disagree with preflight."
  - "Scan a duplicate-free behind-and-beside Chebyshev wedge from radius two through five, beginning exactly at L-2F, for the mod-owned retry feet position."
  - "Require entity-type availability, world-border containment, block collision clearance, and an empty entity box before committing the Professor spawn."

patterns-established:
  - "Accepted-geometry token: bounded validation produces immutable coordinates consumed unchanged by the state transaction."
  - "Five-surface rejection proof: inventory, campaign bytes, entity/presentation counts, selected blocks, and vanilla respawn state are snapshotted around every invalid use."
  - "State-first start: durable START and dirty SavedData precede signed feedback, Professor materialization, presentation, and Contract consumption."

requirements-completed: [FND-07, CAMP-01, CAMP-02, LECT-01]

coverage:
  - id: D1
    description: "Exact arena boundary, combat interior, headroom levels, act lanes/pads/quadrants, facing transforms, and retry order are frozen as pure immutable coordinates."
    requirement: FND-07
    verification:
      - kind: unit
        ref: "LectureGeometryTest (all four facings, coverage/non-overlap, exact retry wedge)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Wrong target, dimension, loaded/border, floor, headroom, retry, active encounter, and spawn-capacity rejections send one stable localized reason without mutating five observed surfaces."
    requirement: CAMP-02
    verification:
      - kind: e2e
        ref: "ContractArenaGameTests rejection matrix"
        status: pass
    human_judgment: false
  - id: D3
    description: "A valid Contract start persists the exact desk, facing, retry point, combat origin, encounter, and Professor identity before spawn and one-item consumption while preserving blocks and vanilla respawn."
    requirement: CAMP-01
    verification:
      - kind: e2e
        ref: "ContractArenaGameTests#validStartCommitsExactGeometryBeforeEffects"
        status: pass
    human_judgment: false
  - id: D4
    description: "The frozen layout exposes the three contiguous Slide Deck lanes, deterministic Surprise Quiz pad anchors/shapes, and four Attendance Check quadrants used by the lecture acts."
    requirement: LECT-01
    verification:
      - kind: unit
        ref: "LectureGeometryTest act-geometry assertions"
        status: pass
    human_judgment: false

duration: 27min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 06: Exact Arena and Atomic Contract Start Summary

**A single bounded validator now turns exact lectern-relative geometry into an immutable accepted start, with actionable atomic rejections and state-first Professor materialization proven in a real 26.2 server.**

## Performance

- **Duration:** 27 min
- **Started:** 2026-08-26T19:31:44Z
- **Completed:** 2026-08-26T19:58:25Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Froze all four facing transforms for the exact 17x17 support boundary, 15x15 four-block-headroom interior, combat center, three lanes, quiz pads, attendance quadrants, and a deterministic 44-position retry wedge through radius five.
- Replaced the tracer's duplicated service-side arena logic with one bounded `ArenaValidator` that checks the real lectern, exact Overworld, already-loaded/world-border-safe coordinates, support/headroom, active ownership, retry safety, and Professor spawn capacity without mutation or chunk generation.
- Made Contract use return one stable localized rejection or pass immutable accepted geometry into the durable START transaction; valid state is dirty before feedback/spawn/presentation and the Contract is consumed last.
- Added padded, explicit-owner GameTests for every rejection family and a valid start, including exact campaign, entity/presentation, 17x17 block, inventory, and vanilla respawn snapshots.

## Requirements (Copied Verbatim)

- **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.
- **CAMP-01**: A new player can discover, craft, and use the Cursed Unpaid Internship Contract without consulting an external wiki or using an admin command.
- **CAMP-02**: Starting the Contract validates a player-selected overworld arena, creates a nearby retry checkpoint, and leaves blocks undamaged by default.
- **LECT-01**: The player can defeat Professor Infinite Slides by learning three readable acts: Slide Deck safe lanes, deterministic Surprise Quiz pads, and Attendance Check positioning.

## Task Commits

Each TDD task was committed at its RED and GREEN gates:

1. **RED — Specify exact arena and act geometry** - `a2cda99` (test)
2. **RED refinement — Cover the complete duplicate-free retry wedge** - `9d79fd1` (test)
3. **GREEN — Freeze lecture geometry and typed validation results** - `a8e075b` (feat)
4. **RED — Specify atomic Contract acceptance and rejection** - `9139bd9` (test)
5. **RED refinement — Require signed/objective/slide presentation copy** - `db803d6` (test)
6. **GREEN — Validate once and make Contract starts state-first** - `fe29401` (feat)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/lecture/LectureGeometry.java` - Immutable local axes, boundary/interior positions, act geometry, and retry ordering.
- `src/main/java/dev/developershell/lecture/ArenaRejection.java` - Closed rejection families with stable translation keys.
- `src/main/java/dev/developershell/lecture/ArenaValidationResult.java` - Immutable accepted layout/retry token or typed rejection.
- `src/main/java/dev/developershell/lecture/ArenaValidator.java` - Pure bounded contract plus logical-server read-only adapter and spawn-capacity preflight.
- `src/main/java/dev/developershell/item/CursedInternshipContractItem.java` - Server-side validate-once interaction, precise feedback, and accepted-start submission.
- `src/main/java/dev/developershell/campaign/CampaignService.java` - Accepted-geometry START transaction, removed duplicate geometry probes, state-first materialization, and retained thin compatibility adapter.
- `src/main/java/dev/developershell/server/DevelopersHellRuntime.java` - Typed accepted/rejected Contract service boundary.
- `src/main/resources/assets/developers_hell/lang/en_us.json` - Exact placement feedback, correction copy, and successful Contract signature message.
- `src/test/java/dev/developershell/lecture/LectureGeometryTest.java` - Exact pure coordinate/range/non-overlap/retry assertions across four facings.
- `src/gametest/java/dev/developershell/gametest/ContractArenaGameTests.java` - Padded invalid/valid real-world transaction matrix with five-surface snapshots.
- `src/gametest/resources/fabric.mod.json` - Exact GameTest entrypoint discovery.

## Decisions Made

- Required four-block headroom only over the 15x15 combat interior, matching the UI contract; the surrounding one-block margin remains part of the solid, loaded, world-border-safe 17x17 boundary.
- Defined retry order as a deterministic behind-and-beside Chebyshev wedge: start at `L-2F`, sweep each shell without duplicates, and stop after radius five.
- Kept `ArenaValidator` as the only geometry/world preflight. Contract use and the retained server-side compatibility entry both delegate to it, then pass its immutable `Accepted` value into the transaction overload.
- Counted existing entities in the Professor spawn AABB in addition to type availability, border, and block collision so a nominally spawnable but occupied origin rejects before durable state changes.
- Preserved the existing reducer/service contract: accepted START is persisted and marked dirty before materialized effects; known rejection paths never dispatch, consume, spawn, edit blocks, or touch vanilla respawn.

## Automated Evidence

- Pinned Temurin `25.0.4+7`, Loom `1.17.19`, and offline Gradle gate `clean test --tests LectureGeometryTest runGameTest auditDirectDependencies build` - PASS.
- Fresh persistent GameTest world - `16 GAME TESTS COMPLETE`; `All 16 required tests passed` - PASS.
- Focused `compileJava`, `compileGametest`, and `LectureGeometryTest` gate - PASS.
- Direct dependency audit retained the approved five declarations, Loom `1.17.19`, injection baseline `145`, and SHA-256 baseline `a3fef1ae...` - PASS.
- Rejection snapshots cover Contract count, serialized campaign state, mod Professor/runtime/presentation counts, every observed 17x17 block, player RespawnConfig, and world RespawnData - PASS.
- Valid-start proof observes exact saved desk/facing/retry/combat origin and dirty state, one matching Professor/runtime, ordered signed/objective/slide messages, unchanged arena blocks, and unchanged vanilla respawn - PASS.
- Stub/skip scan, no tracked deletions, no client imports in common code, no forced chunk load, no block/respawn setter, and no runtime network access in changed production files - PASS.
- No visible client was launched, no `dist` artifact was published or replaced, and no human visual UAT is claimed.

## Threat Mitigations

- **T-02-ARENA-01:** Every selected coordinate is derived from the server-observed lectern/facing and read before the START event; invalid paths are proven no-ops across five surfaces.
- **T-02-ARENA-02:** Boundary, headroom, retry, runtime, and spawn checks are fixed-size bounded scans, use `isLoaded`, and never generate chunks or perform a global entity/world search.
- **T-02-ARENA-03:** Client target authority stops at the interaction; the logical server re-reads the lectern, derives all axes and spawn coordinates, and submits only the immutable server-accepted layout.
- No new endpoint, dependency, file-access path, schema boundary, client authority, runtime network, or unplanned trust boundary was introduced.

## TDD Gate Compliance

- Task 1 RED commits `a2cda99` and `9d79fd1` established the missing geometry/result contract and complete retry order before GREEN commit `a8e075b`; the focused Java 25 test gate then passed all exact-facing assertions.
- Task 2 RED commits `9139bd9` and `db803d6` started from `clean`: retained tests and wrong-target atomicity passed while eight new acceptance/rejection cases failed before production wiring. GREEN commit `fe29401` made the same persistent suite pass all 16 required tests.
- No separate refactor commit was needed after either GREEN gate.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Routed immutable accepted geometry through the existing transaction boundary**
- **Found during:** Task 2 design/implementation
- **Issue:** The plan listed Contract and validator files, but `CampaignService` still contained an independent, mismatched arena scan and `DevelopersHellRuntime` still accepted raw coordinates. Leaving them unchanged would validate twice, reject the specified safety-margin geometry, and let persistence select a different retry point.
- **Fix:** With the orchestrator's bounded ownership approval, removed duplicated service geometry, changed the runtime/service seam to consume `ArenaValidationResult.Accepted`, and retained all prior reducer/lifecycle ordering contracts.
- **Files modified:** `src/main/java/dev/developershell/campaign/CampaignService.java`, `src/main/java/dev/developershell/server/DevelopersHellRuntime.java`, `src/main/java/dev/developershell/item/CursedInternshipContractItem.java`
- **Verification:** Exact Task 2 full gate; all 16 GameTests pass, including valid safety-margin headroom, exact persisted retry, state dirty before effects, and every atomic rejection.
- **Committed in:** `fe29401`

**2. [Rule 3 - Blocking] Preserved the retained lifecycle caller without restoring duplicate validation**
- **Found during:** Task 2 GREEN compile
- **Issue:** `LectureLifecycleGameTests` still invoked the prior `CampaignService.start(player, desk, facing, contract)` signature, so `compileGametestJava` failed after the typed transaction seam replaced it.
- **Fix:** Kept the legacy server-side signature as a thin compatibility adapter that calls the sole `ArenaValidator` once and forwards only its immutable accepted value to the transaction overload.
- **Files modified:** `src/main/java/dev/developershell/campaign/CampaignService.java`
- **Verification:** `compileGametestJava` passed and all retained lifecycle plus new Contract GameTests passed in the clean 16-test run.
- **Committed in:** `fe29401`

**3. [Rule 1 - Bug] Included occupied entities in Professor spawn capacity**
- **Found during:** Task 2 GREEN GameTest attempt 1
- **Issue:** Minecraft 26.2 `noCollision(AABB)` admitted the living Vindicator fixture, so block collision alone allowed an overlapping Professor and the spawn-capacity test was the only failure (15/16 passed).
- **Fix:** Added one bounded entity query over the exact Professor spawn AABB and reject when occupied, before START persistence.
- **Files modified:** `src/main/java/dev/developershell/lecture/ArenaValidator.java`
- **Verification:** The next clean run and the final full gate both passed all 16 required GameTests.
- **Committed in:** `fe29401`

---

**Total deviations:** 3 auto-fixed (1 missing critical, 1 blocking compatibility issue, 1 runtime bug).
**Impact on plan:** The bounded expansion was necessary to enforce one validator and atomic state-first starts; it added no new feature, dependency, schema, client surface, or runtime service. Broken-window entries 7-9 are resolved.

## Issues Encountered

- The initial Task 2 GREEN compile exposed the retained service signature described in deviation 2; the thin validator adapter restored compatibility.
- The first clean GREEN GameTest run passed 15/16 and isolated entity occupancy as the only missing spawn preflight; the corrected run and final full gate passed 16/16.
- Clean GameTest startup emitted the existing warnings for absent transient `server.properties`, `eula.txt`, and client-resource output, then loaded normally and completed successfully.

## User Setup Required

None - arena validation and Contract starts are local, offline, and require no account, credential, external service, runtime network, or client launch.

## Next Phase Readiness

- Plan 02-07 and Retake work can reuse the same immutable layout/retry contract and typed rejection keys instead of recomputing geometry.
- Lecture act implementations can consume frozen lanes, pad anchors/shapes, attendance quadrants, and combat center for deterministic telegraphs and damage admission.
- No open broken-window entry, stub, skipped test, unrun verification, or implementation blocker remains; human visual UAT and later distribution remain explicitly outside this plan.

## Self-Check: PASSED

- All eleven declared implementation/test/resource files and this canonical summary exist on disk.
- RED commits `a2cda99`/`9d79fd1`/`9139bd9`/`db803d6` and GREEN commits `a8e075b`/`fe29401` exist in repository history with no tracked-file deletion.
- Requirement coverage, actuals, TDD compliance, threat evidence, verification results, `status: complete`, and three resolved deviation-ledger entries are present.
- No stub, TODO, FIXME, skipped test, unrun verification, unknown coverage, open broken-window entry, or deferred blocker remains.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-26*
