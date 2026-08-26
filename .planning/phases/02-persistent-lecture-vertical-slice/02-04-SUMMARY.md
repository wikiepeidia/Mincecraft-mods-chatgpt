---
phase: 02-persistent-lecture-vertical-slice
plan: 04
subsystem: campaign-persistence
tags: [fabric-26.2, saved-data, codec, reducer, replay-safety, tdd]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 01
    provides: Lecture tracer state, service, encounter manager, and GameTest lifecycle
  - phase: 02-persistent-lecture-vertical-slice
    plan: 14
    provides: Frozen schema-1 identities, PlayerCampaignState, and state-before-effect boundary
provides:
  - Closed owner-scoped campaign events and immutable accepted/no-op transitions
  - Pure monotonic reducer for starts, terminals, reloads, victory, rewards, retry fallback, and cooldowns
  - Complete schema-1 codec with explicit read-only preservation of future or corrupt documents
  - Sole persist-dirty-effect CampaignService boundary
affects: [lecture-lifecycle, retake-reconciliation, reward-recovery, remote-cooldown, save-reload]

actuals:
  tokens: 21125
  tasks: 2
  commits: 5

tech-stack:
  added: []
  patterns: [pure reducer with immutable intents, passthrough read-only codec fallback, persist-before-effect facade]

key-files:
  created:
    - src/main/java/dev/developershell/campaign/CampaignEvent.java
    - src/main/java/dev/developershell/campaign/CampaignTransition.java
    - src/main/java/dev/developershell/campaign/CampaignReducer.java
    - src/test/java/dev/developershell/campaign/CampaignReducerTest.java
    - src/test/java/dev/developershell/campaign/CampaignCodecTest.java
  modified:
    - src/main/java/dev/developershell/campaign/PlayerCampaignState.java
    - src/main/java/dev/developershell/campaign/CampaignSavedData.java
    - src/main/java/dev/developershell/campaign/CampaignService.java

key-decisions:
  - "Decode strict schema 1 first, then retain any future or malformed document as an explicit read-only Dynamic so SavedDataStorage cannot replace it with defaults."
  - "Compose two flat MapCodec groups to preserve every established schema-1 field name beyond RecordCodecBuilder's 16-field limit."
  - "Treat failed encounter materialization as a committed ABORT terminal transition so attempt counters never roll back."
  - "Dispatch every immutable effect only after CampaignSavedData replacement and setDirty through CampaignService."

patterns-established:
  - "Reducer boundary: one closed event produces an immutable accepted/no-op transition without Minecraft runtime, filesystem, network, clock, or random access."
  - "Persistence boundary: supported schema is mutable; future, malformed, or owner-inconsistent data is visible but read-only."
  - "Effect boundary: accepted state replacement and dirty marking precede every inventory, entity, or presentation effect."

requirements-completed: [FND-06, FND-07, LECT-02]

coverage:
  - id: D1
    description: "All Phase 2 durable inputs reduce through owner/encounter guards into exact monotonic accepted or no-op transitions."
    requirement: FND-06
    verification:
      - kind: unit
        ref: "src/test/java/dev/developershell/campaign/CampaignReducerTest.java (8 deterministic matrix tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Schema 1 round-trips every durable field while malformed, mismatched-owner, and future documents remain explicit read-only data."
    requirement: FND-06
    verification:
      - kind: unit
        ref: "src/test/java/dev/developershell/campaign/CampaignCodecTest.java#schemaOneRoundTripsEveryDurableFieldWithStableNames"
        status: pass
      - kind: unit
        ref: "src/test/java/dev/developershell/campaign/CampaignCodecTest.java malformed, map-key, and future-schema cases"
        status: pass
    human_judgment: false
  - id: D3
    description: "CampaignService commits and marks accepted state dirty before effects, while rejected and replayed events neither write nor dispatch."
    requirement: LECT-02
    verification:
      - kind: unit
        ref: "src/test/java/dev/developershell/campaign/CampaignCodecTest.java#serviceCommitsBeforeEffectsAndNoOpWritesNothing"
        status: pass
      - kind: integration
        ref: "Pinned Temurin 25 offline runGameTest: all 3 retained lecture tracer tests passed"
        status: pass
    human_judgment: false
  - id: D4
    description: "The pure reducer, codec, dependency tuple, and retained real encounter lifecycle pass the automated release gates."
    requirement: FND-07
    verification:
      - kind: integration
        ref: "gradlew.bat clean test compileJava runGameTest auditDirectDependencies jar --offline"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 04: Monotonic Reducer and Schema-v1 Persistence Summary

**A pure replay-safe campaign reducer now feeds a versioned SavedData codec whose single service facade commits durable state before bounded Minecraft effects.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-08-26T18:18:15Z
- **Completed:** 2026-08-26T18:43:17Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added a sealed campaign event family, immutable transition/effect model, and pure reducer covering START, eight terminal reasons, reload normalization, victory, Retake fallback, Sheet recovery, Remote cooldown, and ready notice.
- Extended schema 1 with monotonic recovery/notice ledgers and complete stable-name round trips while preserving future or corrupt source documents as explicit read-only values.
- Consolidated accepted writes behind CampaignService so replacement and `setDirty()` always precede effects; replays, mismatches, occupied desks, and incompatible data perform no write or effect.
- Proved the seam with 14 focused deterministic campaign tests, the full 37-test unit suite, and all 3 retained server GameTests.

## Task Commits

Each TDD gate was committed atomically:

