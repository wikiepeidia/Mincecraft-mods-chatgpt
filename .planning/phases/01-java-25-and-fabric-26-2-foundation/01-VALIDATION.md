---
phase: 01-java-25-and-fabric-26-2-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-26
---

# Phase 1 — Validation Strategy

> Nyquist contract for a reproducible, side-safe, offline-installable Fabric 26.2 walking skeleton. Test sampling follows the actual task ordering: exact-JDK/pristine-upstream proof, repository tracer, unit gate, GameTest/production audit, automated release proof, one bounded observation-only human UAT, then automated public-safe evidence finalization.

## Test Infrastructure

| Property | Value |
|---|---|
| Unit framework | `net.fabricmc:fabric-loader-junit:0.19.3` on JUnit Platform |
| Integrated framework | Fabric server GameTest from Fabric API `0.158.0+26.2` |
| Build entrypoint | Committed `gradlew.bat` Wrapper `9.5.1` under the checksum-verified official Eclipse Temurin `25.0.4+7` Windows x64 JDK retained in ignored `.work/toolchain` |
| Repository build | Wrapper `build` only after Task 01-01-02 creates tracer source; every Plan 01–03 build captures a fresh mechanical configured/resolved Loom/artifact-SHA probe and matches toolchain evidence |
| Offline build | Detached clean project worktree at committed `HEAD`: its tracked wrapper and committed Loom probe run online `clean build`, then same-cache `--offline clean build`, with separate fresh logs/resolution checks |
| Worktree safety | Canonical Win32 final paths; direct GUID child of canonical OS temp; exact porcelain registration/HEAD; only non-recursive `git worktree remove --force -- <exact path>`; exact child gone and pre-existing registration bytes restored while repository/home/temp roots remain |
| Archive license | Official Fabric Jar rename produces exactly `LICENSE_developers-hell`; source `LICENSE` stays tracked, while unrenamed/duplicate production entries fail |
| Security audit | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\audit-foundation.ps1 -SourceAndDependencies` across `src/main`, `src/client`, direct declarations, repositories, side boundary, and Git hygiene |
| Distribution contract | Copy the verified equal clean-checkout JAR before worktree removal to ignored `dist/developers-hell-0.1.0.jar`; online/offline/distribution SHA-256 must match, and all production launches hash the same bytes before/after |
| Full automated phase gate | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-foundation.ps1 -PrimeAndCompare -RunServerSmoke -ClientPreflight -ValidateEvidence -DistributionPath .\dist\developers-hell-0.1.0.jar` |
| Verifier shell contract | Inline `<automated>` bodies are direct PowerShell statements; checked-in scripts use `powershell.exe ... -File`, never a nested double-quoted `-Command` |
| Runtime-offline proof | One fresh unique firewall group with exactly two outbound-block rules for verified java/javaw binaries, independently checked by reachable/blocked probes and `finally` removal of both exact IDs to zero group members; Gradle `--offline` never satisfies this row |
| Human/final gate | Task 01-04-01 automation starts hidden `-SuperviseInteractiveUat` and both visible clients; the blocking-human checkpoint observes only gameplay/normal exits, then Task 01-04-03 verifies the hashed COMPLETE receipt, supervisor exit, cleanup, and evidence |

## Sampling Rate

