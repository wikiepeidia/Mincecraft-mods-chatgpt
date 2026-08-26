---
phase: 02-persistent-lecture-vertical-slice
plan: 01
subsystem: campaign-lecture-tracer
tags: [fabric-26.2, saved-data, gametest, server-boss-event, exact-once-rewards]

requires:
  - phase: 01-java-25-and-fabric-26-2-foundation
    provides: Checksum-pinned Java 25/Fabric 26.2 build, split source sets, stable registry bootstrap, and transformed GameTest runner
provides:
  - Schema-v1 Overworld campaign SavedData with atomic owner/encounter start and victory commits
  - Stable Contract, Attendance Sheet, Infinite Slides Remote, and Professor Infinite Slides identities
  - Owner-scoped Professor runtime with a five-second Slide Deck cue, vulnerability window, boss bar, and no entity loot
  - Real survival-player Contract-to-first-reward GameTest covering duplicate, competing, spoofed, stale, and replayed events
affects: [phase-2-persistence-expansion, lecture-combat, campaign-rewards, client-rendering, phase-6-release]

actuals:
  tokens: 12164
  tasks: 1
  commits: 3

tech-stack:
  added: []
  patterns: [state-before-effects transactions, owner-plus-encounter idempotency, ephemeral runtime over durable SavedData, Fabric pre-block interaction]

key-files:
  created:
    - src/main/java/dev/developershell/campaign/CampaignSavedData.java
    - src/main/java/dev/developershell/campaign/CampaignService.java
    - src/main/java/dev/developershell/item/CursedInternshipContractItem.java
    - src/main/java/dev/developershell/lecture/LectureEncounterManager.java
    - src/main/java/dev/developershell/registry/ModEntities.java
  modified:
    - src/main/java/dev/developershell/DevelopersHell.java
    - src/main/java/dev/developershell/registry/ModItems.java
    - src/gametest/java/dev/developershell/gametest/FoundationGameTests.java

key-decisions:
  - "Use Fabric UseBlockCallback as the normal Contract-to-lectern entrypoint because Minecraft 26.2 consumes empty-lectern handling before Item.useOn."
  - "Decode unsupported schema numbers into read-only campaign state so computeIfAbsent cannot replace future data after a Codec error."
  - "Match both owner UUID and active encounter UUID, and dirty the ledger before spawning, shrinking, cleanup, boss-bar changes, or inventory grants."
  - "Keep the Professor as a no-loot Vindicator-derived server identity while the encounter manager owns the bounded runtime and owner-only ServerBossEvent."

patterns-established:
  - "Campaign transaction: validate request, atomically accept durable state, setDirty, then materialize bounded effects."
  - "Runtime identity: one owner plus one encounter UUID plus one Professor UUID; stale, wrong-owner, duplicate, and replayed callbacks are complete no-ops."
  - "GameTest player: use a connected fixed-UUID survival ServerPlayer so boss-bar and ownership behavior exercise the transformed production path."

requirements-completed: [FND-06, FND-07, CAMP-02, LECT-01, LECT-02]

coverage:
  - id: D1
    description: "Schema-v1 Overworld campaign state accepts one start and one matching victory while rejecting duplicate, stale, spoofed, and replayed events before effects."
    requirement: FND-06
    verification:
      - kind: integration
        ref: "src/gametest/java/dev/developershell/gametest/FoundationGameTests.java#contractStartsSlideWindowAndCommitsFirstReward"
        status: pass
    human_judgment: false
  - id: D2
    description: "A valid 17x17 lectern arena yields one nearby retry record, one runtime, and unchanged blocks; duplicate and competing starts consume nothing."
    requirement: CAMP-02
    verification:
      - kind: e2e
        ref: "Fabric runGameTest: contractStartsSlideWindowAndCommitsFirstReward"
        status: pass
    human_judgment: false
  - id: D3
    description: "Professor Infinite Slides presents a five-second Slide Deck cue and accepts damage only from its owner during the open window."
    requirement: LECT-01
    verification:
      - kind: e2e
        ref: "Fabric runGameTest: owner-only Slide Deck vulnerability assertions"
        status: pass
    human_judgment: false
  - id: D4
    description: "The first matching victory commits PASSED, Sheet entitlement, and Remote-issued ledger before granting exactly one physical Sheet and Remote."
    requirement: LECT-02
    verification:
      - kind: e2e
        ref: "Fabric runGameTest: matching victory and replay no-op assertions"
        status: pass
    human_judgment: false
  - id: D5
    description: "The pinned Java 25 offline build runs the transformed lifecycle test and inspects a fresh ordinary JAR without GameTest leakage."
    requirement: FND-07
    verification:
      - kind: integration
        ref: "gradlew clean test compileJava processResources runGameTest jar --offline plus archive inspection"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 01: Contract-to-Reward Tracer Summary

