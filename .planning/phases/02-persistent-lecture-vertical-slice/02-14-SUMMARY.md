---
phase: 02-persistent-lecture-vertical-slice
plan: 14
subsystem: campaign-stable-interfaces
tags: [fabric-26.2, saved-data, entity-persistence, stable-registries, exact-once-rewards]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 01
    provides: Contract-to-reward tracer with state-before-effects transactions and owner-plus-encounter idempotency
provides:
  - Immutable schema-v1 player campaign state with explicit lecture, checkpoint, encounter, entitlement, Retake, and Remote fields
  - Stable unconditional Phase 2 item-key catalog including the Retake Form
  - Registered no-loot Professor Infinite Slides runtime type with persisted owner and encounter identity
  - Saved-state and live-runtime damage admission checks that preserve the tracer's exactly-once reward path
affects: [phase-2-persistence-expansion, lecture-combat, campaign-rewards, retake-flow, remote-cooldown]

actuals:
  tokens: 9213
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns: [immutable durable records, explicit stable serialized names, zero-state compatibility views, state-before-effects transactions]

key-files:
  created:
    - src/main/java/dev/developershell/campaign/PlayerCampaignState.java
    - src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java
  modified:
    - src/main/java/dev/developershell/campaign/CampaignSavedData.java
    - src/main/java/dev/developershell/campaign/CampaignService.java
    - src/main/java/dev/developershell/registry/ModEntities.java
    - src/main/java/dev/developershell/registry/ModItemIds.java
    - src/main/java/dev/developershell/registry/ModItems.java

key-decisions:
  - "Make PlayerCampaignState and ProfessorInfiniteSlidesEntity the only state-owning final types while retaining narrow zero-state compatibility views for the already-committed tracer manager."
  - "Keep schema version 1 and every established persisted key, add new Phase 2 fields as optional compatible fields, and reject UUID map-key mismatches through a read-only state."
  - "Validate attacker, runtime participant, saved owner, and active encounter before Professor damage, while leaving reward authority solely in CampaignService."
  - "Retain the foundation-only ModItemIds.all compatibility view and expose the complete unconditional five-item catalog through ModItemIds.phaseTwo."

patterns-established:
  - "Durable identity: explicit owner UUID plus active encounter UUID plus Professor UUID, with stable serialized enum names independent of Java enum spelling."
  - "Entity boundary: the registered final Professor type persists combat identity but owns no campaign reward ledger or loot path."
  - "Schema evolution: optional codec fields preserve schema-v1 tracer saves; encoded owner and UUID map key must agree before writes are accepted."

requirements-completed: [FND-06, FND-07, LECT-01, LECT-02]

coverage:
  - id: D1
    description: "Schema-v1 campaign state carries the full owner, chapter, lecture, checkpoint, attempt, active encounter, entitlement, Retake, reward-ledger, and Remote-deadline contract without weakening existing saves."
    requirement: FND-06
    verification:
      - kind: integration
        ref: "Pinned offline compile plus Contract-to-reward Fabric GameTest"
        status: pass
    human_judgment: false
  - id: D2
    description: "The registered Professor persists owner and encounter UUIDs and admits damage only when attacker, live runtime, and saved active encounter all agree."
    requirement: LECT-01
    verification:
      - kind: e2e
        ref: "FoundationGameTests wrong-owner damage and matching encounter assertions"
        status: pass
    human_judgment: false
  - id: D3
    description: "Duplicate and competing starts, matching victory, stale or wrong-owner victory, and replayed victory preserve one active attempt and one first-reward ledger."
    requirement: LECT-02
    verification:
      - kind: e2e
        ref: "Fabric runGameTest: all 3 required tests passed"
        status: pass
    human_judgment: false
  - id: D4
    description: "The exact checksum-pinned Java 25 offline build compiles both final types, runs the transformed lifecycle suite, and packages an ordinary JAR without GameTest leakage."
    requirement: FND-07
    verification:
      - kind: integration
        ref: "gradlew test compileJava runGameTest jar --offline plus archive inspection"
        status: pass
    human_judgment: false

duration: 14min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 14: Stable Campaign and Professor Interfaces Summary

**The green Contract-to-reward tracer now rests on immutable campaign state, unconditional stable item keys, and a registered Professor type whose persisted owner/encounter identity gates damage without gaining reward authority.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-08-26T17:13:34Z
- **Completed:** 2026-08-26T17:27:17Z
- **Tasks:** 1
- **Files modified:** 7

## Accomplishments

- Extracted all schema-v1 player values into a validated immutable `PlayerCampaignState`, including explicit chapter/lecture names, desk and retry identity, active encounter reference, reward ledgers, Retake representation, and Remote deadline.
- Kept every established registry and persistence name intact while adding the unconditional `retake_form` key and routing the registered Professor factory to `ProfessorInfiniteSlidesEntity`.
- Moved owner/encounter persistence and combat callbacks into the final no-loot Professor type while keeping the encounter manager as the bounded ephemeral runtime.
- Preserved state-before-effects start and victory commits, then strengthened Professor damage admission across attacker, runtime participant, writable saved state, owner, and encounter identity.

## Requirements (Copied Verbatim)

- **FND-06**: Versioned campaign, encounter, reward, and module state survives save, quit, reload, death, and chunk unload without duplication or regression.
- **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.
- **LECT-01**: The player can defeat Professor Infinite Slides by learning three readable acts: Slide Deck safe lanes, deterministic Surprise Quiz pads, and Attendance Check positioning.
- **LECT-02**: Defeating Professor Infinite Slides grants the Attendance Sheet and a cooldown-limited Infinite Slides Remote after a fight that supports failure, cleanup, retry, and mid-campaign persistence.

