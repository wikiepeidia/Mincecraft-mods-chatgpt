---
phase: 01-java-25-and-fabric-26-2-foundation
verified: 2026-08-26T14:12:09Z
verified_head: 24777079d4486ab76766dd02ace2a7661bd35e2f
status: passed
score: 4/4 must-haves verified
behavior_unverified: 0
overrides_applied: 0
decision_coverage:
  honored: 0
  total: 0
  not_honored: []
---

# Phase 1: Java 25 and Fabric 26.2 Foundation Verification Report

**Phase Goal:** Players and contributors have one reproducible, offline-installable Fabric 26.2 mod foundation proven on both client and dedicated server.
**Verified:** 2026-08-26T14:12:09Z
**Status:** passed
**Re-verification:** No — initial goal-backward verification after review convergence

## User Flow Coverage

| Step | Expected | Evidence | Status |
|---|---|---|---|
| Install | One ordinary JAR with the documented Fabric 26.2/Loader/API/Java tuple | `README.md`, parsed Loader metadata, and retained distribution SHA-256 `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8` | ✓ VERIFIED |
| Enter and save | Load Developer's Hell, enter a world, obtain the translated Foundation Token, save, and exit | Completed receipt-bound UAT session `c2dd34bac9984a7bb042bd53ed7a5de5`; the user accepted the loaded model and vanilla map/paper appearance | ✓ VERIFIED |
| Repeat isolated | Repeat on a distinct normal-exit client while two exact Java rules are active, then restore host state | Canonical receipt: distinct ready clients, two active rules, reachable/blocked probes, both rules absent, group count zero, cleanup PASS | ✓ VERIFIED |
| Rebuild | Reproduce the ordinary JAR online and from the same cache offline | Detached-worktree evidence records equal online/offline/distribution hashes, exact tracked wrapper/probe, guarded removal, and registry/root preservation | ✓ VERIFIED |

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | A guarded detached committed checkout using checksum-bound Temurin 25.0.4+7 and the tracked Gradle/Loom surface produces equal online and same-cache-offline ordinary JARs, preserves prior worktrees/roots, and retains the same distribution bytes. | ✓ VERIFIED | `01-FOUNDATION-EVIDENCE.md` records tracked-manifest, worktree registration/removal/registry restoration, root preservation, exact Loom `1.17.19`, and three equal SHA-256 values. No production Java/resource, tuple, dependency, or archive wiring changed between evidenced HEAD `143e355` and verified HEAD; final pinned-JDK offline audit/test passed. |
| 2 | A player can install the exact distribution, enter/save/exit online, then repeat while the committed wrapper holds exactly two Java/javaw block rules and restores them in `finally`. | ✓ VERIFIED | User checkpoint acceptance plus the authenticated COMPLETE receipt; all eight bounded evidence markers are PASS. Receipt payload SHA-256 is `28caa498d6eb0619e37f99cb1e5a5aa211f47ff0b9a2f626502bdf9341754436`, with two active rules, blocked isolated probe, both rules absent, group zero, and cleanup PASS. |
| 3 | The same bytes reach a playable client world and a ready/clean-stop dedicated server without client-only linkage failures or residual firewall members. | ✓ VERIFIED | Evidence records online/isolated server ready and clean-stop PASS, online/isolated client ready PASS, distinct normal client exits, equal pre/post hashes, and zero residual group members; the comprehensive side/archive audit passed. |
| 4 | Stable content identity is registered independently of all eight behavior gates so toggle changes cannot remove saved IDs. | ✓ VERIFIED | `DevelopersHell.onInitialize()` unconditionally calls `ModItems.initialize()`; the one stable ID is an immutable `developers_hell:foundation_token` catalog. Eight active unit tests exercise all-on/all-off/single gates and assert the catalog is identical across every gate; the server GameTest resolves the live registry key. |

**Score:** 4/4 truths verified (0 present-but-behavior-unverified)

### Required Artifacts

| Artifact group | Expected | Status | Details |
|---|---|---|---|
| Build/toolchain surface | Frozen Java 25/Fabric 26.2 tuple and reproducible wrapper | ✓ EXISTS + SUBSTANTIVE | All 5 Plan 01 artifacts passed structural verification; exact Wrapper 9.5.1 and Loom 1.17.19 evidence is present. |
| Registry/module surface | Unconditional stable catalog and eight immutable behavior gates | ✓ EXISTS + SUBSTANTIVE | All 5 Plan 02 artifacts passed; actual Java code and eight non-skipped value/behavior tests were inspected. |
| Runtime verification surface | GameTest, production tasks, audits, and fail-closed supervisor | ✓ EXISTS + SUBSTANTIVE | All 6 Plan 03 artifacts passed; clean code review closed firewall, cancellation, process-tree, client-discovery, and Loom-baseline negative paths. |
| Player/evidence surface | Install guide, verified JAR, public-safe UAT evidence | ✓ EXISTS + SUBSTANTIVE | All 3 Plan 04 artifacts passed; retained JAR has 11 required entries, one renamed license, and no unit/GameTest output. |

