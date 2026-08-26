---
phase: 02-persistent-lecture-vertical-slice
plan: 08
subsystem: retake-interactions
tags: [fabric-26.2, gametest, item-entity, custom-data, commands, retake, state-first]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 05
    provides: State-first encounter lifecycle exits and bounded runtime cleanup
  - phase: 02-persistent-lecture-vertical-slice
    plan: 06
    provides: Complete immutable arena validation and atomic Contract start
  - phase: 02-persistent-lecture-vertical-slice
    plan: 07
    provides: Durable failed-encounter Retake key, reservation/materialized distinction, and crash-window compensation
provides:
  - Exactly one owner-bound inventory Form or tracked owner-targeted fallback per durable Retake entitlement
  - Loss-fenced fallback reload/unload handling and matching-Desk empty-hand recovery
  - Stable custom RetakeFormItem with full arena revalidation and commit-before-consume retry
  - Game-master-only abort and Retake recovery commands routed through shared services
affects: [phase-2-persistence, phase-2-lecture-retry, phase-2-verification, phase-2-uat]

actuals:
  tokens: 17218
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns: [durable-authority-lossy-projection, owner-bound-custom-data, reserve-materialize-record, commit-before-consume, permission-gated-shared-service]

key-files:
  created:
    - src/main/java/dev/developershell/item/RetakeFormItem.java
    - src/main/java/dev/developershell/server/DeskInteraction.java
    - src/gametest/java/dev/developershell/gametest/RetakeGameTests.java
  modified:
    - src/main/java/dev/developershell/lecture/RetakeService.java
    - src/main/java/dev/developershell/registry/ModItems.java
    - src/main/java/dev/developershell/command/DevHellCommands.java
    - src/main/java/dev/developershell/DevelopersHell.java
    - src/main/java/dev/developershell/server/DevelopersHellRuntime.java
    - src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java
    - src/gametest/resources/fabric.mod.json
    - src/main/resources/assets/developers_hell/lang/en_us.json

key-decisions:
  - "Encode the exact owner plus failed-encounter RetakeKey in the Form's vanilla CUSTOM_DATA component; physical stacks never manufacture authority."
  - "Fence stale fallback chunk copies at ALLOW_LOAD and clear only the matching reserved/materialized UUID on unload before permitting replacement."
  - "Use one pre-block Desk callback for held Retake Forms and empty-hand recovery so lectern handling cannot bypass server validation."
  - "Invoke physical reconciliation only after LifecycleAdapter receives an accepted persisted ReconcileRetake intent."
  - "Keep the existing RETAKE_FORM registry key and replace only its factory with RetakeFormItem::new."

patterns-established:
  - "Exactly-one projection: durable entitlement is authoritative; inventory Form and fallback entity are bounded, repairable projections."
  - "State-first lifecycle bridge: persist terminal/reload transition, accept ReconcileRetake, then materialize and report its outcome."
  - "Retry transaction: validate owner, key, saved Desk, state, and complete arena; persist deterministic START; start runtime; consume the old Form last."

requirements-completed: [FND-06, FND-07, CAMP-02, LECT-02]

coverage:
  - id: D1
    description: "A failed encounter materializes at most one usable owner-bound inventory Form or tracked fallback, and loss remains recoverable without duplication."
    requirement: FND-06
    verification:
      - kind: e2e
        ref: "RetakeGameTests inventory issuance, full-inventory fallback, target ownership, loss/reload fence, and Desk recovery matrix"
        status: pass
      - kind: e2e
        ref: "Pinned Java 25 clean Fabric server run: all 22 required GameTests passed"
        status: pass
    human_judgment: false
  - id: D2
    description: "The production Retake Form rejects wrong owner, Desk, state, or changed arena and persists a new attempt before consuming exactly one Form."
    requirement: LECT-02
    verification:
      - kind: e2e
        ref: "RetakeGameTests productionRetakeItemRevalidatesCommitsAndConsumesExactlyOne and wrongOwnerDeskStateAndChangedArenaAreAtomicNoOps"
        status: pass
      - kind: other
        ref: "Offline clean test run: 54 unit tests passed with zero failures"
        status: pass
    human_judgment: false
  - id: D3
    description: "Abort and Retake recovery commands require game-master permission and reuse lifecycle/Retake services rather than bypassing state authority."
    requirement: FND-07
    verification:
      - kind: e2e
        ref: "RetakeGameTests abortAndRecoverCommandsRequireGameMasterAndUseSharedServices"
        status: pass
      - kind: other
        ref: "auditDirectDependencies and offline build"
        status: pass
    human_judgment: false
  - id: D4
    description: "Every retry reruns the complete saved-Desk arena validator and leaves blocks and physical entitlement unchanged on rejection."
    requirement: CAMP-02
    verification:
      - kind: e2e
        ref: "RetakeGameTests changed-floor rejection and restored-arena retry assertions"
        status: pass
    human_judgment: false

duration: 23min
completed: 2026-08-27
status: complete
---

