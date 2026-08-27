---
phase: 02
fixed_at: 2026-08-27T03:36:03.7897313Z
review_path: .planning/phases/02-persistent-lecture-vertical-slice/02-REVIEW.md
iteration: 1
findings_in_scope: 13
fixed: 13
skipped: 0
unresolved: 0
status: all_fixed
---

# Phase 02: Code Review Fix Report

**Fixed at:** 2026-08-27T03:36:03.7897313Z
**Source review:** `.planning/phases/02-persistent-lecture-vertical-slice/02-REVIEW.md`
**Iteration:** 1

## Summary

- Findings in scope: 13 (9 Critical, 4 Warning)
- Fixed: 13
- Skipped: 0
- Unresolved: 0
- Distribution/final evidence promotion: deliberately not performed during this fix pass
- Manual-client boundary: exactly seven checks remain `PENDING`

## Fixed Issues

### CR-01: Configured professor health from 40 through 80 permanently soft-locks Act 1

**Status:** fixed: requires human verification
**Files modified:** `src/main/java/dev/developershell/config/DevHellConfig.java`, `src/main/java/dev/developershell/config/DevHellConfigLoader.java`, `src/main/java/dev/developershell/lecture/LectureRules.java`, `src/test/java/dev/developershell/config/DevHellConfigTest.java`, `src/test/java/dev/developershell/lecture/LectureStateMachineTest.java`
**Commit:** `fb1ad07`
**Applied fix:** Raised and centralized the valid professor-health floor so every accepted configuration can cross the first-act threshold.
**Regression tests:** Config boundary rejection/default tests and minimum-health three-act state-machine coverage; final unit receipt 86/86 and GameTest receipt 41/41.

### CR-02: First-victory reward projection can permanently lose the Remote

**Status:** fixed: requires human verification
**Files modified:** `src/gametest/java/dev/developershell/gametest/RemoteGameTests.java`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/gametest/java/dev/developershell/lecture/RewardServiceGameTestAccess.java`, `src/main/java/dev/developershell/campaign/CampaignEvent.java`, `src/main/java/dev/developershell/campaign/CampaignReducer.java`, `src/main/java/dev/developershell/campaign/CampaignSavedData.java`, `src/main/java/dev/developershell/campaign/PlayerCampaignState.java`, `src/main/java/dev/developershell/item/InfiniteSlidesRemoteItem.java`, `src/main/java/dev/developershell/lecture/RewardService.java`, `src/main/java/dev/developershell/server/CampaignLifecycle.java`, `src/main/java/dev/developershell/server/DeskInteraction.java`, `src/main/resources/assets/developers_hell/lang/en_us.json`, `src/test/java/dev/developershell/campaign/CampaignCodecTest.java`, `src/test/java/dev/developershell/campaign/CampaignReducerTest.java`
**Commit:** `e8c04e5`
**Applied fix:** Persisted independent sheet/Remote projection state, reconciled only missing owner-bound representations, and cleared pending state only after an observable representation exists.
**Regression tests:** Reducer/codec replay permutations plus real, partial, total-failure, inventory/fallback, join, desk, lost-Remote, and intruder GameTests; fresh 41/41 and final exact manifest receipts.

### CR-03: Initial runtime-start failure persists a hidden Retake state and reports a false success

**Status:** fixed: requires human verification
**Files modified:** `src/gametest/java/dev/developershell/campaign/CampaignServiceGameTestAccess.java`, `src/gametest/java/dev/developershell/gametest/ContractArenaGameTests.java`, `src/main/java/dev/developershell/campaign/CampaignSavedData.java`, `src/main/java/dev/developershell/campaign/CampaignService.java`, `src/main/java/dev/developershell/lecture/LectureEncounterManager.java`
**Commit:** `529c40a`
**Applied fix:** Added an explicit compensating transition when runtime encounter creation fails, preventing false success and hidden Retake entitlement.
**Regression tests:** Failed-runtime-start GameTest asserts persisted rollback, no success report, no active encounter, and no hidden Retake; final 41/41 GameTests.

### CR-04: A structurally valid schema-1 save can be writable yet permanently unrecoverable

**Status:** fixed: requires human verification
**Files modified:** `src/main/java/dev/developershell/campaign/CampaignSavedData.java`, `src/main/java/dev/developershell/campaign/PlayerCampaignState.java`, `src/test/java/dev/developershell/campaign/CampaignCodecTest.java`
**Commit:** `a1320f4`
**Applied fix:** Enforced constructor and codec cross-field invariants so writable schema-1 states cannot encode unreachable materialization/reservation combinations.
**Regression tests:** Constructor rejection and codec round-trip/rejection tests for reachable and impossible reward/Retake states; final unit receipt 86/86.

### CR-05: Arena validation accepts damaging floors and occupancy

**Status:** fixed: requires human verification
**Files modified:** `src/main/java/dev/developershell/lecture/ArenaValidator.java`, `src/gametest/java/dev/developershell/gametest/ContractArenaGameTests.java`
**Commit:** `6ac7d88`
**Applied fix:** Rejected damaging floor/occupancy states, including fire and magma, before any encounter effect or persistence commit.
**Regression tests:** Atomic fire-occupancy, fire-retry, magma-floor, non-solid-floor, headroom, border, capacity, and valid-start GameTests; fresh-world and final 41/41 receipts.

### CR-06: A hit at the half-open vulnerability deadline is admitted one tick late

**Status:** fixed: requires human verification
**Files modified:** `src/gametest/java/dev/developershell/gametest/LectureBossGameTests.java`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/main/java/dev/developershell/entity/ProfessorInfiniteSlidesEntity.java`, `src/main/java/dev/developershell/lecture/LectureEncounterManager.java`, `src/main/java/dev/developershell/lecture/LectureStateMachine.java`, `src/test/java/dev/developershell/lecture/LectureStateMachineTest.java`
**Commit:** `d3d6729`
**Applied fix:** Unified server-authoritative damage admission on the half-open interval and removed the one-tick-late boundary path.
**Regression tests:** Exact deadline/adjacent-tick unit assertions and authoritative Professor damage GameTest; final unit 86/86 and GameTest 41/41.