**Artifacts:** 19/19 declared artifacts verified.

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| Loader metadata | Common/client entrypoints | Side-specific Fabric entrypoint declarations | ✓ WIRED | Common initializer is server-safe; client initializer is physically under `src/client`. |
| Common initializer | Stable item registry | Unconditional `ModItems.initialize()` | ✓ WIRED | No `ModuleGate` dependency exists in registry classes. |
| Module gates | Stable catalog | Behavioral unit assertions across all gate states | ✓ WIRED | `stableCatalogIsExactImmutableAndIndependentOfEveryGate` covers all-on, all-off, and each one-at-a-time gate. |
| GameTest descriptor | Live registry assertion | `fabric-gametest` invoker | ✓ WIRED | `FoundationGameTests` resolves `ModItems.FOUNDATION_TOKEN` through `BuiltInRegistries.ITEM`. |
| Verification harness | Audit/build/runtime evidence | Committed audit, Loom probe, production tasks, supervisor receipt | ✓ WIRED | PS5.1 and PowerShell 7 self-checks plus final evidence validation passed at the sealed source revision. |
| Distribution | Player/runtime profiles | Exact SHA-bound ordinary JAR | ✓ WIRED | Online/offline/dist/runtime hashes agree; client and dedicated-server evidence reference the same bytes. |

**Wiring:** 6/6 connections verified.

### Behavioral Spot-Checks

| Behavior | Command/evidence | Result | Status |
|---|---|---|---|
| Harness invariants on Windows PowerShell 5.1 | `scripts/verify-foundation.ps1 -SelfCheck` | Exit 0; tuple, archive, firewall, supervisor, cancellation, timeout-tree, and ignore checks PASS | ✓ PASS |
| Harness invariants on PowerShell 7 | `scripts/verify-foundation.ps1 -SelfCheck` | Exit 0; same complete self-check PASS | ✓ PASS |
| Exact dependency/test surface | Pinned Temurin 25 `gradlew.bat --offline auditDirectDependencies test ...` | BUILD SUCCESS; exact Loom injection baseline `145/a3fef1ae...`; all unit tests pass | ✓ PASS |
| Final evidence and archive | `verify-foundation.ps1 -ValidateEvidence -RequireUatPass ...` | Comprehensive audit and evidence validation PASS; distribution hash unchanged | ✓ PASS |
| Real client/isolation lifecycle | Canonical ignored receipt + finalized public evidence | COMPLETE, supervisor exit 0, distinct ready clients, normal exits, exact cleanup | ✓ PASS |

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|---|---|---|---|
| FND-01 | Install one JAR and enter a world offline | ✓ SATISFIED | Documented exact profile, online and isolated world UAT, retained exact distribution. |
| FND-02 | Rebuild from a fresh checkout with frozen Java/Fabric tuple | ✓ SATISFIED | Guarded detached online/offline parity evidence and final pinned offline dependency/test audit. |
| FND-03 | Launch client world and dedicated server without client-only loading failures | ✓ SATISFIED | Side-split code/archive audit plus online/isolated client and clean-stop server evidence. |
| FND-04 | Stable IDs remain registered regardless of toggles | ✓ SATISFIED | Unconditional live registration and exhaustive catalog/gate tests. |

**Coverage:** 4/4 requirements satisfied.

### Test Quality Audit

| Test File | Linked Req | Active | Skipped | Circular | Assertion Level | Verdict |
|---|---|---:|---:|---|---|---|
| `ModuleGateTest.java` | FND-04 | 8 | 0 | No | Behavioral/value | PASS |
| `FoundationGameTests.java` | FND-03, FND-04 | 1 live registry test | 0 | No | Behavioral/value | PASS |
| `verify-foundation.ps1 -SelfCheck` | FND-01, FND-02, FND-03 | Deterministic negative-path suite | 0 | No | Behavioral/invariant | PASS |

**Disabled tests on requirements:** 0. **Circular patterns:** 0. **Insufficient assertions:** 0.

### Anti-Patterns Found

None — no TODO/FIXME/placeholder/not-implemented stubs, disabled requirement tests, circular fixture writers, unfiltered host process inventory, runtime network/API surface, or unresolved clean-review finding was found.

### Decision Coverage

No trackable decisions in `01-CONTEXT.md`; the decision-coverage gate skipped with `0/0` and no warning.

## Human Verification Required

None. The phase does have a user-facing install/world flow, but its planned human checkpoint is already complete: the user explicitly accepted the loaded model and vanilla map/paper Foundation Token appearance and instructed continuation. The hashed machine receipt independently supplies process, artifact, isolation, normal-exit, and cleanup facts. No new launch is required.

## Gaps Summary

**No gaps found.** Phase goal achieved. The vanilla Foundation Token art remains explicitly accepted cosmetic debt for a later asset/release phase, not a missing Phase 1 behavior.

## Verification Metadata

**Verification approach:** Goal-backward from the four ROADMAP success criteria, with PLAN truths deduplicated into the same four observable outcomes.  
**Automated checks:** 19 artifact checks, 6 key links, 5 behavioral evidence groups, 4 requirements, and 3 test-quality rows passed; 0 failed.  
**Human checks required:** 0 outstanding (the planned checkpoint is complete).  
**Code review:** clean at sealed HEAD; 23 files, 0 critical, 0 warning, 0 info findings.

---
_Verified: 2026-08-26T14:12:09Z_  
_Verifier: Codex (inline gsd-verifier-equivalent after agent-thread limit)_
