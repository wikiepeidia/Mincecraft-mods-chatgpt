# Phase 1: Java 25 and Fabric 26.2 Foundation - Research

**Researched:** 2026-08-25
**Domain:** Minecraft Java 26.2 Fabric foundation, reproducible builds, side safety, and runtime validation
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Toolchain and Dependency Pinning
- Install or select a full Eclipse Temurin JDK 25 and fail setup early unless both `java` and `javac` report major version 25.
- Start from the current official Fabric 26.2 example rather than translating an older tutorial or generator output.
- Freeze Minecraft `26.2`, Loader `0.19.3`, Fabric API `0.158.0+26.2`, and Gradle Wrapper `9.5.1` after live verification.
- Try fixed Loom `1.17.19`; if the untouched official template proves it incompatible, use only the template's `1.17-SNAPSHOT` value and record the resolved build. Do not change several pins at once.
- Use Minecraft 26.2's unobfuscated names with `net.fabricmc.fabric-loom`; do not add Yarn, a mappings dependency, or legacy remap Loom.

### Project Identity and Source Boundaries
- Use mod ID `developers_hell`, artifact name `developers-hell`, initial version `0.1.0`, and base Java package `dev.developershell`.
- Keep common/server-safe registration and state under `src/main`; keep renderers, screens, HUD, keybinds, and client initialization under `src/client`.
- Register every stable item, entity, effect, component, payload, command, and other content ID unconditionally. Later module toggles may gate behavior but never remove registry identity.
- Use Java plus Fabric API and vanilla facilities only for this foundation; do not add Kotlin, Architectury, GeckoLib, config libraries, OpenAI SDKs, telemetry, or runtime HTTP dependencies.
- Keep public metadata and defaults fictional and offline-safe from the first artifact.

### Build and Verification Contract
- Commit the Gradle wrapper and make wrapper commands the only documented build entry point.
- Prove the untouched scaffold with `help` and `build` before introducing registrations, then preserve a known-good checkpoint.
- Add one unit smoke test and one minimal Fabric GameTest that exercises real mod initialization or a stable registered object.
- Launch a production client world and a production dedicated server; a dev-client launch alone is insufficient evidence.
- Prime dependencies once, then prove `--offline build` produces the same remapped production JAR and inspect the archive for `fabric.mod.json`, classes, and resources.

### Sprint Scope and Failure Policy
- Treat any unresolved Java, Loom, Gradle, Fabric, or side-only classloading error as a Phase 1 blocker; do not mask it with gameplay work.
- Prefer the smallest official project shape and deterministic checks over optional tooling, elaborate CI, or abstraction layers.
- Record exact commands, resolved versions, artifact location, and any snapshot fallback in phase evidence so later agents never re-guess the environment.
- Preserve the existing repository license and planning history; generated build caches, run worlds, IDE files, and machine-local settings remain ignored.

### the agent's Discretion
- Exact names of the minimal registered smoke-test content and test packages.
- Whether the initial client/server smoke is automated or documented as a bounded manual process when Minecraft's launcher cannot terminate reliably in CI.
- Minor Gradle organization choices that stay identical in behavior to the official 26.2 example.

### Deferred Ideas (OUT OF SCOPE)
- Campaign state, bosses, HUD, terminal screens, module behavior, generated textures, audio, and showcase content begin in later phases.
- Broader mod-loader support, automated release publishing, and extensive CI matrices remain outside this foundation sprint.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FND-01 | A player can install one Developer's Hell JAR with the documented Fabric 26.2 prerequisites and enter a world while the machine is offline. | Exact player tuple, clean production-client run, network-disabled repeat, archive inspection, and explicit cache boundary. |
| FND-02 | A contributor can build the production JAR from a fresh checkout through the committed Gradle wrapper and a frozen Java 25/Fabric 26.2 dependency tuple. | Exact pins, JDK bootstrap gate, wrapper-only commands, online prime, offline rebuild, and hash comparison. |
| FND-03 | The production mod can launch both a client world and a dedicated server without client-only classloading failures. | Split source sets, separate entrypoints, client-import audit, and production client/server smoke checks. |
| FND-04 | All stable items, entities, effects, payloads, and other content IDs remain registered regardless of module-toggle values so existing saves remain loadable. | Unconditional registry architecture, behavior-only gates, a gate unit test, and an actual registry GameTest. |
</phase_requirements>

## Summary

Build from FabricMC's official `26.2` example branch and change only identity, fixed pins, tests, and production-smoke wiring before gameplay. The live template pins Minecraft `26.2`, Loader `0.19.3`, Fabric API `0.158.0+26.2`, Java release 25, Loom `1.17-SNAPSHOT`, and Gradle Wrapper `9.5.1`. Official Fabric Maven publishes fixed Loom `1.17.19`, but publication does not prove compatibility with this exact template; `help` then `build` on a usable JDK 25 must decide it. [VERIFIED: https://github.com/FabricMC/fabric-example-mod/tree/26.2] [VERIFIED: https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/1.17.19/]

