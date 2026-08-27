---
status: resolved
trigger: "Authoritative -Verify exits: Foundation audit returned non-zero exit 1."
created: 2026-08-27T07:08:39.2378628Z
updated: 2026-08-27T07:50:56.4616292Z
---

## Current Focus

hypothesis: Confirmed and human-verified: the exact mixin contract plus bounded exact root re-observation remove both sequential verifier failures without weakening the audit or owned-process boundary.
test: Authoritative clean -Verify on main f68a8a404c5e1318c2c860cff08e03951b715b4b.
expecting: Exit 0 after every gate, atomic promotion, an owned server root/child receipt, and a promoted SHA-256.
next_action: None — resolved and archived; commit this record with the knowledge-base entry under commit_docs=true.
bug_class: concurrency
known_pattern_candidate: Undefaulted optional or empty valid/falsy value passed into a mandatory filesystem LiteralPath parameter.
reasoning_checkpoint:
  hypothesis: The audit child completes while owned-process polling samples its lifecycle; Win32_Process can expose one root row with blank ExecutablePath, and Get-OwnedProcessTree immediately passes that blank to Resolve-Path instead of requerying the exact PID/start-time identity.
  confirming_evidence:
    - The authoritative run refreshed the build JAR and 55-test GameTest receipt but did not touch the production-server profile or log, localizing failure to the post-build/audit owned-process boundary before server initialization.
    - A controlled blank root CIM row passed through the unchanged Get-OwnedProcessTree reproduces the exact System.Management.Automation.ParameterBindingValidationException and message; the same row with the canonical nonempty executable returns one valid snapshot.
    - The complete call-site audit found line 899 is the only post-audit LiteralPath consumer fed by an unchecked possibly-empty producer; descendant process rows explicitly requery blanks before Resolve-Path.
  falsification_test: This diagnosis is false if the unchanged function does not reproduce the exact error from a blank root row, or if the patched function ever calls Resolve-Path on blank, accepts a PID without exact start-time plus canonical executable identity, or repeats the same failure in the parent clean -Verify run.
  fix_rationale: A bounded re-observation at the ownership boundary handles transient incomplete CIM data without weakening ownership: it distinguishes an exited root, requires the original start ticks, resolves only a nonblank path, compares the canonical executable, and fails closed after the bound.
  blind_spots: The original run did not retain its transient CIM row, so the exact OS timing cannot be replayed; localization instead combines the unique unchecked call, stage timestamps, exact controlled reproduction, and a nonempty control. This worktree cannot run the publishing full -Verify because the debug session is intentionally untracked and publication is forbidden; the parent will rerun it cleanly.
  candidate_causes:
    - code: Get-OwnedProcessTree checks the root row count but omits the blank-path bounded requery used for descendants before Resolve-Path.
    - environment: a short-lived owned PowerShell process can transition while separate Process and Win32_Process observations are sampled, producing incomplete CIM identity data.
    - config: empty EvidencePath or DistributionPath was considered but eliminated because Resolve-SafeRepositoryPath canonicalizes nonempty absolute defaults before Verify mode.
    - data: missing or malformed publication destinations were considered but eliminated because the failure precedes production-server initialization and publication, with canonical destinations already resolved.
  and_gate: yes - both the transient/incomplete root observation and the missing code guard are required; a complete row passes unchanged, while a blank row becomes the raw LiteralPath failure only through the unguarded root branch.
tdd_checkpoint: null

## Symptoms

expected: The Java 25 -Verify harness completes all automated gates and atomically publishes a new JAR/evidence pair.
actual: The harness exits before publication when its foundation audit subprocess returns exit 1.
errors: "FAIL: Developer's Hell lecture verification harness: Foundation audit returned non-zero exit 1."
reproduction: Run scripts/verify-lecture.ps1 -Verify with the pinned repository Temurin 25 toolchain, lecture evidence path, and dist JAR path from clean main f7d1c55.
started: First observed immediately after integrating the iteration-3 reward lifecycle fixes; the isolated pinned Gradle clean/test/GameTest/audit/build gate had passed.

## Eliminated

- hypothesis: The audit fails because the lecture harness does not propagate the retained Java 25 runtime.
  evidence: The standalone exact audit used the retained Temurin 25.0.4+7 path, passed PREREQUISITES and both Gradle sections, and still failed only PRODUCTION_ARCHIVE.
  timestamp: 2026-08-27T07:47:00.0000000Z