- **Task 01-01-01:** checksum-verify the official Temurin `25.0.4+7` x64 ZIP/sidecar; derive runtime/vendor/arch/home/executable hashes; derive origin/ref/commit/tree/diff/tuple/wrapper checksums from retained official Git; overwrite fresh logs and attach the mechanical Loom probe to every exact `help`/`build`/resolution rerun; classify only the same-run stream; and hash-compare the copied scaffold. This is the locked prerequisite exception; repository `build` is not sampled yet.
- **Task 01-01-02:** reselect the same checksum-bound JDK, run the first repository `help`, run `build` with a fresh Loom probe/log matching selected/resolved/SHA evidence, and inspect exact license, metadata, entrypoints, and item/model resources.
- **Task 01-02-01:** first create compiling production types with every final signature but deliberately make `allDisabled()` return all modules; then write the unchanged `allDisabledDisablesEveryModule` assertion and capture its expected-false/actual-true RED. Delete dedicated log/result files before each focused RED and GREEN `--rerun-tasks --info` invocation, attach the Loom probe to both, reject skip markers only on exact `:test`, require fresh method-specific JUnit XML, compare each selected/resolved/artifact-SHA tuple to evidence, then retain the separate probed full build. No GameTest claim exists yet.
- **Task 01-03-01:** under exact JDK, run fail-first/restored GameTest builds with fresh per-build Loom proof, require production JAR test exclusions/license, run audits, then commit/syntax/self-check the complete canonical-worktree, single-distribution, two-rule, interactive-receipt harness.
- **Task 01-04-01:** finish clean-worktree/dist/server/preflight gates, fail before checkpoint if elevation/isolation is unavailable, then start hidden committed `-SuperviseInteractiveUat` with a fresh ignored GUID session and wait only for atomic `ONLINE_READY` plus matching live supervisor/visible-client PIDs.
- **Task 01-04-02:** observe only in-game behavior and normally exit the already-open online client; wait for automation to launch isolated client, repeat, normally exit, and return eight values. The checkpoint invokes no command/setup/network/validation/cleanup/commit action.
- **Task 01-04-03:** consume eight values plus unique pointer/status/canonical-payload-hashed COMPLETE receipt, confirm two normal client exits, supervisor exit, distribution/probes/rules cleanup, write public-safe evidence, validate, and commit.
- **Before phase verification:** require `uat_status: PASS`, equal online/offline/distribution SHA-256, fresh matching Loom evidence for every build, exact two-rule probe/cleanup records, clean Git hygiene, and all task-specific gates below.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirements | Automated behavior | Automated command | Human | Status |
|---|---:|---:|---|---|---|---|---|
| 01-01-01 | 01 | 1 | FND-02, FND-03 | Official JDK archive/sidecar and exact runtime/vendor/arch/home/binary/path hashes pass; Git-derived official origin/ref/commit/tree and semantic one-file Loom diff pass; every rerun has a fresh same-run log/exit/classification; actual resolved Loom build/artifact hash, tuple, copied-file hashes, and wrapper checksum equal evidence | Direct Task 01-01-01 PowerShell verifier against retained `.work/toolchain`, fresh ignored command logs/probe, official checkout, and repository scaffold | none | ⬜ pending |
| 01-01-02 | 01 | 1 | FND-02, FND-03, FND-04 | First exact-JDK repository build freshly proves the frozen Loom resolved build/SHA and produces ordinary `developers-hell-0.1.0.jar` with exact license, entrypoints, and item/model resources | Task 01-01-02 direct PowerShell around exact JDK, `help`, probe-attached captured `build`, archive/source/Git gates | none | ⬜ pending |
| 01-02-01 | 02 | 2 | FND-02, FND-04 | Compiling final-signature skeleton deliberately returns all modules from `allDisabled()`; fresh probe-attached RED XML proves the exact expected-false/actual-true assertion, then corrected GREEN XML passes. Exact `:test` skip markers are rejected without misclassifying unrelated NO-SOURCE tasks; every invocation proves independent Loom selected/resolved/artifact-SHA equality and the full build repeats the freeze | Direct Task 01-02-01 PowerShell validates hash-bound RED log/XML/receipt, captures fresh GREEN XML and full-build log, and parses scoped task plus Loom proofs | none | ⬜ pending |
| 01-03-01 | 03 | 3 | FND-02, FND-03, FND-04 | Fail-first/restored GameTest builds each prove the frozen Loom artifact; production JAR license/exclusions, tasks/audits, committed probe, and complete distribution/isolation/receipt harness pass | Direct exact-JDK captured wrapper/probe plus `powershell.exe -File` script/self-check/negative validation | none | ⬜ pending |
| 01-04-01 | 04 | 4 | FND-01, FND-02, FND-03 | Automated gates pass, then elevated hidden supervisor starts a visible exact-distribution ONLINE client and hands off only at atomic ONLINE_READY with both PIDs live | Synchronous harness gate followed by direct `Start-Process -WindowStyle Hidden ... -SuperviseInteractiveUat` and bounded status/liveness assertions | none | ⬜ pending |
| 01-04-02 | 04 | 4 | FND-01, FND-03 | Eight in-game online/isolated observations and normal exits are returned; automation alone advances sessions/isolation | Observation-only `blocking-human`; no command, setup, network, file, validation, cleanup, or commit instruction | blocking human world UAT | ⬜ pending |
| 01-04-03 | 04 | 4 | FND-01, FND-03 | Eight values plus hashed COMPLETE supervisor receipt become public-safe evidence; both client exits, supervisor exit, distribution, probe, and rule/group cleanup are verified | Direct pointer/status/receipt/process/hash checks, then committed `-ValidateEvidence -RequireUatPass -SessionReceiptPath <unique receipt> -DistributionPath .\dist\developers-hell-0.1.0.jar` | none | ⬜ pending |

