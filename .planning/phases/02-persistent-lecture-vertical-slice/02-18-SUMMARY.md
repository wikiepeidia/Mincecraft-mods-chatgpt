---
phase: 02-persistent-lecture-vertical-slice
plan: 18
subsystem: remote-lifecycle
tags: [fabric-26.2, java-25, gametest, cooldown, lifecycle, exactly-once, tdd]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 12
    provides: Persisted Remote deadline, notice identity, bounded registered item use, and reducer replay protection
  - phase: 02-persistent-lecture-vertical-slice
    plan: 17
    provides: Exactly-once physical Remote reward after accepted three-act victory
provides:
  - JOIN and AFTER_RESPAWN projection of the persisted logical-server deadline into the native cooldown group
  - Item-presence and critical-action-bar arbitration before one persisted Remote-ready presentation
  - Production registry, ordinary use, lifecycle, and deferred-ready GameTest proof
affects: [remote-item, reward-service, lecture-lifecycle, campaign-persistence, action-bar]

actuals:
  tokens: 19543
  tasks: 1
  commits: 3

tech-stack:
  added: []
  patterns:
    - Durable logical-server deadlines are projected into transient native UI state at lifecycle edges
    - Ready presentation is dispatched only from an accepted persisted effect after item and action-bar arbitration
    - Minecraft cooldown groups receive one addCooldown call even when several matching stacks are present

key-files:
  created: []
  modified:
    - src/main/java/dev/developershell/lecture/RewardService.java
    - src/main/java/dev/developershell/server/CampaignLifecycle.java
    - src/main/java/dev/developershell/lecture/LectureEncounterManager.java
    - src/test/java/dev/developershell/item/RemoteCooldownTest.java
    - src/gametest/java/dev/developershell/gametest/RemoteGameTests.java
    - src/gametest/resources/fabric.mod.json

key-decisions:
  - "Project one native cooldown group from the first present production Remote; matching stacks share that group without duplicate packets."
  - "Leave an elapsed ready edge pending while the Remote is absent or a critical owner action bar is active; persist its exact deadline only when presentation is safe."
  - "Run ready reconciliation after encounter runtime ticks so a just-closed fight releases priority before the same production tick polls players."
  - "Preserve CampaignLifecycle orphan rejection and prove critical priority with a real ACTIVE encounter instead of fabricating a PASSED-state Professor."

patterns-established:
  - "Lifecycle projection: persisted deadline -> clamped server-tick remainder -> one native cooldown-group update."
  - "Ready edge: due persisted deadline -> item/priority decision -> accepted RemoteReadyNotice -> localized overlay and one sound."

requirements-completed: [FND-06, FND-07, LECT-02]

coverage:
  - id: D1
    description: Successful and rejected ordinary uses run through the stable registered production Remote after a real three-act victory
    requirement: FND-07
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/RemoteGameTests.java#productionRemoteUseCommitsOneEffectAndRejectedUseDoesNotReset
        status: pass
    human_judgment: false
  - id: D2
    description: Death, respawn, and join restore the clamped persisted remainder into one native cooldown group without chat
    requirement: FND-06
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/RemoteGameTests.java#deathRespawnAndJoinRestorePersistedRemainingOverlayWithoutChat
        status: pass
    human_judgment: false
  - id: D3
    description: Item absence and critical action priority defer the deadline edge, then a production manager tick presents it exactly once
    requirement: LECT-02
    verification:
      - kind: integration
        ref: src/gametest/java/dev/developershell/gametest/RemoteGameTests.java#readyEdgeWaitsForPresentRemoteAndCriticalInstructionThenEmitsOnce
        status: pass
      - kind: unit
        ref: src/test/java/dev/developershell/item/RemoteCooldownTest.java#readyCuePolicyRequiresAPresentRemoteAndYieldsToCriticalBossInstructions
        status: pass
    human_judgment: false

duration: 32min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 18: Durable Remote Lifecycle and Ready Edge Summary