- hypothesis: The audit fails because rg is unavailable in the child PowerShell environment.
  evidence: The exact standalone child resolved rg.exe and passed PREREQUISITES plus every recursive source scan before the archive-only failure.
  timestamp: 2026-08-27T07:47:00.0000000Z

- hypothesis: The new exception is caused by an empty EvidencePath, DistributionPath, or publication staging path.
  evidence: Both optional inputs are replaced by nonempty defaults and resolved as exact absolute repository children before Verify mode; retained server files were not updated, so the failing run never reached the later publication transaction.
  timestamp: 2026-08-27T09:18:00.0000000Z

- hypothesis: The new exception is caused by an empty ComSpec, PSHOME, or SystemRoot-derived Start-OwnedProcess input.
  evidence: ComSpec and SystemRoot/conhost resolved to existing nonempty paths. The PowerShell 7 sibling powershell.exe path is nonempty but absent and Resolve-Path is explicitly silent there; none of these expressions pass an empty string to LiteralPath in the reproduced environment.
  timestamp: 2026-08-27T09:18:00.0000000Z

## Evidence

- timestamp: 2026-08-27T07:08:39.2378628Z
  checked: Publication boundary after the failed -Verify run.
  found: The harness reported only the foundation-audit exit and did not report successful atomic publication.
  implication: Treat the existing dist/evidence pair as unchanged and stale until a full rerun passes.

- timestamp: 2026-08-27T07:24:00.0000000Z
  checked: Configured gsd-debugger skills and project-local skill directories.
  found: The configured agent-skill query returned no injected skills, and neither .codex/skills nor .agents/skills was present in the repository.
  implication: No additional project skill rules constrain this investigation beyond AGENTS.md and the GSD debugger protocol.

- timestamp: 2026-08-27T07:24:00.0000000Z
  checked: .planning/debug/knowledge-base.md.
  found: The knowledge-base file does not exist.
  implication: Phase-0 known-pattern recall has no durable local match; investigate the current failure directly.

- timestamp: 2026-08-27T07:27:00.0000000Z
  checked: Historical Developer's Hell foundation rollout.
  found: The earlier verifier required an absolute canonical rg.exe path, and detached PowerShell had previously lacked rg and Get-FileHash; .NET SHA-256 plus attestation self-tests were the eventual working path.
  implication: Missing subprocess tool binding is a high-priority environment/config candidate, but must be checked against the current script and reproduced rather than assumed.

- timestamp: 2026-08-27T07:33:00.0000000Z
  checked: Current repository identity and worktree status.
  found: HEAD is f7d1c559f55cc49363480105bb2b49cf2b4e2baa as scoped; the only visible change is the untracked .planning/debug session directory.
  implication: Code is at the requested baseline. A full -Verify rerun would now be confounded by its clean-worktree gate, so isolate the audit subprocess first.

- timestamp: 2026-08-27T07:33:00.0000000Z
  checked: verify-lecture.ps1 foundation-audit call sites.
  found: Invoke-BoundedFoundationAudit invokes scripts/audit-foundation.ps1 with -SourceAndDependencies and build/libs/developers-hell-0.1.0.jar; Assert-FoundationAuditGreen throws the reported message on any nonzero exit.
  implication: The wrapper symptom hides the audit section that failed; capturing the standalone audit's complete output is the correct observability-first test.

- timestamp: 2026-08-27T07:33:00.0000000Z
  checked: Failure repeatability classification.
  found: The reported audit exit is a fixed subprocess result at a pinned commit/toolchain boundary, with no timing or interleaving symptom.
  implication: Classify as a Bohrbug and use deterministic reproduction/working-backwards; SBFL is inapplicable because this is a PowerShell audit gate rather than a failing covered test.

- timestamp: 2026-08-27T07:42:00.0000000Z
  checked: Complete verify-lecture.ps1 and audit-foundation.ps1 implementations.
  found: Get-VerifiedJdk rejects any JAVA_HOME other than the retained checksum-bound .work JDK, prepends its bin directory to Path, and Start-OwnedProcess inherits that environment. The audit independently resolves rg from PATH, runs two pinned Gradle reports, scans source/archive, and emits named section failures before exit 1.
  implication: The reported wrapper error is a loss-of-observability problem. A standalone audit run can identify the actual failing invariant without changing publication state.

