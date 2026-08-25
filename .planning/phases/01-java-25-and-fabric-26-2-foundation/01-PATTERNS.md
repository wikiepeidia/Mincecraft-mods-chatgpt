# Phase 1: Java 25 and Fabric 26.2 Foundation - Pattern Map

**Mapped:** 2026-08-25
**Files classified:** 21
**Local implementation analogs found:** 0 / 21
**Authoritative external or existing-target patterns found:** 19 / 21

## Greenfield Finding

The repository has no Java, Gradle, Fabric metadata, resource JSON, or test implementation to copy. The only existing product files are README.md, LICENSE, and .gitignore; README.md and .gitignore are targets to preserve or extend, not implementation precedent.

Accordingly, every entry marked external-template or external-doc below is an authoritative FabricMC 26.2 pattern cited by 01-RESEARCH.md. It is not claimed as a local convention. ModuleGate and its unit test have no upstream Fabric analog and must follow the project invariant in 01-RESEARCH.md:225-234.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| build.gradle | config | batch / build graph | FabricMC fabric-example-mod 26.2 build.gradle plus Fabric testing and production-task docs | external-template exact plus project extension |
| settings.gradle | config | dependency resolution | FabricMC fabric-example-mod 26.2 settings.gradle | external-template exact |
| gradle.properties | config | key-value configuration | FabricMC fabric-example-mod 26.2 gradle.properties | external-template exact with frozen project values |
| gradlew | config / launcher | file-I/O / process launch | FabricMC fabric-example-mod 26.2 wrapper script | external-template exact |
| gradlew.bat | config / launcher | file-I/O / process launch | FabricMC fabric-example-mod 26.2 wrapper script | external-template exact |
| gradle/wrapper/gradle-wrapper.jar | config / launcher | file-I/O / process launch | FabricMC/Gradle generated wrapper binary | external-template exact; never hand-edit |
| gradle/wrapper/gradle-wrapper.properties | config | dependency resolution | FabricMC fabric-example-mod 26.2 wrapper properties | external-template exact |
| .gitignore | config | file-I/O filtering | Existing .gitignore plus FabricMC 26.2 .gitignore | existing target plus external-template merge |
| README.md | documentation | file-I/O / human workflow | Existing README.md plus locked install/build contract | existing target; content rewrite |
| src/main/java/dev/developershell/DevelopersHell.java | provider / entrypoint | event-driven initialization | FabricMC 26.2 ExampleMod.java | external-template exact |
| src/client/java/dev/developershell/client/DevelopersHellClient.java | provider / client entrypoint | event-driven initialization | Fabric docs latest ExampleModClient.java | external-doc role-match |
| src/main/java/dev/developershell/module/ModuleGate.java | service / policy | request-response / transform | None locally or in Fabric; project Registration Before Behavior invariant | no analog |
| src/main/java/dev/developershell/registry/ModItemIds.java | model / ID catalog | transform | Fabric docs latest ModItemIds.java | external-doc exact |
| src/main/java/dev/developershell/registry/ModItems.java | provider / registry | event-driven registration | Fabric docs latest ModItems.java | external-doc exact |
| src/main/resources/fabric.mod.json | config / manifest | Loader discovery | FabricMC fabric-example-mod 26.2 fabric.mod.json | external-template exact |
| src/main/resources/assets/developers_hell/lang/en_us.json | config / localization | key-value transform | Fabric docs latest generated en_us.json | external-doc role-match |
| src/main/resources/assets/developers_hell/items/foundation_token.json | config / client item | resource lookup | Fabric docs latest simple client-item JSON | external-doc role-match |
| src/main/resources/assets/developers_hell/models/item/foundation_token.json | config / item model | resource lookup | Fabric docs latest simple item-model JSON | external-doc role-match |
| src/test/java/dev/developershell/module/ModuleGateTest.java | test | request-response / transform | Fabric Loader JUnit structure; no domain analog | framework match, domain no analog |
| src/gametest/java/dev/developershell/gametest/FoundationGameTests.java | test | event-driven / in-runtime assertion | Fabric docs latest ExampleModGameTest.java | external-doc exact structure |
| src/gametest/resources/fabric.mod.json | config / test manifest | Loader discovery | Fabric docs latest GameTest fabric.mod.json | external-doc exact |

## Pattern Assignments

### build.gradle (config, batch/build graph)

**Primary analog:** FabricMC fabric-example-mod branch 26.2, build.gradle lines 1-60:
https://github.com/FabricMC/fabric-example-mod/blob/26.2/build.gradle

**Project extension sources:** 01-RESEARCH.md:326-352 and 01-RESEARCH.md:462-513; official Fabric automated-testing and production-run-task documentation.

**Core pattern to adapt:**

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

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}

test {
    useJUnitPlatform()
}
~~~

**GameTest pattern:**

