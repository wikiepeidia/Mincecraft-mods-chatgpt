---
phase: 02-persistent-lecture-vertical-slice
reviewed: 2026-08-27T08:12:20Z
depth: standard
files_reviewed: 69
files_reviewed_list:
  - README.md
  - scripts/verify-lecture.ps1
  - src/client/java/dev/developershell/client/DevelopersHellClient.java
  - src/gametest/java/dev/developershell/gametest/ContractArenaGameTests.java
  - src/gametest/java/dev/developershell/gametest/FoundationGameTests.java
  - src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java
  - src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java
  - src/gametest/java/dev/developershell/gametest/RemoteGameTests.java
  - src/gametest/java/dev/developershell/gametest/RetakeGameTests.java
  - src/gametest/java/dev/developershell/gametest/RewardGameTests.java
  - src/gametest/resources/fabric.mod.json
  - src/main/java/dev/developershell/DevelopersHell.java
  - src/main/java/dev/developershell/campaign/CampaignEvent.java
  - src/main/java/dev/developershell/campaign/CampaignReducer.java
  - src/main/java/dev/developershell/campaign/CampaignSavedData.java
  - src/main/java/dev/developershell/campaign/CampaignService.java
  - src/main/java/dev/developershell/campaign/CampaignTransition.java
  - src/main/java/dev/developershell/campaign/PlayerCampaignState.java
  - src/main/java/dev/developershell/command/DevHellCommands.java
  - src/main/java/dev/developershell/config/ConfigIssue.java
  - src/main/java/dev/developershell/config/DevHellConfig.java
  - src/main/java/dev/developershell/config/DevHellConfigLoader.java
  - src/main/java/dev/developershell/entity/HomeworkAddEntity.java
  - src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java
  - src/main/java/dev/developershell/item/AttendanceSheetItem.java
  - src/main/java/dev/developershell/item/CursedInternshipContractItem.java
  - src/main/java/dev/developershell/item/InfiniteSlidesRemoteItem.java
  - src/main/java/dev/developershell/item/RetakeFormItem.java
  - src/main/java/dev/developershell/lecture/ArenaRejection.java
  - src/main/java/dev/developershell/lecture/ArenaValidationResult.java
  - src/main/java/dev/developershell/lecture/ArenaValidator.java
  - src/main/java/dev/developershell/lecture/LectureAct.java
  - src/main/java/dev/developershell/lecture/LectureEncounterManager.java
  - src/main/java/dev/developershell/lecture/LectureGeometry.java
  - src/main/java/dev/developershell/lecture/LecturePresentation.java
  - src/main/java/dev/developershell/lecture/LectureRules.java
  - src/main/java/dev/developershell/lecture/LectureStateMachine.java
  - src/main/java/dev/developershell/lecture/RetakeService.java
  - src/main/java/dev/developershell/lecture/RewardService.java
  - src/main/java/dev/developershell/registry/ModEntities.java
  - src/main/java/dev/developershell/registry/ModItemIds.java
  - src/main/java/dev/developershell/registry/ModItems.java
  - src/main/java/dev/developershell/server/CampaignLifecycle.java
  - src/main/java/dev/developershell/server/DeskInteraction.java
  - src/main/java/dev/developershell/server/DevelopersHellRuntime.java
  - src/main/resources/assets/developers_hell/items/attendance_sheet.json
  - src/main/resources/assets/developers_hell/items/cursed_unpaid_internship_contract.json
  - src/main/resources/assets/developers_hell/items/infinite_slides_remote.json
  - src/main/resources/assets/developers_hell/items/retake_form.json
  - src/main/resources/assets/developers_hell/lang/en_us.json
  - src/main/resources/assets/developers_hell/models/item/attendance_sheet.json
  - src/main/resources/assets/developers_hell/models/item/cursed_unpaid_internship_contract.json
  - src/main/resources/assets/developers_hell/models/item/infinite_slides_remote.json
  - src/main/resources/assets/developers_hell/models/item/retake_form.json
  - src/main/resources/data/developers_hell/advancement/a_suspicious_opportunity.json
  - src/main/resources/data/developers_hell/recipe/cursed_unpaid_internship_contract.json
  - src/test/java/dev/developershell/campaign/CampaignCodecTest.java
  - src/test/java/dev/developershell/campaign/CampaignReducerTest.java
  - src/test/java/dev/developershell/config/DevHellConfigTest.java
  - src/test/java/dev/developershell/item/RemoteCooldownTest.java
  - src/test/java/dev/developershell/lecture/LectureGeometryTest.java
  - src/test/java/dev/developershell/lecture/LectureStateMachineTest.java
  - scripts/audit-foundation.ps1
  - scripts/lecture-test-manifest.json
  - src/gametest/java/dev/developershell/lecture/RewardServiceGameTestAccess.java
  - src/main/java/dev/developershell/mixin/InventoryRewardDropMixin.java
  - src/main/java/dev/developershell/mixin/ServerLevelRewardAdmissionMixin.java
  - src/main/resources/developers_hell.mixins.json
  - src/main/resources/fabric.mod.json