**A connected survival player can now commit one validated Contract start, defeat an owner-scoped Professor through the five-second Slide Deck window, and receive exactly one persisted Sheet/Remote reward ledger.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-08-26T16:49:13Z
- **Completed:** 2026-08-26T17:09:13Z
- **Tasks:** 1
- **Files modified:** 12

## Accomplishments

- Added schema-v1 Overworld `SavedData` and a state-first service that commits accepted START/victory state before entity, inventory, item-stack, cleanup, or boss-bar effects.
- Registered stable lower-snake-case Contract, Sheet, Remote, and Professor identities unconditionally, with the Professor carrying durable owner/encounter identity and no loot table.
- Added one bounded server runtime with a participant-only `ServerBossEvent`, a 100-tick Slide Deck cue, an 80-tick owner-only vulnerability window, and idempotent cleanup.
- Proved the entire path with fixed-UUID connected survival players, same-tick duplicate/competition attempts, block snapshots, wrong-owner damage, and replayed victory assertions.

## Requirements (Copied Verbatim)

- **FND-06**: Versioned campaign, encounter, reward, and module state survives save, quit, reload, death, and chunk unload without duplication or regression.
- **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.
- **CAMP-02**: Starting the Contract validates a player-selected overworld arena, creates a nearby retry checkpoint, and leaves blocks undamaged by default.
- **LECT-01**: The player can defeat Professor Infinite Slides by learning three readable acts: Slide Deck safe lanes, deterministic Surprise Quiz pads, and Attendance Check positioning.
- **LECT-02**: Defeating Professor Infinite Slides grants the Attendance Sheet and a cooldown-limited Infinite Slides Remote after a fight that supports failure, cleanup, retry, and mid-campaign persistence.

## Task Commits

