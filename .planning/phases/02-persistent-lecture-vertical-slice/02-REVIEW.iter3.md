---
phase: 02-persistent-lecture-vertical-slice
reviewed: 2026-08-27T03:56:10.8758252Z
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
  critical: 4
  warning: 0
  info: 0
  total: 4
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-08-27T03:56:10.8758252Z  
**Depth:** standard  
**Files Reviewed:** 62  
**Status:** issues_found

## Summary

The integrated fixes close the ordinary happy paths for the previous health, arena, damage-window, runtime-start, save-invariant, verifier, redaction, radius, lectern, and publication-transaction findings. The reward fix is incomplete at migration and failure-recovery boundaries: legacy PASSED saves can permanently strand the Remote, reward fallback entities are not durably identified across torn persistence, and arbitrary owner-binding data in another holder can acknowledge or block delivery. The documented install artifact is also still the pre-fix Phase 2 JAR; the fix report explicitly records that final promotion was not performed.

The isolated 86-unit/41-GameTest results in `02-REVIEW-FIX.md` are useful evidence, not proof against these paths. No visible client was run. The seven direct-client checks remain PENDING boundaries below.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Schema-1 migration makes old Remotes permanently unusable or unrecoverable

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/campaign/CampaignSavedData.java:55-79`; `src/main/java/dev/developershell/campaign/CampaignSavedData.java:233-269`; `src/main/java/dev/developershell/campaign/PlayerCampaignState.java:110-156`; `src/main/java/dev/developershell/item/InfiniteSlidesRemoteItem.java:126-136`; `src/test/java/dev/developershell/campaign/CampaignCodecTest.java:259-284`

**Issue:** The implementation keeps schema version 1 while adding optional pending flags that default to `false`. When an old PASSED record omits those fields, decode backfills a deterministic projection UUID but deliberately leaves both projections non-pending. The new Remote authorization now requires that exact binding, whereas Remotes delivered by the old implementation are unbound. The compatibility test explicitly freezes the unsafe `pending=false` migration.

**Impact / reproduction:** Load a pre-fix schema-1 PASSED record with `remote_issued=true` and no `sheet_projection_pending`, `remote_projection_pending`, or `remote_projection_uuid`. If the old grant failed, `RewardService.reconcilePending` exits `ALREADY_PRESENT` at lines 97-98 and no Remote can ever be created. If the old grant succeeded, its unbound Remote fails the new binding check and still cannot be replaced. Existing worlds therefore lose the campaign reward on upgrade.

**Fix:** Introduce a presence-aware schema-2 migration. For a legacy PASSED record, adopt and bind at most one owner-held legacy Remote when present; otherwise create an explicit durable pending/reservation state and reconcile it before confirming. Do not infer “materialized” from `remote_issued`. Add upgrade tests for both old-delivered and old-failed Remote records.

### CR-02: Untracked reward fallback entities can duplicate after torn persistence

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/lecture/RewardService.java:343-429`; `src/main/java/dev/developershell/lecture/RewardService.java:431-467`; `src/main/java/dev/developershell/lecture/RewardService.java:470-515`; `src/main/java/dev/developershell/lecture/RewardService.java:534-583`

**Issue:** Sheet and Remote fallback entities receive random entity UUIDs that are never reserved or stored. Reconciliation searches only online inventories and currently loaded entities, then load admission accepts every entity carrying the current owner/generation binding. Unlike Retake fallback, there is no durable identity distinguishing the one authorized fallback from a duplicate.

**Impact / reproduction:** Persist `pending=true`, then model a torn crash save in which the newly spawned fallback entity was persisted but the confirmation was not. Restart with that entity's chunk unloaded and reconcile on JOIN or at the Desk. The scan cannot see the original, so it issues and confirms a second copy. Loading the old chunk later admits the first entity because the Sheet sequence/Remote projection UUID is still current. Both copies are current and owner-usable, violating exactly-once reward projection.

**Fix:** Use the Retake pattern: persist state-first Sheet and Remote fallback reservations with exact entity UUIDs, materialize only the reserved UUID, and reconcile the tracked UUID without reissuing merely because its chunk is unloaded. Reject untracked same-binding entities. Add torn-write, restart, and unloaded-chunk GameTests for both rewards.