findings:
  critical: 3
  warning: 0
  info: 0
  total: 3
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-08-27T08:12:20Z
**Depth:** standard
**Files Reviewed:** 69
**Status:** issues_found

## Summary

The exact 69-file final Phase 02 scope was re-reviewed at current main HEAD `3b894c9121cc17e0eb3e015f7502bb7c4ba504aa`. The review traced reward admission, transfer rollback, pickup after vanilla empties an `ItemEntity`, same- and cross-dimension relocation, stale-disk and duplicate-UUID fencing, exact reward authority/count, both common-side mixins, the GameTest-only seam, verifier receipt parsing, the foundation audit allowlist, bounded root-process cleanup, and release provenance.

Three blocker-class reward-loss defects remain. All are reachable through ordinary Minecraft/Fabric lifecycle ordering that the current GameTests either do not exercise or replace with a direct seam call. Passing receipts therefore do not establish the affected invariants.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: An earlier Fabric admission listener bypasses live-drop rollback

**Severity:** BLOCKER (Critical tier)

**Files:** `src/main/java/dev/developershell/lecture/RewardService.java:327-335,865-903,970-1091`; `src/main/java/dev/developershell/mixin/ServerLevelRewardAdmissionMixin.java:14-20`; `src/gametest/java/dev/developershell/gametest/RewardGameTests.java:1140-1163,1476-1492`

**Issue:** The Q/death-drop rollback ticket is created only when this mod's `ALLOW_LOAD` callback reaches `transferAuthoritativeLiveDrop` and stores `LIVE_TRANSFERS` at lines 1027-1033. Fabric invokes `ALLOW_LOAD` listeners in registration order and returns immediately on the first `false`. If a listener registered before Developer's Hell rejects the entity, `allowRewardLoad` never runs. The `ServerLevel.addEntity` RETURN mixin still calls `onEntityAddResult(false)`, but lines 1064-1066 find no `LIVE_TRANSFERS` entry, and the unrelated dimension-transfer compensation has no pending Q/death handoff. Nothing restores the stack that vanilla already removed from the player.

**Reproduction / impact:** Register an `ALLOW_LOAD` listener before Developer's Hell initialization that rejects bound reward `ItemEntity` instances, then Q-drop or death-drop a confirmed Sheet or Remote. The add returns false after vanilla has removed the source stack; the durable transfer was never staged, so the RETURN hook cannot roll back. A confirmed Remote is permanently lost, and the Sheet disappears until an explicit recovery path is invoked. The current rejection test registers its listener at runtime after the mod callback and explicitly asserts only the "later rejection seam" at lines 1140-1163. Lines 1476-1492 manually stage and invoke dimension-transfer seams; they do not exercise an earlier Fabric listener against a live Q/death drop.

**Fix:** Capture and persist an exact source-ref/binding compensation ticket before the ordered Fabric callback chain can reject the add, using a narrowly scoped Q/drop and death-inventory source hook. Make `onEntityAddResult(false)` restore from that ticket independently of whether this mod's `ALLOW_LOAD` callback executed. Add deterministic earlier- and later-listener rejection GameTests for Sheet and Remote Q/death drops, each asserting exact stack restoration, cleared durable fallback authority, and one total representation.

### CR-02: Legitimate container and non-selected-slot exits are rejected after their source is consumed

**Severity:** BLOCKER (Critical tier)

**Files:** `src/main/java/dev/developershell/lecture/RewardService.java:970-1007`; `src/gametest/java/dev/developershell/gametest/RewardGameTests.java:1053-1085`

**Issue:** `transferAuthoritativeLiveDrop` authenticates only two shapes: a thrower-owned entity while the owner's selected slot is empty, or an ownerless death entity whose exact stack object is still found in the dead player's inventory. Legitimate vanilla reward exits from other sources fail both predicates. Container breaking/ejection splits the source stack before spawning an ownerless entity; GUI `THROW` removes the stack from its slot before `Player.drop`; a full-inventory cursor drop on menu close/disconnect is likewise no longer an inventory identity. These vanilla callers ignore a failed entity-add result, so rejection consumes the already-removed source.

**Reproduction / impact:** Put a max-stack-one bound Sheet or Remote in a chest and break it, or throw it from a non-selected inventory slot while the selected hotbar slot remains occupied. `ownerQDrop` and `ownerDeathDrop` are both false at lines 1003-1005, admission rejects the entity, and vanilla has no source left to restore. The confirmed Remote is permanently lost; the Sheet requires recovery. Current live-drop tests force the reward into the selected slot and remove all other inventory state at lines 1053-1056, then call `owner.drop(false)` at lines 1083-1085. No reviewed test covers container break/ejection, GUI throw from a non-selected slot, or carried-cursor close/disconnect.

**Fix:** Introduce exact, bounded transfer tickets at every supported vanilla source mutation before the stack is split/removed, and consume the ticket atomically during admission or rollback. If a source path cannot be authenticated safely, prevent that movement without consuming the item. Add GameTests for chest/container destruction, non-selected GUI throw, and full-inventory carried-cursor close/disconnect for both reward types, asserting exactly one authoritative representation after admission rejection as well as success.