# Phase 2 Plan 08: Retake Delivery, Recovery, and Retry Summary

**Durable failed-encounter authority now projects to one owner-bound Form or tracked fallback, recovers physical loss idempotently, and starts a fully revalidated retry before consuming the Form.**

## Performance

- **Duration:** 23 min
- **Started:** 2026-08-26T20:40:08Z
- **Completed:** 2026-08-26T21:03:27Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Materialized Plan 07's durable Retake key as one inventory Form or one reserved-then-recorded owner-targeted `ItemEntity`, with stale chunk copies fenced and exact matching loss cleared before recovery.
- Added one matching-Desk interaction adapter for idempotent empty-hand recovery and production Form use, preserving exact localized issued/fallback/recovered/already/nothing feedback.
- Replaced only the stable Retake registry factory with `RetakeFormItem::new`; the item verifies owner, exact failed encounter, saved Desk/facing, `RETAKE_READY`, Overworld, retry point, and complete current arena before starting.
- Persisted the deterministic keyed retry before runtime startup and consumed/discarded physical representations only after accepted startup; existing compensation restores a recoverable entitlement when runtime start fails.
- Added game-master-only `/devhell abort` and `/devhell recover retake` adapters that call the shared lifecycle and Retake services.

## Requirements (Copied Verbatim)

- **FND-06:** Versioned campaign, encounter, reward, and module state survives save, quit, reload, death, and chunk unload without duplication or regression.
- **FND-07:** Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.
- **CAMP-02:** Starting the Contract validates a player-selected overworld arena, creates a nearby retry checkpoint, and leaves blocks undamaged by default.
- **LECT-02:** Defeating Professor Infinite Slides grants the Attendance Sheet and a cooldown-limited Infinite Slides Remote after a fight that supports failure, cleanup, retry, and mid-campaign persistence.

## Task Commits

1. **Task 1 RED — Specify Form/fallback delivery and recovery** - `8dbdec1` (test)
2. **Task 1 GREEN — Materialize exactly one Retake projection** - `1c0aab5` (feat)
3. **Task 2 RED — Specify production retry and command safety** - `cecfd13` (test)
4. **Task 2 GREEN — Commit safe Retake retries** - `2ddf393` (feat)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `src/main/java/dev/developershell/item/RetakeFormItem.java` - Owner/key/Desk/state/arena validation and production retry entry point.
- `src/main/java/dev/developershell/server/DeskInteraction.java` - Single pre-block callback for held Form use and matching-Desk empty-hand recovery.
- `src/main/java/dev/developershell/lecture/RetakeService.java` - Concrete inventory/entity projection adapter, fallback lifecycle fence, deterministic production retry, and physical cleanup ordering.
- `src/main/java/dev/developershell/registry/ModItems.java` - Stable Retake registry key now constructs the custom non-stackable item.
- `src/main/java/dev/developershell/command/DevHellCommands.java` - Game-master abort and recovery adapters through shared services.
- `src/main/java/dev/developershell/DevelopersHell.java` - One-time fallback lifecycle, reconciler, and Desk callback wiring.
- `src/main/java/dev/developershell/server/DevelopersHellRuntime.java` - Explicit accepted-intent Retake reconciler bridge and exact outcome feedback.
- `src/main/resources/assets/developers_hell/lang/en_us.json` - Required issuance/recovery/nothing copy and Retake Form tooltip.
- `src/gametest/java/dev/developershell/gametest/RetakeGameTests.java` - Six server tests covering physical delivery, loss, ownership, recovery, retry, changed arena, registry, and commands.
- `src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java` - Exact lifecycle message expectations when persisted reconciliation issues a Form.
- `src/gametest/resources/fabric.mod.json` - Retake GameTest entrypoint registration.

## Decisions Made

- Bound Forms with vanilla `DataComponents.CUSTOM_DATA` using the exact owner and failed encounter UUID. Missing, malformed, stale, or wrong-owner data fails closed.
- Targeted fallback entities to the owner UUID and persisted a reservation before spawn, then the materialized UUID after spawn. `ALLOW_LOAD` rejects any stale duplicate not matching current durable authority.
- Routed both empty-hand recovery and held Form use through one `UseBlockCallback`; this keeps lectern behavior from intercepting the transaction and prevents parallel callbacks.
- Generated retry encounter/professor identities deterministically from owner, saved Desk, and the monotonic next attempt, while preserving Plan 07's collision and runtime-failure compensation behavior.
- Kept recovery commands as operator/testing surfaces only; normal discovery remains the Contract/Desk/item path.

## Automated Evidence

