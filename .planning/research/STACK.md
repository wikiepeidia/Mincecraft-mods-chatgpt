# Technology Stack

**Project:** Developer's Hell
**Researched:** 2026-08-25
**Target:** Minecraft Java Edition 26.2, Fabric, Windows-first development
**Overall confidence:** MEDIUM

The confidence label follows the GSD source classifier for cross-checked official pages. The core pins are supported by Mojang, Fabric, Gradle, and Adoptium primary sources; the remaining uncertainty is concentrated in Minecraft 26.2 API churn and the deliberate replacement of Fabric's moving `1.17-SNAPSHOT` Loom alias with a concrete Loom release.

## Prescriptive Decision

Build one Java 25 Fabric mod JAR against Minecraft 26.2. Start from the official Fabric 26.2 example project, retain its Groovy Gradle layout and split client/common source sets, and pin all build inputs. Use the full Fabric API umbrella for this sprint. Use vanilla entity AI, `ServerBossEvent`, registries, synced entity data, saved world data, sounds, particles, translations, and advancements before considering any third-party library.

The exact baseline is:

| Layer | Exact choice | Confidence | Reason |
|---|---:|---|---|
| Minecraft Java | `26.2` | MEDIUM | Current requested stable target; Mojang released it on 2026-06-16. |
| Java language/runtime | Java `25`; Eclipse Temurin `25.0.4+7` x64 JDK | MEDIUM | Fabric 26.2 requires Java 25; Temurin gives a reproducible Windows distribution. |
| Fabric Loader | `0.19.3` | MEDIUM | Exact official 26.2 example-project pin. |
| Fabric API | `0.158.0+26.2` | MEDIUM | Exact official 26.2 example-project pin and current artifact in Fabric Maven. |
| Fabric Loom | `1.17.19` | MEDIUM | Concrete stable release in the same Loom 1.17 line recommended for 26.2. The official example uses the moving `1.17-SNAPSHOT` alias; validate this substitution immediately. |
| Gradle | Wrapper `9.5.1` | MEDIUM | Exact Fabric 26.2 recommendation and example-project wrapper; supports running on Java 25. |
| Mapping model | Mojang's unobfuscated names; **no Yarn dependency** | MEDIUM | Minecraft 26.1+ ships unobfuscated with parameter names; Fabric no longer maintains Yarn for these versions. |
| Gradle DSL | Groovy `build.gradle` and `settings.gradle` | MEDIUM | Matches the official example and minimizes sprint setup work. |
| Mod ID / artifact | `developers_hell` / `developers-hell` | MEDIUM | Stable lowercase namespace for resources, saves, commands, and tests. |
| Initial mod version | `0.1.0` | MEDIUM | Honest anthology MVP version; avoid implying API stability. |
| Resource/data formats | Resource pack `88.0`; data pack `107.1` | MEDIUM | Values published for Minecraft 26.2. These matter to generated or external packs, although an embedded Fabric resource set is discovered through `fabric.mod.json`. |

Do not upgrade one item in isolation during the 1–2 day build. Treat this matrix as a unit. In particular, a newer Gradle exists, but upgrading beyond the official 26.2 wrapper gives no gameplay benefit and adds Loom compatibility risk.

## Compatibility Matrix and Hard Gates

