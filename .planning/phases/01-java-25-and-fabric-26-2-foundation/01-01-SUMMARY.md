---
phase: 01-java-25-and-fabric-26-2-foundation
plan: 01
subsystem: build-foundation
tags: [minecraft-26.2, fabric, java-25, gradle-9.5.1, loom-1.17.19]

requires: []
provides:
  - Checksum-bound Eclipse Temurin 25.0.4+7 and first-party Fabric 26.2 scaffold evidence
  - Frozen Loom 1.17.19 and committed Gradle 9.5.1 wrapper
  - Side-split developers_hell entrypoints and unconditional Foundation Token registration
  - Ordinary developers-hell-0.1.0.jar with current 26.2 item/model resources
affects: [01-02-registry-and-module-gate, 01-03-gametest-and-production-harness, 01-04-offline-runtime-proof]

actuals:
  tokens: 6661
  tasks: 2
  commits: 3

tech-stack:
  added: [Eclipse Temurin 25.0.4+7, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Fabric Loom 1.17.19, Gradle Wrapper 9.5.1]
  patterns: [checksum-bound local JDK, mechanical Loom resolution probe, split environment source sets, registration before behavior]

key-files:
  created:
    - gradlew.bat
    - gradle/wrapper/gradle-wrapper.jar
    - .planning/phases/01-java-25-and-fabric-26-2-foundation/01-TOOLCHAIN-EVIDENCE.md
    - src/main/java/dev/developershell/DevelopersHell.java
    - src/client/java/dev/developershell/client/DevelopersHellClient.java
    - src/main/resources/fabric.mod.json
  modified:
    - .gitignore
    - settings.gradle
    - gradle.properties
    - build.gradle

key-decisions:
  - "Fixed Fabric Loom 1.17.19 passed every untouched-template probe, so the moving 1.17-SNAPSHOT fallback was not used."
  - "The Foundation Token is registered unconditionally from common initialization; the client entrypoint has no authoritative behavior."
  - "The first visible item reuses vanilla paper through Minecraft 26.2's two-JSON client-item path, avoiding an unnecessary custom texture in the foundation."

patterns-established:
  - "Exact toolchain binding: every wrapper process selects one checksum-verified ignored Temurin root with auto-detection and auto-download disabled."
  - "Registration before behavior: stable registry identity is common-side and unconditional, while client behavior remains physically isolated."
  - "Fresh resolution evidence: probe-attached builds derive Loom request, resolved component, code-source artifact, and SHA-256 from each invocation."

requirements-completed: [FND-02, FND-03, FND-04]

coverage:
  - id: D1
    description: "The official Fabric 26.2 scaffold builds through a checksum-bound Java 25 and frozen wrapper/dependency tuple."
    requirement: FND-02
    verification:
      - kind: integration
        ref: "01-01-PLAN.md Task 1 automated verifier"
        status: pass
    human_judgment: false
  - id: D2
    description: "One ordinary production JAR packages physically separated common and client entrypoints without common-side client imports."
    requirement: FND-03
    verification:
      - kind: integration
        ref: "01-01-PLAN.md Task 2 automated verifier and tracer feedback rerun"
        status: pass
    human_judgment: false
  - id: D3
    description: "developers_hell:foundation_token is registered unconditionally and packages the current 26.2 client item/model resource chain."
    requirement: FND-04
    verification:
      - kind: integration
        ref: "01-01-PLAN.md Task 2 archive and source audit"
        status: pass
    human_judgment: false

duration: 17min
completed: 2026-08-25
status: complete
---

# Phase 1 Plan 01: Proven Scaffold and Foundation Token Tracer Summary

**Checksum-bound Java 25/Fabric 26.2 build with fixed Loom 1.17.19 now produces a side-split ordinary JAR containing one unconditional, client-visible Foundation Token.**

## Performance

- **Duration:** 17 min
- **Started:** 2026-08-25T19:38:01Z
- **Completed:** 2026-08-25T19:55:20Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments

