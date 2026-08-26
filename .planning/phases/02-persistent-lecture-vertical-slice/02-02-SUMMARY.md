---
phase: 02-persistent-lecture-vertical-slice
plan: 02
subsystem: configuration-runtime
tags: [fabric-26.2, strict-json, immutable-config, module-gates, localized-command]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 15
    provides: Stable Contract and lecture-manager runtime hooks with immutable bounded LectureRules
provides:
  - Strict fixed-child schema-v1 loader with aggregate validation and all-or-default semantics
  - Immutable safe defaults, bounded lecture tuning, explicit schedules, and all eight module gates
  - One startup-composed runtime snapshot used by the real Contract and lecture tick paths
  - Localized read-only player status plus sanitized startup diagnostics
affects: [campaign-discovery, recovery-commands, module-behavior, lecture-tuning, release-verification]

actuals:
  tokens: 17200
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns: [strict streaming Gson from runtime graph, fixed-child FileFacts seam, immutable session snapshot, scoped static-service compatibility adapters]

key-files:
  created:
    - src/main/java/dev/developershell/config/ConfigIssue.java
    - src/main/java/dev/developershell/config/DevHellConfig.java
    - src/main/java/dev/developershell/config/DevHellConfigLoader.java
    - src/main/java/dev/developershell/server/DevelopersHellRuntime.java
    - src/main/java/dev/developershell/command/DevHellCommands.java
  modified:
    - src/main/java/dev/developershell/DevelopersHell.java
    - src/main/java/dev/developershell/item/CursedInternshipContractItem.java
    - src/main/resources/assets/developers_hell/lang/en_us.json
    - src/test/java/dev/developershell/config/DevHellConfigTest.java

key-decisions:
  - "Treat one complete strict schema-v1 document as the only accepted unit; every invalid family activates the complete immutable defaults and never rewrites the rejected bytes."
  - "Use scoped adapters over the retained static campaign and lecture services so the accepted snapshot drives the actual Contract and tick paths without an out-of-scope service-layer rewrite."
  - "Keep /devhell status player-readable and read-only; later recovery plans own any game-master-gated mutation children."

patterns-established:
  - "Configuration boundary: read only the fixed developers-hell.json child once, cap it at 65,536 bytes, reject links/non-files, and sanitize bounded issue metadata."
  - "Behavior-only gates: initialize every stable item/entity before loading configuration, then pass immutable gates and rules only to behavior adapters."
  - "Safe observability: log one bounded startup summary and each sanitized issue once; expose only translated effective state to players."

requirements-completed: [FND-05, FND-07]

coverage:
  - id: D1
    description: "Missing/default, fully accepted, malformed, duplicate, unknown, missing, type, enum, range, link, non-file, oversize, read/write-failure, no-overwrite, and immutable-snapshot behavior is deterministic."
    requirement: FND-05
    verification:
      - kind: unit
        ref: "DevHellConfigTest: 15 tests, 0 failures/errors/skips"
        status: pass
    human_judgment: false
  - id: D2
    description: "Stable registries initialize first, one accepted snapshot drives the Contract campaign gate and lecture rules, and /devhell status reads localized effective state without mutation."
    requirement: FND-05
    verification:
      - kind: integration
        ref: "Pinned offline build plus bootstrap/load-count/registry-coupling/translation acceptance scan"
        status: pass
    human_judgment: false
  - id: D3
    description: "Existing campaign lifecycle invariants remain green in a clean transformed dedicated-server run while dependency and runtime-surface policies remain unchanged."
    requirement: FND-07
    verification:
      - kind: e2e
        ref: "Fabric runGameTest: all 3 required tests passed; auditDirectDependencies exact tuple/hash passed"
        status: pass
    human_judgment: false

duration: 22min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 02: Fail-Closed Local Configuration and Immutable Runtime Composition Summary

**Developer's Hell now loads one strict local schema at startup, activates complete safe defaults on any invalid document, and drives real campaign/lecture behavior plus a localized read-only status command from one immutable session snapshot.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-26T17:44:15Z
- **Completed:** 2026-08-26T18:06:39Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Added a dependency-free strict Gson streaming loader for the fixed `developers-hell.json` child, with a 64 KiB cap, link/non-file guards, duplicate and unknown detection, complete schema validation, sanitized aggregate issues, and invalid-file no-overwrite behavior.
- Defined immutable schema-v1 defaults and bounded tuning for campaign, difficulty, destructive/accessibility toggles, both manual schedules, and all eight existing module names.
- Composed the accepted result once after unconditional item/entity initialization; the real Contract start and lecture-manager tick paths now consume the captured campaign gate and `LectureRules` through scoped adapters.
- Added localized `/devhell status` output for source, campaign, difficulty, safety toggles, schedules, and all eight modules without adding a mutation surface.
- Preserved the existing offline dependency tuple, clean transformed server lifecycle, and known-good distributable JAR.

