---
phase: 02-persistent-lecture-vertical-slice
reviewed: 2026-08-27T01:22:17Z
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
  critical: 9
  warning: 4
  info: 0
  total: 13
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-08-27T01:22:17Z  
**Depth:** standard  
**Files Reviewed:** 62  
**Status:** issues_found

## Summary

The Phase 2 diff contains nine blocker-tier correctness, data-integrity, security, or verification-integrity defects and four warnings. The most direct gameplay blockers are a configurable boss-health soft lock, reward states that can permanently lose the Remote, a writable but unrecoverable Retake save state, and unsafe arena admission. The release verifier can also publish PASS evidence without proving the named tests executed.

The current test reports and artifact checks are useful evidence, not proof of correctness. The inspected unit XML currently reports 75 tests with zero failures, errors, or skips; the scoped resources parse; no common-side import of the client package, runtime network/API dependency, telemetry enablement, or obvious archive residue was found. These positive observations do not neutralize the concrete paths below.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Configured professor health from 40 through 80 permanently soft-locks Act 1

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/config/DevHellConfig.java:144`; `src/main/java/dev/developershell/config/DevHellConfigLoader.java:217`; `src/main/java/dev/developershell/lecture/LectureRules.java:92`; `src/main/java/dev/developershell/lecture/LectureAct.java:7-9`; `src/main/java/dev/developershell/lecture/LectureStateMachine.java:514-520`

**Issue:** The accepted/configured boss maximum is `40..400`, but the first act has an absolute health floor of 80. The domain state therefore starts at or below its first floor for any accepted value from 40 through 80 and rejects every damage event as `AT_THRESHOLD`. The Minecraft entity independently infers a lower floor from its physical health, so physical health can fall to zero while the domain remains in Slide Deck.

**Impact / reproduction:** Set `lecture.professorHealth` to `80`, start the Lecture, complete the first telegraph, and attack during the open window. The entity can fall toward the 40/0 physical floors, while `LectureStateMachine` keeps domain health at 80 and never enters threshold recovery. Once physical health reaches zero, later windows cannot reopen entity vulnerability, leaving the campaign permanently active until an external abort/failure.

**Fix:** Either reject `professorHealth <= 80` (minimum 81, with 120 as the safer documented minimum) or derive all three act floors from the configured maximum. Make the manager supply the active act's exact floor to entity admission rather than inferring it from physical health. Add 40/80/81 boundary tests that complete all three acts.

### CR-02: First-victory reward projection can permanently lose the Remote

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/campaign/CampaignReducer.java:188-209`; `src/main/java/dev/developershell/lecture/RewardService.java:70-93`; `src/main/java/dev/developershell/lecture/RewardService.java:304-328`; `src/main/java/dev/developershell/lecture/LectureEncounterManager.java:471-500`

**Issue:** Victory first commits `PASSED`, `sheetEntitled=true`, and `remoteIssued=true`, then attempts both physical grants once. `RewardService` returns `ALREADY_PRESENT` when either representation exists, so one item suppresses reconciliation of the other. If insertion/fallback of either item fails, there is no durable pending projection or compensation. Sheet recovery exists, but no path can issue a Remote whose ledger already says it was issued, and the manager closes the encounter regardless of the reconciliation result.

**Impact / reproduction:** Fill the inventory, move the world border so the saved retry position is outside it immediately before the final hit, then win. Both inventory insertions and fallbacks fail, but the player remains `PASSED` with `remoteIssued=true`. Expanding the border and using Desk recovery can restore only the Sheet; victory replay is a no-op and no code can materialize the missing Remote. A partial failure is also permanent: if the Sheet succeeds and the Remote fails, the Sheet makes the next reconciliation return at lines 70-72 before checking the Remote.

**Fix:** Reconcile Sheet and Remote independently. Persist pending/materialized projection state before effects, retry pending projections on join/Desk interaction, and distinguish “never materialized” from “issued and later lost” so recovery cannot duplicate a legitimately issued Remote. Add sheet-only, remote-only, Sheet-success/Remote-failure, and failed-fallback-then-recovery GameTests.