1. **Task 1: Run one state-first Contract-to-first-reward server path** - `28faf45` (feat)
2. **Task 1 verification cleanup: replace deprecated arena predicate** - `6fc5585` (fix)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/campaign/CampaignSavedData.java` - Encodes schema, per-player attempt/checkpoint state, active encounter identity, and first-reward ledger.
- `src/main/java/dev/developershell/campaign/CampaignService.java` - Validates the Overworld arena and serializes state-before-effects START/victory transactions.
- `src/main/java/dev/developershell/lecture/LectureEncounterManager.java` - Owns the ephemeral Professor, boss bar, Slide Deck timing, vulnerability window, and cleanup.
- `src/main/java/dev/developershell/registry/ModEntities.java` - Registers the no-loot Professor and enforces owner/window damage plus persisted entity identity.
- `src/main/java/dev/developershell/item/CursedInternshipContractItem.java` - Routes ordinary lectern use through the Fabric pre-block callback to logical-server validation.
- `src/main/java/dev/developershell/registry/ModItems.java` - Registers the Contract, Attendance Sheet, and Infinite Slides Remote stable identities.
- `src/main/java/dev/developershell/DevelopersHell.java` - Initializes items/entities/interaction and advances the encounter on the server tick.
- `src/gametest/java/dev/developershell/gametest/FoundationGameTests.java` - Runs the fixed-seed, fixed-owner, connected-survival end-to-end tracer.

## Decisions Made

- Used `UseBlockCallback` for the ordinary Contract interaction while retaining `Item.useOn` as a direct/shared seam; this follows the actual Minecraft 26.2 lectern dispatch order.
- Allowed future schema numbers to decode into a read-only object and rejected mutations without dirtying it, avoiding the `computeIfAbsent` data-replacement trap.
- Generated encounter and Professor UUIDs deterministically from kind, owner, desk, and attempt while keeping runtime timers on logical game time only.
- Issued both campaign items from the committed service transaction, never from entity loot.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added the Fabric pre-block Contract hook**
- **Found during:** Task 1 exact 26.2 interaction inspection
- **Issue:** Empty-lectern vanilla handling returns `TRY_WITH_EMPTY_HAND` then `CONSUME`, so relying on `Item.useOn` would make the planned Contract interaction unreachable.
- **Fix:** Registered one idempotent `UseBlockCallback` listener and routed both that listener and `Item.useOn` through the same logical-server transaction.
- **Files modified:** `CursedInternshipContractItem.java`, `DevelopersHell.java`, `FoundationGameTests.java`
- **Verification:** The transformed GameTest invoked the registered listener and completed the Contract-to-reward path.
- **Committed in:** `28faf45`

**2. [Rule 3 - Blocking] Removed an invalid stale mapping import**
- **Found during:** Task 1 compile gate
- **Issue:** An unused `net.minecraft.Util` import does not exist under the exact 26.2 unobfuscated classpath and blocked compilation.
- **Fix:** Removed the import without changing behavior.
- **Files modified:** `CampaignSavedData.java`
- **Verification:** Pinned `compileJava compileGametestJava` passed offline.
- **Committed in:** `28faf45`

**3. [Rule 3 - Blocking] Replaced the deprecated solid-floor predicate**
- **Found during:** Task 1 clean verification
- **Issue:** `BlockState.isSolid()` compiled with a 26.2 deprecation warning.
- **Fix:** Used the supported upward-face `isFaceSturdy` predicate for arena and retry floors.
- **Files modified:** `CampaignService.java`
- **Verification:** A rerun compile emitted no deprecation warning, then the full clean transformed gate passed again.
- **Committed in:** `6fc5585`

---

**Total deviations:** 3 auto-fixed (1 Rule 2, 2 Rule 3).
**Impact on plan:** The fixes make the planned player interaction reachable and keep the exact 26.2 implementation warning-free without adding dependencies or expanding gameplay scope.

## Issues Encountered

- The Fabric GameTest launcher reported expected missing transient `server.properties`/`eula.txt` files before creating its test server; it then started normally and all three required tests passed.
- Context7 was unavailable in this executor environment, so exact version-specific behavior was verified from the locally cached Minecraft 26.2 and Fabric API classes/source artifacts required by the pinned offline build.

## Verification

- Pinned Temurin `25.0.4+7`, offline Gradle `clean test compileJava processResources runGameTest jar` - PASS on the implementation gate, tracer-feedback gate, and final warning-free gate.
- Fabric transformed server - `3 GAME TESTS COMPLETE`; `All 3 required tests passed`.
- Fresh `build/libs/developers-hell-0.1.0.jar` timestamp - PASS.
- Fresh archive contains `CampaignService.class` and excludes GameTest output - PASS.
- Retained `dist/developers-hell-0.1.0.jar` SHA-256 remains `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8` - PASS.
- No visible Minecraft client was launched; no external package, network/API integration, secret, database, wall-clock gameplay decision, or runtime asset download was added.

## User Setup Required

None - this server tracer adds no external service, account, key, dependency, or manual setup.

## Next Phase Readiness

- Later Phase 2 plans can extend the retained `CampaignSavedData`, `CampaignService`, and `LectureEncounterManager` interfaces with reload normalization, failure/retry, the remaining readable acts, presentation, resources, and cooldown behavior.
- The known-good distribution JAR remains untouched; only the ordinary build candidate under `build/libs` was regenerated for verification.
- No test player, Professor runtime, boss bar, or GameTest server remains active.

## Self-Check: PASSED

- All five created production files and the canonical `02-01-SUMMARY.md` exist.
- Task commits `28faf45` and `6fc5585` exist in repository history.
- Summary extraction returns all five declared requirement IDs, all key files, four decisions, and the substantive one-line result.
- The final exact gate passed after the last production commit, and the retained distribution hash remains unchanged.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-26*
