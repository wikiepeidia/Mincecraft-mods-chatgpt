---
phase: 02-persistent-lecture-vertical-slice
fixed_at: 2026-08-27T04:52:49Z
review_path: .planning/phases/02-persistent-lecture-vertical-slice/02-REVIEW.md
iteration: 2
findings_in_scope: 4
fixed: 3
skipped: 0
deferred: 1
status: partial
---

# Phase 02: Code Review Fix Report

**Fixed at:** 2026-08-27T04:52:49Z

**Source review:** `.planning/phases/02-persistent-lecture-vertical-slice/02-REVIEW.md`

**Iteration:** 2

**Summary:**

- Findings in scope: 4
- Fixed: 3
- Skipped: 0
- Deferred to orchestrator: 1

## Fixed Issues

### CR-01: Schema-1 migration makes old Remotes permanently unusable or unrecoverable

**Status:** fixed: requires human verification

**Files modified:** `scripts/lecture-test-manifest.json`, `scripts/verify-lecture.ps1`, `src/gametest/java/dev/developershell/campaign/CampaignServiceGameTestAccess.java`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/main/java/dev/developershell/campaign/CampaignEvent.java`, `src/main/java/dev/developershell/campaign/CampaignReducer.java`, `src/main/java/dev/developershell/campaign/CampaignSavedData.java`, `src/main/java/dev/developershell/campaign/PlayerCampaignState.java`, `src/main/java/dev/developershell/item/InfiniteSlidesRemoteItem.java`, `src/main/java/dev/developershell/lecture/RewardService.java`, `src/test/java/dev/developershell/campaign/CampaignCodecTest.java`, `src/test/java/dev/developershell/campaign/CampaignReducerTest.java`

**Commit:** `b0920f6`

**Applied fix:** Added presence-aware schema-1 to schema-2 migration. A legacy PASSED record now persists an explicit adoption-pending state, adopts at most one truly unbound owner-held Remote, fails closed on partial private tags, and persists known absence before ordinary replacement. Old-delivered and old-failed paths are replay-safe and retain the deterministic projection identity.

**Regression proof:** Targeted codec/reducer tests passed; common and GameTest compilation passed; fresh `clean runGameTest` passed 43/43.

### CR-02: Untracked reward fallback entities can duplicate after torn persistence

**Status:** fixed: requires human verification

**Files modified:** `scripts/lecture-test-manifest.json`, `scripts/verify-lecture.ps1`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/main/java/dev/developershell/campaign/CampaignEvent.java`, `src/main/java/dev/developershell/campaign/CampaignReducer.java`, `src/main/java/dev/developershell/campaign/CampaignSavedData.java`, `src/main/java/dev/developershell/campaign/CampaignTransition.java`, `src/main/java/dev/developershell/campaign/PlayerCampaignState.java`, `src/main/java/dev/developershell/lecture/RewardService.java`, `src/test/java/dev/developershell/campaign/CampaignCodecTest.java`, `src/test/java/dev/developershell/campaign/CampaignReducerTest.java`

**Commit:** `32706ef`

**Applied fix:** Added durable per-projection fallback references containing the exact ItemEntity UUID, dimension, position, and materialization state. Reservation is persisted before spawning. Reconciliation waits when the tracked chunk is unloaded without force-loading it, treats loaded-and-missing as destructive loss, allocates a fresh UUID before replacement, and admits only the exact current UUID plus generation binding. Entity load re-snapshots after synchronous callbacks; non-destructive unload records relocation; destructive unload clears authority so stale disk copies cannot return.

**Regression proof:** Targeted codec/reducer tests and GameTest compilation passed. Fresh `clean runGameTest` passed 45/45, including torn reservation, unloaded-chunk, UUID save/load, synchronous confirmation, relocation, stale-copy, and untracked same-binding cases for Sheet and Remote.

### CR-03: Another holder's mutable binding can acknowledge or block the owner's reward

**Status:** fixed: requires human verification

**Files modified:** `scripts/lecture-test-manifest.json`, `scripts/verify-lecture.ps1`, `src/gametest/java/dev/developershell/gametest/RewardGameTests.java`, `src/main/java/dev/developershell/lecture/RewardService.java`

**Commit:** `921bfff`

**Applied fix:** Sheet and Remote inventory representation now counts only when the physical inventory holder UUID equals the binding owner UUID. World fallback authority remains fenced by the exact durable entity reservation; admitted entities are retargeted to the owner before confirmation.

**Regression proof:** Pinned common/GameTest compilation passed. Fresh `clean runGameTest` passed 47/47, including exact-binding intruder cases that cannot acknowledge failed delivery or block next-generation Sheet recovery.

## Deferred Issue

### CR-04: Installation instructions still ship the pre-fix JAR and obsolete evidence

**Disposition:** deferred to orchestrator; not skipped and not claimed fixed

**Files intentionally untouched:** `README.md`, `dist/developers-hell-0.1.0.jar`, `.planning/phases/02-persistent-lecture-vertical-slice/02-LECTURE-EVIDENCE.md`

**Reason:** Release promotion was explicitly reserved for the root orchestrator after these ordered source commits integrate. The orchestrator must run the hardened full `-Verify` transaction from the clean integrated commit, atomically promote the new JAR/evidence pair, and only then update README with the resulting hash, exact 88/47 receipt counts, and matching source commit/tree provenance.

## Verification

Verification ran in the isolated worktree `D:\PROJEct\GAME\Mincecraft-mods-chatgpt\.claude\worktrees\rf-02-iter2-20260827a` on branch `gsd-reviewfix/02-iter2-20260827a` at source commit `921bfff2f21c2ad0d07b8224d0a93e48e33b0c42`.

- Pinned JDK: Eclipse Temurin `25.0.4+7`; Gradle Java auto-detection and auto-download disabled.
- Fresh offline transaction: `clean test runGameTest auditDirectDependencies build` — PASS.
- Unit execution receipt: 88 tests, 0 failures, 0 errors, 0 skipped across 8 suites.
- GameTest execution receipt: 47 unique tests, 0 failures, 0 errors, 0 skipped; server emitted first-tick readiness, real stop-cleanup, orderly stop, and all-dimensions-saved markers.
- Direct-dependency audit — PASS; ordinary JAR built at `build/libs/developers-hell-0.1.0.jar`, SHA-256 `adbd326c4d1606a5d2f3c0f54c48f3ee9e10e5703c6ff169bc4be74bda913ed8`.
- Windows PowerShell 5.1 `-SelfCheck` — PASS.
- PowerShell 7 `-SelfCheck` — PASS.
- SelfChecks used only an empty ignored worktree `dist/` parent required by canonical-path validation; no release artifact was copied or promoted.
- PowerShell parser errors: 0. Manifest JSON parsed and resolved to exact 88/47 entries. `git diff --check` passed before this uncommitted report was written.
- Exactly seven manual/client rows remain `PENDING`.
- No client was launched. README, retained distribution, and evidence were not promoted.

## Integration Handoff

Cherry-pick these commits in order:

1. `b0920f6` — CR-01 legacy Remote migration
2. `32706ef` — CR-02 durable exact reward fallbacks
3. `921bfff` — CR-03 owner-holder inventory rule

This report is intentionally uncommitted for the orchestrator-owned documentation step.

---

_Fixed: 2026-08-27T04:52:49Z_

_Fixer: the agent (gsd-code-fixer)_

_Iteration: 2_