### CR-03: Initial runtime-start failure persists a hidden Retake state and reports a false success

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/campaign/CampaignService.java:91-118`; `src/main/java/dev/developershell/campaign/CampaignReducer.java:149-154`

**Issue:** The start effect sends the “contract signed” message before `LectureEncounterManager.start` returns success. When runtime creation/spawn returns false, compensation submits `Terminal(ABORT)` but consumes only `CleanupEncounter`; it drops the reducer's `ReconcileRetake` intent. The Contract remains unconsumed, yet durable state becomes `RETAKE_READY` without a Form.

**Impact / reproduction:** Force `ModEntities.PROFESSOR.create` or `ServerLevel.addFreshEntity` to fail after arena validation. The player sees signed-success copy followed by a spawn rejection, retains the Contract, and has no physical Retake Form. A second Contract start is rejected because `RETAKE_READY` requires the missing Retake key; recovery is possible only if the player discovers the separate empty-hand/admin recovery path.

**Fix:** Send signed-success copy only after runtime start succeeds. Define the failure semantics explicitly: either roll back to the exact pre-start state or treat it as a failed attempt and route every compensation intent through the same lifecycle/`RetakeService` reconciliation and notice path. Add an injected false-runtime-start GameTest that asserts state, Contract count, messages, entities, and Form projection.

### CR-04: A structurally valid schema-1 save can be writable yet permanently unrecoverable

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/campaign/CampaignSavedData.java:61-74`; `src/main/java/dev/developershell/campaign/CampaignSavedData.java:181-248`; `src/main/java/dev/developershell/campaign/PlayerCampaignState.java:51-69`; `src/main/java/dev/developershell/campaign/CampaignReducer.java:66-76`; `src/main/java/dev/developershell/lecture/RetakeService.java:395-404`

**Issue:** The codec defaults omitted `retake_entitled` to false and accepts absent Retake identity. Its semantic validation checks active encounter pairing and PASSED/chapter agreement but never requires `RETAKE_READY` to carry a Retake entitlement/key. The record constructor also lacks the status-to-entitlement invariant, so the malformed state remains `WRITABLE` rather than falling back to the raw read-only corruption path.

**Impact / reproduction:** Load a schema-1 player record with `lecture_status: "retake_ready"`, a positive attempt, valid desk/retry fields, and no Retake fields. Decode succeeds. Contract start fails with `missing_retake_key`, while `RetakeService` returns `NOT_ENTITLED`, so neither a new Contract nor recovery can advance the player.

**Fix:** Enforce cross-field invariants during decode and record construction: `RETAKE_READY` iff Retake entitlement and failed-encounter key exist; `ACTIVE` iff an active reference exists; PASSED reward ledgers must satisfy their intended invariants. Reject invalid documents into the preserved read-only `CORRUPT_DATA` path or migrate them explicitly. Add malformed-but-structurally-valid codec cases.

### CR-05: Arena validation accepts damaging floors and occupancy

**Severity:** BLOCKER (Critical tier)  
**File:** `src/main/java/dev/developershell/lecture/ArenaValidator.java:72-100`; `src/main/java/dev/developershell/lecture/ArenaValidator.java:156-163`

**Issue:** A “solid” floor is only checked with `isFaceSturdy`, while “passable and non-hazardous” means only empty collision shape plus empty fluid state. Those predicates accept damaging terrain such as a sturdy magma floor and empty-collision fire in combat/retry occupancy.

**Impact / reproduction:** Build the 17x17 floor from magma blocks, or put fire in an otherwise empty interior/retry body cell. The validator accepts the Contract even though the admitted arena damages the player and can turn an ostensibly safe retry into immediate injury/death.

**Fix:** Centralize explicit `isSafeStandingSurface` and `isSafeOccupancy` checks using appropriate vanilla tags/state properties. Reject fire, magma, lit campfires, cactus/berry/wither-rose contact, powder snow, and other damaging or trapping states for both combat and retry cells. Add GameTests using actual harmful `BlockState` values, not only solid-block/fluid fixtures.

