---
phase: 01-java-25-and-fabric-26-2-foundation
plan: 02
subsystem: module-foundation
tags: [fabric-loader-junit, immutable-gate, stable-registry, minecraft-26.2, tdd]

requires:
  - phase: 01-01
    provides: Checksum-bound Java 25/Fabric 26.2 scaffold and unconditional Foundation Token tracer
provides:
  - Dedicated unconditional Foundation Token ID and item registry catalogs
  - Eight exact immutable behavior-module keys and one pure ModuleGate policy object
  - Fail-first Loader JUnit proof that module gates cannot change stable content identity
affects: [01-03-gametest-and-production-harness, phase-2-configuration, phase-5-module-independence]

actuals:
  tokens: 2589
  tasks: 1
  commits: 3

tech-stack:
  added: [Fabric Loader JUnit 0.19.3 test-only]
  patterns: [registration before behavior, immutable defensive-copy gate, compile-before-RED evidence]

key-files:
  created:
    - src/main/java/dev/developershell/registry/ModItemIds.java
    - src/main/java/dev/developershell/registry/ModItems.java
    - src/main/java/dev/developershell/module/ModuleId.java
    - src/main/java/dev/developershell/module/ModuleGate.java
    - src/test/java/dev/developershell/module/ModuleGateTest.java
    - src/main/resources/assets/developers_hell/lang/en_us.json
  modified:
    - build.gradle
    - src/main/java/dev/developershell/DevelopersHell.java

key-decisions:
  - "Stable item registration remains unconditional and has no dependency on ModuleGate; toggles can select behavior only."
  - "The eight serialized module keys are frozen as explicit snake-case enum values behind a defensive-copy, immutable gate."
  - "The final unit-test source was written once after the production compile gate and remained byte-identical through RED and GREEN."

patterns-established:
  - "Registration before behavior: common initialization invokes ModItems.initialize before any future behavior hook."
  - "Gate purity: ModuleGate depends only on Java collections and rejects null sets, members, and queries."
  - "Fail-first evidence: a focused assertion RED is hash-bound before the production-only fix and fresh full-suite GREEN."

requirements-completed: [FND-02, FND-04]

coverage:
  - id: D1
    description: "Foundation Token identity moved into dedicated unconditional registry catalogs without changing developers_hell:foundation_token."
    requirement: FND-04
    verification:
      - kind: unit
        ref: "src/test/java/dev/developershell/module/ModuleGateTest.java#stableCatalogIsExactImmutableAndIndependentOfEveryGate"
        status: pass
      - kind: integration
        ref: "01-02-PLAN.md embedded automated verifier archive and registry audit"
        status: pass
    human_judgment: false
  - id: D2
    description: "Exactly eight module keys share immutable all-enabled, all-disabled, and explicit behavior gates with null and defensive-copy guarantees."
    requirement: FND-04
    verification:
      - kind: unit
        ref: "src/test/java/dev/developershell/module/ModuleGateTest.java (8 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Loader JUnit 0.19.3 is test-only and the frozen Java 25/Loom 1.17.19 build packages the expanded ordinary production JAR."
    requirement: FND-02
    verification:
      - kind: integration
        ref: ".\\gradlew.bat build --no-daemon --init-script .work/loom-resolution.init.gradle"
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-08-25
status: complete
---

# Phase 1 Plan 02: Stable Registry and Module Gate Summary

**Dedicated Foundation Token registries and an immutable eight-module behavior gate now pass a hash-bound compile-before-RED, focused assertion RED, eight-test GREEN, and frozen-Loom production build.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-08-25T20:03:55Z
- **Completed:** 2026-08-25T20:13:19Z
- **Tasks:** 1
- **Files modified:** 8

## Accomplishments

- Extracted `developers_hell:foundation_token` from the common entrypoint into immutable `ModItemIds` and unconditional `ModItems` catalogs while retaining the existing two-file Minecraft 26.2 client resource path.
- Froze all eight anthology module keys and implemented pure all-on, all-off, explicit, null-safe, defensive-copy, and immutable-view behavior through `ModuleGate`.
- Added exact test-only Loader JUnit `0.19.3`; captured the intended expected-false/actual-true RED, restored an 8/8 GREEN, and built the ordinary production JAR with independent Loom resolution proof.

## Task Commits

The TDD task was committed at both mandatory gates:

