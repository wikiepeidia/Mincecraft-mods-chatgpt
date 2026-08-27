---
phase: 02-persistent-lecture-vertical-slice
fixed_at: 2026-08-27T07:00:13Z
review_path: .planning/phases/02-persistent-lecture-vertical-slice/02-REVIEW.md
iteration: 3
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 02: Code Review Fix Report

**Fixed at:** 2026-08-27T07:00:13Z

**Source review:** `.planning/phases/02-persistent-lecture-vertical-slice/02-REVIEW.md` (reviewed 2026-08-27T05:18:50Z)

**Iteration:** 3

**Summary:**

- Findings in scope: 3
- Fixed: 3
- Skipped: 0
- Result: all three confirmed reward-lifecycle blockers are fixed in ordered atomic commits.

All three fixes change concurrency/state-transition logic and therefore retain the required `fixed: requires human verification` disposition despite passing automated regressions and independent review.

## Fixed Issues

### CR-01: Entity admission deletes legitimate owner-dropped rewards

**Status:** fixed: requires human verification

**Files modified:** `scripts/lecture-test-manifest.json`, `scripts/verify-lecture.ps1`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/main/java/dev/developershell/campaign/CampaignEvent.java`, `src/main/java/dev/developershell/campaign/CampaignReducer.java`, `src/main/java/dev/developershell/lecture/RewardService.java`, `src/main/java/dev/developershell/mixin/InventoryRewardDropMixin.java`, `src/main/java/dev/developershell/mixin/ServerLevelRewardAdmissionMixin.java`, `src/main/resources/developers_hell.mixins.json`, `src/main/resources/fabric.mod.json`

**Commit:** `fb91fda5083c3b1ea4ced160eca30c7f5819ffe1`

**Applied fix:** Added an exact state-first `TRANSFERRED` transition for authenticated live reward drops and kept disk admission on the exact durable UUID/context path. The live origin is derived from the real owner Q-drop and `Inventory.dropAll` death-drop call contexts, not from item CustomData alone. A fail-hard common-side mixin observes the exact `ServerLevel.addFreshEntity(Entity):boolean` return and compensates rejected adds by rolling durable authority back and restoring the exact removed reward once to its authenticated source. Copy-equal death stacks cannot authenticate as the removed source. No client imports, runtime toggle, or recursive admission path was introduced.

**Regression proof:** Fresh GameTests passed 51/51. Sheet and Remote each cover Q-drop and death-drop, exact one-representation survival, rejected-add rollback without restoring or deleting an unrelated stack, and Remote usability/recovery.

### CR-02: Pickup leaves a durable fallback reservation that admits a stale disk duplicate

**Status:** fixed: requires human verification

**Files modified:** `scripts/lecture-test-manifest.json`, `scripts/verify-lecture.ps1`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/main/java/dev/developershell/campaign/CampaignSavedData.java`, `src/main/java/dev/developershell/lecture/RewardService.java`

**Commit:** `4ea2f09858d380d46ba15c843f2fec6b95a08d5c`

**Applied fix:** Added exact `ItemEntity` UUID lookup over durable Sheet/Remote fallback references. Unload/discard now resolves fallback authority by UUID even after vanilla pickup empties the entity stack; duplicate durable UUID claims fail closed. The exact reservation is cleared before a stale pre-pickup disk entity can become authoritative, leaving the authenticated owner inventory as the only representation.

**Regression proof:** Fresh GameTests passed 53/53. Both Sheet and Remote use the real pickup path, then attempt a simulated stale pre-pickup disk load and require inventory-only authority with exactly one representation.

### CR-03: A stale pre-relocation disk copy can reclaim current fallback authority

**Status:** fixed: requires human verification