| Combination | Status | Required action | Confidence |
|---|---|---|---|
| Minecraft `26.2` + Java `25` | Required | Fail setup early if `java --version` or `javac --version` reports anything other than 25. | MEDIUM |
| Loader `0.19.3` + Fabric API `0.158.0+26.2` | Recommended exact runtime | Ship/test with these exact JARs even if `fabric.mod.json` permits a compatible range. | MEDIUM |
| Loom `1.17.19` + Gradle `9.5.1` + Java `25` | Recommended reproducible build | First gate: run `help`, then `build`. If plugin resolution or Minecraft setup fails, use the official template value `1.17-SNAPSHOT` and record the resolved Loom build in the lock/handoff. Do not change Minecraft, Loader, API, and Gradle at the same time. | MEDIUM |
| `net.fabricmc.fabric-loom` + no mappings dependency | Required for 26.2 | Reject generated builds that add Yarn, intermediary mappings, or `net.fabricmc.fabric-loom-remap`. | MEDIUM |
| Client/common split | Required | Keep renderers, keybinds, screens, HUD code, and client initializers under `src/client`; common entity/state code belongs under `src/main`. Run a dedicated-server smoke test. | MEDIUM |
| Loader metadata `minecraft: ~26.2` | Required | Follow the official template. Avoid `.x` ranges because Loader 0.19.3 documents a version-range parsing issue for versions with more than three components. | MEDIUM |
| Supported renderer abstraction | Required | Exercise both default and Vulkan-capable paths when practical; never call raw OpenGL. | MEDIUM |

The Loom choice is the one deliberate divergence from the example template: `1.17.19` is a fixed artifact, whereas `1.17-SNAPSHOT` can move. If the fixed pin does not resolve cleanly with the untouched official template, the sprint-safe fallback is the template's snapshot alias—not a random older Loom release. This must be resolved in the project-bootstrap phase before feature work.

## Recommended Stack

### Core Framework

| Technology | Version | Purpose | Why | Confidence |
|---|---:|---|---|---|
| Minecraft Java Edition | `26.2` | Game runtime and vanilla gameplay APIs | Requested target and current stable release under scope. | MEDIUM |
| Eclipse Temurin JDK | `25.0.4+7` | Compile and run Gradle/Minecraft | Fabric requires Java 25; a full JDK supplies `javac` and tooling. | MEDIUM |
| Fabric Loader | `0.19.3` | Discover and initialize the mod | Official 26.2 template pin; lightweight and appropriate for one JAR. | MEDIUM |
| Fabric API | `0.158.0+26.2` | Supported hooks, registries, networking, rendering, datagen, and tests | The umbrella dependency is faster and safer than hand-curating modules during a two-day sprint. | MEDIUM |
| Fabric Loom | `1.17.19` | Gradle plugin for Minecraft development, run configs, remapped output, datagen, and tests | Stable concrete 1.17 artifact avoids a moving snapshot while staying on Fabric's recommended 26.2 Loom line. | MEDIUM |
| Gradle Wrapper | `9.5.1` | Reproducible build entry point | Official 26.2 template pin and compatible with Java 25. | MEDIUM |

### Mapping and Source Naming

Minecraft 26.1 and later are distributed unobfuscated and include parameter names. Therefore:

- Use plugin ID `net.fabricmc.fabric-loom`.
- Omit the old `mappings` dependency entirely.
- Write code against the names visible in the 26.2 sources, commonly described as Mojang/unobfuscated names.
- Configure Blockbench code export for Mojang mappings when an export path asks for a mapping set.
- Translate old tutorials conceptually; do not paste Yarn-named 1.20/1.21 code and patch it by guesswork.
- Do not use `net.fabricmc.fabric-loom-remap`; Fabric reserves that legacy path for Minecraft 1.21.11 and earlier.

**Confidence:** MEDIUM. The migration direction is official and clear; individual entity, GUI, renderer, and registry symbols still need IDE/source confirmation because the 26.2 API surface is young.

### Database

| Technology | Version | Purpose | Why | Confidence |
|---|---:|---|---|---|
| None | N/A | N/A | Boss progress and module state should use vanilla world/player persistence. A database would add deployment, corruption, and synchronization risk with no value for an offline singleplayer-first comedy mod. | MEDIUM |

Use vanilla `SavedData` for world/campaign state and entity/player persistent data only where required. Store human-editable mod settings in one local JSON config. Keep dialogue, loot, tags, advancements, sounds, and models in namespaced resources/data so they remain diffable and testable.

### Infrastructure