## Task Commits

1. **Task 1: Freeze stable schema and entity interfaces without changing the tracer path** - `b51f12b` (refactor)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/campaign/PlayerCampaignState.java` - Defines immutable authoritative campaign progress and active encounter identity with explicit stable serialized names.
- `src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java` - Persists owner/encounter UUIDs, gates combat, and routes accepted death to the state-first victory service.
- `src/main/java/dev/developershell/campaign/CampaignSavedData.java` - Encodes the final schema-v1 record, validates encoded owners against UUID map keys, and preserves compatible tracer reads.
- `src/main/java/dev/developershell/campaign/CampaignService.java` - Remains the sole save-then-effect facade and now validates authoritative damage identity.
- `src/main/java/dev/developershell/registry/ModEntities.java` - Registers the final Professor runtime under the unchanged no-loot entity key.
- `src/main/java/dev/developershell/registry/ModItemIds.java` - Centralizes the unchanged Contract, Sheet, Remote, and Foundation keys plus the stable Retake Form key.
- `src/main/java/dev/developershell/registry/ModItems.java` - Registers every Phase 2 item unconditionally from the centralized key catalog.

## Decisions Made

- Used explicit serialized strings for campaign chapter and lecture status so persistence remains stable if Java enum identifiers are ever refactored.
- Treated UUID map-key disagreement and unsupported schema versions as read-only data rather than silently replacing or mutating them.
- Kept `ProfessorInfiniteSlidesEntity` as the final state-owning runtime type and reduced the old nested Professor base to a zero-state compatibility seam required by the already-committed encounter manager.
- Preserved the legacy foundation-only `ModItemIds.all()` contract for its committed test consumer and added `phaseTwo()` as the complete immutable catalog; actual registrations remain unconditional under every module snapshot.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Reset retained generated GameTest world state before deterministic verification**
- **Found during:** Task 1 baseline characterization gate
- **Issue:** The fixed test UUID's prior `PASSED` campaign data remained under the generated `build/run/gameTest/world`, so the correct idempotency guard rejected a new first Contract start.
- **Fix:** Ran the pinned Gradle `clean` lifecycle task before both the baseline and final exact gates; no tracked source or known-good distribution artifact was removed.
- **Files modified:** None (generated build state only)
- **Verification:** A fresh generated world completed all three required Fabric GameTests before and after extraction.
- **Committed in:** Not applicable (verification environment only)

---

**Total deviations:** 1 auto-fixed (1 Rule 3).
**Impact on plan:** The reset restored the deterministic test precondition without changing production behavior, weakening idempotency, or touching the retained distribution JAR.

## TDD Gate Compliance

- The plan's duplicate-start, competing-start, matching/stale/wrong-owner victory, and replay assertions already existed in the committed Plan 01 GameTest suite before this behavior-preserving extraction.
- Exact file ownership excluded the GameTest source from Plan 14, so the committed suite was used as a green characterization gate before and after refactoring rather than manufacturing a false RED commit.
- No assertion was removed, weakened, skipped, or marked pending; all three transformed tests passed on the final fresh-world gate.

## Issues Encountered

- The Fabric GameTest launcher printed expected first-run messages for absent transient `server.properties` and `eula.txt`, then created its isolated server and completed normally.
- Context7 and its CLI fallback were unavailable in this executor environment; exact Minecraft 26.2 signatures were validated against the pinned offline compile and transformed runtime instead of inferred from a different version.

## Verification

- Eclipse Temurin `25.0.4+7` and `javac 25.0.4` from `.work/toolchain/temurin-25.0.4+7-x64` - PASS.
- Clean baseline characterization gate with the committed Plan 01 suite - PASS; all three required Fabric GameTests completed.
- Final plan command `test compileJava runGameTest jar --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle` after a pinned Gradle `clean` - PASS.
- Required test-source patterns `contractStartsSlideWindowAndCommitsFirstReward`, duplicate, competing, and replay - PASS.
- Source contract scan for exact registry names, persisted keys, UUID map-key rejection, state-before-effects ordering, common/client separation, offline operation, and placeholder markers - PASS.
- Fresh `build/libs/developers-hell-0.1.0.jar` SHA-256 `f099bad6e811f19432a2eae87a099be14476b4a6856d3af3cded4a03f1907138` contains both final classes and excludes GameTest classes - PASS.
- Retained `dist/developers-hell-0.1.0.jar` remains 10,206 bytes with SHA-256 `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8` - PASS.
- No visible Minecraft client was launched; no dependency, network/API integration, secret, telemetry, database, or runtime download was added.

## User Setup Required

None - this extraction adds no external service, account, credential, dependency, or manual setup.

## Next Phase Readiness

- Later Phase 2 plans can expand retry/drop behavior, codecs, encounter acts, resources, and cooldown handling against final stable state, item, and entity identities.
- The real Plan 01 tracer remains the integration oracle, including its owner/encounter checks and exactly-once reward ledger.
- The known-good distribution JAR remains untouched; only the ordinary candidate under `build/libs` was regenerated.

## Self-Check: PASSED

- All seven declared production files and the canonical `02-14-SUMMARY.md` exist.
- Task commit `b51f12b` exists in repository history and contains no tracked-file deletion.
- The summary contains all four declared requirement IDs, exact verification hashes, coverage metadata, decisions, and the substantive result.
- The final exact offline gate passed after the last production edit, and the retained distribution hash remains unchanged.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-26*