- timestamp: 2026-08-27T07:47:00.0000000Z
  checked: Exact standalone foundation audit under retained Temurin 25.0.4+7.
  found: JAVA_HOME and rg resolved successfully; PREREQUISITES, source scans, client linkage, repositories, direct dependencies, runtime classpath, and Git hygiene all passed. PRODUCTION_ARCHIVE alone failed on "Example or mixin residue found in production archive: dev/developershell/mixin/", and the audit exited 1.
  implication: Environment/tool-path candidates are eliminated for this reproduction. The defect lies in either unintended mixin content in the JAR or an obsolete blanket archive rule.

- timestamp: 2026-08-27T07:52:00.0000000Z
  checked: Production JAR mixin entries, source mixins, mixin config, fabric.mod.json, and introducing commit fb91fda.
  found: The JAR contains exactly InventoryRewardDropMixin.class, ServerLevelRewardAdmissionMixin.class, and developers_hell.mixins.json. The required JAVA_25 config lists exactly those classes; fabric.mod.json registers it; commit fb91fda added them with four owner Q-drop/death-drop GameTests and reward-transfer compensation logic.
  implication: The directory is intentional production behavior, not an example/template artifact. The remaining question is whether the foundation audit policy simply predates this reviewed production hook.

- timestamp: 2026-08-27T07:58:00.0000000Z
  checked: Git provenance of the failing regex and fresh GameTest XML receipt.
  found: The blanket mixin ban came from Phase 1 commit 8e87296 on 2026-08-26; the two production mixins arrived later in fb91fda on 2026-08-27 without an audit update. The fresh 07:07 UTC GameTest receipt contains all four owner Q-drop/death-drop mixin-backed cases as passing testcase nodes and no failure/error/skipped child nodes.
  implication: The stale-policy hypothesis is confirmed. The regression is an audit/code-contract mismatch introduced when a new reviewed production seam was added without evolving the Phase 1 archive allowlist.

- timestamp: 2026-08-27T08:19:00.0000000Z
  checked: Patched script syntax, diff shape, and exact target audit.
  found: PowerShell AST parsing reported zero errors; git diff --check found no whitespace error; the change is additive contract validation rather than behavior deletion; the exact standalone audit now passes all eight sections and FINAL_RESULT with exit 0.
  implication: The original subprocess symptom is green. Negative boundary mutations are still required to prove the new allowlist fails closed rather than suppressing archive validation.

- timestamp: 2026-08-27T08:25:00.0000000Z
  checked: Specified-oracle boundary mutations around the exact mixin artifact/config contract.
  found: Missing ServerLevelRewardAdmissionMixin, added UnexpectedRewardMixin, required=false config, and added com/example/ExampleMod.class each returned exit 1 with the expected PRODUCTION_ARCHIVE rejection; the unmodified JAR returned exit 0.
  implication: The fix distinguishes the valid exact set from N-1, N+1, invalid-config, and adjacent example-residue cases. It does not weaken the archive gate into a blanket mixin acceptance.

- timestamp: 2026-08-27T08:31:00.0000000Z
  checked: Revert-and-reconfirm in a disposable detached HEAD worktree.
  found: Untouched HEAD f7d1c55 running the pre-fix audit against the same build JAR returned exit 1 on the original dev/developershell/mixin/ residue error; the disposable worktree was removed. Reapplying was represented by the unchanged patched main worktree, which already returned exit 0.
  implication: The bug returns without this source change and disappears with it, establishing direct causality without touching the protected stash or publishing artifacts.

- timestamp: 2026-08-27T08:39:00.0000000Z
  checked: Adjacent pinned Java 25 gates and post-build target audit.
  found: verify-lecture.ps1 -SelfCheck exited 0; the retained-JDK offline clean test runGameTest auditDirectDependencies build transaction completed BUILD SUCCESSFUL with all 55 required GameTests passing; the patched foundation audit then passed all sections on the newly built JAR with exit 0.
  implication: The audit fix survives a clean rebuild and does not regress the Java tests, runtime mixin loading, GameTests, dependency allowlist, archive assembly, or verifier self-check.

- timestamp: 2026-08-27T08:45:00.0000000Z
  checked: Nested/anonymous mixin companion boundary and final repository scope.
  found: An added InventoryRewardDropMixin$1.class returned exit 1 under the exact artifact set. All temporary fixtures and the detached worktree were removed; only scripts/audit-foundation.ps1 and .planning/debug are visible; HEAD remains f7d1c55; no additional worktree exists; no Stryker configuration is present.
  implication: Unapproved mixin companion classes fail closed, the manual mutation matrix substitutes for unavailable PowerShell/Stryker tooling, and the fix remains isolated to the requested audit source plus its persistent debug state.