**Persisted logical-server deadlines now restore Minecraft's native Remote cooldown across respawn and join, then emit one boss-safe localized ready edge without replay or wall-clock authority**

## Performance

- **Duration:** 32 min
- **Started:** 2026-08-26T23:01:48Z
- **Completed:** 2026-08-26T23:33:10Z
- **Tasks:** 1
- **Files modified:** 6

## Accomplishments

- Added silent JOIN and AFTER_RESPAWN restoration from `remoteCooldownUntilGameTime`, clamped to the native 0-400 tick range and applied once to Minecraft's shared Remote cooldown group.
- Added server-owned ready polling that leaves the persisted edge pending while no Remote is present or a real encounter reserves the owner's critical action bar.
- Preserved state-before-effect ordering: the reducer records the exact accepted deadline marker before the localized overlay and one short sound are dispatched, so later ticks cannot spam either cue.
- Added production-path proof using `ModItems.INFINITE_SLIDES_REMOTE`, registry identity, `ServerPlayerGameMode.useItem`, real three-act victory/reward flow, connected lifecycle players, and the production manager tick.

## Task Commits

1. **Task 1 RED: Specify Remote lifecycle and ready policy** — `3a55ea5` (test)
2. **Task 1 GREEN: Restore durable Remote overlay and emit one safe edge** — `62473d5` (feat)

