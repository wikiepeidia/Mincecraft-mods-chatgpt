---
phase: 01-java-25-and-fabric-26-2-foundation
plan: 03
subsystem: runtime-verification
tags: [fabric-gametest, production-runs, offline-audit, clean-worktree, minecraft-26.2]

requires:
  - phase: 01-02
    provides: Stable unconditional Foundation Token registry and immutable eight-module behavior gate
provides:
  - Wrapper-owned server GameTest proving the live Foundation Token registry key
  - Ordinary-JAR production client/server launch tasks bound to the checksum-verified Java 25 runtime
  - Fail-closed source, dependency, repository, archive, and Git-hygiene audit
  - Precommitted clean-worktree, offline parity, server/client isolation, UAT, and receipt verification harness
affects: [01-04-runtime-proof-and-uat, phase-2-configuration, phase-3-campaign]

actuals:
  tokens: 48051
  tasks: 1
  commits: 2

tech-stack:
  added: [Fabric API server GameTest source set, Loom production run tasks, Windows PowerShell verification harness]
  patterns: [fail-first in-runtime proof, ordinary-JAR production launch, exact direct-declaration allowlist, hash-bound clean-worktree verification]

key-files:
  created:
    - src/gametest/java/dev/developershell/gametest/FoundationGameTests.java
    - src/gametest/resources/fabric.mod.json
    - scripts/loom-resolution.init.gradle
    - scripts/audit-foundation.ps1
    - scripts/verify-foundation.ps1
  modified:
    - build.gradle

key-decisions:
  - "Wrapper build owns the real server GameTest; successful compilation or discovery alone is never accepted as registry proof."
  - "Production tasks launch the ordinary project JAR with exact Fabric API support from separate ignored profiles and never depend on remapJar."
  - "Direct dependency policy audits project declarations separately from fixed Loom injections and the report-only transitive runtime graph."
  - "Plan 04 must execute the already committed verification harness unchanged against one hash-bound distribution JAR."

patterns-established:
  - "Fail-first GameTest: preserve a fresh named assertion failure, then a fresh in-server restored pass under the same frozen Loom artifact."
  - "Offline-surface audit: scan arbitrary main/client text, side linkage, repositories, direct declarations, JAR contents, and Git dirt through one entrypoint."
  - "Clean verification handoff: self-check the manifest, canonical-path guards, exact firewall primitive, supervisor state machine, and receipt contract before runtime proof."

requirements-completed: [FND-02, FND-03, FND-04]

coverage:
  - id: D1
    description: "The live Minecraft item registry resolves the Foundation Token as developers_hell:foundation_token inside a wrapper-owned server GameTest."
    requirement: FND-04
    verification:
      - kind: integration
        ref: "src/gametest/java/dev/developershell/gametest/FoundationGameTests.java#foundationTokenIsRegistered"
        status: pass
      - kind: integration
        ref: ".work/plan03-final-restored-build-loom.log"
        status: pass
    human_judgment: false
  - id: D2
    description: "Production client/server tasks select Java 25, isolated profiles, the ordinary JAR, exact Fabric API, and the frozen 1.1.2/0.19.3/26.2 server tuple."
    requirement: FND-02
    verification:
      - kind: integration
        ref: "build.gradle#auditDirectDependencies"
        status: pass
      - kind: integration
        ref: ".\\gradlew.bat tasks --all and auditDirectDependencies"
        status: pass
    human_judgment: false
  - id: D3
    description: "Main/client runtime surfaces, side linkage, repositories, exact direct declarations, production archive contents, and Git hygiene fail closed."
    requirement: FND-03
    verification:
      - kind: security
        ref: "scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath build/libs/developers-hell-0.1.0.jar"
        status: pass
    human_judgment: false
  - id: D4
    description: "The committed Plan 04 harness owns clean detached builds, parity hashes, exact two-rule isolation, production launches, UAT supervision, and evidence validation."
    requirement: FND-02
    verification:
      - kind: integration
        ref: "scripts/verify-foundation.ps1 -SelfCheck"
        status: pass
      - kind: negative
        ref: "scripts/verify-foundation.ps1 -ValidateEvidence -EvidencePath <nonexistent>"
        status: pass
    human_judgment: false

duration: 29min
completed: 2026-08-25
status: complete
---