| Technology | Version | Purpose | Why | Confidence |
|---|---:|---|---|---|
| Gradle Wrapper | `9.5.1` | Build, test, datagen, and launch tasks | No global Gradle installation is required or desired. | MEDIUM |
| GitHub Actions | `actions/checkout@v6`, `actions/setup-java@v5` | Optional CI build using Temurin 25 | Current major releases; enough for compile, unit, and server GameTest gates. | MEDIUM |
| Local Minecraft run configs | Generated by Loom 1.17 | Client/server smoke testing | Fastest feedback loop for bosses, assets, commands, and dedicated-server safety. | MEDIUM |
| External service | None | Runtime | All gameplay must work without network access, an OpenAI account, keys, or a remote server. | MEDIUM |

CI is useful but should not block the first playable build. The local reproducible command is the source of truth. If GitHub is used, the minimum workflow is Ubuntu, Temurin 25, Gradle cache, and `./gradlew build --no-daemon`. Client GameTests can run separately under XVFB, but Fabric notes that their network synchronizer can be flaky in GitHub Actions; use `-Dfabric.client.gametest.disableNetworkSynchronizer=true` only for that CI runner and retain a local visual smoke test.

### Supporting APIs and Libraries

Use the full Fabric API dependency at the pinned version. These are the relevant surfaces, not additional third-party dependencies:

| API / vanilla surface | Purpose in Developer's Hell | Use when | Confidence |
|---|---|---|---|
| Fabric lifecycle/tick events | Campaign triggers, timed attacks, encounter cleanup | An official event exists; keep ticking bounded and server-authoritative. | MEDIUM |
| Fabric Command API v2 | `/devhell` start, reset, status, module toggles, debug hooks | Admin/config/test control; commands also avoid spending sprint time on a config GUI. | MEDIUM |
| Fabric entity APIs and vanilla entity registration | Chairman, lecture, jury, intern mobs and their attributes | Register entity types/default attributes and reuse vanilla goal AI. | MEDIUM |
| Vanilla `ServerBossEvent` | Boss health/name presentation | Every boss phase; drive it from server state. | MEDIUM |
| Vanilla goal AI and navigation | Boss movement and attacks | Prefer composable goals over a bespoke behavior-tree dependency. | MEDIUM |
| Synced entity data and Fabric Networking API v1 | Minimal authoritative state for animations/HUD | Only for state not already synchronized by vanilla. Use typed payloads; never make gameplay client-authoritative. | MEDIUM |
| Fabric particles and vanilla sound events | Readable telegraphs, punchlines, impact feedback | Every encounter, with rate limits and accessibility-aware timing. | MEDIUM |
| Fabric HUD/rendering APIs | Boss-specific client overlays and visual effects | Only in `src/client`; stay inside supported render-state and Blaze3D abstractions. | MEDIUM |
| Fabric Resource Loader | Bundled assets/data and optional bundled data-driven content | Load only local resources from the JAR or local packs. | MEDIUM |
| Fabric Data Generation | Recipes, tags, loot tables, advancements, language, model JSON | Generate repetitive JSON and check it into source control. | MEDIUM |
| Fabric GameTest support | Deterministic in-game validation | Boss lifecycle, rewards, phase changes, persistence, and server safety. | MEDIUM |
| Fabric Loot API v3 / Item API | Comedy drops and custom tool behavior | Use only for features that cannot be expressed as ordinary data packs/registrations. | MEDIUM |
| SLF4J already exposed by the runtime | Structured logging | Startup summary, config errors, encounter transitions; no `System.out`. | MEDIUM |
| Mixin already integrated through Loader/Loom | Last-resort hook | Only after proving no Fabric event or vanilla subclass seam exists. Keep each injection narrow and tested. | MEDIUM |

Do not add an OpenAI SDK. “Rich ChatGPT” is a fictional, scripted in-game character: all dialogue, behavior, textures, sounds, and encounter logic are bundled locally. The joke may reference lavish token spending, but the mod must never make an API call, request a key, phone home, or silently download content.

## Project Build Configuration

### `gradle.properties`

Use the exact pins below. Project metadata may change without touching the platform pins.