### CR-03: Another holder's mutable binding can acknowledge or block the owner's reward

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/lecture/RewardService.java:155-161`; `src/main/java/dev/developershell/lecture/RewardService.java:470-515`

**Issue:** Representation scans inspect every online player's inventory and trust only owner UUID/generation stored in item CustomData. They never require the physical inventory holder to equal the binding owner. The same global predicate is used to confirm pending first rewards and to suppress Sheet recovery.

**Impact / reproduction:** Persist a pending victory, place an exact owner-bound Sheet or Remote in an intruder's inventory, and call `reconcilePending(owner)`. The owner's pending bit clears even though the owner received nothing; the intruder cannot use the owner-bound Remote, and the owner has no Remote recovery path. Separately, an intruder holding a current bound Sheet makes the owner's matching-Desk recovery return `ALREADY_PRESENT` indefinitely while that intruder is online.

**Fix:** Count inventory representation only when the inventory holder UUID equals the binding owner. Count world fallbacks only by the exact durable entity reservation and expected target owner, not arbitrary CustomData. Add GameTests where an intruder holds an exact owner binding and assert that owner delivery/recovery remains pending.

### CR-04: Installation instructions still ship the pre-fix JAR and obsolete evidence

**Severity:** BLOCKER (Critical tier)  
**File:** `README.md:18-22`; `README.md:42`

**Issue:** README tells users to install and not substitute hash `3e6917...907c`, and says the current verifier promoted it after all gates. That exact `dist` JAR and its evidence were produced before the thirteen review-fix commits: the evidence still reports 75 unit tests, 31 source anchors, no source commit/tree fields, and the old non-structured contract. The fix report records 86/41 receipts and explicitly says final `-Verify` promotion was not run.

**Impact / reproduction:** Follow README and install `dist/developers-hell-0.1.0.jar`. Its timestamp/evidence precede the integrated CR-01..CR-09 and WR-01..WR-04 fixes, so the user receives the known-defective implementation even though the source tree contains fixes. Running the new `-ValidateEvidence` contract against the old evidence cannot establish the claimed current release provenance.

**Fix:** After the remaining blockers are fixed and committed, run the new full `-Verify` transaction from that clean commit to publish a new JAR/evidence pair. Update README's hash, 86/41 receipt counts, and provenance claim only after promotion succeeds. Add a release check that README's advertised hash and evidence source commit/tree match the promoted pair.

## Prior Finding Verification

| Prior finding | Result on integrated code |
|---|---|
| CR-01 professor health | Resolved: config minimum is 81 and minimum-health three-act coverage exists. |
| CR-02 reward projection | Incomplete: normal independent retry works, but CR-01 through CR-03 above remain. |
| CR-03 runtime-start failure | Resolved: false start restores the exact prior state and defers success messaging. |
| CR-04 save invariants | Resolved for newly encoded states; legacy reward migration remains CR-01 above. |
| CR-05 arena hazards | Resolved for the reviewed harmful floor/occupancy cases. |
| CR-06 deadline damage | Resolved: admission uses authoritative level time and a state-identity ticket before entity mutation. |
| CR-07 exact execution receipts | Resolved in the verifier: unit and GameTest XML IDs must exactly equal the manifest with zero failures/errors/skips/duplicates. |
| CR-08 evidence grammar | Resolved: exact ordered fields and exact PASS values replace substring acceptance. |
| CR-09 config redaction | Resolved: paths are allowlisted and arbitrary rejected values are not logged. |
| WR-01 clean provenance | Verifier implementation resolved; no post-fix clean-tree release has yet been published (CR-04). |
| WR-02 paired publication | Transaction and rollback implementation resolved; no post-fix pair has yet been published (CR-04). |
| WR-03 arena radius | Resolved: configured radius reaches validation/geometry with 1/5/8 boundaries. |
| WR-04 lectern interception | Resolved: a nonmatching Desk returns `PASS`. |

## Test and UAT Boundary

- The fix report records 86 exact unit IDs and 41 exact GameTest IDs with zero missing, extra, failed, errored, skipped, or duplicate receipts. Those tests do not cover legacy delivered/failed Remote migration, torn reward persistence with an unloaded fallback chunk, or an intruder holding the owner's exact binding.
- The seven direct-client rows remain honestly `PENDING`: `MANUAL-UI-01`, `MANUAL-I18N-02`, `MANUAL-EFFECTS-03`, `MANUAL-ACCESS-04`, `MANUAL-MOTION-05`, `MANUAL-MODELS-06`, and `MANUAL-REMOTE-07`.
- No client was launched, so this review does not claim visual readability, localization wrapping, accessibility equivalence, motion comfort, model rendering, or Remote HUD success.

---

_Reviewed: 2026-08-27T03:56:10.8758252Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_