~~~groovy
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

**Production-run pattern:** use the Java 25 toolchain and separate run directories. Copy the adapted task declarations from 01-RESEARCH.md:490-513. Do not manually add jar/remapJar to the task: Loom 1.17 selects the project artifact.

**Validation/error pattern:**

- Keep the top-level repositories block empty; Loom supplies the official Minecraft/Fabric repositories.
- Start with Loom 1.17.19. Change only loom_version to 1.17-SNAPSHOT after a captured untouched-template failure.
- Fail the phase on Java/toolchain, dependency resolution, GameTest, client-side linkage, or production-task failure. Do not catch or suppress Gradle failures.
- Include the existing LICENSE in the output JAR using the official template jar task.

---

### settings.gradle, gradle.properties, and Gradle wrapper files (config, dependency resolution)

**Analogs:**

- settings.gradle: https://github.com/FabricMC/fabric-example-mod/blob/26.2/settings.gradle, lines 1-13.
- gradle.properties: https://github.com/FabricMC/fabric-example-mod/blob/26.2/gradle.properties, lines 1-19.
- wrapper properties: https://github.com/FabricMC/fabric-example-mod/blob/26.2/gradle/wrapper/gradle-wrapper.properties, lines 1-9.
- Frozen project values: 01-RESEARCH.md:515-531.

**settings.gradle pattern:**

~~~groovy
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = 'developers-hell'
~~~

**gradle.properties pattern:**

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

**Wrapper pattern:** copy gradlew, gradlew.bat, and gradle-wrapper.jar from the official 26.2 template or generate exactly Gradle 9.5.1, then preserve them as generated files. The wrapper properties must retain:

~~~properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
validateDistributionUrl=true
~~~

Add Gradle's official distributionSha256Sum when verified. Do not place a machine-local JAVA_HOME or JDK path in any committed file.

---

### .gitignore (config, file filtering)

**Existing target:** .gitignore lines 1-24 already ignores Java archives and crash files.

**External analog:** FabricMC fabric-example-mod 26.2 .gitignore lines 1-40:
https://github.com/FabricMC/fabric-example-mod/blob/26.2/.gitignore

**Merge pattern:** preserve useful existing rules and add the official build/run/IDE exclusions:

~~~gitignore
.gradle/
build/
out/
classes/
.idea/
*.iml
.settings/
.vscode/
bin/
run/
hs_err_*.log
replay_*.log
*.hprof
*.jfr
~~~

Do not ignore gradle/wrapper/gradle-wrapper.jar or the intended distributable evidence path merely because the current file has a broad *.jar rule; the executor must explicitly force-add only a release artifact if the plan requires tracking it.

---

### README.md (documentation, human workflow)

**Existing target:** README.md lines 1-2 contains the original token-sprint idea and a misspelled placeholder title.

**Pattern assignment:** retain the joke as project context, but make the file operational. Required sections are:

1. Developer's Hell description and fictional-satire disclaimer.
2. Player prerequisites: Minecraft Java 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.
3. Install one developers-hell-0.1.0.jar.
4. Contributor bootstrap using only gradlew.bat.
5. Online-prime versus offline-runtime/offline-build boundary.
6. Exact build, test, production-client, and production-server commands.
7. Artifact path and a short manual smoke checklist.

Use the command and evidence contracts from 01-RESEARCH.md:252-274 and 01-RESEARCH.md:388-437. Do not claim an unprimed fresh machine can build or launch offline.

---

### src/main/java/dev/developershell/DevelopersHell.java (provider, event-driven initialization)

**Analog:** FabricMC 26.2 ExampleMod.java lines 1-28:
https://github.com/FabricMC/fabric-example-mod/blob/26.2/src/main/java/com/example/ExampleMod.java

**Project-adapted excerpt:** 01-RESEARCH.md:600-617.

~~~java
package dev.developershell;

import dev.developershell.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DevelopersHell implements ModInitializer {
    public static final String MOD_ID = "developers_hell";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.initialize();
        LOGGER.info("Developer's Hell initialized");
    }
}
~~~

**Order/guard pattern:** stable registry initializers run unconditionally before any behavior callback. ModuleGate must never guard ModItems.initialize().

**Error handling:** no blanket try/catch around registration. An invalid duplicate or malformed registry operation must fail startup visibly. Use the Loader-provided SLF4J facade, never System.out.

---

### src/client/java/dev/developershell/client/DevelopersHellClient.java (client provider, event-driven initialization)

**Analog:** Fabric docs latest ExampleModClient.java lines 1-31:
https://github.com/FabricMC/fabric-docs/blob/main/reference/latest/src/client/java/com/example/docs/ExampleModClient.java

**Minimal Phase 1 pattern:** 01-RESEARCH.md:619-627.

~~~java
package dev.developershell.client;

import net.fabricmc.api.ClientModInitializer;