# Phase 1 Plan 03: Live GameTest, Production Tasks, and Offline-Surface Audit Summary

**A named fail-first server GameTest now proves the live Foundation Token registry while exact ordinary-JAR production tasks, comprehensive offline-surface auditing, and the precommitted Plan 04 verification harness all pass their automated gates.**

## Performance

- **Duration:** 29 min
- **Started:** 2026-08-25T20:18:44Z
- **Completed:** 2026-08-25T20:47:37Z
- **Tasks:** 1
- **Files modified:** 6

## Accomplishments

- Added a separate `developers_hell_test` GameTest mod whose server entrypoint resolves `ModItems.FOUNDATION_TOKEN` through the live item registry and requires `developers_hell:foundation_token`.
- Added exact Java-25 production client/server tasks over the ordinary project JAR, exact Fabric API runtime support, isolated profiles, and the fixed Installer/Loader/Minecraft tuple.
- Added one fail-closed audit for runtime network/remote-service surfaces, physical-side linkage, repositories, exact direct declarations, report-only transitives, production archive exclusions, wrapper integrity, and Git hygiene.
- Committed the full clean-worktree/offline-parity/runtime-isolation/UAT verification harness before Plan 04, including canonical path guards, exact two-rule firewall cleanup, a two-client supervisor, and canonical-payload receipt hashing.

## Task Commits

1. **Add live runtime proof** - `8e87296` (test)

**Plan metadata:** committed with this summary and sequential tracking updates.

## Files Created/Modified

- `build.gradle` - Wires server GameTests, exact production runtime mods/tasks, Java 25 launchers, and the fail-closed direct-declaration/production-task audit.
- `src/gametest/java/dev/developershell/gametest/FoundationGameTests.java` - Performs the live Foundation Token registry-key assertion through Fabric's official invoker lifecycle.
- `src/gametest/resources/fabric.mod.json` - Declares only the `developers_hell_test` `fabric-gametest` entrypoint.
- `scripts/loom-resolution.init.gradle` - Emits and validates the selected/resolved Loom component, implementation version, code-source artifact, and SHA-256.
- `scripts/audit-foundation.ps1` - Audits source, client/common separation, repositories, direct dependencies, transitive evidence, production archive, wrapper, and Git dirt.
- `scripts/verify-foundation.ps1` - Owns clean detached builds, online/offline/distribution equality, server/client runtime proof, firewall isolation, supervised UAT, and final evidence validation.

## Fail-First GameTest Evidence

- **RED:** wrapper `build` exited `1`; `developers_hell_test:foundation_game_tests_foundation_token_is_registered` failed on tick 0 because the deliberate expected key `developers_hell:not_foundation_token` observed the real `developers_hell:foundation_token`. RED log SHA-256 is `55c32932b5653cf8a897ffb931edb8bb1f8c13b6d1eb2c71822c6c7da79c36e2`.
- **GREEN:** the restored wrapper `build` exited `0`, executed `:runGameTest`, and the server reported `All 2 required tests passed :)`. Fresh GREEN log SHA-256 is `5a9606646071a91f40ad2b6ab70c7432eabdc6b16d0ccef675ce1235dde726be`.
- **Frozen Loom:** both streams selected/resolved/implemented Loom `1.17.19` from the same artifact SHA-256 `ad331736d7ee6cd5f21c45b19584b951c716ba5de8ace8662b42813d110452b8`.
- **Production archive:** `build/libs/developers-hell-0.1.0.jar` SHA-256 is `c33536aeb4999a57d40151255babbdaf16eb191ad0bcf68e2de359c037d1c4be`; it contains one production descriptor and renamed license with no GameTest/unit-test output or test identity.

## Audit and Harness Evidence

- Task discovery found `runGameTest`, `runProductionClient`, `runProductionServer`, and `auditDirectDependencies`.
- The direct audit emitted exactly five approved configuration/coordinate pairs and the `ordinary-jar|java25|isolated-runs|installer-1.1.2` production marker.
- Adversarial unknown-configuration, dynamic-version, and file-dependency injections each exited `1`; expected Fabric/Minecraft transitives remained report-only.
- The comprehensive final audit reported `PASS: FOUNDATION_AUDIT`; evidence SHA-256 is `6e30d6063a46f90bc64c7e8a0c1a187c653e60a33a3b86dcecbfccba1deb8a82`.
- Verification-harness syntax and `-SelfCheck` passed. Validation of a deliberately nonexistent evidence path exited `1`, as required.