**Plan metadata:** committed separately after tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/lecture/RewardService.java` — Native cooldown restoration, pure item/priority decision, accepted ready-edge persistence, localized overlay, and one player-local sound.
- `src/main/java/dev/developershell/server/CampaignLifecycle.java` — Always restores the Remote projection after JOIN normalization/pending notice handling and after respawn terminal handling.
- `src/main/java/dev/developershell/lecture/LectureEncounterManager.java` — Polls connected players after runtime ticks and reserves action-bar priority for live owner encounters.
- `src/test/java/dev/developershell/item/RemoteCooldownTest.java` — Pure present/absent and critical/noncritical ready-cue policy matrix.
- `src/gametest/java/dev/developershell/gametest/RemoteGameTests.java` — Real successful/rejected use, native group restoration, item-absent deferral, real encounter priority, and once-only ready proof.
- `src/gametest/resources/fabric.mod.json` — Registers `RemoteGameTests` in the transformed server test mod.

The existing exact `message.developers_hell.remote.ready` localization was reused; no language-file change was necessary.

## Decisions Made

- Native cooldowns are keyed by cooldown group, so one `addCooldown` call on a present registered Remote authoritatively covers all matching stacks and avoids duplicate packets.
- Item absence and boss priority are checked before submitting `RemoteReadyNotice`; a deferred edge remains durable and eligible instead of being consumed invisibly.
- Only an accepted matching `NotifyRemoteReady` effect may present the cue, retaining the campaign service's persist-and-dirty-before-effect contract.
- Critical-priority proof uses the real authorized ACTIVE runtime. The lifecycle `ALLOW_LOAD` orphan guard remains authoritative and unchanged.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking Test API] Corrected exact Minecraft 26.2 cooldown-group expectations and ordinary-use dispatch**
- **Found during:** Task 1 GREEN compile/integration
- **Issue:** The fail-first fixture expected one native start per matching stack and initially used an obsolete `PacketFlow` package/direct callback seam.
- **Fix:** Asserted one shared cooldown-group start plus both stacks reporting cooldown, imported `net.minecraft.network.protocol.PacketFlow`, and dispatched ordinary use through `ServerPlayerGameMode.useItem`.
- **Files modified:** `RemoteGameTests.java`
- **Verification:** Focused compile passed and all 32 server GameTests passed.
- **Committed in:** `62473d5`

**2. [Rule 3 - Blocking Test Fixture] Replaced connectionless lifecycle players with connected 26.2 server players**
- **Found during:** First clean transformed GameTest run
- **Issue:** `ServerPlayer.setGameMode` on a detached player reached `ClientboundPlayerInfoUpdatePacket` and dereferenced the missing connection latency before the lifecycle assertion.
- **Fix:** Closed each prior connection and used real connected replacement players for respawn and rejoin; the explicit JOIN invocation occurs after installing the production Remote stacks.
- **Files modified:** `RemoteGameTests.java`
- **Verification:** The lifecycle test passed on the next clean run.
- **Committed in:** `62473d5`

**3. [Rule 3 - Blocking Test Authority] Removed an unauthorized synthetic Professor priority fixture**
- **Found during:** Clean GameTest diagnosis of the final ready test
- **Issue:** Fabric `ServerEntityEvents.ALLOW_LOAD` correctly rejected a fabricated ACTIVE Professor because the durable owner was already PASSED. Weakening that guard would violate state-before-effect and orphan rejection.
- **Fix:** Proved manager priority during the real ACTIVE three-act encounter, exercised explicit critical deferral through `RewardService`, then let the production manager tick emit after the Remote returned. No lifecycle or entity-load guard changed.
- **Files modified:** `RemoteGameTests.java`
- **Verification:** Final clean run passed all 32 required GameTests.
- **Committed in:** `62473d5`

---

**Total deviations:** 3 auto-fixed test/harness issues.
**Impact on plan:** The fixes align proof with exact 26.2 APIs and existing authority boundaries; production scope, campaign schema, reward, Retake, lifecycle, and three-act invariants remain unchanged.

## Issues Encountered

- The intentional RED gate failed compilation only on the absent `RewardService.ReadyCueDecision` and `readyCueDecision` contracts.
- The first clean run passed 30/32 tests and exposed the two fixture defects above. After the connected-player correction it passed 31/32; after removing the unauthorized synthetic Professor, the exact final clean gate passed 32/32.
- Existing removal warnings for the already-deprecated compatibility `CampaignService.victory` calls remain outside this plan's owned files and did not affect the successful gate.

## TDD Gate Compliance

- RED commit `3a55ea5` captured policy and transformed-runtime expectations before implementation and failed on the missing ready-cue contract.
- GREEN commit `62473d5` supplied lifecycle projection, polling/arbitration, accepted presentation, and exact production GameTests.
- Commit order is RED then GREEN; no separate refactor commit was required.

## Verification

- Project-pinned Eclipse Temurin `25.0.4+7`, Loom `1.17.19`, and offline dependency caches were used with Java toolchain auto-detection/download disabled.
- Exact final gate passed from `clean`: `test runGameTest auditDirectDependencies build --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle`.
- All 32 required persistent GameTests passed, including prior reward, lifecycle, Retake, three-act boss, discovery, arena, and foundation suites.
- `RemoteCooldownTest` passed; the dependency audit remained on Minecraft `26.2`, Fabric Loader `0.19.3`, and Fabric API `0.158.0+26.2`; the full production build passed.
- Source assertion confirmed `RemoteGameTests` uses `ModItems.INFINITE_SLIDES_REMOTE`; registry identity is also asserted at runtime.
- Stub scan found no TODO, FIXME, placeholder, skipped test, UI-flowing empty value, or unrun verification in the six changed files.
- No visible client, distribution replacement/publication, network service, or human UAT was run or claimed.

## Known Stubs

None.

## User Setup Required

None - the lifecycle and ready behavior is offline, world-local, and requires no account, API, credential, telemetry, or external service.

## Next Phase Readiness

- The Remote's durable deadline, native overlay, and ready marker now form a complete server-authoritative lifecycle from real reward through reconnect and once-only completion.
- Reward, Retake, lifecycle, and three-act suites remain green, so later phase work can build on the same schema and production registry identity.
- Human visual UAT remains explicitly unclaimed and belongs to the phase verification/backstop workflow.

## Self-Check: PASSED

- All six modified implementation and test files plus this Summary exist.
- RED/GREEN commits `3a55ea5` and `62473d5` are present in repository history.
- Final source/stub checks contain no temporary diagnostic markers or fabricated runtime fixture.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