### CR-07: The release verifier certifies named tests without proving they executed

**Status:** fixed
**Files modified:** `build.gradle`, `scripts/lecture-test-manifest.json`, `scripts/verify-lecture.ps1`
**Commit:** `3ebfb51`
**Applied fix:** Added a reviewed exact test manifest, machine-readable GameTest XML, exact executed-ID reconciliation, canonical receipt hashes, row-scoped receipt derivation, and strict foundation-audit exit handling.
**Regression tests:** Missing/extra/duplicate/fail/error/skip/comment-only/source-count and row-tamper mutations; final XML equals 86 unit IDs and 41 GameTest IDs (40 project plus pinned `minecraft:always_pass`) with zero drift.

### CR-08: Evidence validation accepts explicitly negative or forged “green” markers

**Status:** fixed
**Files modified:** `scripts/verify-lecture.ps1`
**Commit:** `2001207`
**Applied fix:** Replaced free-form marker trust with an exact ordered structured schema and exact status/detail/receipt validation.
**Regression tests:** Rejects BYPASS, unequal, unclean, `NOT_PASS`, free-form PASS, missing/unknown/duplicate/out-of-order/outside markers, extra rows, and receipt tampering; LF/CRLF production shapes pass in PowerShell 5.1 and 7.

### CR-09: Public-safe config diagnostics can log arbitrary secrets and identities

**Status:** fixed
**Files modified:** `src/main/java/dev/developershell/DevelopersHell.java`, `src/main/java/dev/developershell/config/ConfigIssue.java`, `src/main/java/dev/developershell/config/DevHellConfigLoader.java`, `src/test/java/dev/developershell/config/DevHellConfigTest.java`
**Commit:** `f20372d`
**Applied fix:** Restricted diagnostic paths/values to schema-known names and typed sentinels; unknown or personal fields collapse to safe redacted forms and rejected raw values are never logged.
**Regression tests:** 17/17 config tests, including tokens, passwords, random scalars, duplicate personal-name fields, and direct-constructor attempts.

### WR-01: Verification artifacts are not bound to a clean tracked commit

**Status:** fixed
**Files modified:** `scripts/verify-lecture.ps1`
**Commit:** `26fc709`
**Applied fix:** Required an exact clean tracked/untracked status with only the user-global excludes file disabled, recorded full object-format/commit/tree provenance, and rechecked the same clean snapshot immediately before publication.
**Regression tests:** Modified/untracked status, forged DIRTY claim, commit/tree mismatch, and standard `.gitignore` behavior mutations under PowerShell 5.1 and 7.

