---
phase: 02-persistent-lecture-vertical-slice
reviewed: 2026-08-27T05:18:50Z
depth: standard
files_reviewed: 62
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
findings:
  critical: 3
  warning: 0
  info: 0
  total: 3
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-08-27T05:18:50Z
**Depth:** standard
**Files Reviewed:** 62
**Status:** issues_found

## Summary

The exact 62-file iteration-2 scope was re-reviewed against the integrated source tree. The legacy schema-2 migration/adoption paths, owner-holder enforcement, release publication, and all previously resolved non-fallback findings showed no regression. The hardened release metadata and artifact were independently checked: evidence source commit 494ccf0773611c1e8061b64eefdae9e11aa45f12 resolves to tree 4267ce1eb009444b4a039aebfe53017267e0b669; the evidence records exact 88-unit and 47-GameTest receipts; the JAR SHA-256 is cd114cab56f6d697aaa0400373dc87b709b778c8952e753685dd815953e494a0 and matches the value published in the evidence and README; and the JAR has 198 entries with no source or test leakage.

Three blocker-class correctness defects remain in the exact durable reward protocol. A normal owner drop can be deleted at entity admission, pickup can leave a durable reservation that admits a stale disk copy, and a pre-relocation disk copy can reclaim authority from the current physical fallback. These are reward-loss or reward-duplication risks and prevent a clean release verdict.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Entity admission deletes legitimate owner-dropped rewards

**Severity:** BLOCKER (Critical tier)

**Files:** src/main/java/dev/developershell/lecture/RewardService.java:804-819; src/gametest/java/dev/developershell/gametest/RewardGameTests.java:454-470

**Issue:** allowRewardLoad ignores both loadedFromDisk and spawnReason. Every bound Sheet is admitted only when its new ItemEntity UUID already equals the durable Sheet fallback UUID, and every bound Remote is admitted only when its UUID already equals the durable Remote fallback UUID. Fabric invokes this admission callback from the entity-add path as well as disk loading. A normal owner Q-drop or death drop creates a fresh ItemEntity UUID, so it is rejected even though it is a legitimate transition from the owner's inventory rather than a stale disk replay.

**Impact / reproduction:** Give the owner a current bound Attendance Sheet or Infinite Slides Remote and drop it normally. ServerPlayer removes the stack from inventory before calling the entity-add path; the new entity is rejected at RewardService.java:815 or :819, and the vanilla drop path does not restore the removed stack when admission fails. The reward therefore disappears. This is permanent for a confirmed Remote because its projection is no longer pending and the protocol intentionally does not replay a lost confirmed Remote. The existing admission tests exercise only EntitySpawnReason.LOAD with loadedFromDisk=true and therefore do not cover this path.

**Fix:** Branch admission on the actual load context. For a legitimate owner-originated live drop, atomically transfer/register exact authority to the new ItemEntity UUID before admission, or reject the drop before inventory removal and restore the stack. Keep exact UUID fencing for disk-loaded candidates. Add GameTests for normal Q-drop and death-drop flows for both rewards, asserting exactly one representation survives and the Remote remains usable or recoverable.

### CR-02: Pickup leaves a durable fallback reservation that admits a stale disk duplicate

**Severity:** BLOCKER (Critical tier)

**Files:** src/main/java/dev/developershell/lecture/RewardService.java:846-866; src/gametest/java/dev/developershell/gametest/RewardGameTests.java:501-522

**Issue:** onEntityUnload discovers a fallback's owner and projection key only by decoding item.getItem(). During ItemEntity.playerTouch, vanilla transfers the same stack into the player's inventory, empties it, and then discards the entity. ENTITY_UNLOAD fires synchronously during that discard, while item.getItem() is empty. Both binding lookups at RewardService.java:850 and :858 therefore fail, so the exact durable Sheet or Remote reservation is neither cleared nor transferred to the inventory representation.

