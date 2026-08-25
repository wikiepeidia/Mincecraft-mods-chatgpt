# Walking Skeleton — Developer's Hell

**Phase:** 1
**Generated:** 2026-08-25

## Capability Proven End-to-End

> A player or contributor can use the checksum-bound official Eclipse Temurin 25.0.4+7 runtime and a canonically guarded detached clean checkout of the committed Fabric 26.2 wrapper project to produce the exact ignored `dist/developers-hell-0.1.0.jar` with `LICENSE_developers-hell`, load those same bytes in a production client, enter a singleplayer world, and start and cleanly stop a production dedicated server; after official dependencies are primed, the same source builds and that distribution runs without network access.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Game framework | Minecraft Java `26.2` + Fabric Loader `0.19.3` + Fabric API `0.158.0+26.2` | This is the locked stable target and official 26.2 ecosystem. |
| Build runtime | Checksum-verified official Eclipse Temurin Java `25.0.4+7` Windows x64 ZIP, Gradle Wrapper `9.5.1`, Loom `1.17.19` first | Runtime/vendor/arch/home/executable hashes bind every task to one retained ignored JDK; every build reruns a fresh mechanical configured/resolved/artifact-SHA probe and must match the frozen fixed or eligible-fallback evidence. |
| Mapping/output model | Unobfuscated Minecraft names through `net.fabricmc.fabric-loom`; ordinary `jar` output | Minecraft 26.2 does not use Yarn or legacy remap Loom. |
| Public identity | Mod ID `developers_hell`, artifact `developers-hell`, package `dev.developershell`, version `0.1.0` | These names are the cross-phase registry, save, resource, and distribution contract. |
| Runtime shape | One Java-only Fabric JAR with unconditional stable registrations and behavior-only module gates | Toggle changes cannot make existing worlds lose registered identities. |
| Physical-side boundary | Common/server-safe code in `src/main`; client initialization and future rendering in `src/client` | A dedicated server must never link client-only classes. |
| Data layer | Not applicable in Phase 1 | No mutable campaign state is introduced; later world state uses vanilla persistence rather than a database. |
| Authentication | Not applicable | The offline mod has no application account or authentication surface. Launcher authentication is outside the mod. |
| Web routing | Not applicable | This is a native Minecraft mod with Loader entrypoints, not a web application. |
| HTTP API | Not applicable | Fabric API is an in-process mod library; gameplay has no external HTTP/SDK/service integration. |
| Web UI | Not applicable | The only Phase 1 presentation is a bundled Minecraft item resource loaded by the production client. |
| Deployment target | Local Fabric 26.2 client profile, local Fabric 26.2 dedicated server, and ignored `dist/developers-hell-0.1.0.jar` | The distribution is copied from the equal clean online/offline artifact before worktree removal; every runtime hashes these same bytes before and after launch. |
| Directory layout | Official split Fabric source sets plus `src/test`, `src/gametest`, and Windows verification scripts | It preserves side safety and gives later phases stable test seams. |

## Stack Touched in Phase 1

- [ ] Project scaffold — official Fabric 26.2 Groovy Gradle shape, committed Gradle 9.5.1 wrapper, Java 25 toolchain, and frozen dependency tuple.
- [ ] Common registration — `developers_hell:foundation_token` is registered unconditionally from the common initializer.
- [ ] Client resource — the production client loads the localized Foundation Token model and reaches a singleplayer world.
- [ ] Behavior-gate contract — all eight module keys can be enabled or disabled without changing the stable content catalog.
- [ ] Unit and server integration tests — Loader JUnit and Fabric GameTest prove the gate and real registry key, while their classes and test metadata remain excluded from the ordinary production JAR.
- [ ] Dedicated server — the exact distribution reaches ready state without client-only linkage and stops cleanly online and under a unique exactly-two-rule Java/javaw block whose two IDs are removed in `finally` and group membership returns to zero.
- [ ] Offline artifact — exact `LICENSE_developers-hell`, production entries/test exclusions, per-build Loom-resolution/SHA freeze, and three-way online/offline/distribution SHA-256 equality are proven from an exactly registered canonical GUID-temp-child worktree; exact registration cleanup preserves every pre-existing worktree byte-for-byte, and runtime isolation is proved separately with reachable/blocked exact-Java probes.
- [ ] Routing — not applicable; Fabric Loader entrypoints replace application routes.
- [ ] Database — not applicable; Phase 1 has no mutable domain data.
- [ ] Auth — not applicable; no mod account or web session exists.
- [ ] HTTP API — not applicable; no remote service is called at runtime.
- [ ] Web UI — not applicable; native Minecraft resources provide the client-visible proof.

## Walking-Skeleton Task Budget

The executable plan set contains six automated tasks plus one non-bypassable blocking-human observation checkpoint:

1. Checksum-verify the official Eclipse Temurin 25.0.4+7 x64 archive/runtime, then preserve and prove the untouched official Fabric 26.2 scaffold as an atomic prerequisite with fresh per-command Loom resolution/SHA evidence, template provenance, and exact tuple before customization.
2. Deliver the first production tracer through the exact retained JDK and attach the Loom probe to its build: final `developers_hell` common/client entrypoints, player-visible `foundation_token`, and official renamed `LICENSE_developers-hell` archive entry.
3. Define the eight-module behavior-only gate; every freshly executed focused RED and GREEN Loader JUnit invocation independently proves the same frozen Loom artifact before the full build repeats it.
4. Prove the live Foundation Token registry with fail-first/restored GameTest and per-build Loom evidence while excluding tests and requiring the renamed license; add production tasks, comprehensive audits, the committed Loom probe, and the complete safe-worktree/distribution/two-rule/interactive verification harness.
5. Use that unchanged harness for clean-checkout/dist/server gates, then automation starts hidden `-SuperviseInteractiveUat` in a fresh ignored GUID session and launches the visible online client; checkpoint handoff occurs only at machine-proven ONLINE_READY with both processes live.
6. At the sole observation-only checkpoint, interact only with the already-open online Minecraft client, exit normally, wait for automation to launch the isolated client, repeat, exit normally, and report eight in-game PASS/FAIL values—no command/setup/network/file work.
7. Automatically consume the canonical-payload-hashed COMPLETE supervisor receipt plus eight observations, confirm both client exits, supervisor exit, exact rules/group cleanup and distribution hashes, validate, and commit public-safe evidence/README.

## Out of Scope (Deferred to Later Slices)

- Campaign state, bosses, encounters, rewards, persistence, and retry semantics.
- HUD, terminal screens, renderers, keybinds, custom textures, audio, and showcase media.
- Module gameplay behavior, configuration parsing, and Metadata Roulette traits.
- A database, HTTP service, runtime AI, telemetry, remote assets, multiplayer balancing, or another mod loader.
- Automated release publishing and broad CI matrices.

## Subsequent Slice Plan

Each later phase adds a vertical slice without changing this foundation contract:

- Phase 2: Complete the persistent Contract-to-Lecture encounter slice on the stable registration/test seams.
- Phase 3: Extend the encounter framework through Jury, Chairman, Rich ChatGPT transformation, Codex Overdraft, and Diploma.
- Phase 4: Add the six bounded developer-tool sandbox loops behind the module-gate contract.
- Phase 5: Add curated reversible Metadata Roulette and prove all eight module combinations remain save-safe.
- Phase 6: Finish accessibility, generated-asset provenance, offline release evidence, and the GitHub showcase.