### CR-03: Loaded-chunk-to-loaded-chunk movement never updates durable reward location

**Severity:** BLOCKER (Critical tier)

**Files:** `src/main/java/dev/developershell/lecture/RewardService.java:823-831,914-923,1242-1315`; `src/gametest/java/dev/developershell/gametest/RewardGameTests.java:1278-1307`

**Issue:** Disk admission requires the candidate dimension/chunk to equal the durable fallback reference. Production updates that reference only through `ENTITY_UNLOAD` in `onEntityUnload`. Vanilla's section manager does not call `onTrackingEnd` when an entity crosses between two already accessible chunks; it only calls `onSectionChange`, and Fabric emits `ENTITY_UNLOAD` from `onTrackingEnd`. A normal water/current/teleport movement between loaded chunks therefore leaves the durable reference at the old chunk indefinitely.

**Reproduction / impact:** Materialize a tracked reward in loaded chunk A, keep chunks A and B accessible, and move the entity into B. Trigger an autosave while it remains tracked, then restart from that save (or crash before a later unload callback can repair the reference). The entity is serialized in B while durable authority still names A. On reload, lines 914-918 reject the exact disk entity at `durableChunkContextAllowed`, leaving no physical confirmed Remote and no Sheet until recovery. The reviewed test moves the entity 48 blocks at lines 1278-1281, observes that no real callback occurred, and then directly invokes `ENTITY_UNLOAD` at lines 1283-1291. The final receipt records `REWARD_RELOCATION_TEST_PATH=PRODUCTION_UNLOAD_ADAPTER`, so the passing test proves the manual adapter, not the natural lifecycle.

**Fix:** Observe the actual server section-change/movement hook and CAS-update the exact bound entity's durable dimension/chunk when it crosses a chunk boundary, before that entity can be persisted in the new section; a bounded server-side synchronization loop is an alternative if the hook cannot be made stable. Add a no-seam GameTest that keeps both chunks loaded, moves the real entity naturally, saves/reloads, and proves exact UUID/binding authority, old-chunk rejection, and one representation.

## Prior Finding Verification

| Prior iteration area | Final review result |
|---|---|
| Original live Q/death transfer and later-listener rollback | The state-first transfer and exact stack restoration work when Developer's Hell's listener executes first. CR-01 identifies the still-unhandled earlier-listener ordering. |
| Pickup after vanilla empties the `ItemEntity` stack | Resolved. `onEntityUnload` now resolves authority by exact entity UUID at `RewardService.java:1250-1259`, so it no longer depends on decoding the emptied stack. |
| Stale disk replay and duplicate live UUID | Resolved for the reviewed admission paths. Disk candidates require exact durable context and a distinct live instance with the durable UUID vetoes replay. |
| Cross-dimension relocation CAS and rejection compensation | Resolved for the reviewed real transfer path: pending/admitted state uses exact prior refs, target CAS is state-first, and both rejection compensation paths restore the source. The package-private GameTest seam is not packaged. |
| Same-dimension relocation | Not resolved. CR-03 shows that the test's direct unload invocation masks the missing accessible-to-accessible section-change hook. |
| Exact reward authority/count and owner-holder enforcement | No additional defect found beyond CR-01 through CR-03. Durable refs, binding owner/projection checks, and exact-count reconciliation otherwise fail closed. |
| Mixin descriptors, ordering, and common-side safety | Verified. The two configured mixins target the intended server/common descriptors, are fail-hard, contain no client imports, and the production JAR contains only the expected mixin classes/config. |
| GameTest access seam | Verified inert in production. `RewardServiceGameTestAccess` exists only in the generated GameTest source set and is absent from the production JAR. |
| Verifier, audit, process ownership, and publication transaction | Verified. Receipt parsing uses exact JUnit identities/counts; the foundation audit uses an exact mixin allowlist; root-process ownership/cleanup is bounded and fail-closed; failed verification cannot replace the retained distribution. |
| Release provenance | Verified against source release commit `f68a8a404c5e1318c2c860cff08e03951b715b4b`, tree `3400035f77fd13586643450284c87d1aeca6054d`, the evidence file, README, and current artifact. |

## Release Evidence and UAT Boundary

The current artifact is `dist/developers-hell-0.1.0.jar`, SHA-256 `768723bb534b5e35553a9b714f23c416c838b3688791a552cdf0bc91fdebf423`, with 207 entries. The published receipts record exactly 88 unit tests and 55 GameTests, with zero failures, errors, or skips. These receipts do not cover the three reproductions above and therefore do not override the findings.

No direct client was launched. The following seven rows remain **PENDING** and are not claimed as passed: `MANUAL-UI-01`, `MANUAL-I18N-02`, `MANUAL-EFFECTS-03`, `MANUAL-ACCESS-04`, `MANUAL-MOTION-05`, `MANUAL-MODELS-06`, and `MANUAL-REMOTE-07`.

---

_Reviewed: 2026-08-27T08:12:20Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