**Impact / reproduction:** Materialize an exact tracked fallback, let its owner pick it up, and persist player data while retaining a pre-pickup chunk image (the ordinary torn-persistence ordering this protocol is intended to tolerate). After restart, the player has the reward, but the unchanged durable fallback reference still authorizes the old disk entity UUID. allowRewardLoad accepts that stale entity, producing simultaneous inventory and world copies of the current binding. The current tests manually drive non-destructive relocation and destructive removal; they do not exercise owner pickup followed by stale chunk reload.

**Fix:** Transfer or clear exact fallback authority in a pickup hook before the ItemStack is mutated, or make unload resolution use the exact ItemEntity UUID against durable fallback indexes instead of reading the now-empty stack. Add Sheet and Remote GameTests covering pickup, state save, and simulated stale pre-pickup chunk load; each must prove that only the inventory representation remains authoritative.

### CR-03: A stale pre-relocation disk copy can reclaim current fallback authority

**Severity:** BLOCKER (Critical tier)

**Files:** src/main/java/dev/developershell/lecture/RewardService.java:623-646,761-770,804-843; src/main/java/dev/developershell/campaign/CampaignReducer.java:344-367; src/gametest/java/dev/developershell/gametest/RewardGameTests.java:501-522

**Issue:** loadContextAllowed returns true unconditionally whenever the durable fallback is marked materialized. Admission therefore checks the binding and entity UUID but not the durable dimension/chunk for materialized references. onEntityLoad then emits MATERIALIZED and rewrites the reference to the loading entity's location. The reducer accepts MATERIALIZED/RELOCATED by UUID without comparing the previously authoritative location or movement epoch.

**Impact / reproduction:** Start with a materialized tracked fallback, relocate it so the durable reference points to the new location or dimension, and retain a torn-persistence copy in the old chunk. Load that old copy while the new physical entity remains current. Because the UUID and binding match and materialized bypasses context checks at RewardService.java:765-766, the stale copy is admitted; RewardService.java:826-843 then lets it overwrite the durable location. Across dimensions, independent level entity stores can hold both same-UUID entities, yielding two physical copies of the current reward. The existing relocation test checks only that a relocation is recorded and that a copy is rejected after destructive clearing; it never reloads the old copy while the reference remains materialized.

**Fix:** For disk admission, require the candidate dimension/chunk to equal the durable current context even when materialized, or add a durable movement epoch/CAS token and accept only the expected source-to-target transition. MATERIALIZED and RELOCATED reducer events must compare expected prior authority before rewriting it. Add same-dimension and cross-dimension stale-pre-relocation GameTests while the current entity remains materialized.

## Prior Finding Verification

| Iteration-2 item | Result |
|---|---|
| CR-01 legacy schema-2 Remote migration/adoption | Resolved for the reviewed migration and adoption paths; no regression found. |
| CR-02 exact durable Sheet/Remote fallback reservations | Not fully resolved: CR-02 and CR-03 above expose pickup and pre-relocation torn-persistence gaps; CR-01 exposes live-drop loss at the same admission boundary. |
| CR-03 owner-holder enforcement | Resolved: observed Sheet and Remote inventory representations are accepted only when the holder UUID equals the binding owner UUID. |
| CR-04 release publication | Resolved: commit/tree identity, exact receipts, shared digest, 198-entry archive, and no test/source leakage were verified against the current published files. |
| Earlier resolved Phase 02 findings | No regression found in the reviewed health, arena, damage, runtime, save invariant, receipt, evidence grammar, redaction, radius, lectern, provenance, or paired-publication paths. |

## Test and UAT Boundary

Automated evidence remains exactly 88 unit tests and 47 GameTests as recorded by the validated release receipts. Those receipts do not exercise the three reproduction paths above and do not negate the findings.

The seven direct-client checks remain honest PENDING UAT boundaries, not code-review defects: MANUAL-UI-01, MANUAL-I18N-02, MANUAL-EFFECTS-03, MANUAL-ACCESS-04, MANUAL-MOTION-05, MANUAL-MODELS-06, and MANUAL-REMOTE-07.

---

_Reviewed: 2026-08-27T05:18:50Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_