**Files modified:** `scripts/lecture-test-manifest.json`, `scripts/verify-lecture.ps1`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/gametest/java/dev/developershell/lecture/RewardServiceGameTestAccess.java`, `src/main/java/dev/developershell/campaign/CampaignEvent.java`, `src/main/java/dev/developershell/campaign/CampaignReducer.java`, `src/main/java/dev/developershell/lecture/RewardService.java`, `src/test/java/dev/developershell/campaign/CampaignReducerTest.java`

**Commit:** `f7d1c559f55cc49363480105bb2b49cf2b4e2baa`

**Applied fix:** Disk admission now requires the exact durable dimension/chunk even for materialized fallbacks and rejects a distinct live instance with the same durable UUID. `MATERIALIZED`, `RELOCATED`, `LOST`, and `CLEARED` transitions carry and compare the exact expected-prior fallback reference, so stale callbacks cannot overwrite newer authority. Dimension travel uses an exact pending source-ref handoff: source unload cannot clear authority before target CAS, target admission moves authority state-first, and both later-listener rejection and earlier ALLOW short-circuit compensate back to the exact source without creating an alternate authority. Production still does not force-load chunks.

**Regression proof:** Reducer CAS regressions and fresh GameTests passed. The same-dimension Sheet test first proves that a distinct disk instance with the same bound UUID is rejected solely by the current live-UUID conflict, then exercises either the real lifecycle callback or the exact production unload adapter when the headless fixture omits that callback. The real branch requires the complete durable ref plus a live, nonempty, exact-bound entity immediately and exact UUID visibility, no old-source-chunk binding, and one representation within three server ticks; the adapter branch proves the same final invariants synchronously. The cross-dimension Remote test uses real `ItemEntity.teleport`, proves state-first target authority, later-listener rollback, deterministic earlier-listener compensation, accepted retry, exact UUID/source absence, one binding, and stale-source rejection. Its test-only target chunk ticket is bounded and released in every terminal path.

## Lifecycle Ordering Verified

The implementation was checked against the pinned Minecraft 26.2/Fabric source rather than inferred from older mappings:

- `ItemEntity.teleport(TeleportTransition)` creates a `DIMENSION_TRAVEL` replacement, copies identity/state, removes the source with `CHANGED_DIMENSION`, invokes target `addDuringTeleport`, and does not receive the private add boolean.
- Fabric `ENTITY_UNLOAD` runs during removal before the entity disappears from visible lookup.
- `ALLOW_LOAD` is an ordered short-circuit callback; an earlier listener can reject before this mod's listener executes.
- Fabric `ENTITY_LOAD` runs only after admission and visible lookup/tracking.
- The exact common-side mixin target is the private server add descriptor returning `boolean`; the injection is fail-hard and has no client dependency.

These ordering facts determine the transfer ticket, CAS, return-value compensation, and exact-UUID lookup rules above.

## Review Iteration Dispositions

| Item | Final disposition |
|---|---|
| Iteration-2 legacy schema-1 Remote migration/adoption | Preserved. Presence-aware migration, at-most-one adoption, partial-tag fail-closed behavior, and ordinary replacement remain covered. |
| Iteration-2 exact durable reward fallbacks | Extended by CR-01 through CR-03. No-force-load, unloaded-chunk waiting, exact reservation, and destructive-loss behavior remain intact. |
| Iteration-2 owner-holder enforcement | Preserved. Inventory authority still requires the physical holder UUID to equal the binding owner. |
| Iteration-2 release publication | Remains orchestrator-owned. README, retained distribution, and evidence were intentionally not promoted in this fixer worktree. |
| CR-01 rejected-add reviewer checkpoint | Resolved by exact source restoration after state rollback, including full-inventory-safe bounded fallback and unrelated-stack noninterference regressions. |
| CR-03 first independent review | Two gaps were confirmed: earlier-listener ALLOW rejection and a distinct live same-UUID instance. Tests demonstrated exactly two pre-fix failures; production compensation and live-UUID fencing fixed both. |
| CR-03 dimension fixture review | The deterministic test seam remains package-private/GameTest-only and inert in production. It exposes no command, config, or runtime toggle. |
| CR-03 same-dimension lifecycle variance | Split into strict real-callback and exact-adapter paths. No permissive either-outcome assertion remains; the selected path is logged, and both converge on complete durable context, source absence, stale-copy rejection, and one physical representation. |
| Final independent CR-03 review | PASS after the bounded visibility and old-source-chunk assertions; no production or test blocker remained. |

## Verification

Verification ran in the isolated worktree `D:\PROJEct\GAME\Mincecraft-mods-chatgpt\.claude\worktrees\rf-02-iter3-19224-20260827122343` on branch `gsd-reviewfix/02-iter3-19224-20260827122343`, source commit `f7d1c559f55cc49363480105bb2b49cf2b4e2baa`, tree `f43d54dfda987344ca08691e6bc9f6019053b1b2`.

- Pinned JDK: Eclipse Temurin 25.0.4+7; Gradle Java auto-detection and auto-download disabled.
- Fresh pinned offline transaction: `clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle` — PASS, `BUILD SUCCESSFUL`.
- Unit execution receipt: exact manifest equality 88/88; 0 failures, 0 errors, 0 skipped.
- GameTest execution receipt: exact manifest equality 55/55; 0 failures, 0 errors, 0 skipped. The final full run logged `REWARD_RELOCATION_TEST_PATH=PRODUCTION_UNLOAD_ADAPTER` for the headless same-dimension fixture.
- Direct-dependency and production-task audits — PASS. Ordinary build JAR SHA-256: `d1fdebf1c8222532b054ed6b8a88aecc8646a434940d84dff1b922ddca6179c5` (build output only; not promoted).
- Windows PowerShell 5.1 `-SelfCheck` — PASS.
- PowerShell 7 `-SelfCheck` — PASS.
- SelfChecks used only a transient empty ignored worktree `dist/` parent required by canonical-path validation; it remained empty and was removed non-recursively. No distribution artifact was created, copied, or replaced.
- Manifest JSON parse and exact XML receipt-set comparison — PASS. Committed-range `git diff --check` — PASS.
- Exactly seven distinct manual/client rows remain `PENDING`.
- No client was launched. README, `.planning/.../02-LECTURE-EVIDENCE.md`, and `dist/developers-hell-0.1.0.jar` were not changed or promoted.

## Integration Handoff

Apply the commits in this order:

1. `fb91fda5083c3b1ea4ced160eca30c7f5819ffe1` — CR-01 authoritative live reward drops
2. `4ea2f09858d380d46ba15c843f2fec6b95a08d5c` — CR-02 exact pickup fallback authority
3. `f7d1c559f55cc49363480105bb2b49cf2b4e2baa` — CR-03 stale relocation fencing and CAS

This report is intentionally uncommitted for the orchestrator-owned documentation step.

---

_Fixed: 2026-08-27T07:00:13Z_

_Fixer: the agent (gsd-code-fixer)_

_Iteration: 3_