public final class DevelopersHellClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Client-only registrations belong here in later phases.
    }
}
~~~

Keep every net.minecraft.client and rendering import in src/client. An empty initializer is intentional in Phase 1 and proves the metadata/source-set seam without adding deferred UI.

---

### src/main/java/dev/developershell/registry/ModItemIds.java (model/ID catalog, transform)

**Analog:** Fabric docs latest ModItemIds.java lines 1-63, especially lines 57-60:
https://github.com/FabricMC/fabric-docs/blob/main/reference/latest/src/main/java/com/example/docs/item/ModItemIds.java

**Project-adapted excerpt:** 01-RESEARCH.md:559-570.

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
~~~

Keep IDs in a dedicated catalog, construct them from DevelopersHell.MOD_ID, and add only the Phase 1 smoke ID. Do not speculate future boss, effect, payload, or module IDs.

---

### src/main/java/dev/developershell/registry/ModItems.java (registry provider, event-driven registration)

**Analog:** Fabric docs latest ModItems.java lines 218-230:
https://github.com/FabricMC/fabric-docs/blob/main/reference/latest/src/main/java/com/example/docs/item/ModItems.java

**Project-adapted excerpt:** 01-RESEARCH.md:572-597.

~~~java
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
        // Class loading performs stable registration.
    }

    private ModItems() {
    }
}
~~~

Do not pass ModuleGate into this class. Registry identity is stable; later toggles may gate interactions, spawns, callbacks, or schedules only.

---

### src/main/java/dev/developershell/module/ModuleGate.java (policy service, request-response)

**Analog:** none. Fabric has no project-specific eight-module policy abstraction.

**Required project pattern:** Registration Before Behavior in 01-RESEARCH.md:225-234.

The implementation should be a small deterministic, side-safe value/policy object with no Fabric registry, client, filesystem, network, clock, or random dependency. It may answer whether a behavior is enabled; it must not expose a method that conditionally performs registration.

Required invariants:

- Default state is deterministic.
- All-enabled and all-disabled snapshots leave the stable ID catalog unchanged.
- Calls are pure and safe in unit tests.
- No client imports.
- No catch-all fallback that silently enables or disables a module after malformed input; configuration parsing belongs to a later phase.

Planner should avoid inventing a configuration file or all eight module implementations here. Phase 1 needs only the seam and its invariant.

---

### src/test/java/dev/developershell/module/ModuleGateTest.java (test, transform)

**Framework analog:** Fabric automated-testing documentation lines 328-400:
https://docs.fabricmc.net/develop/automatic-testing

**Domain analog:** none; derive assertions from FND-04 and 01-RESEARCH.md:354-363.

**Test pattern:** construct the final minimal gate in all-off and all-on states, assert the expected behavior answer, and assert that ModItemIds.FOUNDATION_TOKEN remains the same declared key. Adapt the test to the final ModuleGate API rather than creating a second API merely to match an example.

If the test touches registry-dependent Minecraft classes, use SharedConstants.tryDetectVersion() and Bootstrap.bootStrap() once in BeforeAll as documented by Fabric. Prefer a pure test that avoids bootstrapping if possible.

---

### src/gametest/java/dev/developershell/gametest/FoundationGameTests.java (test, event-driven in-runtime assertion)

**Analog:** Fabric docs latest ExampleModGameTest.java lines 1-22:
https://github.com/FabricMC/fabric-docs/blob/main/reference/latest/src/gametest/java/com/example/docs/ExampleModGameTest.java

**Project contract:** 01-RESEARCH.md:365-375.

~~~java
public final class FoundationGameTests implements CustomTestMethodInvoker {
    @GameTest
    public void foundationTokenIsRegistered(GameTestHelper context) {
        var key = BuiltInRegistries.ITEM.getKey(ModItems.FOUNDATION_TOKEN);
        if (!DevelopersHell.id("foundation_token").equals(key)) {
            throw new AssertionError("foundation token registry key mismatch: " + key);
        }
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method)
            throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}
~~~

Compile-check Identifier and registry lookup names against generated 26.2 sources; the research explicitly marks getKey as an assumption. Preserve the CustomTestMethodInvoker plus GameTestHelper lifecycle from the official analog.

---

### src/gametest/resources/fabric.mod.json (test manifest, Loader discovery)

**Analog:** Fabric docs latest GameTest metadata lines 1-15:
https://github.com/FabricMC/fabric-docs/blob/main/reference/latest/src/gametest/resources/fabric.mod.json

~~~json
{
  "schemaVersion": 1,
  "id": "developers_hell_test",
  "version": "0.1.0",
  "name": "Developer's Hell Foundation Tests",
  "environment": "*",
  "entrypoints": {
    "fabric-gametest": [
      "dev.developershell.gametest.FoundationGameTests"
    ]
  }
}
~~~