```properties
# Gradle
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true
org.gradle.configuration-cache=false

# Fabric platform
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17.19
fabric_api_version=0.158.0+26.2

# Mod metadata
mod_version=0.1.0
maven_group=dev.developershell
archives_base_name=developers-hell
```

Keep configuration cache disabled. The current official Fabric example does so because of a Loom issue. Raising the Gradle heap to `2G` is acceptable only if datagen or tests demonstrate an out-of-memory failure; it is not a first-step optimization.

### `build.gradle` baseline

The dependency configurations below intentionally match the unobfuscated 26.2 example: `implementation`, not old tutorial-era `modImplementation`, and no `mappings` line.

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    // Loom supplies the repositories needed for Minecraft and Fabric.
    // Add no repository until a declared dependency actually requires it.
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    testImplementation "net.fabricmc:fabric-loader-junit:${project.loader_version}"
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

fabricApi {
    configureDataGeneration {
        client = true
    }

    configureTests {
        createSourceSet = true
        modId = "developers_hell_test"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

test {
    useJUnitPlatform()
}
```

Setting `eula = true` records acceptance of the Minecraft EULA for automated GameTest servers. Retain it only if the project owner accepts that condition. The exact DSL should be copied from the current 26.2 Fabric testing documentation during bootstrap, because this is one of the Loom surfaces most likely to change.

### `fabric.mod.json` dependency policy

Use separate common and client entrypoints and the official compatibility shape:

```json
{
  "schemaVersion": 1,
  "id": "developers_hell",
  "version": "${version}",
  "environment": "*",
  "entrypoints": {
    "main": ["dev.developershell.DevelopersHell"],
    "client": ["dev.developershell.client.DevelopersHellClient"]
  },
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```

The build pins the exact Fabric API artifact and release instructions must name that exact version. The metadata wildcard follows the official template and avoids duplicating Minecraft compatibility in a compound Fabric API version. Test the release against exactly `0.158.0+26.2` before publishing.

## Source-Set and Package Choices

| Location | Allowed responsibility | Forbidden responsibility | Confidence |
|---|---|---|---|
| `src/main/java` | Registries, entities, AI, combat, commands, campaign state, config, common payload declarations | `Minecraft`, renderer, screen, HUD, keybind, client-only imports | MEDIUM |
| `src/client/java` | Entity renderers/models, HUD, screens, particles that require client classes, client payload handlers | Authoritative encounter state, loot, damage, progression | MEDIUM |
| `src/main/resources/assets/developers_hell` | Textures, models, sounds, `sounds.json`, translations | Generated downloads or secrets | MEDIUM |
| `src/main/resources/data/developers_hell` | Loot, recipes, tags, advancements, structures/GameTest data | Mutable player progress | MEDIUM |
| `src/test/java` | Pure deterministic unit tests | Tests that require a transformed Minecraft runtime | MEDIUM |
| generated GameTest source set | In-runtime server/client tests | General unit logic that can run without Minecraft | MEDIUM |

This split is a release gate, not cleanup. A mod can work in an integrated client and still crash a dedicated server if common classes load a client symbol.

## Testing Stack

### Mandatory in the sprint

| Layer | Tool/version | Scope | Gate | Confidence |
|---|---|---|---|---|
| Java unit tests | `fabric-loader-junit:0.19.3`, JUnit Platform | Weighted shuffle determinism, phase transitions, cooldown math, dialogue/config validation | `test` passes | MEDIUM |
| Server GameTests | Fabric API/Loom `0.158.0+26.2` / `1.17.19` | Spawn, aggro, boss-bar lifecycle, phase changes, death rewards, reset, save/reload-safe state | Included in `build` after DSL setup | MEDIUM |
| Dedicated server smoke | Loom `runServer` | Detect client-only class loading and registration errors | Server reaches ready state and loads the mod | MEDIUM |
| Client smoke | Loom `runClient` | Models, textures, sounds, telegraphs, boss bars, commands, complete campaign loop | Manual short checklist | MEDIUM |

### Valuable if time remains

| Layer | Tool | Scope | Note | Confidence |
|---|---|---|---|---|
| Client GameTests | Fabric client GameTest support | HUD/render smoke and automated screenshots | Run with `runClientGameTest`; keep visual assertions coarse because pixels vary by renderer/driver. | MEDIUM |
| CI | GitHub Actions | Clean Temurin 25 build and server tests | Do not spend sprint time stabilizing flaky client CI before the local campaign is playable. | MEDIUM |
| Seeded soak test | GameTest/debug command | Run randomized shuffles and repeated boss transitions | Log the seed so every failure is reproducible. | MEDIUM |

Plain JUnit alone is not sufficient for classes that depend on Minecraft runtime transformation or Mixins. Put only pure logic in ordinary unit tests; use Fabric's Loader JUnit bridge and GameTests for game-integrated behavior.

## Asset Toolchain

### Authoring Tools

| Tool | Version | Purpose | When to use | Confidence |
|---|---:|---|---|---|
| Blockbench | `5.1.6` stable | Low-poly entity models, UVs, simple animations, texture painting | Primary boss asset tool; select Mojang mappings for code exports. | MEDIUM |
| Blockbench texture editor | Bundled with `5.1.6` | Fast 16x16/32x32 pixel cleanup | Default texture workflow; avoids installing another editor. | MEDIUM |
| Aseprite | `1.3.18.2` | Optional pixel-art cleanup and palette control | Use only if already licensed/available; it is not a build dependency. | MEDIUM |
| Audacity | `3.7.8` stable | Trim, normalize, and export sound effects/voice stingers | Export positional boss/entity audio as mono OGG Vorbis. Avoid the Audacity 4 beta during the sprint. | MEDIUM |
| AI image generation | Session capability, not runtime software | Original concept/source art and texture starting points | Generate source art, then crop/pixel-clean and commit deterministic PNGs. Never generate or fetch art at runtime. | MEDIUM |
| Fabric datagen | Pinned Fabric API/Loom | JSON resources/data | Generate repetitive models, recipes, tags, loot, advancements, and language entries. | MEDIUM |

### Asset Constraints

- Use PNG for textures. Start item/block icons at Minecraft's conventional 16x16 baseline; use 32x32 only where the visual gain is obvious.
- Keep each entity texture's declared dimensions identical to its `LayerDefinition`/model texture dimensions.
- Put assets under `assets/developers_hell/...` and data under `data/developers_hell/...`.
- Put English text in `assets/developers_hell/lang/en_us.json`; do not hard-code dialogue that should be translatable.
- Put boss/entity SFX in `assets/developers_hell/sounds/...` as mono OGG Vorbis, register them in `sounds.json`, and provide subtitle translation keys.
- Keep source files such as `.bbmodel` in a clearly marked source-art directory; ship only runtime PNG/JSON/OGG resources in the JAR unless the license calls for sources.
- Record asset provenance. Use original/generated assets or assets with explicit compatible licenses; do not scrape Minecraft skin sites or reuse unverified community packs.
- Prefer a small number of readable silhouettes, palette variants, vanilla-compatible rigs, particles, and sounds over complex animation middleware.

**Confidence:** MEDIUM. Minecraft formats are supported by official documentation; exact creative-tool versions are current release pins but those desktop applications are not required by the build.

## Installation and Bootstrap

### 1. Install and verify Java 25

This workstation currently reports Eclipse Temurin `21.0.11` for both `java` and `javac`, and `winget` is not available. Install the Windows x64 **JDK**, not merely a JRE, from Adoptium's Temurin `25.0.4+7` release using its MSI or ZIP. Open a new PowerShell after installation and verify:

```powershell
where.exe java
where.exe javac
java --version
javac --version
```

Both version commands must report Java 25 before Gradle bootstrap. If several JDKs are installed, point the terminal/IDE Gradle JVM to the Temurin 25 JDK. Do not uninstall Java 21 merely to satisfy this project.

### 2. Scaffold from the official 26.2 template

Use the Fabric Template Mod Generator or the `26.2` branch of `FabricMC/fabric-example-mod`. Copy the build/wrapper/source skeleton into this existing repository; do not clone a second Git repository over it. Then apply the project identity and exact pins from this document.

Immediate checks:

```powershell
.\gradlew.bat --version
.\gradlew.bat help --no-daemon
.\gradlew.bat build --no-daemon
```

The first command must show Gradle 9.5.1 running on Java 25. The second isolates Loom/plugin resolution. The third proves Minecraft, Loader, Fabric API, Loom, compilation, and test wiring before any boss code is written.

### 3. Daily development commands

```powershell
# Generate and review JSON assets/data
.\gradlew.bat runDatagen

# Fast pure-logic tests
.\gradlew.bat test

# Full compile, server GameTests, resource processing, and distributable JAR
.\gradlew.bat build --no-daemon

# Manual gameplay and dedicated-server checks
.\gradlew.bat runClient
.\gradlew.bat runServer

# Optional client GameTests
.\gradlew.bat runClientGameTest

# List the exact Fabric/Loom tasks if a task name changed
.\gradlew.bat tasks --group fabric
```

The distributable mod JAR will be under `build\libs\`. Ignore `*-sources.jar` for end-user installation.

### 4. Offline build check

Initial dependency resolution requires network access to obtain Gradle, Minecraft, Fabric, and libraries. Runtime gameplay does not. After one successful online build, verify the dependency cache with:

```powershell
.\gradlew.bat --offline build --no-daemon
```

An offline build is a useful resilience check, not a promise that a fresh machine can build without first obtaining dependencies. A playable installation needs only locally installed Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, and the Developer's Hell JAR.

## Alternatives Considered

| Category | Recommended | Alternative | Why not for this sprint | Confidence |
|---|---|---|---|---|
| Mod loader | Fabric Loader `0.19.3` | NeoForge | User selected Fabric; changing ecosystems sacrifices the verified 26.2 template and available sprint time. | MEDIUM |
| Loom | `1.17.19` | `1.17-SNAPSHOT` | Snapshot is the official template value but moves over time. Keep only as the documented fallback if the stable pin fails the untouched-template compile gate. | MEDIUM |
| Gradle | Wrapper `9.5.1` | Newest Gradle | Fabric's 26.2 recommendation is the compatibility target; a wrapper upgrade has no user-facing payoff. | MEDIUM |
| Mappings | Unobfuscated/Mojang names | Yarn or legacy remap Loom | Yarn is no longer maintained for 26.1+ and old names make tutorials misleading. | MEDIUM |
| Fabric dependencies | Full Fabric API `0.158.0+26.2` | Individual Fabric modules | Module trimming saves little in a one-JAR comedy mod and increases setup/debug time. Optimize after the playable campaign exists. | MEDIUM |
| Entity animation | Vanilla model/animation code, simple rigs | GeckoLib | Another runtime dependency and a 26.2 compatibility surface are poor tradeoffs for a two-day first release. Revisit only if a required boss animation cannot be expressed simply. | MEDIUM |
| Configuration | One local JSON file plus `/devhell` commands | Cloth Config + Mod Menu | A GUI and two more dependencies are not core gameplay. Add an optional integration in a later release if players demand it. | MEDIUM |
| Language | Java 25 | Fabric Language Kotlin | Java matches Minecraft/Fabric APIs directly and avoids a Kotlin runtime/language-adapter dependency. | MEDIUM |
| Distribution architecture | Single Fabric mod | Architectury/multiloader | Cross-loader abstractions multiply code paths and testing beyond the sprint. | MEDIUM |
| Persistence | Vanilla save data | SQLite/external database | Offline world-local progress does not justify database lifecycle and schema work. | MEDIUM |
| AI character | Scripted local content | OpenAI API/SDK | Runtime must be offline; remote AI adds cost, latency, moderation, privacy, credential, and failure-mode complexity. | MEDIUM |
| Rendering | Blaze3D and supported Fabric/Minecraft abstractions | Raw OpenGL/custom backend | Minecraft 26.2 has an optional Vulkan backend and does not support raw OpenGL calls. | MEDIUM |
| Asset pipeline | Stable Blockbench/Audacity and committed output | Beta tools/runtime generation | Deterministic bundled assets are testable and work offline. | MEDIUM |

## Explicitly Do Not Use

- Minecraft snapshots, including any 26.3 development build.
- Yarn mappings, a `mappings` dependency, or `net.fabricmc.fabric-loom-remap` for this 26.2 project.
- A system-installed Gradle; only `gradlew.bat` is authoritative.
- Raw OpenGL calls, direct renderer backend assumptions, or custom shaders in the MVP.
- Client imports from common entrypoints or entity/business-logic classes.
- OpenAI libraries, API keys, HTTP clients for gameplay, telemetry, analytics, remote config, or runtime asset downloads.
- GeckoLib, Architectury, Kotlin, Cloth Config, Mod Menu, or mixin-helper libraries unless a later phase proves a concrete need.
- A separate database or web service.
- Fabric API nightlies or arbitrary “latest” dependency upgrades after the baseline build passes.
- Large voice/music files, beta creative-tool versions, or assets with unclear reuse rights.
- Unseeded random behavior in tests. Store/log the seed for every shuffle or procedural scenario.

## Minecraft 26.2 Churn and Verification Gates

These items need a compile/spike before the roadmap commits to implementation details:

1. **Loom stable pin:** Confirm `1.17.19` resolves and builds the untouched official 26.2 example with Gradle 9.5.1 and Java 25. Fall back only to the template's `1.17-SNAPSHOT` alias if this fails.
2. **Unobfuscated symbols:** Confirm the exact 26.2 class and method names in generated sources/IDE before writing shared abstractions. Do not infer names from Yarn tutorials.
3. **Rendering model:** Minecraft 26.2 adds an optional Vulkan renderer and continues the render-state extraction transition. Keep custom rendering inside current Fabric/Minecraft APIs; smoke test the selected renderer path and remove raw GL assumptions.
4. **GUI/HUD moves:** Relevant methods have been reorganized around `Gui`/`Hud`; compile a tiny boss overlay before designing an elaborate client UI.
5. **Registration/datagen changes:** 26.2 separates identifiers such as `BlockIds`, `BlockItemIds`, and `ItemIds`, and older builder patterns such as `valueLookupBuilder` have changed. Copy current 26.2 documentation, not cached snippets.
6. **Networking:** Confirm current typed payload registration and client/server handler names with 26.2 sources. Network only data that vanilla entity synchronization does not already cover.
7. **Dedicated server:** Launch `runServer` immediately after the first custom entity/renderer registration; this catches bad source-set boundaries cheaply.
8. **GameTest DSL/tasks:** Run `tasks --group fabric` and one trivial server GameTest before depending on task names in CI.

The bootstrap phase is complete only when the clean template builds, a client launches, a dedicated server reaches ready state, a trivial server GameTest passes, and `--offline build` succeeds after dependency priming.

## Sources

### Official platform and build sources

- [Minecraft Java Edition 26.2 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2) — version and resource/data pack formats. **Confidence: MEDIUM**.
- [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html) — Java 25, Loom 1.17/Gradle 9.5.1 guidance, mapping and renderer changes. **Confidence: MEDIUM**.
- [Fabric 26.2 example `gradle.properties`](https://github.com/FabricMC/fabric-example-mod/blob/26.2/gradle.properties) — official Minecraft, Loader, Loom alias, and Fabric API pins. **Confidence: MEDIUM**.
- [Fabric 26.2 example `build.gradle`](https://github.com/FabricMC/fabric-example-mod/blob/26.2/build.gradle) — plugin ID, Java release, dependency configurations, and source-set conventions. **Confidence: MEDIUM**.
- [Fabric 26.2 Gradle wrapper properties](https://github.com/FabricMC/fabric-example-mod/blob/26.2/gradle/wrapper/gradle-wrapper.properties) — wrapper 9.5.1. **Confidence: MEDIUM**.
- [Fabric 26.2 example `fabric.mod.json`](https://github.com/FabricMC/fabric-example-mod/blob/26.2/src/main/resources/fabric.mod.json) — official dependency-range shape. **Confidence: MEDIUM**.
- [Fabric Maven: Fabric API 0.158.0+26.2](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.158.0%2B26.2/) — exact published artifact. **Confidence: MEDIUM**.
- [Fabric Maven: Loader index](https://maven.fabricmc.net/net/fabricmc/fabric-loader/) — Loader artifact availability. **Confidence: MEDIUM**.
- [Fabric Maven: Loom plugin index](https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/) — stable Loom 1.17.19 artifact. **Confidence: MEDIUM**.
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/) — Loom usage and generated development environment. **Confidence: MEDIUM**.
- [Fabric mapping migration documentation](https://github.com/FabricMC/fabric-docs/blob/main/develop/porting/mappings/index.md) — unobfuscated 26.1+ mapping model. **Confidence: MEDIUM**.
- [Gradle 9.5.1 release notes](https://docs.gradle.org/9.5.1/release-notes.html) and [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html) — wrapper release and Java 25 runtime support. **Confidence: MEDIUM**.
- [Adoptium installation documentation](https://adoptium.net/installation/) and [Temurin 25.0.4+7 release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4%2B7) — Windows JDK distribution and patch pin. **Confidence: MEDIUM**.

### Official Fabric implementation sources

- [Fabric API Loom DSL](https://docs.fabricmc.net/develop/loom/fabric-api) — datagen/test DSL surface. **Confidence: MEDIUM**.
- [Fabric automatic testing](https://docs.fabricmc.net/develop/automatic-testing) — Loader JUnit, server/client GameTests, and CI guidance. **Confidence: MEDIUM**.
- [Fabric data generation setup](https://docs.fabricmc.net/develop/data-generation/setup) — generated resource/data configuration. **Confidence: MEDIUM**.
- [Fabric custom entity documentation](https://docs.fabricmc.net/develop/entities/first-entity) — current entity registration/model pipeline. **Confidence: MEDIUM**.
- [Fabric rendering concepts](https://docs.fabricmc.net/develop/rendering/basic-concepts) — supported renderer abstractions and render-state direction. **Confidence: MEDIUM**.
- [Fabric custom sound documentation](https://docs.fabricmc.net/develop/sounds/custom) — OGG layout, `sounds.json`, and subtitles. **Confidence: MEDIUM**.
- [Fabric mod metadata reference](https://docs.fabricmc.net/develop/loader/fabric-mod-json) — entrypoints and dependency metadata. **Confidence: MEDIUM**.

### Asset and CI tool sources

- [Blockbench releases](https://github.com/JannisX11/blockbench/releases) — stable 5.1.6. **Confidence: MEDIUM**.
- [Aseprite release notes](https://www.aseprite.org/release-notes/) — optional 1.3.18.2 editor. **Confidence: MEDIUM**.
- [Audacity downloads](https://www.audacityteam.org/download/) — stable 3.7.8 line. **Confidence: MEDIUM**.
- [GitHub `actions/checkout`](https://github.com/actions/checkout) and [`actions/setup-java`](https://github.com/actions/setup-java) — current action majors and Temurin/Gradle cache setup. **Confidence: MEDIUM**.

## Research Gaps

- The fixed Loom `1.17.19` recommendation is source-backed but was not compile-tested in this research-only step because the workspace has Java 21, not Java 25. Resolve this first in implementation.
- Exact 26.2 names/signatures for boss render states, typed payloads, GUI/HUD hooks, registry bootstrap, and GameTest tasks should be taken from generated sources after the clean template builds.
- The content roadmap must decide whether any boss truly needs a bespoke model. The stack assumes vanilla-derived/simple Blockbench rigs and deliberately excludes GeckoLib.
- Client GameTest stability under the eventual CI GPU/display environment remains an implementation-time check; do not make it a release blocker before local visual testing exists.