## Wave 0 Requirements

- [ ] The exact official Temurin `25.0.4+7` Windows x64 ZIP matches its official release sidecar, is extracted under ignored `.work/toolchain`, and machine-derived vendor/runtime/arch/home/path/binary hashes are recorded without a private absolute path.
- [ ] `.gitignore` is corrected before the wrapper is copied or invoked; `gradle/wrapper/gradle-wrapper.jar` is not ignored while build/run/world/EULA/log/IDE/dump/recording/local-JDK state is ignored.
- [ ] Exact official-template origin/ref/commit/tree, semantic Loom-only diff, Wrapper 9.5.1/checksums, Minecraft 26.2, Loader 0.19.3, Fabric API 0.158.0+26.2, Java/release 25, fixed-first/fallback category, commands, and numeric exits are machine-derived and cross-checked.
- [ ] Loader JUnit, GameTest metadata/source, production client/server tasks, and Fabric API `productionRuntimeMods` wiring are created by their assigned tasks before their validations run.
- [ ] Plan 03 commits `scripts/loom-resolution.init.gradle`, `scripts/audit-foundation.ps1`, and the complete distribution/two-rule/interactive `scripts/verify-foundation.ps1`; parser/self-check and nonexistent-evidence negative test pass before the final gate.
- [ ] Administrative control for two exact temporary Java/javaw firewall rules is available; otherwise execution fails closed rather than weakening the contract.

## Manual-Only Verification

| Behavior | Why automated proof is insufficient | Procedure | Owner |
|---|---|---|---|
| Exact distribution lists mods, enters a world, renders token, saves/quits, and exits normally online/isolated | GUI/world interaction cannot be inferred from logs | Automation opens online then isolated Minecraft through its hidden supervisor; Task 01-04-02 only observes and returns eight values. Task 01-04-03 consumes hashed receipt and confirms machine cleanup | Blocking-human checkpoint, then automated finalizer |

## Validation Sign-Off

- [ ] Every automated task has an automated command; Task 01-04-02 is the sole observation-only `blocking-human` checkpoint and intentionally has no mutation or automated verifier.
- [ ] No repository `build` is sampled before Task 01-01-02 creates the tracer.
- [ ] No GameTest is sampled before Task 01-03-01 creates the GameTest source/metadata.
- [ ] `ModuleGateTest` proves exact stable catalog identity/order under every required gate state.
- [ ] The server GameTest proves the live `developers_hell:foundation_token` registry key.
- [ ] GameTest execution has fail-first/restored evidence, while the ordinary production JAR has exactly one root `developers_hell` `fabric.mod.json`, exactly `LICENSE_developers-hell`, and excludes unrenamed/duplicate license plus all test/GameTest classes, `developers_hell_test`, and `fabric-gametest` metadata.
- [ ] The comprehensive audit runs in Plan 03 and repeats in Plan 04 across both source sets, direct declarations, the final JAR, side boundaries, repositories, and Git hygiene.
- [ ] Direct runtime declarations are limited to Minecraft, Fabric Loader, and Fabric API; Loader JUnit remains test-only, Fabric API is in `productionRuntimeMods`, and expected transitive Fabric/Minecraft modules are reported without false positives.
- [ ] A canonical GUID-temp-child detached worktree tracks every required input/probe, runs online/offline builds with fresh matching Loom resolution/SHA, copies their equal artifact to ignored `dist`, and is removed only by exact registered-target cleanup while pre-existing registrations/roots survive.
- [ ] Production server uses the exact distribution and reaches ready/clean stop online and under a unique exactly-two-rule Java/javaw block; hashes remain equal before/after.
- [ ] Evidence records group, two rule IDs, both program hashes, membership two, reachable/blocked probes, both-rule absence, and final group zero; Gradle cache mode is never substituted.
- [ ] Task 01-04-01 owns hidden-supervisor startup and ONLINE_READY handoff; Task 01-04-02 returns eight in-game PASS observations only; Task 01-04-03 verifies hashed COMPLETE receipt, both normal exits, supervisor exit, distribution hash and both-rule/group cleanup, then validates `uat_status: PASS`.
- [ ] Exactly eight unresolved specless fallback assumptions and exactly two descriptor-less unresolved prohibitions remain represented in the plan set.