Do not add a client GameTest entrypoint in Phase 1 because enableClientGameTests is false.

---

### src/main/resources/fabric.mod.json (manifest, Loader discovery)

**Analog:** FabricMC fabric-example-mod 26.2 fabric.mod.json lines 1-38:
https://github.com/FabricMC/fabric-example-mod/blob/26.2/src/main/resources/fabric.mod.json

**Project-adapted excerpt:** 01-RESEARCH.md:533-557.

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

Omit template mixin entries because Phase 1 adds no mixins. Do not point at a missing icon. Keep public metadata fictional and do not claim real sponsorship.

---

### en_us.json and foundation-token item JSON (resource configs, lookup)

**Analogs:**

- Translation keys: https://github.com/FabricMC/fabric-docs/blob/main/reference/latest/src/main/generated/assets/example-mod/lang/en_us.json
- Current two-file client-item/model structure: https://docs.fabricmc.net/develop/data-generation/item-models
- Project resource recommendation: 01-RESEARCH.md:559-598.

**src/main/resources/assets/developers_hell/lang/en_us.json:**

~~~json
{
  "item.developers_hell.foundation_token": "Foundation Token"
}
~~~

**src/main/resources/assets/developers_hell/items/foundation_token.json:**

~~~json
{
  "model": {
    "type": "minecraft:model",
    "model": "developers_hell:item/foundation_token"
  }
}
~~~

**src/main/resources/assets/developers_hell/models/item/foundation_token.json:**

~~~json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/paper"
  }
}
~~~

This deliberately reuses the vanilla paper texture for the foundation smoke item. It introduces no generated or third-party texture in Phase 1 and still exercises the current 26.2 client-item indirection.

## Shared Patterns

### Side Safety

**Source:** official splitEnvironmentSourceSets pattern and 01-RESEARCH.md:236-243.

**Apply to:** build.gradle, DevelopersHell.java, DevelopersHellClient.java, ModItemIds.java, ModItems.java, ModuleGate.java.

- Common code has no net.minecraft.client or com.mojang.blaze3d import anywhere, including fields, annotations, signatures, generics, and static initializers.
- Client code may depend on main; main never depends on client.
- The production dedicated-server launch is the decisive linkage check.

### Registration Before Behavior

**Source:** 01-RESEARCH.md:225-234.

**Apply to:** DevelopersHell.java, every registry class, ModuleGate.java, and ModuleGateTest.java.

~~~text
common initializer
  -> stable registrations always execute
  -> behavior callbacks may consult ModuleGate
~~~

No toggle may wrap Registry.register, payload type registration, component registration, or any stable command/content identity.

### Logging and Failure Handling

**Source:** FabricMC ExampleMod.java lines 9-22 and locked Phase 1 failure policy.

- Logger name equals developers_hell.
- Initialization logs concise version/state evidence without private paths or secrets.
- Do not swallow initialization, registration, or side-loading errors.
- Build/evidence checks fail on wrong Java major, missing JAR entries, hash mismatch, example residue, or client imports in main.

### Validation Layers

**Source:** Fabric automated-testing docs and 01-RESEARCH.md:326-437.

1. Pure ModuleGate unit test via Fabric Loader JUnit.
2. In-runtime server GameTest for the actual item registry key.
3. Static client-import audit.
4. Production client world entry and clean exit.
5. Production server ready state and clean stop.
6. Exact JAR entry inspection.
7. Online then offline clean-build SHA-256 equality after cache prime.

### Security and Supply Chain

- Only official Fabric/Gradle repositories and the pinned tuple are allowed.
- No runtime HTTP, telemetry, API key, remote config, or asset download.
- No machine-local JDK path or private employer/personal value is committed.
- Preserve LICENSE and include it in the JAR.

## No Analog Found

| File | Role | Data Flow | Reason / Planner Direction |
|---|---|---|---|
| src/main/java/dev/developershell/module/ModuleGate.java | service / policy | request-response / transform | Project-specific invariant. Implement the smallest pure behavior gate described in 01-RESEARCH.md:225-234. |
| src/test/java/dev/developershell/module/ModuleGateTest.java | test | request-response / transform | Fabric supplies the JUnit framework only; assertions must prove the project-specific stable-catalog invariant. |

## Metadata

**Local analog search scope:** repository root excluding .git, .gradle, and build output.

**Local files scanned:** README.md, LICENSE, AGENTS.md, .gitignore, and planning artifacts.

**Implementation scan result:** no .java, .gradle, gradle.properties, project fabric.mod.json, or implementation resource JSON existed at mapping time.

**Authoritative analog set:** FabricMC fabric-example-mod branch 26.2, Fabric documentation/reference latest for Minecraft 26.2, and the code examples already recorded in 01-RESEARCH.md.

**Pattern extraction date:** 2026-08-25.
