---
phase: 02-persistent-lecture-vertical-slice
plan: 12
subsystem: gameplay
tags: [fabric, item, saved-data, cooldown, server-authoritative]

requires:
  - phase: 02-17
    provides: First-victory Remote issuance backed by the durable reward ledger
provides:
  - Registry-backed Infinite Slides Remote with one state-first bounded slide
  - Exact 400-tick logical-server deadline and rejected-use ceiling arithmetic
  - Replay-safe deadline and ready-notice tests for the existing schema-v1 fields
affects: [02-18-durable-remote-lifecycle, reward-lifecycle, remote-gametest]

actuals:
  tokens: 5275
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns:
    - Fabric UseItemCallback before the vanilla cooldown short-circuit
    - SavedData mutation and setDirty before bounded item effects
    - EntityTypeTest maxResults ceiling for bounded living-entity scans

key-files:
  created:
    - src/main/java/dev/developershell/item/InfiniteSlidesRemoteItem.java
    - src/test/java/dev/developershell/item/RemoteCooldownTest.java
  modified:
    - src/main/java/dev/developershell/campaign/CampaignService.java
    - src/main/java/dev/developershell/registry/ModItems.java
    - src/main/resources/assets/developers_hell/lang/en_us.json

key-decisions:
  - "Reuse the frozen optional schema-v1 Remote deadline and notice fields/events instead of widening or bumping campaign persistence."
  - "Route the held Remote through Fabric UseItemCallback on the logical server because vanilla checks native cooldown before Item.use; the client callback remains PASS-only."
  - "Dispatch the six-block, six-target slide only from the accepted ApplyRemoteCooldown intent after CampaignService has replaced and dirtied SavedData."

patterns-established:
  - "Durable item authority: persisted logical-server deadline decides READY versus COOLDOWN; the native overlay is presentation only."
  - "Bounded item work: max-results entity query, exact range predicate, fixed impulse, one particle call, and one sound call."

requirements-completed: [FND-06, FND-07, LECT-02]

coverage:
  - id: D1
    description: "The stable Infinite Slides Remote commits now plus 400 before one capped six-block and six-target slide."
    requirement: LECT-02
    verification:
      - kind: unit
        ref: "src/test/java/dev/developershell/item/RemoteCooldownTest.java#exactDeadlineAndCeilingSecondsUseServerTickArithmetic"
        status: pass
      - kind: other
        ref: "gradlew test --tests dev.developershell.item.RemoteCooldownTest compileJava --offline"
        status: pass
    human_judgment: false
  - id: D2
    description: "Cooldown rejection, replay, stale deadline, wrong owner, restore clamp, and once-only ready-edge arithmetic are deterministic."
    requirement: FND-06
    verification:
      - kind: unit
        ref: "src/test/java/dev/developershell/item/RemoteCooldownTest.java#acceptedDeadlineAndReadyEventsAreOwnerBoundAndReplaySafe"
        status: pass
      - kind: unit
        ref: "src/test/java/dev/developershell/item/RemoteCooldownTest.java#overlayRestoreIsClampedAndReadyNoticeIsOneExactDeadlineEdge"
        status: pass
    human_judgment: false
  - id: D3
    description: "The production registry key constructs the custom Remote and its common code contains no network, wall-clock, OpenAI, or client authority surface."
    requirement: FND-07
    verification:
      - kind: other
        ref: "02-12 plan registry and forbidden-surface source assertions"
        status: pass
    human_judgment: false

duration: 14min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 12: Durable Infinite Slides Remote Summary

**A registry-backed Remote now commits an exact logical-server cooldown before one locally scripted, strictly bounded knockback slide**

## Performance

- **Duration:** 14 min
- **Started:** 2026-08-26T22:42:46Z
- **Completed:** 2026-08-26T22:56:30Z
- **Tasks:** 1
- **Files modified:** 5

## Accomplishments

- Replaced only the generic factory behind the existing `developers_hell:infinite_slides_remote` key with `InfiniteSlidesRemoteItem::new`.
- Added a state-first server use path: `CampaignService` commits and dirties `gameTime + 400` before the item applies its native overlay, bounded scan, impulse, particles, sound, or feedback.
- Made cooldown attempts effect-free and replay-safe while reporting ceiling whole seconds, and exposed pure clamp/ready-edge arithmetic for Plan 02-18 lifecycle projection.
- Added localized effect/cooldown tooltips and fired, recharging, ready, and unauthorized feedback without a custom packet, remote API, client decision, or wall clock.

## Task Commits

1. **Task 1 RED: Specify Remote cooldown, replay, and effect ceilings** — `dec97f3` (test)
2. **Task 1 GREEN: Implement state-first bounded Remote use** — `3e8889c` (feat)