1. **Task 1 RED: Specify every monotonic event and replay rule** - `9ec3b4f` (test)
2. **Task 1 GREEN: Implement the closed event family and pure reducer** - `1f64660` (feat)
3. **Task 2 RED: Specify codec, read-only, normalization, and commit-order behavior** - `679a7fc` (test)
4. **Task 2 GREEN: Implement versioned persistence and the sole stateful facade** - `97c6f62` (feat)

The summary and sequential tracking state are recorded in the plan closeout commit.

## Files Created/Modified

- `src/main/java/dev/developershell/campaign/PlayerCampaignState.java` - Adds durable Sheet recovery and Remote-ready replay markers while retaining the frozen constructor seam.
- `src/main/java/dev/developershell/campaign/CampaignEvent.java` - Closed, validated durable input family with stable terminal/fallback names.
- `src/main/java/dev/developershell/campaign/CampaignTransition.java` - Immutable accepted/no-op state plus bounded effect intents.
- `src/main/java/dev/developershell/campaign/CampaignReducer.java` - Pure owner/encounter-guarded monotonic transition matrix.
- `src/main/java/dev/developershell/campaign/CampaignSavedData.java` - Flat schema-1 codec, Overworld storage, owner-key validation, and retained read-only raw documents.
- `src/main/java/dev/developershell/campaign/CampaignService.java` - Sole reduce-replace-dirty-effect adapter and preserved tracer start/victory entrypoints.
- `src/test/java/dev/developershell/campaign/CampaignReducerTest.java` - Deterministic event, replay, mismatch, and monotonicity matrix.
- `src/test/java/dev/developershell/campaign/CampaignCodecTest.java` - Round-trip, malformed/future, owner-key, reload, and effect-order proof.

## Evidence

- Task 1 gate passed `CampaignReducerTest` plus `compileJava` on checksum-pinned Temurin 25.0.4+7 and Loom 1.17.19 in offline mode.
- Task 2 gate passed all `dev.developershell.campaign.*` tests, `compileJava`, and `auditDirectDependencies`; direct Minecraft/Fabric pins remained unchanged.
- Full clean regression passed 37 unit tests, all 3 required Fabric server GameTests, `compileJava`, `auditDirectDependencies`, and `jar` offline.
- The full regression launched only the headless GameTest server; no visible client ran.
- `dist/developers-hell-0.1.0.jar` remained unchanged at SHA-256 `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8` (10,206 bytes).
- Stub and prohibited runtime-network scans found no TODO/FIXME/placeholder path, HTTP client, OpenAI, telemetry, analytics, wall-clock, or unseeded randomness surface in the changed campaign code.

## Requirements Delivered

- **FND-06:** Versioned campaign, encounter, reward, fallback, recovery, and cooldown state survives serialization and rejects replay/regression.
- **FND-07:** Deterministic unit coverage and the retained real lecture encounter GameTests both pass.
- **LECT-02:** Victory rewards and failure/retry state flow through a persistent, first-only, cleanup-capable transition seam.

## Decisions Made

- Used `Codec.either(strictSchemaOne, Codec.PASSTHROUGH)` so unsupported or malformed documents decode visibly without a default overwrite and re-encode from the retained `Dynamic`.
- Grouped identity and durable `MapCodec` fields while leaving the persisted JSON/NBT field layout flat and stable.
- Converted failed encounter materialization into an ABORT transition instead of restoring a previous attempt, preserving the monotonic attempt ledger.
- Kept timer counters, boss bars, particles, players, levels, and other runtime objects out of SavedData.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Preserved mismatched map-key records for explicit read-only inspection**

- **Found during:** Task 2 GREEN (`mapKeyMismatchIsVisibleAndReadOnly`)
- **Issue:** A short-circuit owner mismatch check skipped insertion into the decoded map, so the incompatible record was marked read-only but not inspectable by its persisted key.
- **Fix:** Inserted first, recorded duplicate status separately, then combined duplicate and owner-mismatch validation without dropping the decoded record.
- **Files modified:** `src/main/java/dev/developershell/campaign/CampaignSavedData.java`
- **Verification:** The focused codec test passed on the next run, followed by the complete campaign and full regression gates.
- **Committed in:** `97c6f62`

---

**Total deviations:** 1 auto-fixed (1 Rule 1 bug)
**Impact on plan:** The fix completes the planned tamper-safe inspection contract with no scope expansion; `.planning/WINDOWS.md` entry 4 is resolved.

## Issues Encountered

- The first Task 2 GREEN run compiled successfully but exposed the map-key short-circuit bug above; the focused second run passed all six codec/service tests.
- Context7 was unavailable in this executor environment, so exact 26.2 signatures were verified against the pinned local Minecraft/Fabric classpath and compilation gate.

## User Setup Required

None - no external service, account, credential, network access, or client launch is required.

## Next Phase Readiness

- Lifecycle, retry, reward-recovery, and Remote adapters can submit closed events through `CampaignService.apply` without direct progression mutation.
- Future/corrupt saves remain intact and read-only for explicit migration or operator recovery rather than being silently reset.
- The original lecture tracer remains green end to end, and there are no open broken-windows entries or blockers from this plan.

## Self-Check: PASSED

- All eight declared implementation/test files and this summary exist on disk.
- Task commits `9ec3b4f`, `1f64660`, `679a7fc`, and `97c6f62` exist in repository history.
- Required status, actuals, coverage, verification evidence, requirement IDs, and resolved deviation metadata are present.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-26*