## Requirements (Copied Verbatim)

- **FND-05**: The mod validates configuration at startup, reports actionable errors, and defaults destructive or scheduled chaos to opt-in behavior.
- **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.

## Task Commits

1. **Task 1 RED: Define the strict configuration contract** - `159c744` (test)
2. **Task 1 GREEN: Implement the fail-closed loader and immutable schema** - `92815dc` (feat)
3. **Task 2: Compose the accepted snapshot and expose safe status** - `943c94d` (feat)

## Files Created/Modified

- `src/main/java/dev/developershell/config/ConfigIssue.java` - Bounded, public-safe rejected-field diagnostics.
- `src/main/java/dev/developershell/config/DevHellConfig.java` - Immutable schema, defaults, enums, bounds, and defensive module snapshot.
- `src/main/java/dev/developershell/config/DevHellConfigLoader.java` - Fixed-child bounded I/O, strict parser, aggregate validator, optional missing-file template, and immutable result.
- `src/main/java/dev/developershell/server/DevelopersHellRuntime.java` - One session composition and actual campaign/lecture compatibility adapters.
- `src/main/java/dev/developershell/command/DevHellCommands.java` - Localized current-player `/devhell status` surface.
- `src/main/java/dev/developershell/DevelopersHell.java` - Registry-first load-once bootstrap, sanitized startup reporting, and adapter registration.
- `src/main/java/dev/developershell/item/CursedInternshipContractItem.java` - Injected campaign adapter with fail-closed pre-initialization behavior.
- `src/main/resources/assets/developers_hell/lang/en_us.json` - Status, source, module, schedule, and disabled-campaign translations.
- `src/test/java/dev/developershell/config/DevHellConfigTest.java` - Strict input/default/no-overwrite/module/snapshot matrix.

## Decisions Made

- A config file is accepted only when the entire schema-v1 document is present, known, correctly typed, and bounded. No valid-looking subset survives an error.
- Missing configuration may create one readable default template; invalid existing bytes are never overwritten, renamed, or deleted.
- The accepted `LoadResult`, derived `ModuleGate`, and mapped `LectureRules` are held for the session and never reloaded from a gameplay callback.
- Existing static campaign/manager implementations remain the compatibility core for this sprint, but the runtime facade is not cosmetic: the Contract and server tick callbacks execute through it.
- `/devhell status` remains available to the current player without game-master permission because it is read-only. No placeholder mutation commands were added; their owning later plan must gate real mutations at child roots.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Adapted retained static campaign/manager utilities through scoped runtime facades**

- **Found during:** Task 2
- **Issue:** The plan described instance composition, but both retained services are static utilities with private constructors; dummy instances would not control production behavior, while a full service-layer rewrite would exceed this plan.
- **Fix:** Added narrow adapters that capture only immutable policy/rules and delegate to the retained implementations; bootstrap ticks and Contract starts now use those adapters as their real path.
- **Files modified:** `DevelopersHellRuntime.java`, `DevelopersHell.java`
- **Commit:** `943c94d`

**2. [Rule 2 - Missing Critical Functionality] Bound campaign-disabled policy to the real Contract interaction**

- **Found during:** Task 2
- **Issue:** `CursedInternshipContractItem` called the static campaign service directly, so a runtime holder alone could not enforce the required disabled-campaign rejection.
- **Fix:** Injected the one-shot campaign adapter during bootstrap, rejected uninitialized use safely, and returned localized disabled text before any campaign effect.
- **Files modified:** `CursedInternshipContractItem.java`, `DevelopersHellRuntime.java`, `en_us.json`
- **Commit:** `943c94d`

## Issues Encountered

- An initial ad-hoc compile invocation omitted the three mandatory Gradle Java-installation system properties. Compilation and tests were already green, but the dependency audit correctly rejected that invocation; the exact plan command with the sole pinned JDK and discovery/download disabled passed immediately.
- The clean Fabric GameTest runner emitted its expected first-run messages for absent transient `server.properties`, `eula.txt`, and the empty client resource output, then started normally and passed every required test.
- Context7 and its CLI fallback were unavailable; all version-specific APIs were confirmed against the pinned local Fabric/Minecraft artifacts, compilation, and transformed runtime.
- No package, authentication, network, client-launch, or architecture checkpoint blocked execution.