- Pinned Temurin `25.0.4+7`, Loom `1.17.19`, offline final gate `clean test runGameTest auditDirectDependencies build` - PASS.
- Java unit suite - `54` tests, `0` failures, `0` errors, `0` skipped - PASS.
- Fabric server GameTests - `22 GAME TESTS COMPLETE`; `All 22 required tests passed` - PASS.
- Direct dependency audit retained the approved declarations and Loom/injection baselines - PASS.
- Source assertions found `RetakeFormItem::new` on the stable registry key and production `ModItems.RETAKE_FORM` use in the retry GameTest - PASS.
- Localization JSON parse, diff whitespace check, tracked-deletion check, and stub/TODO/FIXME scan - PASS.
- Independent read-only invariant review found no issue in state-first ordering, exact binding, rejection preservation, validation, collision checks, compensation, permissions, or 26.2 API use - PASS.
- No visible client was launched, no `dist` artifact was replaced or published, and no human UAT is claimed.

## Threat Mitigations

- **T-02-RET-01 (Spoofing):** Every Form/fallback carries the exact owner plus failed encounter identity; owner targeting, saved authority, and wrong-owner GameTests prevent another player from starting or clearing the owner's retry.
- **T-02-RET-02 (Tampering):** Full arena validation and the keyed START complete before physical consumption. Wrong Desk/state/arena requests write, spawn, and consume nothing.
- **T-02-RET-03 (Elevation of Privilege):** Both recovery mutations use `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` and delegate to the same production lifecycle/Retake services.
- No dependency, endpoint, client authority, runtime network, credential, external file-access, schema, or reducer surface was added.

## TDD Gate Compliance

- Task 1 RED commit `8dbdec1` failed only on the absent production Form-binding/materialization API before GREEN commit `1c0aab5`; the clean gate then passed all 19 required GameTests.
- Task 2 RED commit `cecfd13` failed only because `RetakeFormItem` did not yet exist before GREEN commit `2ddf393`; the final clean gate passed 54 unit tests, 22 required GameTests, dependency audit, and offline build.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added the accepted-intent lifecycle reconciliation bridge**
- **Found during:** Task 1 (materialize one Form or tracked fallback)
- **Issue:** Terminal/reload transitions persisted `ReconcileRetake`, but no concrete runtime adapter invoked the physical service afterward; inventory/fallback issuance would never occur in production.
- **Fix:** With bounded orchestrator approval, made `DevelopersHellRuntime.LifecycleAdapter` accept an explicit `RetakeReconciler`, invoke it only after the accepted persisted intent, and preserve existing feedback plus exact issued/fallback copy.
- **Files modified:** `src/main/java/dev/developershell/server/DevelopersHellRuntime.java`, `src/main/java/dev/developershell/DevelopersHell.java`
- **Verification:** Task 1 clean 54-unit/19-GameTest gate and final clean 54-unit/22-GameTest gate.
- **Committed in:** `1c0aab5`

**2. [Rule 3 - Blocking] Updated retained lifecycle message expectations for real issuance**
- **Found during:** Task 1 retained lifecycle GameTests
- **Issue:** Existing death/reload/server-stop expectations ended after generic Retake guidance, but accepted reconciliation now correctly emits `message.developers_hell.retake.issued` when it creates the Form.
- **Fix:** With bounded orchestrator approval, added the exact issued key only to scenarios where persisted reconciliation actually materializes the inventory Form, preserving all ordering, state, and entity assertions.
- **Files modified:** `src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java`
- **Verification:** Clean retained plus new GameTests; all 22 required tests passed.
- **Committed in:** `1c0aab5`

---

**Total deviations:** 2 auto-fixed (1 missing critical production bridge, 1 blocking retained-test expectation update).
**Impact on plan:** Both bounded changes expose the already-planned state-first behavior without changing schema, reducer, dependency, discovery, or command authority.

## Known Stubs

None. All planned production adapters are concrete and every new automated verification ran.

## Issues Encountered

- Context7 was unavailable in the custom-agent environment; current 26.2 signatures were verified from the resolved Fabric/Minecraft classes and then compile- and GameTest-proven under the pinned toolchain.
- GameTest startup emitted the existing transient missing `server.properties`, `eula.txt`, and client-resource-output warnings, then loaded normally and passed all required tests.

## User Setup Required

None - Retake delivery/retry is local and offline, with no account, credential, external service, runtime network, visible client launch, or manual UAT requirement.

## Next Phase Readiness

- The persisted lecture slice now has concrete, loss-recoverable Retake delivery and a production retry path suitable for later end-to-end phase verification.
- Exactly-one authority, fallback lifecycle fencing, command permissions, and retry ordering have automated coverage with no open implementation blocker.
- Player-facing visual/comedy judgment remains for later explicit UAT; this plan makes no human-UAT claim.

## Self-Check: PASSED

- All eleven implementation, resource, metadata, and GameTest files changed by this plan exist on disk.
- RED/GREEN commits `8dbdec1`, `1c0aab5`, `cecfd13`, and `2ddf393` exist in repository history with no tracked-file deletion.
- The canonical summary contains `status: complete`, actuals on the estimate scale, requirement/coverage mapping, automated evidence, TDD compliance, and both approved deviations.
- No stub, TODO, FIXME, skipped test, unrun verification, unknown coverage, or implementation blocker remains.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-27*