1. **RED: Add the signature-complete registry/gate skeleton and final failing contract** - `d8cd229` (test)
2. **GREEN: Correct the deliberate all-disabled defect and package localization** - `9f76fe5` (feat)

**Plan metadata:** committed with this summary and sequential tracking updates.

## Files Created/Modified

- `build.gradle` - Adds exact Loader JUnit `0.19.3` under `testImplementation` and enables JUnit Platform.
- `src/main/java/dev/developershell/DevelopersHell.java` - Calls `ModItems.initialize()` unconditionally before any future behavior hook.
- `src/main/java/dev/developershell/registry/ModItemIds.java` - Owns the exact Foundation Token key and immutable one-entry catalog.
- `src/main/java/dev/developershell/registry/ModItems.java` - Owns the current 26.2 item construction and unconditional registration path.
- `src/main/java/dev/developershell/module/ModuleId.java` - Defines the eight exact stable snake-case module names.
- `src/main/java/dev/developershell/module/ModuleGate.java` - Provides the pure immutable behavior-only gate.
- `src/test/java/dev/developershell/module/ModuleGateTest.java` - Proves all gate, naming, null, copy, immutability, and stable-catalog invariants.
- `src/main/resources/assets/developers_hell/lang/en_us.json` - Localizes the Foundation Token.

## TDD Evidence

- **Compile-before-RED:** `compileJava --rerun-tasks` passed while the test path was absent; log SHA-256 `49687a11034cbace1588e9d3f56e19dd133528a17e7fff53c361f60d861b62d4`.
- **RED:** focused `allDisabledDisablesEveryModule` exited `1` with one assertion failure: `allDisabled must disable graduation_anyfail`, expected `false` but was `true`. RED log SHA-256 is `13247f998751d3a3b5138c1e429e424f880f3fe4a19321cba1098edcf9d85431`; copied JUnit XML SHA-256 is `9242dc203311e3ca407ee884f2696d622752f832d7766460bf07e1388291e270`.
- **Frozen test source:** SHA-256 `3d09e054a7b9c1aa66cea1c37c2655183417a1d4da8f89b5a342c90e6a53852c` in the RED receipt and after every GREEN/build verifier.
- **GREEN:** 8 tests, 0 failures, 0 errors; the plan's embedded verifier reran the suite freshly and passed.
- **Build:** ordinary `build/libs/developers-hell-0.1.0.jar` SHA-256 `c33536aeb4999a57d40151255babbdaf16eb191ad0bcf68e2de359c037d1c4be`; registry/module classes and localization are packaged while test output is absent.
- **Loom:** compile, RED, GREEN, and full build independently selected/resolved `1.17.19` with artifact SHA-256 `ad331736d7ee6cd5f21c45b19584b951c716ba5de8ace8662b42813d110452b8`.

## Decisions Made

- Kept content identity and behavior selection as separate types: registry classes cannot import, accept, or query `ModuleGate`.
- Used an enum with explicit serialized names instead of deriving save/config keys from Java constant names, making the eight public keys deliberate and stable.
- Used immutable Java collection snapshots only; Phase 1 adds no configuration parser, Fabric registry dependency, client dependency, filesystem, network, clock, or random source to the gate.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- A supplemental acceptance-report timestamp check initially double-converted a `ConvertFrom-Json` date through the local timezone. The report was corrected to parse the receipt's raw ISO-8601 value as `DateTimeOffset`; the plan's verifier, RED receipt, product code, and test results were unaffected.

## Authentication Gates

None.

## Known Stubs

None. `ModItems.initialize()` is the intentional class-initialization trigger that performs registration before returning.

## User Setup Required

None - no external service configuration is required.

## Next Phase Readiness

- Ready for `01-03-PLAN.md` to add the real registry GameTest, production launch tasks, source/dependency audit, and offline/distribution harness.
- The ignored exact-JDK, RED/GREEN receipt, and Loom probe evidence remain available for Plan 03.

## Self-Check: PASSED

- All eight implementation/resource files and this summary exist.
- RED commit `d8cd229` and GREEN commit `9f76fe5` resolve to commit objects.
- The test source still matches the RED receipt SHA-256, and the embedded plan verifier passed after both commits.

---
*Phase: 01-java-25-and-fabric-26-2-foundation*
*Completed: 2026-08-25*