## Verification

- Pinned Eclipse Temurin `25.0.4+7` / Java 25, Gradle Wrapper `9.5.1`, Loom `1.17.19`, Minecraft `26.2`, Loader `0.19.3`, and Fabric API `0.158.0+26.2` - PASS.
- Task 1 exact gate `test --tests DevHellConfigTest auditDirectDependencies compileJava --offline` - PASS.
- Clean final gate `test --tests DevHellConfigTest --tests ModuleGateTest auditDirectDependencies build --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle` - PASS.
- `DevHellConfigTest` - 15 tests, 0 failures/errors/skips; every required invalid family, default/template result, no-overwrite path, module combination, and immutable runtime snapshot is covered.
- Fresh transformed dedicated server - `3 GAME TESTS COMPLETE`; `All 3 required tests passed` - PASS.
- Direct dependency allowlist - exact five declared tuples and Loom injection count/hash `145/a3fef1ae5a4b68b3c02af8e92827285f0c859ec0e9df4be85803181eb3cc767b` - PASS.
- Acceptance scan - registry-first order, exactly one runtime load, zero registry-to-config coupling, read-only status root, eight module translations, no retained server/level/player/client field, and strict language JSON - PASS.
- Runtime external-surface scan - no HTTP, SDK/account key, authorization, or `java.net` integration in `src/main/java` - PASS.
- Ordinary candidate `build/libs/developers-hell-0.1.0.jar` SHA-256 `7c5c38b8d8fd70f20e46b3dd0d0bf87f57b9a56bccae8f0d385c3304fabe3596`; contains config/runtime/command classes and excludes GameTest classes - PASS.
- Retained `dist/developers-hell-0.1.0.jar` remains SHA-256 `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8` - PASS.
- No visible Minecraft client was launched and no known-good distribution artifact was replaced.

## Threat Mitigations

| Threat | Result | Evidence |
|---|---|---|
| T-02-CFG-01 tampering | MITIGATED | Fixed child, link/non-file/64 KiB guards, strict syntax/schema, aggregate duplicate/unknown rejection, and byte-preserving invalid tests. |
| T-02-CFG-02 parser denial of service | MITIGATED | Bounded file and issue fields, finite accepted schema, one startup read, and no callback reload. |
| T-02-CFG-03 diagnostic disclosure | MITIGATED | Sanitized bounded issue metadata, one log layer, translated effective status, and forbidden-surface scan. |
| T-02-CFG-04 command privilege | MITIGATED | Status is current-player/read-only and this plan adds no mutation or diagnostic-state command root. |
| T-02-CFG-05 stable registry tampering | MITIGATED | Items/entities initialize before config; registry classes import no config/gate types; all-off/all-on/single gates preserve the phase-two item catalog. |

## Known Stubs

None. No placeholder command, skipped test, TODO/FIXME, mock runtime value, or unbound adapter remains in the files changed by this plan.

## User Setup Required

None. The mod uses safe in-memory defaults automatically and may create the optional local template on first startup; it adds no account, credential, service, dependency, or network requirement.

## Next Phase Readiness

- Later gameplay plans can read the immutable runtime gate/rule snapshot rather than reopening configuration.
- The recovery-command plan can add real child commands beneath `/devhell`, applying `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` only to mutations while preserving public read-only status.
- Module plans can keep stable registrations unconditional and gate only their behavior through the existing eight-name `ModuleGate`.

## TDD Gate Compliance

- RED commit `159c744` added the complete strict-loader contract and failed because the production config types did not exist.
- GREEN commit `92815dc` implemented the minimal immutable schema/loader and passed the exact focused gate.
- Task 2 extended the same suite with the accepted runtime-snapshot mapping assertion; the final clean gate remained green.
- No test was removed, weakened, skipped, or marked pending.

## Self-Check: PASSED

- All five created production artifacts and this summary exist on disk.
- Task commits `159c744`, `92815dc`, and `943c94d` resolve as commits in repository history.
- Summary frontmatter includes `status: complete`, estimate-scale actuals, requirements, and coverage evidence.
- Changed-file stub/skip scan is clean, and the final worktree contains only the expected uncommitted summary before tracking updates.