- timestamp: 2026-08-27T09:00:00.0000000Z
  checked: Parent clean-worktree full -Verify result on committed audit fix 2b795c6.
  found: The verifier passed beyond the original foundation audit, then exited 1 after about 35 seconds with "Cannot bind argument to parameter 'LiteralPath' because it is an empty string" reported by the top-level catch at verify-lecture.ps1 line 2174; no dist/evidence publication was reported.
  implication: The original audit fix is confirmed in the authoritative workflow, but a later deterministic path/cleanup contract now blocks the same end-to-end verifier transaction. Publication artifacts remain untrusted and out of scope.

- timestamp: 2026-08-27T09:05:00.0000000Z
  checked: Complete 2,176-line verify-lecture.ps1 implementation and every LiteralPath call site.
  found: The catch at line 2174 only formats the exception. In the post-audit process path, Get-OwnedProcessTree line 899 calls Resolve-Path directly on rootRows[0].ExecutablePath after checking only row count; descendant rows at lines 912-920 explicitly detect a blank ExecutablePath and requery before their Resolve-Path call. Canonical verify/publication file inputs are resolved to nonempty absolute repository children before use.
  implication: The asymmetric root-row identity handling is the only observed post-audit LiteralPath call whose producer can legitimately return an empty string during CIM process observation. It is a specific falsifiable candidate, not yet a confirmed root cause.

- timestamp: 2026-08-27T09:12:00.0000000Z
  checked: Retained receipt timestamps and environment-derived process paths after the authoritative failure.
  found: The production JAR and GameTest XML were freshly updated at 07:27-07:28Z, while run/production-server files and latest.log remained at 05:05Z; ComSpec and conhost were existing nonempty paths, and the PSHOME-derived Windows PowerShell candidate was a nonempty missing path handled with SilentlyContinue.
  implication: The failure occurred after the Gradle transaction and before production-server initialization/publication. Empty configured artifact destinations and empty environment executable inputs do not explain the LiteralPath binder message.

- timestamp: 2026-08-27T09:15:00.0000000Z
  checked: Controlled Get-OwnedProcessTree reproduction using the production function AST and a synthetic exact root CIM row.
  found: ExecutablePath='' deterministically raised System.Management.Automation.ParameterBindingValidationException with the exact message "Cannot bind argument to parameter 'LiteralPath' because it is an empty string." Changing only ExecutablePath to the current canonical pwsh.exe path returned one valid Root snapshot.
  implication: The line-899 root producer/consumer pair is sufficient to produce the reported failure, while the nonempty boundary control disproves unrelated process-tree logic as necessary.

- timestamp: 2026-08-27T09:18:00.0000000Z
  checked: Git provenance and concurrency taxonomy checklist.
  found: Commit c2f59ce introduced both the unguarded root Resolve-Path and a guarded descendant requery. The owned-root check is non-atomic across HasExited, Process.StartTime, CIM row retrieval, and path resolution; process termination can interleave with observation, while no lock/deadlock mechanism is involved.
  implication: Classify the new defect as a concurrency/order observation race. The fix must preserve exact start-time/executable ownership, retry only within a bound, distinguish exit from unverifiable identity, and fail closed rather than trusting PID alone.

- timestamp: 2026-08-27T09:28:00.0000000Z
  checked: Minimal owned-root implementation and deterministic regression delta.
  found: Get-VerifiedOwnedRootObservation now bounds observation to three attempts, rechecks HasExited, requires the original start ticks, never resolves blank, canonicalizes and compares the expected executable, and fails closed on persistent incompleteness. Get-OwnedProcessTree verifies the root before enumerating descendants and retains the existing descendant cleanup/identity checks. SelfCheck covers blank-then-complete, empty/whitespace persistent blank, valid first row, PID/start mismatch, canonical path mismatch, and already-exited root.
  implication: The code delta directly targets the root ownership boundary and adds specified-oracle neighbors without weakening child cleanup or accepting PID-only ownership; verification remains pending.

- timestamp: 2026-08-27T09:31:00.0000000Z
  checked: First AST/diff/SelfCheck verification pass under retained JDK 25 and Windows PowerShell 5.1.
  found: AST parsing and git diff --check passed, but SelfCheck failed under StrictMode with "The property 'Count' cannot be found" when the helper's conditional empty-array expression was pipeline-unrolled to null.
  implication: The root-identity design is not yet accepted. Normalize the empty row collection explicitly for Windows PowerShell 5.1, then rerun the unchanged target test.