## Decisions Made

- Kept the GameTest source set physically separate and relied on captured RED/GREEN execution rather than production-JAR inclusion for test proof.
- Modeled direct declarations at configuration time, exempting only exact fixed Loom injections and never applying the five-coordinate allowlist to resolved transitives.
- Bound production launchers and audit wrapper calls to the sole checksum-verified Temurin root with auto-detection and auto-download disabled.
- Kept every runtime mode tied to the ignored canonical distribution path; no runtime mode may rebuild or silently substitute another JAR.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected Loom artifact singleton selection**
- **Found during:** Task 1 probe validation
- **Issue:** Groovy's `LinkedHashSet` did not expose the assumed `single()` helper.
- **Fix:** Required exactly one artifact and selected it through the set iterator before code-source/hash validation.
- **Files modified:** `scripts/loom-resolution.init.gradle`
- **Commit:** `8e87296`

**2. [Rule 2 - Missing critical functionality] Closed direct-declaration audit escape paths**
- **Found during:** Task 1 adversarial dependency testing
- **Issue:** Loom injects fixed annotations/client outputs, while a custom declarable configuration could otherwise escape a narrow project-configuration list.
- **Fix:** Exempted only the fixed Loom-owned configurations/injections and audited every other nonempty declarable configuration; unknown, dynamic, and file declarations now fail.
- **Files modified:** `build.gradle`
- **Commit:** `8e87296`

**3. [Rule 1 - Bug] Propagated the exact Java toolchain into nested audit wrapper calls**
- **Found during:** Task 1 audit/harness integration
- **Issue:** `JAVA_HOME` alone did not satisfy the mandatory sole-installation/auto-detection-disabled Gradle contract.
- **Fix:** The audit independently validates the retained JDK hashes/path and passes all three required Gradle JVM properties to both wrapper calls.
- **Files modified:** `scripts/audit-foundation.ps1`
- **Commit:** `8e87296`

**4. [Rule 1 - Bug] Hardened adversarial audit coverage without binary false positives**
- **Found during:** Task 1 final static review
- **Issue:** Extension-whitelisted scans and narrow residue/private patterns could miss extensionless text, Analytics/Sentry variants, example/mixin classes, or key/certificate paths; forcing binary assets to text would create future false positives.
- **Fix:** Used unrestricted rg text-file detection, broadened the named patterns, and added adversarial regex probes while preserving normal binary skipping.
- **Files modified:** `scripts/audit-foundation.ps1`
- **Commit:** `8e87296`

**5. [Rule 1 - Bug] Removed duplicate production-server `nogui` argument**
- **Found during:** Task 1 resolved production-task inspection
- **Issue:** Loom already supplies `nogui`, so an explicit second argument duplicated it.
- **Fix:** Relied on Loom's single resolved `nogui` argument and re-proved the server tuple/task model.
- **Files modified:** `build.gradle`
- **Commit:** `8e87296`

## Authentication Gates

None.

## Known Stubs

None. `PENDING` values in the verification harness are intentional evidence-state sentinels that Plan 04 must replace with machine/runtime/UAT proof; they are validated state, not shipped gameplay placeholders.

## User Setup Required

None for this plan. Plan 04 owns elevated firewall control and visible client supervision when it executes the precommitted runtime modes.

## Next Phase Readiness

- `01-04-PLAN.md` can now invoke the committed `-PrimeAndCompare` mode from HEAD, produce the single ignored distribution JAR, run server/client isolation proof, supervise both visible clients, and finalize evidence without changing the harness.
- RED/GREEN, final build, final audit, and retained Java/Loom evidence remain in ignored `.work` paths for the runtime proof.

## Self-Check: PASSED

- All six implementation files and this summary exist.
- Task commit `8e87296` resolves to a commit object with no tracked-file deletions.
- RED, final GREEN, and final comprehensive-audit evidence still match the SHA-256 values recorded above.

---
*Phase: 01-java-25-and-fabric-26-2-foundation*
*Completed: 2026-08-25*