**Plan metadata:** committed separately after tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/item/InfiniteSlidesRemoteItem.java` — Exact cooldown math, pre-vanilla use callback, translated feedback, native overlay, and capped slide.
- `src/test/java/dev/developershell/item/RemoteCooldownTest.java` — Exact deadline, ceiling, restore clamp, ready edge, owner, stale, and replay proofs.
- `src/main/java/dev/developershell/campaign/CampaignService.java` — Logical-server Remote transaction boundary using the level game clock and commit-before-intent ordering.
- `src/main/java/dev/developershell/registry/ModItems.java` — Stable Remote key now constructs the one custom item and registers its use callback once.
- `src/main/resources/assets/developers_hell/lang/en_us.json` — Remote tooltip and action-bar feedback copy.

## Decisions Made

- Kept schema 1 byte-compatible. The deadline, ready-notice identity, optional codec defaults, closed events, and reducer branches already existed from the schema-freezing work; this plan wired them into production rather than adding duplicate state.
- Treated the persisted deadline as authority and Minecraft's native cooldown as a projection. Missing or stale overlay state cannot grant an extra use, and an overlay cannot extend the durable deadline.
- Used Fabric's supported `UseItemCallback` because Minecraft 26.2 returns before `Item.use` while native cooldown is active. The callback runs before that check on the server, provides the required rejected-use message, and adds no custom networking.
- Deferred JOIN/respawn restoration and boss-safe ready presentation to dependent Plan 02-18, which owns `CampaignLifecycle`, `RewardService`, and ready-cue arbitration.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Routed cooldown clicks before vanilla's Item.use short-circuit**
- **Found during:** Task 1 API verification
- **Issue:** Minecraft 26.2 checks `ItemCooldowns.isOnCooldown` and returns before invoking the custom item, which would make the required recharging message unreachable.
- **Fix:** Registered one guarded common `UseItemCallback`; client and non-Remote calls pass through, while the logical server routes the held Remote directly to its authoritative `use` implementation.
- **Files modified:** `InfiniteSlidesRemoteItem.java`, `ModItems.java`
- **Verification:** Cached Fabric API source confirms the callback injects at the head of server item use; focused tests and `compileJava` pass.
- **Committed in:** `3e8889c`

**2. [Rule 3 - Blocking] Isolated pure cooldown arithmetic from Item registry bootstrap**
- **Found during:** Task 1 GREEN focused test
- **Issue:** Loader JUnit initialized the Minecraft `Item` superclass when calling an outer static helper, before built-in registries were bootstrapped.
- **Fix:** Moved the same arithmetic into the independently loadable `InfiniteSlidesRemoteItem.Cooldown` nested helper.
- **Files modified:** `InfiniteSlidesRemoteItem.java`, `RemoteCooldownTest.java`, `CampaignService.java`
- **Verification:** All four focused Remote tests and the full Java unit suite pass without bootstrapping the runtime item registry.
- **Committed in:** `3e8889c`

---

**Total deviations:** 2 auto-fixed (2 blocking correctness issues)
**Impact on plan:** Both fixes preserve the requested 26.2 behavior and testability without expanding runtime authority, dependencies, or schema.

## Issues Encountered

- Context7 was unavailable in this environment. Exact 26.2 signatures and callback ordering were verified from the pinned cached Minecraft bytecode and Fabric API source JAR instead of relying on older mapping tutorials.

## TDD Gate Compliance

- RED commit `dec97f3` failed at test compilation because `InfiniteSlidesRemoteItem` and its arithmetic/bound contracts did not exist.
- GREEN commit `3e8889c` added the production class and transaction path; `RemoteCooldownTest` passes 4/4 and the full Java unit suite remains green.
- Commit order is RED then GREEN.

## Verification

- Pinned Eclipse Temurin `25.0.4+7`, Loom `1.17.19`, and offline dependency resolution were used for every Gradle gate.
- Exact plan gate passed: focused `RemoteCooldownTest`, `compileJava`, stable-factory assertion, valid localization JSON, and forbidden authority-surface scan.
- Full Java unit suite passed after GREEN.
- No visible client, broad GameTest lifecycle run, distribution replacement/publication, or human UAT was performed or claimed. Plan 02-18 owns the real lifecycle/ready-edge GameTests.
- Stub scan found no TODO, FIXME, placeholder copy, skipped tests, or UI-flowing empty values. Existing `null` checks are explicit optional-state guards.

## User Setup Required

None — the Remote is fully local and requires no service, account, API key, credential, telemetry, or network access.

## Next Phase Readiness

- Plan 02-18 can call `InfiniteSlidesRemoteItem.Cooldown.restoredOverlayTicks(...)` from JOIN/respawn handling and `readyNoticeDue(...)` for the persisted once-only ready edge.
- `CampaignEvent.RemoteReadyNotice` and its reducer intent remain ready for lifecycle/boss-instruction arbitration without a schema change.
- No blocker remains for Remote GameTests against the production `ModItems.INFINITE_SLIDES_REMOTE` instance.

## Self-Check: PASSED

- Created implementation/test files and this Summary exist.
- RED/GREEN commits `dec97f3` and `3e8889c` are present in repository history.
- Focused and full unit verification evidence is recorded above.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