- Verified the exact official Temurin `25.0.4+7` Windows x64 archive against its release sidecar, bound both Java binaries and the redacted canonical-path hash, and committed wrapper-safe repository hygiene.
- Proved FabricMC's actual `26.2` template at commit `34080f0b6644dd726519d578f339f8e4e50ad331` with fresh `help`, `build`, and resolution streams; fixed Loom `1.17.19` resolved consistently to artifact SHA-256 `ad331736d7ee6cd5f21c45b19584b951c716ba5de8ace8662b42813d110452b8`.
- Built `build/libs/developers-hell-0.1.0.jar` with common/client entrypoints, exact `LICENSE_developers-hell`, unconditional `developers_hell:foundation_token`, and both Minecraft 26.2 item/model JSON resources.

## Task Commits

Each task was committed atomically:

1. **Task 1: Prove and atomically commit the pristine official scaffold prerequisite** - `1c9c455` (chore)
2. **Task 2: Complete the walking skeleton with one registered client-visible Foundation Token** - `2b3bba7` (feat)

**Plan metadata:** committed with this summary and tracking updates.

## Files Created/Modified

- `.gitignore` - Keeps the wrapper JAR trackable while excluding Gradle, build, distribution, run, world, IDE, diagnostic, recording, and local-JDK state.
- `settings.gradle`, `gradle.properties`, `build.gradle` - Frozen `developers-hell` identity, Java 25 release, split source sets, exact Fabric tuple, and ordinary JAR configuration.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*` - Byte-identical official Gradle 9.5.1 wrapper surface.
- `.planning/phases/01-java-25-and-fabric-26-2-foundation/01-TOOLCHAIN-EVIDENCE.md` - Public-safe JDK, upstream, tuple, command, log, Loom, and wrapper evidence.
- `src/main/java/dev/developershell/DevelopersHell.java` - Common initializer, namespace helper, and unconditional Foundation Token registration.
- `src/client/java/dev/developershell/client/DevelopersHellClient.java` - Physical-client-only initialization seam with no authoritative behavior.
- `src/main/resources/fabric.mod.json` - Public-safe Loader identity, dependency ranges, and side-specific entrypoints.
- `src/main/resources/assets/developers_hell/items/foundation_token.json` and `models/item/foundation_token.json` - Minecraft 26.2 client-item indirection backed by vanilla paper.

## Decisions Made

- Retained fixed Loom `1.17.19`; all three initial official-template invocations and the full verifier reruns resolved the exact same plugin artifact, so no snapshot fallback was eligible or necessary.
- Kept only Minecraft, Fabric Loader, and Fabric API as direct runtime inputs, with no mappings dependency, legacy remap plugin, runtime networking, telemetry, OpenAI SDK, or extra library.
- Kept `DevelopersHellClient.onInitializeClient()` intentionally behavior-free: this plan proves the physical source/entrypoint boundary while deferring actual client systems to later plans.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- The sandboxed Windows TLS provider could not acquire credentials for the official Temurin download. The same exact first-party URL was retried with approved network access, after which the release sidecar and computed archive SHA-256 matched exactly.
- An internal acceptance-report regex initially omitted optional CRLF whitespace. The product files were unchanged; the corrected audit reran all seven criteria and passed.

## Authentication Gates

None.

## Known Stubs

None. The empty client initializer is the deliberate side-boundary endpoint required by this plan and does not prevent the tracer goal.

## User Setup Required

None - no external service configuration is required. The verified ignored JDK and dependency cache remain available for subsequent foundation plans.

## Next Phase Readiness

- Ready for `01-02-PLAN.md` to extract the unchanged inline registry into stable catalogs and add the eight-module behavior gate under Loader JUnit.
- The retained ignored JDK and Loom probe are hash-bound and ready for every pre-Plan-03 build.
- Production client/server and offline-runtime evidence intentionally remain for Plans 03-04.

## Self-Check: PASSED

- All 15 planned scaffold, tracer, evidence, and summary files exist.
- Task commits `1c9c455` and `2b3bba7` resolve to commit objects.
- The remapped artifact exists at `build/libs/developers-hell-0.1.0.jar`.

---
*Phase: 01-java-25-and-fabric-26-2-foundation*
*Completed: 2026-08-25*