The vertical slice should be one common initializer, one client initializer, one harmless stable item, a behavior-gate seam, one unit test, one server GameTest, and two Loom production tasks. Final proof is a built-output client entering a world, a built-output dedicated server reaching ready state and stopping cleanly, archive inspection, then a second build under `--offline`. Gradle offline mode is cache-only: it cannot make an unprimed checkout, absent wrapper distribution, or never-downloaded Minecraft profile work offline. [VERIFIED: https://docs.gradle.org/current/userguide/dependency_caching.html] [VERIFIED: https://docs.fabricmc.net/develop/loom/production-run-tasks]

**Primary recommendation:** plan three gates in order—Java/template proof, thin playable/tested mod, production/offline evidence—and treat any unresolved toolchain, output-task, or side-loading error as a phase blocker.

## Project Constraints (from AGENTS.md)

| Directive | Planning consequence |
|-----------|----------------------|
| Minecraft `26.2` stable only; no `26.3` snapshots | Reject generator or tutorial output for another game version. |
| Fabric, Java 25, one JAR, offline-first, singleplayer-first | Keep one Java project and no runtime service boundary. |
| Common/client split required | Dedicated-server smoke is a hard gate. |
| No Yarn, mappings dependency, legacy remap Loom, raw OpenGL, or client imports in common | Add build-file/source audits before runtime smoke. |
| No Kotlin, Architectury, GeckoLib, config libraries, OpenAI SDK, telemetry, HTTP, database, or remote assets | Fabric API is the only runtime mod dependency; Loader JUnit is test-only. |
| Wrapper only; no system Gradle | Every build command uses `.\gradlew.bat`. |
| Ignore caches, runs, worlds, IDE files, and local settings | Merge the official Fabric ignore entries before the first build. |
| Preserve license/planning history and public-safe fictional defaults | Keep `Unlicense` metadata, do not rewrite unrelated files, and commit no private value. |

These directives were read from `AGENTS.md` this session. [VERIFIED: AGENTS.md]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| JDK/Gradle/Fabric resolution | Build host | Official repositories during prime | Wrapper/dependencies are build-time; a complete cache supports later offline builds. [VERIFIED: https://docs.gradle.org/current/userguide/dependency_caching.html] |
| Stable content registration | Common / logical server | Client resources | Registry identity affects saves and both physical sides; visuals do not own IDs. [VERIFIED: https://docs.fabricmc.net/develop/items/first-item] |
| Module behavior gate | Common / logical server | Client presentation later | Toggles suppress behavior only, never registry calls. [RECOMMENDED: FND-04 invariant] |
| Client initializer | Physical client | — | Renderer, HUD, screen, and keybind types stay client-only. [VERIFIED: https://docs.fabricmc.net/develop/getting-started/project-structure] |
| Unit tests | JVM test runtime | Vanilla bootstrap if registries touched | Fabric Loader JUnit provides the transformed test runtime. [VERIFIED: https://docs.fabricmc.net/develop/automatic-testing] |
| GameTest | Integrated Minecraft server | Fabric API test source set | Proves initialization and a real registered object in runtime. [VERIFIED: https://docs.fabricmc.net/develop/automatic-testing] |
| Production smoke | Loom production tasks | Project-local run dirs | Tasks exercise the project artifact and production runtime mods without dev-only launch behavior. [VERIFIED: https://raw.githubusercontent.com/FabricMC/fabric-loom/dev/1.17/src/main/java/net/fabricmc/loom/task/prod/AbstractProductionRunTask.java] |
| Database/storage | None in Phase 1 | — | No campaign state or external persistence is in scope. [VERIFIED: 01-CONTEXT.md] |

## Standard Stack

### Core

| Component | Exact pin | Purpose | Evidence |
|-----------|-----------|---------|----------|
| Minecraft Java | `26.2` | Game/runtime target | Official released version and Fabric branch pin. [VERIFIED: https://feedback.minecraft.net/hc/en-us/articles/46690753273997-Minecraft-Java-Edition-26-2] |
| Eclipse Temurin JDK | `25.0.4+7` x64 JDK | Run Gradle and compile Java 25 | Current official Java 25 maintenance binary. [VERIFIED: https://adoptium.net/news/2026/08/eclipse-temurin-8u502-11032-17020-21012-2504-2602-available] |
| Fabric Loader | `0.19.3` | Mod discovery/entrypoints | Template pin and first-party Maven entry. [VERIFIED: https://maven.fabricmc.net/net/fabricmc/fabric-loader/] |
| Fabric API | `0.158.0+26.2` | Supported Fabric APIs and test DSL | Template pin; JAR/POM/signatures published 2026-08-18. [VERIFIED: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.158.0%2B26.2/] |
| Fabric Loom | Try `1.17.19`; only fallback `1.17-SNAPSHOT` | Build/source split/run tasks | Fixed marker exists; template retains snapshot. Compatibility is an execution gate. [VERIFIED: official Fabric Maven and template] |
| Gradle Wrapper | `9.5.1` | Reproducible entrypoint | Exact template pin; Gradle supports Java 25 from 9.1. [VERIFIED: https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/gradle/wrapper/gradle-wrapper.properties] [VERIFIED: https://docs.gradle.org/current/userguide/compatibility.html] |
| Fabric Installer | `1.1.2` | Production server launcher | Current stable per Fabric Meta/Maven; exact 26.2/0.19.3 server artifact exists. [VERIFIED: https://meta.fabricmc.net/v2/versions/installer] |

### Identity and Metadata

| Property | Exact value |
|----------|-------------|
| Mod ID | `developers_hell` |
| Artifact | `developers-hell` |
| Version | `0.1.0` |
| Base package / Maven group | `dev.developershell` |
| Main entrypoint | `dev.developershell.DevelopersHell` |
| Client entrypoint | `dev.developershell.client.DevelopersHellClient` |
| Metadata environment | `*` |
| Minecraft metadata dependency | `~26.2`, not `26.2.x` |
| License metadata | `Unlicense` |

These are locked decisions or direct metadata derivations. [VERIFIED: 01-CONTEXT.md] [VERIFIED: LICENSE]

### Supporting

| Component | Version | Use |
|-----------|---------|-----|
| `net.fabricmc:fabric-loader-junit` | `0.19.3` | Unit smoke test on JUnit Platform. [VERIFIED: https://docs.fabricmc.net/develop/automatic-testing] [VERIFIED: https://maven.fabricmc.net/net/fabricmc/fabric-loader-junit/] |
| Fabric server GameTest | Fabric API `0.158.0+26.2` | Separate `src/gametest` source set; server tests run with `build`. [VERIFIED: official testing docs] |
| SLF4J facade | Loader-provided | Structured initialization logging; add no logging library. [RECOMMENDED: minimal dependencies] |

### Alternatives Considered

| Standard | Alternative | Decision |
|----------|-------------|----------|
| Fixed Loom `1.17.19` first | `1.17-SNAPSHOT` | Snapshot is allowed only after captured fixed-pin failure. |
| Loom production tasks | Hand-built launcher classpath | Use Loom; it owns game/Loader/assets/runtime-mod wiring. |
| One stable smoke item | Placeholder future bosses/items/effects | Do not freeze speculative IDs. |
| Bounded manual client smoke | Headless Windows GUI automation | Manual clean exit is more credible than a forced timeout. |

### Version/Bootstrap Commands

Adoptium documents `winget install EclipseAdoptium.Temurin.25.JDK`, MSI, and ZIP installation routes. This workstation has no `winget`, Chocolatey, or Scoop, so use the official Windows x64 MSI/ZIP, verify its official checksum, and select it outside the repository. [VERIFIED: https://adoptium.net/installation/] [VERIFIED: local environment audit]

~~~powershell
$devHellJdk = '<verified Temurin 25 JDK directory>'
if (-not (Test-Path -LiteralPath "$devHellJdk\bin\java.exe")) { throw 'java.exe missing' }
if (-not (Test-Path -LiteralPath "$devHellJdk\bin\javac.exe")) { throw 'javac.exe missing' }
$env:JAVA_HOME = $devHellJdk
$env:Path = "$devHellJdk\bin;$env:Path"
where.exe java
where.exe javac
java --version
javac --version
~~~

Both Java tools and `.\gradlew.bat --version` must report major 25. Never commit the resolved machine path. [VERIFIED: https://fabricmc.net/2026/06/15/262.html]

## Package Legitimacy Audit

The GSD legitimacy seam accepts npm, PyPI, and crates.io, not Maven; its Maven invocation returned the ecosystem usage error. These dependencies were therefore checked directly against official Fabric docs/repos/Maven and official Gradle/Adoptium distribution sources. [VERIFIED: local gsd-tools result 2026-08-25]

| Package/tool | Official source evidence | Verdict | Disposition |
|--------------|--------------------------|---------|-------------|
| Loader `0.19.3` | Fabric example + Fabric Maven | OK, manual first-party audit | Approved |
| Loader JUnit `0.19.3` | Fabric testing docs + Fabric Maven | OK, manual first-party audit | Approved test-only |
| Fabric API `0.158.0+26.2` | Fabric example + signed Maven artifacts | OK, manual first-party audit | Approved |
| Loom plugin `1.17.19` | Fabric Maven marker published 2026-08-07 | OK existence; compatibility pending | Approved behind build gate |
| Installer `1.1.2` server | Fabric Meta/Maven + exact server endpoint | OK, manual first-party audit | Approved production-task only |
| Gradle `9.5.1` | Official wrapper/release | OK | Approved |
| Temurin `25.0.4+7` | Official Adoptium release | OK | Approved |

**Removed due to SLOP:** none. **Suspicious packages:** none. No third-party runtime library is permitted. [VERIFIED: 01-CONTEXT.md]

## Architecture Patterns

### System Architecture

~~~
Java 25 shell -> committed Gradle 9.5.1 wrapper -> Loom 1.17.x
     -> compile + Loader JUnit + server GameTest
     -> production artifact
          -> production client -> world entered -> clean exit
          -> production server -> ready -> clean stop
          -> archive/hash inspection -> repeat with --offline

fabric.mod.json
     -> main entrypoint on both sides
          -> stable registrations (always)
          -> behavior callbacks (ModuleGate may suppress)
     -> client entrypoint on physical client only
~~~

### Recommended Project Structure

~~~
build.gradle
settings.gradle
gradle.properties
gradlew / gradlew.bat
gradle/wrapper/*
src/main/java/dev/developershell/
  DevelopersHell.java
  module/ModuleGate.java
  registry/ModItemIds.java
  registry/ModItems.java
src/client/java/dev/developershell/client/DevelopersHellClient.java
src/main/resources/fabric.mod.json
src/main/resources/assets/developers_hell/lang/en_us.json
src/main/resources/assets/developers_hell/items/foundation_token.json
src/main/resources/assets/developers_hell/models/item/foundation_token.json
src/test/java/dev/developershell/module/ModuleGateTest.java
src/gametest/java/dev/developershell/gametest/FoundationGameTests.java
src/gametest/resources/fabric.mod.json
~~~

The source-set shape is official; concrete smoke names are recommendations within the user's discretion. [VERIFIED: official template/testing docs] [RECOMMENDED]

### Pattern 1: Untouched Template Gate

Import the official `26.2` shape, test fixed Loom `1.17.19` with `help` then `build` before adding mod code, and change only Loom to `1.17-SNAPSHOT` on a captured failure. Record Java, Gradle, Minecraft, Loader, API, Loom, and output task names. [RECOMMENDED: locked failure policy]

### Pattern 2: Registration Before Behavior

~~~text
DevelopersHell.onInitialize()
  ModItems.initialize()                 // unconditional
  future stable registry initializers  // unconditional once IDs exist
  ModuleBehaviors.initialize(gate)      // callbacks may be conditional
~~~

Never place `Registry.register`, payload type registration, data-component registration, or stable command identity behind `if (moduleEnabled)`. Phase 1 registers only the harmless smoke content; it must not invent future boss/effect/payload IDs. [RECOMMENDED: FND-04]

### Pattern 3: Hard Physical-Side Split

Use `splitEnvironmentSourceSets()`, include `main` and `client` in one mod, and declare distinct metadata entrypoints. Common code must not contain client types in imports, fields, signatures, generics, annotations, or static initializers; an environment check does not prevent JVM linkage failure. The production dedicated server is the authoritative proof. [VERIFIED: official 26.2 template] [RECOMMENDED: FND-03]

### Pattern 4: Output-Aware Production Tasks

Loom production tasks are manually registered and names are project-defined. Loom 1.17 automatically adds the current project artifact and `productionRuntimeMods`. With 26.2's no-remap plugin it selects ordinary `jar`; with remapping enabled it selects `remapJar`. Thus the 26.2 production artifact is technically not a remapped JAR despite older generic documentation wording. [VERIFIED: Loom 1.17.19 `AbstractProductionRunTask` source]

Pin `runProductionClient` and `runProductionServer`, separate run directories, Java 25 launcher, Installer `1.1.2`, and Fabric API in `productionRuntimeMods`. Do not manually add `remapJar`. [VERIFIED: official Loom production task source/docs]

### Pattern 5: Prime Then Prove Offline

Gradle `--offline` consults cached metadata/artifacts only and fails on a missing component. First perform a complete online build and production launches; then repeat `clean build` offline and compare the production JAR SHA-256. Gradle 9 defaults archive timestamps/order to reproducible values, but the measured hash remains the gate. [VERIFIED: https://docs.gradle.org/current/userguide/dependency_caching.html] [VERIFIED: https://docs.gradle.org/current/userguide/best_practices_security.html]

## Recommended Walking-Skeleton Sequence

### Gate A — Toolchain and Scaffold

1. Install/select Temurin 25; verify `java`, `javac`, and Gradle's JVM.
2. Scaffold from FabricMC `26.2` and apply only locked identity plus fixed Loom `1.17.19`.
3. Run `.\gradlew.bat help --no-daemon --stacktrace` and `.\gradlew.bat build --no-daemon --stacktrace`.
4. If and only if fixed Loom fails, use `1.17-SNAPSHOT` and rerun unchanged.
5. Preserve the known-good checkpoint and resolution evidence.

### Gate B — Thin Playable/Tested Mod

1. Add split source sets and main/client entrypoints.
2. Add one harmless stable `foundation_token`, translation/model resources, and behavior-gate seam.
3. Add one pure unit test and one server GameTest that checks the real registry key.
4. Run `test`, `build`, development client, development server, and the client-import audit.

### Gate C — Production and Offline Proof

1. Register production client/server tasks plus Fabric API in `productionRuntimeMods`.
2. Enter a production-client local world and exit cleanly.
3. Reach production-server ready state, enter `stop`, and exit cleanly.
4. Inspect the distributable archive and capture SHA-256.
5. Repeat the build under `--offline` and require the same SHA-256.
6. Record exact commands, versions, artifact path, logs, and any snapshot fallback.

This delivers a vertical walking skeleton before boss/module work and keeps failures attributable to one layer. [RECOMMENDED: planning structure]

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| Build bootstrap | Custom downloader or system Gradle procedure | Committed Gradle 9.5.1 wrapper | Wrapper pins the distribution. [VERIFIED: https://docs.gradle.org/current/userguide/gradle_wrapper.html] |
| Mappings | Yarn conversion/manual remapper | `net.fabricmc.fabric-loom` and unobfuscated names | This is Fabric's 26.2 path. [VERIFIED: https://fabricmc.net/2026/06/15/262.html] |
| Registries | Reflection scanner/deferred-registry dependency | Vanilla `Registry.register` and `BuiltInRegistries` | Official current pattern. [VERIFIED: https://docs.fabricmc.net/develop/items/first-item] |
| Unit runtime | Plain JUnit around transformed Minecraft code | Fabric Loader JUnit | Designed for Loader's transformed runtime. [VERIFIED: official testing docs] |
| In-game tests | Custom server harness | Fabric API GameTest DSL | Integrated into `build`. [VERIFIED: official testing docs] |
| Production launch | Homemade classpath/copy script | Loom production tasks | Loom owns Loader, game, assets, and mod classpaths. [VERIFIED: official production task docs] |
| Offline cache | Vendored Gradle/Minecraft cache in Git | Online prime plus Gradle cache | Cache is machine/repository sensitive and not source. [VERIFIED: Gradle cache docs] |
| Side-safety library | Extra architecture-test dependency | Split sources, `rg` audit, production server | Covers the risk within sprint constraints. [RECOMMENDED] |

## Environment Availability

| Dependency | Available now | Observed state | Required action |
|------------|---------------|----------------|-----------------|
| `java` | Yes, wrong major | Temurin/OpenJDK `21.0.11` on PATH | Install/select Temurin `25.0.4+7`. |
| `javac` | Yes, wrong major | `javac 21.0.11` | Same JDK 25 action. |
| Full JDK 25 | No usable install | Existing `jdk-25.0.2.10-hotspot` directory lacks `bin\java.exe` and `bin\javac.exe` | Treat as incomplete residue; use official MSI/ZIP. |
| `winget` / Chocolatey / Scoop | No | Commands not found | Official MSI/ZIP fallback. |
| System Gradle | No | Correct for this project | Use wrapper only. |
| Wrapper `9.5.1` cache | No | Other wrapper versions exist, 9.5.1 absent | Online wrapper prime. |
| Pinned Loader/API/Loom cache | Not complete | Target entries absent | Online `help`/`build` prime. |
| Minecraft 26.2 assets/libraries | Not proven | No project run yet | First online Loom client/test setup, then offline repeat. |
| GUI session | Interactive Windows | Expected available | Bounded manual production-client checkpoint. |

All observations are from read-only probes on 2026-08-25. [VERIFIED: local environment audit]

**Blocking before first prime:** usable JDK 25, Gradle 9.5.1 distribution, pinned Maven artifacts, and game libraries/assets. The machine cannot truthfully pass offline build now. [VERIFIED: local audit plus Gradle offline semantics]

**Available fallbacks:** official Temurin MSI/ZIP replaces the missing package managers; the committed wrapper replaces global Gradle.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Unit framework | Fabric Loader JUnit `0.19.3` on JUnit Platform. [VERIFIED: official testing docs] |
| Runtime framework | Fabric API/Loom server GameTest using `src/gametest`. [VERIFIED: official testing docs] |
| Quick command | `.\gradlew.bat test --no-daemon` |
| Full automated command | `.\gradlew.bat build --no-daemon`; server GameTests run automatically with `build`. [VERIFIED: official testing docs] |
| Offline command | `.\gradlew.bat --offline clean build --no-daemon` after prime |
| Dev smoke | `.\gradlew.bat runClient --no-daemon` and `.\gradlew.bat runServer --no-daemon` |
| Production smoke | `.\gradlew.bat runProductionClient --no-daemon` and `.\gradlew.bat runProductionServer --no-daemon` |
| Task discovery | `.\gradlew.bat tasks --all` |

### Required Gradle Wiring

~~~groovy
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
    testImplementation "net.fabricmc:fabric-loader-junit:${project.loader_version}"
    productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

test {
    useJUnitPlatform()
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "developers_hell_test"
        enableGameTests = true
        enableClientGameTests = false
        eula = true
    }
}
~~~

This is the current official DSL. `eula = true` explicitly represents acceptance of Minecraft's EULA and should remain visible. [VERIFIED: https://docs.fabricmc.net/develop/automatic-testing]

### Unit Smoke Contract

Use the unit test for pure invariants:

- module-gate defaults are deterministic;
- all-off/all-on gate snapshots do not change the declared stable-content catalog;
- namespace helper yields `developers_hell` IDs;
- no network, wall-clock, or filesystem dependency.

If a unit test touches `ItemStack` or another registry-dependent class, call `SharedConstants.tryDetectVersion()` and `Bootstrap.bootStrap()` once in `@BeforeAll`. [VERIFIED: official Fabric testing docs]

### Minimal Server GameTest Contract

1. Test mod metadata lives in `src/gametest/resources/fabric.mod.json` and declares a `fabric-gametest` entrypoint.
2. The test obtains `ModItems.FOUNDATION_TOKEN`.
3. It asks `BuiltInRegistries.ITEM` for the item's key.
4. It requires exact `developers_hell:foundation_token` and calls `context.succeed()`.

The official test shape uses `CustomTestMethodInvoker`, `@GameTest`, and `GameTestHelper`. The exact 26.2 lookup method is expected to be `BuiltInRegistries.ITEM.getKey(item)` but must be compile-checked against Loom-generated sources. [VERIFIED: official testing and item docs] [ASSUMED]

### Requirements → Test Map

| Req | Behavior | Type | Command/check | Exists? |
|-----|----------|------|---------------|---------|
| FND-01 | Built JAR enters world offline after prime | Production client manual | `.\gradlew.bat --offline runProductionClient --no-daemon`; create/enter world, save/quit | ❌ Wave 0 |
| FND-01 | JAR contains metadata/classes/resources | Archive integration | `jar --list --file build\libs\developers-hell-0.1.0.jar` | ❌ Wave 0 |
| FND-02 | Wrapper/JVM/frozen tuple | Build integration | `--version`, `help`, dependency report, `build` | ❌ Wave 0 |
| FND-02 | Cached build yields identical artifact | Reproducibility | offline `clean build` plus SHA-256 equality | ❌ Wave 0 |
| FND-03 | No client imports in common | Static audit | `rg -n "net\.minecraft\.client|com\.mojang\.blaze3d" src/main`; no matches | ❌ Wave 0 |
| FND-03 | Production client enters world | Manual runtime | `runProductionClient` | ❌ Wave 0 |
| FND-03 | Production server ready/clean stop | Manual runtime | `runProductionServer`, wait ready, type `stop` | ❌ Wave 0 |
| FND-04 | Gate does not mutate stable catalog | Unit | `test --tests "*ModuleGateTest"` | ❌ Wave 0 |
| FND-04 | Smoke item in actual registry | Server GameTest | `build` | ❌ Wave 0 |

### Artifact Inspection

~~~powershell
$jarPath = Resolve-Path 'build\libs\developers-hell-0.1.0.jar'
$entries = & jar --list --file $jarPath
$required = @(
  'fabric.mod.json',
  'dev/developershell/DevelopersHell.class',
  'dev/developershell/client/DevelopersHellClient.class',
  'assets/developers_hell/lang/en_us.json'
)
foreach ($entry in $required) {
    if ($entries -notcontains $entry) { throw "Missing JAR entry: $entry" }
}
if ($entries | Select-String 'com/example|example-mod|modid') {
    throw 'Example scaffold residue found'
}
Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath
~~~

Resolve exactly one expected artifact; do not select “the shortest JAR” because source/dev artifacts may coexist. [RECOMMENDED]

### Online/Offline Equality

~~~powershell
.\gradlew.bat clean build --no-daemon
$onlineHash = (Get-FileHash build\libs\developers-hell-0.1.0.jar -Algorithm SHA256).Hash
.\gradlew.bat --offline clean build --no-daemon
$offlineHash = (Get-FileHash build\libs\developers-hell-0.1.0.jar -Algorithm SHA256).Hash
if ($onlineHash -ne $offlineHash) { throw 'Online/offline JAR hash mismatch' }
~~~

### Production Client Checklist

1. Use clean `run/production-client`.
2. Confirm Loader lists Developer's Hell `0.1.0` and Fabric API.
3. Create and enter a singleplayer world.
4. Confirm no fatal metadata/resource errors.
5. Save/quit to title, then exit normally.
6. Repeat after cache prime with networking disabled or Gradle `--offline`.
7. Preserve `latest.log` and concise manual evidence.

The Windows client is interactive and may not self-terminate; a bounded manual checkpoint proves clean save/exit more credibly than a timer kill. [RECOMMENDED: user's discretion]

### Production Dedicated-Server Checklist

1. Use clean `run/production-server` and explicitly accept the EULA there.
2. Start with Java 25; the production task supplies `nogui`.
3. Confirm Loader lists the mod and the log reaches ready/`Done` state.
4. Reject `NoClassDefFoundError`, `ClassNotFoundException`, client-package, or mixin failures.
5. Type `stop` and require normal Gradle exit.
6. Repeat from the primed directory offline; preserve `logs/latest.log`.

Loom forwards `System.in`, so clean interactive `stop` is supported. First production-server/client launches are not offline proof because missing game/Loader/assets may be downloaded. [VERIFIED: Loom 1.17.19 production task sources]

### Sampling Rate

- **After scaffold:** `help` then `build`.
- **Per implementation task:** `test`.
- **Per wave:** `build` including server GameTests.
- **After common/client boundary changes:** import audit plus development server.
- **Phase gate:** clean online build, identical offline hash, archive inspection, production client world, production server ready/stop.

### Wave 0 Gaps

- [ ] Install/select Temurin `25.0.4+7`.
- [ ] Add Gradle 9.5.1 wrapper and root build files.
- [ ] Add unit and GameTest sources/metadata.
- [ ] Add production client/server tasks.
- [ ] Merge official ignore entries for `.gradle/`, `build/`, `run/`, IDE files, dumps, and recordings. [VERIFIED: https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/.gitignore]
- [ ] Create phase evidence for versions, commands, logs, artifact, and hash.

## Code Examples

### Build and Production Tasks

~~~groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

loom {
    splitEnvironmentSourceSets()
    mods {
        "developers_hell" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
    testImplementation "net.fabricmc:fabric-loader-junit:${project.loader_version}"
    productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

def java25Launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}

tasks.register("runProductionClient", net.fabricmc.loom.task.prod.ClientProductionRunTask) {
    runDir = file("run/production-client")
    javaLauncher = java25Launcher
}

tasks.register("runProductionServer", net.fabricmc.loom.task.prod.ServerProductionRunTask) {
    runDir = file("run/production-server")
    javaLauncher = java25Launcher
    installerVersion = project.fabric_installer_version
    loaderVersion = project.loader_version
    minecraftVersion = project.minecraft_version
}
~~~

Sources: official 26.2 template, testing docs, production task docs, and exact Loom 1.17.19 source. [VERIFIED]

### Frozen Properties

~~~properties
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true
org.gradle.configuration-cache=false
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17.19
fabric_api_version=0.158.0+26.2
fabric_installer_version=1.1.2
mod_version=0.1.0
maven_group=dev.developershell
archives_base_name=developers-hell
~~~

The platform values match official sources except the deliberate fixed Loom test and explicit current Installer pin. [VERIFIED: official template/Maven/Meta]

### Side-Safe `fabric.mod.json`

~~~json
{
  "schemaVersion": 1,
  "id": "developers_hell",
  "version": "${version}",
  "name": "Developer's Hell",
  "description": "An offline fictional comedy campaign about developer life and university bureaucracy.",
  "environment": "*",
  "entrypoints": {
    "main": ["dev.developershell.DevelopersHell"],
    "client": ["dev.developershell.client.DevelopersHellClient"]
  },
  "license": "Unlicense",
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
~~~

The dependency/entrypoint shape follows the official template; identity, description, and license are locked project values. [VERIFIED: https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/src/main/resources/fabric.mod.json] [VERIFIED: 01-CONTEXT.md] [VERIFIED: LICENSE]

### Current Item Registration Pattern

~~~java
public final class ModItemIds {
    public static final ResourceKey<Item> FOUNDATION_TOKEN =
            ResourceKey.create(
                    Registries.ITEM,
                    Identifier.fromNamespaceAndPath(DevelopersHell.MOD_ID, "foundation_token")
            );

    private ModItemIds() {
    }
}

public final class ModItems {
    public static final Item FOUNDATION_TOKEN =
            register(ModItemIds.FOUNDATION_TOKEN, Item::new, new Item.Properties());

    private static Item register(
            ResourceKey<Item> key,
            Function<Item.Properties, Item> factory,
            Item.Properties properties
    ) {
        Item item = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    public static void initialize() {
        // Called unconditionally from the common initializer.
    }

    private ModItems() {
    }
}
~~~

This registration shape is from current official 26.2 docs; `foundation_token` is a recommended smoke ID. [VERIFIED: https://docs.fabricmc.net/develop/items/first-item] [RECOMMENDED]

The item can reuse a vanilla paper model in Phase 1 through the current `assets/developers_hell/items` client-item indirection, avoiding a generated texture while still preventing a missing-model visual. The exact JSON should be tested in the production client. [VERIFIED: https://docs.fabricmc.net/develop/items/first-item] [RECOMMENDED]

### Main and Client Entrypoints

~~~java
package dev.developershell;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DevelopersHell implements ModInitializer {
    public static final String MOD_ID = "developers_hell";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.initialize();
        // Install behavior hooks only after stable registrations.
        LOGGER.info("Developer's Hell initialized");
    }
}
~~~

~~~java
package dev.developershell.client;

import net.fabricmc.api.ClientModInitializer;

public final class DevelopersHellClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // No renderer/HUD behavior is in Phase 1.
    }
}
~~~

Source shape: official Fabric `26.2` example initializers. [VERIFIED: https://github.com/FabricMC/fabric-example-mod/tree/26.2/src]

## Common Pitfalls

### 1. Gradle Actually Runs on Java 21

**Failure:** unsupported runtime/class errors even though a JDK 25-looking directory exists.

**Cause:** PATH currently resolves to Java 21 and the observed JDK 25 directory is incomplete.

**Prevention:** verify `where.exe`, both version commands, and wrapper JVM in one shell before setup. [VERIFIED: local audit]

### 2. Publication Is Mistaken for Loom Compatibility

**Failure:** `1.17.19` exists in Maven but the exact template/plugin combination fails.

**Prevention:** untouched fixed-pin gate; capture failure and change only to official `1.17-SNAPSHOT`. [VERIFIED: official Maven/template] [RECOMMENDED]

### 3. Wrong Artifact or `remapJar` Assumption

**Failure:** script selects sources/dev JAR or depends on a legacy output task.

**Cause:** Loom 1.17 chooses ordinary `jar` for no-remap 26.2 and `remapJar` only when remapping is enabled.

**Prevention:** inspect `tasks --all` and `build/libs`, then require exact archive name/contents. [VERIFIED: Loom source]

### 4. Client Type Leaks into Common

**Failure:** client works while dedicated server crashes during linkage.

**Prevention:** split source sets, zero client-package grep matches in `src/main`, dev server early, production server at gate. [RECOMMENDED: FND-03]

### 5. Toggle Disables Registration

**Failure:** a saved world loses content IDs after restart with a module disabled.

**Prevention:** registry initializers never receive/query `ModuleGate`; only callbacks, spawns, interactions, and scheduled behavior consult it. [RECOMMENDED: FND-04]

### 6. GameTest Exists but Is Not Executed

**Failure:** missing separate source set, test metadata, `fabric-gametest` entrypoint, EULA flag, or build dependency.

**Prevention:** use `fabricApi.configureTests` exactly and deliberately make the test fail once to prove `build` observes it. [VERIFIED: official testing docs] [RECOMMENDED: test-the-test]

### 7. Dev Run Is Called Production Proof

**Failure:** `runClient` succeeds while built output/classpath fails.

**Prevention:** final evidence uses registered production task types or an equally clean external profile. [VERIFIED: production run docs]

### 8. Offline Claim Exceeds Cache Boundary

Separate these contracts:

- **Runtime offline:** Minecraft 26.2, Loader, Fabric API, Developer's Hell, libraries, and assets are already local; the mod makes no network calls.
- **Build offline:** Gradle distribution, Maven metadata/artifacts, Minecraft libraries/assets, and Loom products were successfully primed from the same repositories.

A fresh unprimed machine cannot meet either contract without obtaining prerequisites. [VERIFIED: Gradle caching docs]

### 9. Bad Minecraft Range

Loader `0.19.3` documents a parsing problem with `.x` style versions having more than three components. Preserve `"minecraft": "~26.2"`. [VERIFIED: https://docs.fabricmc.net/develop/loader/fabric-mod-json]

### 10. Build/Run Dirt Is Committed

The current `.gitignore` lacks the complete official Gradle/Fabric set. Merge `.gradle/`, `build/`, `out/`, `run/`, IDE files, dumps, and recordings before first execution; audit `git status --short` after every smoke. [VERIFIED: .gitignore] [VERIFIED: official example .gitignore]

## State of the Art

| Old approach | Current 26.2 approach | Impact |
|--------------|-----------------------|--------|
| Obfuscated game plus Yarn mappings | 26.1+ unobfuscated names and `net.fabricmc.fabric-loom` | Omit mappings and do not translate legacy names by guesswork. [VERIFIED: Fabric 26.2 announcement] |
| Legacy remap Loom | No-remap 26.2 plugin | Production output is normally `jar`, not technically remapped. [VERIFIED: Loom 1.17 source] |
| Dev runs only | Explicit production task types | Built output gets a production-like client/server classpath. [VERIFIED: official Loom docs] |
| Plain JUnit/ad-hoc server | Loader JUnit plus Loom GameTests | Unit and integrated tests live under wrapper `build`. [VERIFIED: official testing docs] |
| Archive reproducibility opt-in | Gradle 9 reproducible timestamps/order by default | SHA-256 equality is practical but must still be measured. [VERIFIED: Gradle security guidance] |

**Do not use:** Yarn, `mappings` dependency, `net.fabricmc.fabric-loom-remap`, Java 21, old wrapper pins, `26.2.x` ranges, raw OpenGL, system Gradle, or runtime network libraries.

## Security Domain

Security enforcement is enabled at ASVS Level 1. This is a local offline mod, so web authentication/session controls mostly do not apply; supply-chain integrity, local input/resource handling, and side-safe execution are the Phase 1 concerns. [VERIFIED: .planning/config.json]

### Applicable ASVS Categories

| Category | Applies | Control |
|----------|---------|---------|
| V2 Authentication | No | No mod account/auth system; launcher authentication is outside the mod. |
| V3 Session Management | No | No web/application session. |
| V4 Access Control | No in Phase 1 | No privileged command, endpoint, or multiplayer admin surface. |
| V5 Validation | Yes | Namespaced bounded IDs; future local config must reject malformed values. |
| V6 Cryptography | No | No secrets/credentials; do not invent cryptography. |
| V10 Malicious Code | Yes | First-party pinned artifacts, wrapper, archive/dependency audit, no network/telemetry. |
| V12 File/Resource Handling | Yes | Loader/project-owned paths only; never execute game-supplied files or paths. |
| V14 Configuration | Yes | Fictional defaults, no committed local JDK path, early version failure. |

### Threat Patterns

| Threat | STRIDE | Mitigation |
|--------|--------|------------|
| Dependency/repository drift | Tampering | Exact pins, first-party sources, dependency evidence, offline rebuild. |
| Run world/private config committed | Information disclosure | Official ignore rules and status audit. |
| Client class on server | Denial of service | Source split, grep audit, production server. |
| Toggle removes ID | Tampering / denial of service | Unconditional registration and behavior gates. |
| Runtime HTTP/telemetry | Information disclosure | No HTTP package, endpoint, key, or network code. |

### Supply-Chain Recommendations

- Preserve official wrapper files and verify `distributionUrl` is Gradle `9.5.1`.
- Add Gradle's official `distributionSha256Sum` after obtaining it from https://gradle.org/release-checksums/. [RECOMMENDED]
- Add no third-party Maven repository; the official example leaves `repositories` empty because Loom supplies essentials. [VERIFIED: official build.gradle]
- Inspect `buildEnvironment`/dependency reports and final JAR.
- Never commit caches, assets, worlds, EULA files from external profiles, IDE JDK paths, or credentials.

## Assumptions Log

| # | Claim | Risk if wrong |
|---|-------|---------------|
| A1 | `BuiltInRegistries.ITEM.getKey(item)` is the exact 26.2 GameTest lookup. | LOW: compile against generated sources and adapt the assertion without changing behavior. |
| A2 | Windows GUI is available for the bounded production-client smoke. | MEDIUM: if headless/locked, leave one human checkpoint while all other gates continue. |
| A3 | Loom/resources preserve byte equality under Gradle 9 defaults. | MEDIUM: if hashes differ, compare entries and fix nondeterminism; do not weaken the locked criterion. |

## Open Questions

1. **Will fixed Loom `1.17.19` pass on Java 25?**
   - Known: official marker exists; template still uses snapshot.
   - Gate: untouched `help`/`build`; snapshot only after captured failure.

2. **What exact output task does the generated no-remap build expose?**
   - Known: Loom production code selects `jar` for no-remap, `remapJar` otherwise.
   - Gate: `tasks --all` and `build/libs`; record actual task/artifact.

3. **Can the overnight environment keep an interactive client session?**
   - Known: entering a world and clean exit are manual on Windows.
   - Fallback: run all automated/server gates, then leave one concise client checkpoint.

4. **Which launcher assets are already cached outside the repo?**
   - Known: neither Gradle nor Minecraft can use absent prerequisites offline.
   - Gate: prime a clean production directory once online, then disconnect and repeat.

## Sources

### Primary (official; MEDIUM confidence from classifier)

- https://github.com/FabricMC/fabric-example-mod/tree/26.2 — canonical template.
- https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/gradle.properties — platform pins.
- https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/build.gradle — plugin, Java, dependencies, source split.
- https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/gradle/wrapper/gradle-wrapper.properties — wrapper.
- https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/src/main/resources/fabric.mod.json — metadata.
- https://fabricmc.net/2026/06/15/262.html — Java/mappings/rendering transition.
- https://docs.fabricmc.net/develop/automatic-testing — Loader JUnit and GameTest.
- https://docs.fabricmc.net/develop/loom/production-run-tasks — production task DSL.
- https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/task/prod/AbstractProductionRunTask.java — artifact/runtime-mod defaults.
- https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/task/prod/ClientProductionRunTask.java — client behavior/assets.
- https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/task/prod/ServerProductionRunTask.java — server behavior/stdin.
- https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.17.19/fabric-loom-1.17.19-sources.jar — exact Loom sources.
- https://docs.fabricmc.net/develop/items/first-item — 26.2 registration/resources.
- https://maven.fabricmc.net/net/fabricmc/fabric-loader/ — Loader.
- https://maven.fabricmc.net/net/fabricmc/fabric-loader-junit/ — test runtime.
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.158.0%2B26.2/ — API artifact.
- https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/1.17.19/ — Loom marker.
- https://meta.fabricmc.net/v2/versions/installer — stable Installer.
- https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.3/1.1.2/server/jar — exact server artifact endpoint.
- https://docs.gradle.org/current/userguide/compatibility.html — Java 25 support.
- https://docs.gradle.org/current/userguide/dependency_caching.html — offline semantics.
- https://docs.gradle.org/current/userguide/best_practices_security.html — reproducible archives.
- https://adoptium.net/installation/ — Windows JDK routes.
- https://adoptium.net/news/2026/08/eclipse-temurin-8u502-11032-17020-21012-2504-2602-available — JDK `25.0.4+7`.
- https://feedback.minecraft.net/hc/en-us/articles/46690753273997-Minecraft-Java-Edition-26-2 — official game release.

### Secondary

None. Community tutorials and third-party examples were excluded.

### Tertiary

None beyond the explicitly logged assumptions.

## Metadata

**Confidence breakdown:**

- Standard stack: MEDIUM — first-party pins are live-checked; fixed Loom needs a Java 25 compile.
- Architecture: MEDIUM — official source split/registries/tasks, with project-specific smoke naming.
- Validation: MEDIUM — commands are official; client interaction and output-task evidence await scaffold.
- Offline guarantee: MEDIUM — semantics verified, actual cache/hash must be measured.
- Pitfalls: MEDIUM — based on primary docs and observed workstation state.

**Research date:** 2026-08-25

**Valid until:** 2026-09-01 for Fabric/Loom patch claims; architectural guidance remains valid while target `26.2` is locked.