### CR-06: A hit at the half-open vulnerability deadline is admitted one tick late

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/lecture/LectureStateMachine.java:499-502`; `src/main/java/dev/developershell/lecture/LectureEncounterManager.java:163-185`; `src/main/java/dev/developershell/lecture/LectureEncounterManager.java:403-439`; `src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java:94-113`

**Issue:** The pure state machine correctly rejects `gameTick >= deadline`, but entity admission consults only cached vulnerability booleans. The entity mutates physical health through `super.hurtServer` before notifying the manager, and the manager processes that delta using `lastObservedGameTime`, which is updated by the manager tick rather than the current level time.

**Impact / reproduction:** Let end-of-tick `D-1` leave the cache open, then attack at level time exactly `D` before the manager's end-tick update. Both admission and domain synchronization use the stale open state/time `D-1`, so the hit lands outside the half-open window and can cross an act or victory threshold.

**Fix:** Expose one atomic manager admission method called before `super.hurtServer`. It should use `level.getGameTime()`, exact runtime/entity/owner identity, active stage, and `[opened, deadline)` to return the permitted damage and floor. Mutate physical health only after that acceptance. Add a GameTest proving `D-1` succeeds and `D` leaves both domain and entity health unchanged.

### CR-07: The release verifier certifies named tests without proving they executed

**Severity:** BLOCKER (Critical tier)  
**File:** `scripts/verify-lecture.ps1:924-942`; `scripts/verify-lecture.ps1:973-978`; `scripts/verify-lecture.ps1:1004-1012`; `scripts/verify-lecture.ps1:1036-1043`

**Issue:** Unit verification accepts any positive aggregate with no failures/errors and permits skipped tests. GameTest “execution” is a regex count of `@GameTest` tokens in source, including comments, while Gradle output is checked only for the task label. The nine validation rows are then emitted as literal `PASS` strings, regardless of which suites/methods ran.

**Impact / reproduction:** Change a required annotation to `// @GameTest`; the regex count is unchanged, but that method is undiscoverable. Or disable/remove a required unit suite while leaving any other unit test green; skipped/missing coverage does not fail the gate. `runGameTest` and `test` can still succeed, after which the script states that all named suites and all source anchors executed.

**Fix:** Define an exact required suite/method/test-ID manifest. Require zero unit skips and parse XML for the complete expected JUnit set. Consume machine-readable GameTest results (or an exact runner transcript/receipt) and compare the executed IDs to the manifest with zero missing, duplicate, failed, or skipped tests. Derive each validation-row status from those measured results rather than literals.

### CR-08: Evidence validation accepts explicitly negative or forged “green” markers

**Severity:** BLOCKER (Critical tier)  
**File:** `scripts/verify-lecture.ps1:325-355`

**Issue:** Marker validation uses the unanchored substring regex `PASS|equal|clean|zero`. Values such as `BYPASS`, `unequal`, and `unclean` therefore satisfy the validator. Automated table rows require the word PASS but are not linked to measured receipts, compounding CR-07.

**Impact / reproduction:** Keep the three required hashes/exits and row text unchanged, but edit evidence to `server_ready: BYPASS`, `hash_equality: unequal`, and `clean_exit: unclean`. Each value matches one of the allowed substrings, so `-ValidateEvidence` accepts evidence that explicitly denies the claimed lifecycle/hash state.

**Fix:** Parse a structured schema and compare each marker against an exact allowed value (for example, exact `PASS` plus separately validated detail fields). Reject unknown fields, duplicates, negated values, and free-form status prefixes. Add self-check mutations for `BYPASS`, `unequal`, `unclean`, duplicate markers, and PASS rows with missing execution receipts.

### CR-09: Public-safe config diagnostics can log arbitrary secrets and identities

**Severity:** BLOCKER (Critical tier)  
**Files:** `src/main/java/dev/developershell/config/ConfigIssue.java:25-39`; `src/main/java/dev/developershell/config/DevHellConfigLoader.java:387-395`; `src/main/java/dev/developershell/DevelopersHell.java:41-46`

**Issue:** The sanitizer permits any short value matching `[a-zA-Z0-9_+.<|>-]+` unless it contains one of six words. Many real credentials and personal identifiers contain none of those words. Invalid enum values and unknown names are passed into `ConfigIssue` and logged verbatim.

**Impact / reproduction:** Put `"difficulty": "sk-proj-AbCd1234"` (or a password-like alphanumeric string) in the local config. Whole-file validation correctly rejects the document, but the value passes the character and denylist checks and is written to the Minecraft log. Logs/evidence later shared for support can therefore disclose the exact secret despite the public-safe privacy contract.

**Fix:** Do not log arbitrary user-controlled scalar/name text. Emit typed fixed sentinels such as `<invalid-enum>`/`<unknown-property>` and retain only inherently safe bounded numeric/boolean values. If actionable correlation is needed, use a local non-reversible digest and length, never the raw input. Add tests for common secret prefixes, JWT-like values, long random alphanumerics, password-like strings, and personal-name keys.

## Warnings

### WR-01: Verification artifacts are not bound to a clean tracked commit

**Severity:** WARNING  
**File:** `scripts/verify-lecture.ps1:1031-1055`

**Issue:** `-Verify` builds the current workspace and records only JAR/log hashes. It does not fail on modified/untracked source, record `HEAD`/tree identity, or build from a clean detached worktree. The invoked foundation audit records working-tree counts but does not reject a non-clean tree.