- timestamp: 2026-08-27T09:36:00.0000000Z
  checked: Corrected AST/diff/SelfCheck verification pass under retained JDK 25 and Windows PowerShell 5.1.
  found: AST parsing reported zero errors, git diff --check passed, and verify-lecture.ps1 -SelfCheck passed all canonical path, freshness, archive, evidence, shutdown, publication mutation, and new owned-root observation cases.
  implication: The deterministic regression is green on the verifier's native Windows PowerShell runtime. Real short-lived child lifecycle stress and fix-acceptance guardrails remain.

- timestamp: 2026-08-27T09:40:00.0000000Z
  checked: Bounded real-process lifecycle stress through patched Start-OwnedProcess, Get-OwnedProcessTree, and Complete-OwnedProcess.
  found: Twenty-five short-lived Windows PowerShell children completed with exit 0 and zero verifier failures; no raw LiteralPath error or owned-process residue was observed.
  implication: The bounded observer remains stable under repeated real process-start/exit interleavings, not only synthetic row inputs.

- timestamp: 2026-08-27T09:43:00.0000000Z
  checked: Exact patched Get-OwnedProcessTree controlled target matrix.
  found: A blank first root CIM row followed by the canonical row recovered in exactly two queries and returned one Root snapshot; a persistently blank live row made exactly three bounded queries and failed with the explicit complete-identity error, never the LiteralPath binder exception.
  implication: The original minimized seed is green through the full production call, its valid neighbor remains accepted, and the persistent-incomplete neighbor remains fail closed.

- timestamp: 2026-08-27T09:47:00.0000000Z
  checked: Mutation tooling, four manual fix-site mutants, and source diff shape.
  found: No Stryker or PowerShell mutation configuration exists. Helper-level mutants disabling the blank guard, start-tick guard, canonical-path guard, and persistent-incomplete fail-closed throw were each killed by the corresponding deterministic regression oracle. The source diff is one file with 181 additions and 20 replacements/removals, consisting of the bounded observer, root routing, and SelfCheck cases rather than behavior deletion.
  implication: Mutation tooling degrades explicitly to four killed manual mutants; the no-op/deletion guard passes and the regression asserts ownership semantics rather than only suppressing the exception.

- timestamp: 2026-08-27T09:50:00.0000000Z
  checked: In-memory committed-HEAD versus patched-working-tree counterfactual on the identical blank-first root input.
  found: Committed HEAD raised the exact ParameterBindingValidationException and LiteralPath empty-string message; the patched source queried twice and returned one Root snapshot with the canonical pwsh.exe executable.
  implication: Revert-and-reconfirm passes without touching the protected stash or filesystem: this exact source delta, not an environment change, removes the original failure while preserving exact executable identity.

- timestamp: 2026-08-27T09:55:00.0000000Z
  checked: Cross-shell and adjacent pinned-toolchain gates.
  found: PowerShell 7 -SelfCheck passed; Windows PowerShell 5.1 -SelfCheck had already passed; and retained Temurin 25 offline Gradle test completed BUILD SUCCESSFUL with all five tasks up to date/green.
  implication: The regression is shell-compatible across the available verifier hosts, and adjacent Java unit/build configuration remains intact.

- timestamp: 2026-08-27T09:58:00.0000000Z
  checked: Atomic source commit and final worktree scope.
  found: Commit f68a8a404c5e1318c2c860cff08e03951b715b4b contains exactly scripts/verify-lecture.ps1 with 181 additions and 20 replacements/removals. The only remaining visible worktree item is the untracked .planning/debug session; the protected stash was not accessed and no dist/evidence publication was performed.
  implication: The source fix is ready for the parent clean full -Verify checkpoint. The session must remain unarchived until that end-to-end result is confirmed.

- timestamp: 2026-08-27T07:50:56.4616292Z
  checked: Authoritative human-verification checkpoint for the clean full verifier on main f68a8a404c5e1318c2c860cff08e03951b715b4b.
  found: The parent reported exit 0 with "PASS: fresh Phase 2 artifact promoted after all gates; server root PID 32752, captured owned children 6, SHA-256 768723bb534b5e35553a9b714f23c416c838b3688791a552cdf0bc91fdebf423" and explicitly confirmed the debug checkpoint could be finalized.
  implication: The original end-to-end failure is resolved in the authoritative clean workflow; the promoted artifact/evidence remain outside this archive-only scope.