### WR-02: Distribution and evidence promotion is not one failure-safe transaction

**Status:** fixed
**Files modified:** `scripts/verify-lecture.ps1`
**Commit:** `ddd8091`
**Applied fix:** Staged and semantically/byte validated the JAR and evidence together, atomically replaced both with backups, rolled both back on every pre-commit failure, bound the recorded previous hash to the transaction snapshot, and made post-commit cleanup diagnostics non-fatal.
**Regression tests:** PowerShell 5.1/7 cover stage rejection, locked evidence, first/second replacement failures, final validation failure, absent destinations, previous-hash drift, incomplete restore reporting/retained backup, locked cleanup backup, exact restoration, and zero normal residue. Independent focused review reported no blocker.

### WR-03: `arenaSearchRadius` is accepted and reported but ignored

**Status:** fixed: requires human verification
**Files modified:** `src/main/java/dev/developershell/DevelopersHell.java`, `src/main/java/dev/developershell/item/CursedInternshipContractItem.java`, `src/main/java/dev/developershell/item/RetakeFormItem.java`, `src/main/java/dev/developershell/lecture/ArenaValidator.java`, `src/main/java/dev/developershell/lecture/LectureGeometry.java`, `src/main/java/dev/developershell/server/DevelopersHellRuntime.java`, `src/test/java/dev/developershell/config/DevHellConfigTest.java`, `src/test/java/dev/developershell/lecture/ArenaValidatorTest.java`, `src/test/java/dev/developershell/lecture/LectureGeometryTest.java`
**Commit:** `8aa7330`
**Applied fix:** Propagated the accepted radius through start and retry geometry as the complete maximum outer shell while preserving the default 44 candidates.
**Regression tests:** Config 16/16, geometry 8/8, validator 3/3; explicit radius 1/5/8 boundaries and fresh 41/41 GameTests.

### WR-04: Recovery callback consumes every unrelated empty-hand lectern interaction

**Status:** fixed: requires human verification
**Files modified:** `src/main/java/dev/developershell/server/DeskInteraction.java`, `src/gametest/java/dev/developershell/gametest/RetakeGameTests.java`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`
**Commit:** `bc05539`
**Applied fix:** Returns `InteractionResult.PASS` immediately for nonmatching lecterns; recovery remains restricted to the exact saved Desk and missing projection.
**Regression tests:** Nonmatching lectern/owner/intruder and matching recovery GameTests; fresh and final 41/41.

## Verification

Verification ran in the isolated worktree `D:\PROJEct\GAME\Mincecraft-mods-chatgpt\.claude\worktrees\rf-02-21364-1787794231` on branch `gsd-reviewfix/02-21364-1787794231` with the checksum-bound Temurin `25.0.4+7` JDK.

- Full gate: `gradlew.bat ... --offline clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle` — exit 0, `BUILD SUCCESSFUL`
- Unit XML: 86 expected, 86 executed, 0 missing/extra/fail/error/skip
- GameTest XML: 41 expected, 41 executed, 0 missing/extra/duplicate/fail/error/skip
- Foundation audit: exit 0; every required section PASS; `FINAL_RESULT` is `PASS: FOUNDATION_AUDIT`
- Verifier SelfCheck: PowerShell 5.1 exit 0; PowerShell 7 exit 0
- PowerShell parser: 0 errors; `git diff --check`: clean
- One earlier non-clean GameTest rerun encountered persisted-world state and was discarded; all reported GameTest results above come from fresh `clean` transactions.

The full `-Verify` promotion mode was intentionally not run. It is hard-bound to the checkout-local retained-JDK path and exact default dist/evidence children, so running it here could not both preserve its clean/path identity contract and honor the quarantine. Final re-review and promotion remain orchestrator-owned after convergence.

## Skipped Issues

None.

## Unresolved Reasons

None. The seven direct-client checks remain explicitly `PENDING` verification boundaries, not unresolved automated findings.

---

_Fixed: 2026-08-27T03:36:03.7897313Z_
_Fixer: gsd-code-fixer_
_Iteration: 1_


