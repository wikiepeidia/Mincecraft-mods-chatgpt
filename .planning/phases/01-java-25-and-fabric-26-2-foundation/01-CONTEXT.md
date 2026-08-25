# Phase 1: Java 25 and Fabric 26.2 Foundation - Context

**Gathered:** 2026-08-25
**Status:** Ready for planning
**Mode:** Smart discuss recommendations auto-accepted under the user's blanket delegation

<domain>
## Phase Boundary

Produce and prove one reproducible, side-safe, offline-installable Minecraft Java 26.2 Fabric foundation. This phase establishes the exact toolchain, project namespace, stable registrations, build/test seams, and production artifact; it does not implement campaign or sandbox gameplay.

</domain>

<decisions>
## Implementation Decisions

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

### Claude's Discretion
- Exact names of the minimal registered smoke-test content and test packages.
- Whether the initial client/server smoke is automated or documented as a bounded manual process when Minecraft's launcher cannot terminate reliably in CI.
- Minor Gradle organization choices that stay identical in behavior to the official 26.2 example.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- No Java, Gradle, Fabric, resource, or test source exists yet; the repository contains only README, license, Git ignore rules, GSD guidance, and planning artifacts.
- `.planning/research/STACK.md` contains the live-researched version tuple, official sources, commands, and compatibility gates.

### Established Patterns
- The project is greenfield and uses a single-JAR, Java-only, offline-first architecture.
- GSD documents and commits are already tracked; implementation commits must preserve unrelated planning history.
- Public-safe fictional defaults and explicit provenance are project-wide constraints from initialization.

### Integration Points
- Root Gradle files and wrapper establish every later phase's build surface.
- `fabric.mod.json` and common/client entrypoints establish side boundaries and namespace stability.
- Minimal registries and test seams become the anchors for campaign entities, module content, GameTests, and production packaging.

</code_context>

<specifics>
## Specific Ideas

- Target the latest stable Minecraft Java 26.2, never the changing 26.3 snapshots.
- The finished artifact must remain usable after the creator's temporary ChatGPT access ends, so both runtime behavior and a primed build need offline proof.
- Phase 1 is intentionally boring but strict: no boss code begins until the exact foundation genuinely launches and builds.

</specifics>

<deferred>
## Deferred Ideas

- Campaign state, bosses, HUD, terminal screens, module behavior, generated textures, audio, and showcase content begin in later phases.
- Broader mod-loader support, automated release publishing, and extensive CI matrices remain outside this foundation sprint.

</deferred>