## Resolution

root_cause: The end-to-end verifier had two sequential defects: (1) the Phase 1 foundation archive audit deterministically rejected the later reviewed reward mixins; and (2) after that fix, an owned audit process lifecycle/CIM observation could yield a blank root ExecutablePath while Get-OwnedProcessTree from c2f59ce passed it directly to Resolve-Path without a bounded exact PID/start-time requery, producing the raw LiteralPath binder failure.
fix: The foundation audit exact mixin contract is committed in 2b795c6. Added a bounded exact-identity root observation helper to scripts/verify-lecture.ps1, routed process-tree enumeration through it before child inference, and added deterministic SelfCheck regression/boundary cases for blank, incomplete, mismatched, valid, and exited observations.
verification:
  target_test:
    result: pass
    check: Exact Get-OwnedProcessTree blank-first reproduction recovers in two observations; persistent blank fails explicitly; Windows PowerShell 5.1 and PowerShell 7 SelfCheck both exit 0.
  mutation_check:
    result: skipped
    reason_if_skipped: No Stryker or PowerShell mutation framework is configured; four manual root-identity mutants were executed instead.
    mutant_killed: true
    manual_mutants_killed: [blank_guard_disabled, start_tick_guard_disabled, canonical_path_guard_disabled, persistent_incomplete_accepts_null]
  no_op_deletion:
    result: pass
    deletion_justified_by_rca: false
    evidence: The one-file diff adds a bounded verifier and specified-oracle SelfCheck cases, routing root ownership through stricter start-time and canonical executable checks rather than deleting or bypassing cleanup.
  adjacent_tests:
    result: pass
    suites_run: [windows-powershell-5.1-SelfCheck, powershell-7-SelfCheck, real-owned-process-stress-25-of-25, pinned-java-25-gradle-test, ast-parse, diff-check]
  revert_and_reconfirm:
    result: pass
    bug_returned_on_revert: true
    fixed_on_reapply: true
    evidence: In-memory committed HEAD returned the exact LiteralPath empty-string binder exception on the blank-root seed; the patched working tree reobserved once and returned one canonical Root snapshot on the same seed.
  guardrail_verdict: accepted
  parent_full_verify:
    result: pass
    branch: main
    commit: f68a8a404c5e1318c2c860cff08e03951b715b4b
    exit_code: 0
    check: "PASS: fresh Phase 2 artifact promoted after all gates; server root PID 32752, captured owned children 6, SHA-256 768723bb534b5e35553a9b714f23c416c838b3688791a552cdf0bc91fdebf423"
  human_verification:
    result: confirmed
    source: authoritative parent checkpoint response
oracle_type: specified
files_changed: [scripts/audit-foundation.ps1, scripts/verify-lecture.ps1]

## Prevention

five_whys:
  code_contract_branch:
    - The foundation gate exited 1 because it classified every production mixin path as template residue.
    - The path was present because reviewed reward integrity behavior later required two production mixins.
    - The audit stayed stale because its Phase 1 blanket prohibition was not evolved into an exact artifact/config contract when that supported hook was introduced.
  process_observation_branch:
    - The later verifier run failed because Resolve-Path received an empty root ExecutablePath.
    - The value could be empty because Process and Win32_Process observations are non-atomic while a short-lived owned audit process exits.
    - The raw binder exception escaped because the root branch checked only row count, unlike the descendant branch's bounded incomplete-identity requery.
  verification_branch:
    - Targeted GameTests proved the reward behavior but did not exercise the production archive policy that classified the implementing classes.
    - The verifier SelfCheck covered shutdown and ownership invariants but had no blank-first root CIM boundary case before this incident.
and_gate: The archive-policy mismatch is independent; the LiteralPath failure required both transient incomplete environment data and the missing root code guard.
why_not_caught: The full -Verify gate eventually caught both defects, but the mixin integration did not run an exact archive-contract regression and verifier SelfCheck lacked blank-first/persistently-blank root observation cases.
recurrence_guard: scripts/audit-foundation.ps1 now enforces the exact approved mixin class/config set and rejects N-1, N+1, required=false, nested-companion, and example-residue mutations; scripts/verify-lecture.ps1 -SelfCheck now covers blank-then-complete, persistent blank/whitespace, valid, PID/start mismatch, executable mismatch, and exited-root observations through the bounded exact-identity helper.