**Impact / reproduction:** Make an uncommitted production/resource change that passes scans, run `-Verify`, and then discard the change. The promoted JAR and green evidence no longer have reproducible source in `HEAD`, even though source/build/dist JAR hashes are equal.

**Fix:** Require an empty `git status --porcelain=v1 --untracked-files=all`, resolve and record `git rev-parse HEAD` plus tree hash, and preferably build from a guarded detached clean worktree at that exact commit. Include the commit/tree identity in the validated evidence contract.

### WR-02: Distribution and evidence promotion is not one failure-safe transaction

**Severity:** WARNING  
**File:** `scripts/verify-lecture.ps1:1050-1055`

**Issue:** The script replaces the distribution first, then constructs/asserts/writes evidence with a non-atomic `WriteAllText`. Any assertion, lock, permission, disk, or write failure leaves a new distribution paired with stale or truncated evidence despite an overall verifier failure.

**Impact / reproduction:** Make the evidence file unwritable or hold it with an exclusive Windows handle, then run a verification that produces a different valid candidate. Line 1050 promotes the candidate; line 1055 fails, and the previous evidence no longer describes `dist`.

**Fix:** Stage and validate both files before touching either destination. Replace them with rollback-capable atomic operations (including a temporary evidence file and backups), and restore the prior pair on any failure. Add a self-check that injects failure after each promotion boundary and asserts both original files remain byte-identical.

### WR-03: `arenaSearchRadius` is accepted and reported but ignored

**Severity:** WARNING  
**Files:** `src/main/java/dev/developershell/config/DevHellConfig.java:125-156`; `src/main/java/dev/developershell/server/DevelopersHellRuntime.java:54-67`; `src/main/java/dev/developershell/lecture/LectureGeometry.java:22-23`; `src/main/java/dev/developershell/lecture/LectureGeometry.java:149-163`

**Issue:** The schema accepts radius `1..8`, but runtime composition omits the field and geometry always searches fixed shells 2 through 5.

**Impact / reproduction:** Configure radius 1 and the validator can still select a retry point at radius 5; configure radius 8 with only a safe radius-6 candidate and the validator still rejects. The accepted immutable config and actual arena behavior disagree.

**Fix:** Pass the accepted radius into the arena/retry layout or remove the field from schema v1. Define whether it is a maximum shell or full range, validate that contract, and add 1/5/8 behavioral boundary tests rather than loader-only assertions.

### WR-04: Recovery callback consumes every unrelated empty-hand lectern interaction

**Severity:** WARNING  
**File:** `src/main/java/dev/developershell/server/DeskInteraction.java:54-83`

**Issue:** Once a player has any campaign record, an empty-hand click on any server-side lectern enters the recovery callback. A nonmatching lectern sends “nothing” and returns `SUCCESS_SERVER`, consuming Fabric's pre-block callback instead of allowing vanilla lectern handling.

**Impact / reproduction:** Start or pass the Lecture at lectern A, then empty-hand right-click a different lectern B, including one holding a book. The persistent campaign record makes the mod intercept B indefinitely, so vanilla lectern behavior is unavailable to that player.

**Fix:** Return `InteractionResult.PASS` immediately when `matchingDesk` is false. Reserve recovery messages and success results for the exact saved Desk (or an explicit recovery item/command). Add a GameTest that verifies a nonmatching lectern passes through.

## Test and UAT Boundary

These are coverage boundaries, not additional classified defects:

- Current unit XML reports 75 tests, zero failures, zero errors, and zero skips. There are 31 `@GameTest` source annotations, but the exact executed GameTest set is not established because of CR-07.
- No current test covers configured professor health 40/80/81, actual magma/fire arena cells, a deadline hit before the manager tick, initial runtime-start failure, semantically invalid `RETAKE_READY` data, or independent/failed reward projections.
- Remote cooldown restoration is tested with the Remote inserted before lifecycle callbacks. The natural keep-inventory-off death -> dropped Remote -> later pickup path has no pickup/inventory reconciliation test; authority still rejects early use, but the native visible countdown after pickup remains unverified.
- The seven direct-client rows remain honestly `PENDING`: `MANUAL-UI-01`, `MANUAL-I18N-02`, `MANUAL-EFFECTS-03`, `MANUAL-ACCESS-04`, `MANUAL-MOTION-05`, `MANUAL-MODELS-06`, and `MANUAL-REMOTE-07`. No client was launched, so this review does not claim visual readability, localization wrapping, accessibility equivalence, motion comfort, model rendering, or Remote HUD success.

---

_Reviewed: 2026-08-27T01:22:17Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_

