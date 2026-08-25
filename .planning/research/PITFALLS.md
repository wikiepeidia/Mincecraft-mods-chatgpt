# Domain Pitfalls

**Domain:** Minecraft Java 26.2 Fabric comedy boss-campaign anthology mod
**Project:** Developer's Hell
**Researched:** 2026-08-25
**Overall confidence:** MEDIUM — current implementation claims are cross-checked against official Fabric 26.2 documentation, the official 26.2 example mod, Gradle documentation, and first-party policy pages; encounter-design guidance is a synthesis of developer talks and Minecraft design interviews.

## Release Gate Legend

| Classification | Meaning for this 1–2 day sprint |
|---|---|
| **MUST-FIX** | Do not publish or hand off the JAR while present. These failures can crash, corrupt a world, softlock the campaign, make installation impossible, or create a public licensing/endorsement problem. |
| **MVP-SAFE SHORTCUT** | Deliberately reduced polish or breadth that preserves a complete, stable, offline singleplayer campaign. Record it in the README instead of spending the release window hiding it. |

The governing rule is simple: protect the start-to-diploma campaign, world integrity, and installability first. A smaller joke implemented with vanilla effects is better than an unfinished custom renderer, custom dimension, or ninth subsystem.

## Critical Pitfalls

### Pitfall 1: Scaffolding From Pre-26.2 Examples or Mixing Toolchain Generations

**Release status:** MUST-FIX

**What goes wrong:** The project fails during dependency resolution or compilation, compiles against the wrong names, or launches with registration/resource errors. A particularly easy mistake is copying a pre-26.1 Loom plugin or mappings setup into the now non-obfuscated 26.2 toolchain.

**Why it happens:** Minecraft 26.2 is current and many search results still target 1.21.x. Fabric's 26.2 guidance calls for the non-remapping `net.fabricmc.fabric-loom` plugin, Loom 1.17, Gradle 9.5.1 at the guide's publication, Java 25, and current 26.2 Loader/API pins. The 26.2 game also separates block and item ID keys for registration/datagen and removed `valueLookupBuilder`; GUI/HUD access moved, and raw OpenGL usage is no longer a safe foundation.

**Warning signs:**

- Gradle cannot resolve Loom, Loader, Minecraft, or Fabric API together.
- Java reports an unsupported class version, or Gradle runs under a different JDK than compilation.
- Source uses Yarn/intermediary-era names or the legacy `fabric-loom` plugin while targeting 26.2.
- Copied tutorials reference removed registration/datagen helpers or old `Minecraft#setScreen` access.
- A dev client works only after IDE-specific cache changes, but `gradlew.bat build` does not.

**Consequences:** The sprint can be consumed by random version changes; CI and another user's machine will not reproduce the build; generated resources may be wrong even after code compiles.

**Prevention:**

- Scaffold directly from Fabric's 26.2 template/example branch and commit the Gradle wrapper.
- Recheck exact Minecraft, Loader, Loom, and Fabric API pins on Fabric Develop immediately before scaffolding; record the known-good tuple in `gradle.properties` and the README.
- Require Java 25 explicitly and print `java -version` plus `gradlew.bat --version` in the build troubleshooting notes.
- Use the 26.2 plugin ID and 26.2 registration/datagen patterns; avoid Mixins and raw rendering calls unless no public API can do the job.
- Freeze the known-good dependency tuple once the first production client launches. Do not chase newer snapshots during the sprint.

**Verification:** On a clean checkout, run `gradlew.bat --no-daemon clean build`; verify Java 25, successful dependency resolution, and a non-development JAR in `build/libs`. Run both development and production launch tasks before content work expands.

**Recovery:** Compare the three build files against the official 26.2 example branch, restore a coherent set of pins, and use `--refresh-dependencies` only after checking the configuration. If an old tutorial drove implementation, port one subsystem at a time instead of blindly renaming symbols.

**Phase mapping:** Phase 1 — toolchain/scaffold. Recheck again in the final release-hardening phase.

### Pitfall 2: Loading Client Classes on the Dedicated Server

**Release status:** MUST-FIX even though gameplay is singleplayer-first

**What goes wrong:** The mod works in the integrated client but a server launch crashes with missing client/render classes. Static initializers are enough to trigger the failure even when the client-only method is never called.

**Why it happens:** Singleplayer contains both a physical client and a logical server, so it cannot prove that common code is physically server-safe. Renderers, screens, HUD code, key bindings, client networking receivers, model layers, and particle providers are client responsibilities.

**Warning signs:**

- `src/main` imports `net.minecraft.client`, renderer, screen, key-mapping, or client-only Fabric packages.
- A common registry/helper class has a static field whose type is client-only.
- Renderers or client packet handlers register from the common `ModInitializer`.
- `runClient` succeeds but `runServer` or a production server stops during mod discovery.

**Consequences:** The public one-JAR distribution is structurally broken and server-safe logic cannot be trusted, including inside future LAN use.

**Prevention:**

- Enable Loom's `splitEnvironmentSourceSets()` immediately.
- Keep gameplay, registries, commands, persistence, and payload type definitions in `src/main`; keep rendering, screens, key mappings, particle factories, and client receivers in `src/client`.
- Declare separate `main` and `client` entrypoints in `fabric.mod.json`; environment remains `*` for a content mod used on both sides.
- Pass primitive or common render-state data across the boundary; common classes must not mention client types in signatures.

**Verification:** Make common compilation unable to see client source, then boot a production dedicated server with the remapped release JAR and Fabric API. Treat a clean startup to world-ready state as a release gate.

**Recovery:** Move the entire client reference chain—not just the final call—into the client source set. If common code needs the result, expose a common interface or synchronized value and register the client implementation from `ClientModInitializer`.

**Phase mapping:** Phase 1 — source-set boundaries; Phase 2 — boss rendering integration; Phase 6 — dedicated-server smoke test.

### Pitfall 3: Incomplete Entity Registration, Spawn Lifecycle, or Cleanup

**Release status:** MUST-FIX

**What goes wrong:** A boss cannot be summoned, spawns without attributes, duplicates after reload, despawns mid-fight, loses its phase, leaves an immortal boss bar, or litters permanent helper entities/projectiles around the world.

**Why it happens:** A custom boss crosses several seams: stable registry ID, entity type, dimensions/category, default attributes, server spawning, client renderer/model layer, synchronized visual state, persistent fields, boss-bar viewers, chunk unload/reload, death, player death, and encounter abort. Implementing only the visible entity class covers a fraction of the lifecycle.

**Warning signs:**

- `/summon` fails, or spawn reports missing default attributes.
- A boss created with a raw constructor is never added through the level's supported spawn path.
- Reloading a save resets health/phase or creates a second boss from the campaign controller.
- Leaving the arena or changing dimension leaves boss bars, music, invulnerable adds, or ticking controllers.
- Temporary entities have no owner encounter ID or bounded lifetime.

**Consequences:** World clutter, unavoidable damage, campaign softlocks, duplicate rewards, and saves that cannot cleanly resume.

**Prevention:**

- Centralize stable IDs and register entity types and attributes during common initialization; register renderers/model layers only on the client.
- Spawn on the logical server through `EntityType` creation/spawn APIs and check the returned entity before advancing campaign state.
- Give every encounter a persistent encounter UUID; tag helpers/projectiles with that owner and a maximum lifetime.
- Persist boss phase and any timer needed to resume safely. Mark boss entities persistent for an active scripted encounter and define explicit death, unload, player-death, timeout, and abort cleanup.
- Make boss-bar viewer add/remove logic idempotent and distance/dimension aware.
- Campaign startup must search for the recorded live boss before creating another one.

**Verification:** For each encounter: summon, fight across one phase, walk out of tracking range, return, unload/reload the chunk, save/quit/reopen, die, defeat the boss, and abort with a debug command. Assert one boss, one controller, no orphan helpers, and no lingering boss bar after every path.

**Recovery:** On load, reconcile campaign state against the encounter UUID. If the boss is missing, reset to a safe restart point; if duplicates exist, retain the recorded entity and clean only entities tagged to the same encounter. Never delete arbitrary nearby mobs.

**Phase mapping:** Phase 2 — vertical boss framework; Phase 3 — campaign progression; Phase 6 — save/reload and cleanup matrix.

### Pitfall 4: Client-Authoritative Boss Logic and Double-Ticked Encounters

**Release status:** MUST-FIX

**What goes wrong:** Attacks happen twice, damage disagrees with visuals, phases skip or repeat, rewards duplicate, and relogging changes the outcome. Cosmetic packets may also be broadcast to every player or serverbound packets may trust arbitrary entity IDs.

**Why it happens:** There is always a logical server, including in singleplayer. Code that runs on both sides without a side guard produces two state machines. Conversely, unsynchronized client-only timers drift from server damage timing.

**Warning signs:**

- Phase/cooldown counters advance from render ticks or client tick events.
- The client spawns damaging projectiles, grants rewards, edits campaign progress, or chooses the next random attack.
- A serverbound payload directly applies an effect without checking player, distance, entity type, encounter, and current phase.
- Two attack sounds or projectile waves occur for one telegraph.
- Reconnecting causes boss health, phase, or token meter to jump.

**Consequences:** Unfair fights in singleplayer now, exploitable behavior later, and bugs that are extremely hard to reproduce.

**Prevention:**

- Run the finite-state machine, random selection, damage, spawning, progress, and reward grant only on the logical server.
- Synchronize only the compact state needed to render: phase ID, attack ID, telegraph start/duration, and a few visual flags. Keep server-only cooldown internals off the wire.
- Register payload codecs on the required physical sides; put client receivers in the client source set.
- Send effects only to players tracking the entity/arena. Prefer vanilla server-broadcast sound/particle APIs where they already solve tracking.
- Validate every serverbound payload; debug controls should require operator permission or be absent from release gameplay.

**Verification:** Add logs keyed by encounter UUID, side, tick, phase, and attack sequence. One telegraph must correspond to one server attack and at most one cosmetic receive per intended client. Repeat after save/reload and with a second local test client if time permits.

**Recovery:** Make transitions idempotent using monotonically increasing attack/phase sequence numbers. On mismatch, the server snapshot wins; the client discards old cosmetic packets rather than replaying gameplay.

**Phase mapping:** Phase 2 — encounter state machine/network contract; Phase 3 — campaign rewards; Phase 6 — integration tests.

### Pitfall 5: Campaign State That Resets, Corrupts, or Cannot Migrate

**Release status:** MUST-FIX

**What goes wrong:** Signing the internship contract is forgotten after restart, a completed boss returns, the diploma can be claimed repeatedly, or one malformed/older save field prevents the world from loading.

**Why it happens:** Static Java fields appear to work for one session but are not world data. Minecraft persistence also requires explicit codecs/save methods and dirty marking. A strict codec that makes every new field mandatory rejects older data.

**Warning signs:**

- Campaign state is held in a singleton without a level/server lifecycle.
- Mutators do not call `setDirty()` or replace a persistent attachment through its API.
- Entity save/load omits phase, encounter ID, or bounded resume information.
- Codec fields have no defaults and no stored schema version.
- A reward is granted before the completion record is durably updated, or vice versa, with no idempotency key.

**Consequences:** Lost progression, duplicated rewards, unrecoverable active encounters, or a world that fails to open after a mod update.

**Prevention:**

- Use server/level `SavedData` for campaign-wide progress; use entity persistence for entity-local state and Data Components for item-local state.
- Store a small explicit schema version. Use optional codec fields with safe defaults for additive changes and a manual upgrader for any incompatible representation.
- Mark data dirty only after validated state transitions, and make the reward transition idempotent (`completedEncounterIds` or equivalent).
- Define recovery states: `NOT_STARTED`, `READY`, `ACTIVE`, `COMPLETED`, plus encounter UUID. Avoid persisting every animation tick when a safe phase restart is enough.
- Back up the test world before schema changes and do not rename registry IDs after a public save has used them.

**Verification:** Save/quit/reopen at contract signed, each boss phase boundary, player death, boss death before reward pickup, and campaign completion. Also load a fixture missing every newly optional field and a fixture with an unknown future field.

**Recovery:** A decode failure should log the exact field and fall back only to a clearly safe campaign checkpoint, never silently mark the campaign complete. Keep the prior `.dat`/world backup. Reconcile `ACTIVE` state with the recorded entity as described above.

**Phase mapping:** Phase 2 — persistence contract before the first boss; Phase 3 — chapter/reward state; every later phase adds migration fixtures when it changes saved shape.

### Pitfall 6: Treating “Metadata Roulette” as Arbitrary Entity Metadata Transplantation

**Release status:** MUST-FIX; arbitrary raw interchange remains explicitly out of scope

**What goes wrong:** A pig receives an Enderman-only tracked accessor/serializer, incompatible AI internals are copied between classes, values exceed safe attribute ranges, or a shuffled mob becomes permanently broken across saves.

**Why it happens:** “Metadata” sounds like a generic bag, but synchronized entity fields are declared for a class hierarchy with specific serializers and client expectations. AI goals also close over entity capabilities. Fabric's entity guidance demonstrates class-specific synchronized accessors rather than interchangeable records. Therefore crash/desync risk from raw swapping is a direct architectural inference, not a supported Fabric feature.

**Warning signs:**

- Reflection or Mixins enumerate private tracked-data slots and write values by numeric index.
- Code passes an accessor declared for one unrelated entity class into another entity's data tracker.
- Goal instances are moved between live mobs rather than selected from tested factories.
- Shuffle values allow NaN, negative scale/health, extreme speed/range, or unbounded effect duration.
- Disabling the module cannot restore or safely leave already shuffled mobs.

**Consequences:** Immediate network decode crashes, corrupted persistent entities, impossible combat, runaway pathfinding, or worlds that crash whenever the affected chunk loads.

**Prevention:**

- Implement a curated `TraitProfile`/adapter registry, not raw metadata access. Each trait declares compatible entity predicates, bounded values, apply behavior, and persistence policy.
- Limit v1 traits to supported surfaces: vanilla attributes with clamps, aggression presets built from tested goal factories, cosmetic scale where supported, sound selection, bounded status effects, and loot-table choice.
- Never mutate tracked-data schemas or serializers. Never copy a live goal selector.
- Use a deterministic seed and log entity type plus chosen traits. Skip incompatible combinations without retry storms.
- Decide one safe disable policy: existing traits remain until entity unload/death, or store an original bounded snapshot and restore through the same supported adapter. Document it.

**Verification:** Generate every allowed trait/entity pair in a GameTest or debug arena, then save/reload and observe for several minutes. Reject any pair that logs serialization, attribute, navigation, or renderer errors. Fuzz only within declared bounds—not arbitrary fields.

**Recovery:** Provide an operator cleanup command that removes Developer's Hell trait attachments/modifiers by namespaced IDs. On adapter decode/apply failure, remove only the mod's modifier/attachment and leave the vanilla entity intact.

**Phase mapping:** Phase 4 — optional modules, after the campaign framework is stable; Phase 6 — compatibility matrix and cleanup test.

### Pitfall 7: Config Toggles That Change Registries or Strand Live Content

**Release status:** MUST-FIX for malformed-config safety and stable registries; restart-required toggles are an MVP-SAFE SHORTCUT

**What goes wrong:** Disabling a module removes registered types that an existing world references, client and server registry sets differ, event handlers continue firing despite “off,” or changing a toggle mid-boss leaves an invulnerable entity and no controller.

**Why it happens:** An anthology encourages conditional initialization, but static registries are bootstrap-time contracts. Runtime event registration is also commonly one-way. A local config file is not automatically authoritative or synchronized.

**Warning signs:**

- `if (config.enabled)` wraps item/entity/data-component/payload type registration.
- The same handler is registered again on config reload.
- Recipes/items disappear from registry rather than becoming inert or unavailable.
- A malformed number, missing field, or old config version aborts startup.
- The UI says a module is off while a global callback still runs its tick scan.

**Consequences:** Missing registry entries, unloadable saves, duplicate callbacks, desync, and softlocked encounters.

**Prevention:**

- Always register the complete stable content and payload set on both required sides. Toggles gate behavior, scheduling, discoverability/recipes where safe, and new encounter starts—not type existence.
- Load into an immutable, validated config snapshot with schema version, defaults, range clamps, unknown-field tolerance, and a clear error log.
- Register one central dispatcher once; every module callback checks the current snapshot and active world/encounter state.
- Make v1 changes apply after restart. Refuse to disable the active campaign module mid-encounter, or first run its explicit abort/cleanup path.
- Let disabled module items/entities remain loadable and inert enough to preserve a world.

**Verification:** Test all-off, all-on, each single module, malformed JSON, missing file, old config, unknown fields, and a restart after toggling with module items already in a chest. Confirm handler counts do not increase.

**Recovery:** Preserve/rename the malformed file, regenerate defaults, and continue with a prominent warning. If a module was disabled while active, reconcile through its namespaced encounter/trait IDs rather than deleting general game state.

**Phase mapping:** Phase 1 — config schema and module dispatcher; Phase 4 — each module's on/off contract; Phase 6 — configuration matrix.

### Pitfall 8: Spectacular Bosses That Are Unreadable, Unfair, or Softlock

**Release status:** MUST-FIX for softlocks and untelegraphed lethal attacks; simplified animation/models are MVP-SAFE SHORTCUTS

**What goes wrong:** Particles obscure the hazard, dialogue covers combat, attacks have no learnable tell, the boss chains damage without recovery, phase thresholds are skipped by burst damage, or the fight becomes permanently invulnerable after losing its target.

**Why it happens:** Comedy encourages visual overload, while four “unique” bosses encourage four bespoke fragile AI systems. Under a two-day sprint, timing and recovery paths receive less attention than spectacle.

**Warning signs:**

- Damage begins on the same tick as the first cue.
- Two attacks share identical color/sound/pose but require different responses.
- The state machine waits forever for an animation callback, target, minion death, dialogue completion, or exact health equality.
- Adds/projectiles have no cap; arena hazards overlap until no safe tile remains.
- Boss invulnerability has no maximum duration or escape transition.
- The player must read chat while dodging, or cannot understand an attack with particles reduced.

**Consequences:** Death feels random rather than funny; encounters become endurance chores; the campaign cannot finish.

**Prevention:**

- Build one reusable server-side maneuver system: `TELEGRAPH → ATTACK → RECOVERY`, each with a hard maximum duration and cleanup hook. Theme bosses through parameters, cues, dialogue, and combinations.
- Give every dangerous move a distinct shape/color plus a distinct audio cue; do not rely on one sensory channel. Start lethal telegraphs generously, then shorten only after playtesting.
- Bound simultaneous hazards, adds, projectiles, particles, and arena-denial area. Guarantee a recovery/damage window after major attacks.
- Use inequality thresholds and explicit one-way phase transitions; clamp or queue transitions when one hit crosses several thresholds.
- Define loss-of-target, player death, dimension change, chunk unload, timeout, and admin-abort behavior. Never wait indefinitely for cosmetic animation completion.
- Queue concise subtitle/action-bar dialogue outside reaction-critical windows; long jokes go before/after phases.

**Verification:** Play each boss with ordinary survival equipment, reduced particles, sound on and sound off, then intentionally leave the arena, die, hide, burst across a phase threshold, and kill adds in unexpected order. Record attack timeline logs and verify every state exits within its bound.

**Recovery:** A watchdog resets only the current maneuver to a neutral recovery state, cleans encounter-owned hazards, and reacquires or resets the fight. A debug command can inspect phase/timer and safely abort/restart without editing NBT manually.

**Phase mapping:** Phase 2 — one complete vertical boss and maneuver library; Phase 3 — reskin/compose the four encounters; Phase 5 — audiovisual readability pass; Phase 6 — adversarial playtest.

### Pitfall 9: Missing or Mispackaged Data, Models, Textures, Sounds, and Translations

**Release status:** MUST-FIX when content is invisible, unloadable, or progression-blocking; vanilla/procedural placeholders are MVP-SAFE SHORTCUTS

**What goes wrong:** Purple-black items, silent cues, untranslated keys, missing loot/reward recipes, failed resource reloads, or resources that work from the IDE but are absent from the release JAR.

**Why it happens:** Fabric/Minecraft assets are namespace- and path-sensitive. In 26.2, an item appearance spans texture, model, client-item JSON, and translation; custom particles and sounds add more matching files. Datagen output also does nothing if its directory is not included in resources.

**Warning signs:**

- Startup or `F3+T` logs missing models, textures, sounds, tags, or registry keys.
- A file uses uppercase/spaces or a namespace that differs from the registered ID.
- Datagen succeeds but Git shows no generated files, or `processResources` excludes the output directory.
- The built JAR lacks `fabric.mod.json`, `assets/developers_hell`, or `data/developers_hell`.
- Boss attacks depend on a custom sound/particle that silently fails, removing their telegraph.

**Consequences:** Broken presentation, unfair attacks, unobtainable progression items, and a JAR different from the tested dev environment.

**Prevention:**

- Establish one lowercase mod ID and central ID constants before content creation.
- Prefer datagen for recipes, loot, tags, advancements, models, and translations; run it deterministically and include its output source set.
- Maintain a small asset checklist per registered item/entity/particle/sound.
- Use vanilla sounds/particles as a fallback cue until each custom asset is verified.
- Inspect the final JAR contents, not just `src/main/resources`.

**Verification:** Run datagen twice and expect no diff; run resource reload and search the log for the mod ID plus missing/failed resource warnings; exercise every item/entity in a clean production client; list the release JAR entries and check required roots.

**Recovery:** Revert the affected cue to a known vanilla asset so gameplay remains readable, then repair paths/JSON separately. Never let a missing cosmetic resource block a server-side phase transition.

**Phase mapping:** Phase 1 — namespace/datagen wiring; Phases 3–5 — per-content asset checklist; Phase 6 — clean-client and JAR inspection.

### Pitfall 10: Per-Tick Scans, Particle Storms, and Orphan Entities Collapse TPS/FPS

**Release status:** MUST-FIX for sustained tick lag, unbounded growth, or save bloat; lower visual density is an MVP-SAFE SHORTCUT

**What goes wrong:** All eight modules scan every loaded entity every tick, boss projectiles/adds accumulate, token/code particles flood clients, or disabled encounters continue ticking. The integrated server falls below its 20 TPS target while the renderer also stutters.

**Why it happens:** Each feature seems cheap alone, but anthology modules multiply global callbacks. Comedy effects often allocate entities/particles faster than they clean them up, and broadcasting packets to every player amplifies the cost.

**Warning signs:**

- Global `getAllEntities`-style iteration inside a tick callback.
- New collections, pathfinders, regex parsing, config reads, or JSON parsing every tick.
- Active helper/projectile/particle count grows throughout a five-minute fight.
- Effects are sent to all players instead of tracking players.
- Leaving the arena does not return tick time/entity counts near baseline.

**Consequences:** Input lag, skipped telegraphs, watchdog crashes, huge saves, and a final boss that is technically present but unplayable.

**Prevention:**

- Tick only active encounters; schedule background module checks at coarse intervals and query a bounded area/chunk set.
- Cache parsed immutable config/data and precompute attack tables.
- Set hard per-encounter caps and lifetimes for adds, projectiles, display entities, particles, sounds, and queued dialogue.
- Use client particles for purely cosmetic local density; use tracking-aware server broadcast when all nearby clients must agree.
- Tag every temporary entity with encounter ownership and clean it on every exit path.
- Provide low/normal visual-intensity config while preserving telegraph shapes.

**Verification:** Run the final boss and all modules enabled for at least ten minutes, recording server tick time, FPS, entity counts, packet/log rate, and save size before/after. Abort the fight and confirm counts/tick time return near baseline. Use Minecraft tick diagnostics or a profiler if the budget is missed.

**Recovery:** Stop the module/encounter scheduler first, invoke namespaced cleanup for owned temporaries, save a backup, and lower caps/interval frequency. Optimize only measured hot paths; do not remove gameplay state to conceal leaks.

**Phase mapping:** Phase 2 — encounter ownership/budgets; Phase 4 — module scheduling; Phase 5 — visual intensity; Phase 6 — soak/profile gate.

### Pitfall 11: Generated Assets or Brand Jokes Create a Public-Release Rights Problem

**Release status:** MUST-FIX before public GitHub/release distribution; private local parody can be less conservative

**What goes wrong:** Generated art imitates a copyrighted community texture or official logo, asset provenance is unknowable, code and assets have ambiguous licenses, or “sponsored by ChatGPT/OpenAI” reads as a real endorsement. Real employer/lecturer identities can also leak into committed defaults.

**Why it happens:** OpenAI's terms assign output to the user as between the parties where law permits, but also say output may be non-unique and the user remains responsible for inputs and use. OpenAI's brand guidance prohibits implied endorsement/sponsorship and restricts logos/model-name branding. Minecraft's usage rules require an unofficial-product disclaimer and distribution of the mod rather than a modded copy of the game.

**Warning signs:**

- Prompts request “exactly like” a named artist, mod pack, game texture, university logo, company logo, or official ChatGPT/OpenAI logo.
- The README, title screen, splash, or boss text says OpenAI/ChatGPT sponsored or officially created the mod.
- Generated PNG/OGG files have no record of generator, date, source inputs, or human edits.
- The repository LICENSE covers code but says nothing about original assets and third-party notices.
- Public defaults include real names, employer claims, unverified dollar/token prices, or a corporate logo.

**Consequences:** Takedown/complaint risk, inability for others to reuse the repository safely, accidental doxxing, and confusion about endorsement.

**Prevention:**

- Keep public defaults fictional. Put creator/company/university customizations in a local ignored config; never commit personal or employer data by inference.
- Phrase credits accurately (“created with assistance from …”) and explicitly state no OpenAI/ChatGPT sponsorship or endorsement. Use an original radiant AI character design rather than OpenAI/ChatGPT logos or copied interface artwork. If the exact branded boss name remains public, conduct a specific brand review before release.
- Generate from text-only original prompts or inputs the project has rights to. Visually inspect every output for signatures, logos, near-copies, illegible pseudo-text, and inappropriate content; pixel-edit before shipping.
- Maintain `ASSET_PROVENANCE.md` with path, tool/model, date, prompt summary, source-input rights, edits, file hash, and license decision.
- State code license, asset license, third-party notices, Minecraft unofficial-product disclaimer, and dependency credits separately.

**Verification:** Every shipped non-code file has a provenance row or an explicit first-party procedural origin. Search source/resources/README for real names, “sponsored by,” official logos, and unverified price claims. Review the release listing against current Minecraft and OpenAI brand guidance.

**Recovery:** Replace questionable media with a procedural/vanilla-compatible placeholder and rewrite public copy to fictional parody plus disclaimer. Preserve the private customization mechanism without committing the private values.

**Phase mapping:** Phase 1 — licensing/provenance template and privacy boundary; Phase 5 — asset generation/review; Phase 6 — public-release audit.

### Pitfall 12: Shipping the Wrong JAR or an Artifact That Was Never Tested Offline

**Release status:** MUST-FIX

**What goes wrong:** The user receives a dev or sources JAR, Fabric API is omitted from installation instructions, `fabric.mod.json` accepts the wrong game/Java versions, resources are missing, or the mod tries to contact a service after the ChatGPT-credit window ends.

**Why it happens:** IDE runs use a development classpath and resources that differ from a remapped production artifact. Loom produces several similarly named JARs. A successful compile is not an installation test.

**Warning signs:**

- The handed-off filename contains `dev` or `sources`, or no checksum/version is recorded.
- Installation succeeds only from an IDE run directory.
- A clean profile reports missing Fabric API, wrong Loader/Minecraft, or invalid mod metadata.
- Runtime contains HTTP clients, API keys, environment-secret reads, analytics, update checks, or remote dialogue/assets.
- README build commands omit Windows syntax/JDK requirement or cannot reproduce `build/libs`.

**Consequences:** The entire goal fails after credits expire: source exists but there is no usable offline mod.

**Prevention:**

- Make runtime content entirely local and data-driven; no API key, remote fetch, login, or network fallback.
- Pin correct `fabric.mod.json` dependencies for Minecraft 26.2, Java 25, Loader, and Fabric API.
- Build with the wrapper and select the shortest-named non-dev JAR from `build/libs`, as Fabric's build guide specifies.
- Add CI using Java 25 and the wrapper. Archive failure reports and the tested release artifact/checksum.
- Write a clean-room README: install Fabric Loader 26.2, install matching Fabric API, copy the release JAR, configure optional local satire values, launch.

**Verification:** Copy only documented files into a fresh launcher profile with networking disabled after dependencies are already installed. Start a new world, sign the contract, summon/test content, save/reload, and confirm no network errors. Repeat from a clean checkout build.

**Recovery:** Rebuild from the tagged commit, retest the remapped artifact, and replace the release attachment rather than renaming a dev JAR. Include the exact tested SHA-256 and dependency tuple.

**Phase mapping:** Phase 1 — reproducible build/CI skeleton; Phase 6 — production artifact, clean-profile offline acceptance, GitHub release.

### Pitfall 13: Breadth Consumes the Sprint Before a Playable Campaign Exists

**Release status:** MUST-FIX if the start-to-diploma loop is incomplete; reduced bespoke breadth is an MVP-SAFE SHORTCUT

**What goes wrong:** Eight module prototypes, four entity models, and hundreds of jokes exist, but the contract cannot start a stable campaign or the final boss/diploma cannot be reached.

**Why it happens:** The requested anthology contains several mods' worth of ideas. Parallel work can produce disconnected content faster than it produces shared lifecycle, persistence, and verification.

**Warning signs:**

- More than one custom subsystem is underway before one boss can be started, fought, saved, resumed, defeated, and rewarded.
- Each boss has a unique base class/state machine instead of composing shared maneuvers.
- Modules require custom GUIs/renderers/worldgen just to demonstrate their joke.
- Asset count rises while no production JAR has completed the campaign.
- “Polish later” includes cleanup, persistence, telegraphs, or installability rather than cosmetics.

**Consequences:** A showcase repository instead of a game; rushed integration creates every crash, desync, and packaging pitfall above.

**Prevention:**

- First vertical slice: contract → one arena encounter → multi-phase boss → persistent completion → reward → reload → production JAR.
- Reuse one encounter controller, maneuver library, dialogue scheduler, boss-bar adapter, cleanup owner, and reward transition across all four encounters.
- Express module content through existing primitives. For v1, a module may be one item plus one bounded event rather than a full independent progression system.
- Establish a midpoint cut line: if the final campaign path is not end-to-end, stop new primitives. Reskin/combine verified attacks and use vanilla/procedural visuals.
- Keep chapter data and dialogue tables separate so content generation cannot destabilize combat code.

**Verification:** Maintain one smoke command/test that runs the entire progression with shortened timers. It must pass after every content wave. Track features as playable/integrated/verified, not merely coded.

**Recovery:** Freeze unfinished modules off by default, retain only their safe registered placeholders, and finish the campaign using existing primitives. Document deferred depth honestly instead of leaving half-active callbacks.

**Phase mapping:** All phases; enforce the vertical slice in Phase 2 and the cut line before Phase 4 module breadth.

## Moderate Pitfalls

### Pitfall 1: Event Handlers and Schedulers Outlive Their Module

**What goes wrong:** Reloading config registers callbacks twice, static collections retain worlds after returning to menu, or a closed singleplayer world continues to influence the next one.

**Prevention:** Register callbacks once, route through a stateless central dispatcher, keep world state attached to server/level lifecycle, and clear caches on server stop. Log module initialization exactly once and test opening two worlds in one client process.

### Pitfall 2: Randomness Makes Bugs and Jokes Impossible to Reproduce

**What goes wrong:** A failing shuffle/boss sequence cannot be replayed; random calls on client and server diverge; tests become flaky.

**Prevention:** Server owns randomness. Derive encounter/module RNG from world seed plus encounter UUID, record the effective seed and selected IDs, and allow a debug seed override. GameTests use fixed seeds.

### Pitfall 3: Dialogue and Boss Bars Become Spam or Hide Combat Information

**What goes wrong:** Chat floods logs, subtitles overlap, long Unicode text clips, or boss bars remain after encounter cleanup.

**Prevention:** Use translation keys, a bounded dialogue queue, cooldown/deduplication, and short action-bar/subtitle lines during combat. Put long jokes in books/terminal UI or between phases. Test GUI scale and narrow resolutions.

### Pitfall 4: Real-Life Satire Becomes Personally Identifying or Mean-Spirited

**What goes wrong:** A fictional lecturer is recognizable from public defaults, an employer cost joke is presented as fact, or config values leak through screenshots/logs.

**Prevention:** Ship fictional archetypes, never assert the USD 15 million/token-price jokes as facts, keep personal overrides local/ignored, and sanitize logs/crash reports. Satirize systems and developer pain rather than alleging misconduct by identifiable people.

### Pitfall 5: Arena Damage Destroys a Real Survival World

**What goes wrong:** Boss explosions burn builds, void items, or permanently trap a player at school after a bug.

**Prevention:** Default boss attacks to entity damage and temporary encounter-owned hazards, respect `mobGriefing`, restore temporary blocks, provide a safe abort/return command or item, and never delete inventory for the joke. A custom campus dimension remains out of scope.

### Pitfall 6: Broad Mixins Create Conflicts and Porting Debt

**What goes wrong:** A Mixin into a hot vanilla entity/tick/render method breaks on 26.2 or conflicts with another mod, even though v1 promises only bounded compatibility.

**Prevention:** Prefer Fabric events, registries, components/attachments, and subclass behavior. If a Mixin is unavoidable, keep it one-purpose, fail loudly, test production remapping, and do not use it for Metadata Roulette.

### Pitfall 7: Dependency Convenience Creates Offline or Packaging Fragility

**What goes wrong:** A config/animation library version does not support 26.2, is declared only in the dev runtime, or is not included/documented for users.

**Prevention:** Keep v1 dependencies to Fabric Loader/API and JDK unless a library removes more risk than it adds. For any extra dependency, verify 26.2 support, license, client/server environment, and whether users must install it or Loom must include it.

## Minor Pitfalls

### Pitfall 1: “Metadata” Naming Promises the Unsafe Feature

**What goes wrong:** Users expect arbitrary entity-data swaps and report curated traits as incomplete.

**Prevention:** Keep the funny module title but describe it in config/README as “safe trait roulette,” listing supported categories and the explicit raw-metadata exclusion.

### Pitfall 2: Missing Translation Fallbacks Make Jokes Look Like Bugs

**What goes wrong:** Raw keys such as `entity.developers_hell.chairman` appear in UI.

**Prevention:** Generate `en_us` entries for every public registry/content key and add a test that compares registered translatable content to the language file.

### Pitfall 3: Debug Commands and Items Leak Into Normal Progression

**What goes wrong:** Players accidentally skip chapters or duplicate rewards using unprotected development hooks.

**Prevention:** Require operator permission, group under a clear debug command, exclude debug recipes/items from normal tabs, and document that debug state changes invalidate progression testing.

### Pitfall 4: Repository Hygiene Omits the Things Needed After the Sprint

**What goes wrong:** Generated assets, wrapper files, licenses, or provenance are missing, while IDE/run/cache files and local personal config are committed.

**Prevention:** Review the clean checkout and `.gitignore`; ensure wrapper, source resources, generated resources, build instructions, license notices, and provenance are tracked while `run/`, Gradle caches, secrets, and local identity config are not.

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Required Mitigation / Exit Evidence |
|---|---|---|
| **1. 26.2 foundation and offline skeleton** | Wrong Loom generation, JDK mismatch, conditional registries, client code in common source | Official 26.2 pins rechecked; wrapper clean build; split source sets; production client and dedicated server reach ready state; config defaults survive malformed file |
| **2. Vertical boss framework** | Double-ticked state, incomplete entity lifecycle, unbounded maneuver | One boss completes telegraph/attack/recovery, save/resume, player death, abort, cleanup, reward; server owns all gameplay state |
| **3. Four-encounter campaign** | Bespoke AI proliferation, phase skips, duplicate rewards, chapter softlock | Encounters compose shared maneuvers; shortened full-campaign smoke reaches one diploma exactly once; each chapter resumes from save |
| **4. Eight optional modules** | Raw metadata mutation, global tick scans, toggle/registry mismatch | Curated compatibility table; hard caps/intervals; all-off/all-on/one-at-a-time config matrix; cleanup command; modules never remove registered types |
| **5. Assets, dialogue, and spectacle** | Missing resources, unreadable telegraphs, brand/provenance problems, FPS collapse | Resource reload log clean; reduced-particle/audio readability pass; asset provenance complete; public text has no sponsorship claim/private identities; intensity caps tested |
| **6. Release hardening** | Dev JAR shipped, hidden server crash, save corruption, offline dependency | Clean checkout CI; production client/server smoke; save fixture matrix; ten-minute soak; fresh offline profile install; release JAR checksum and exact dependency tuple documented |

## Release Blockers vs Acceptable MVP Shortcuts

### Must Fix Before Handoff

- Clean reproducible 26.2/Java 25 build and correct remapped JAR.
- No client-class dedicated-server crash.
- Server-authoritative combat, bounded encounter state, and safe packet validation.
- Stable registrations; bosses spawn with attributes and clean up every owned object/bar/effect.
- Campaign and active encounter survive save/reload without duplication or world-load failure.
- No arbitrary raw tracked-data/AI transplantation.
- Malformed config falls back safely; toggles do not alter static registry existence.
- Every lethal attack is telegraphed and every encounter has timeout/abort/death recovery.
- No missing resource that hides a required item/reward or attack cue.
- No sustained runaway tick/entity/packet growth.
- Public release has fictional/private-safe defaults, provenance/licenses, and no false OpenAI/ChatGPT sponsorship or Mojang/Microsoft endorsement.
- Fresh-profile offline install completes the start-to-diploma campaign.

### Explicitly Acceptable for v1

- Singleplayer balance only; dedicated-server boot safety is tested, but multiplayer encounter scaling/polish may be documented as unsupported.
- Restart required for config changes.
- Shared boss rig/model/state machine with themed textures, attacks, dialogue, and rewards.
- Vanilla sounds/particles/models used where a custom asset is not release-ready.
- Fixed or reused arena in the Overworld; no campus dimension or procedural world generation.
- Jury as a controlled gauntlet built from shared boss/add primitives rather than another unique AI framework.
- Reduced number of shuffled trait combinations, provided every advertised combination is safe and replayable.
- Simple JSON/data tables and in-game config command/status output instead of a custom config GUI.
- English (`en_us`) only for the first release, with all text still using translation keys.
- No live ChatGPT/Codex integration, update check, telemetry, or online content.

## Minimal Verification Matrix

| Scenario | Pass Condition |
|---|---|
| Clean build | Java 25 wrapper build succeeds from clean checkout; expected non-dev JAR produced |
| Production client | Fresh profile with only matching Loader, Fabric API, and release JAR reaches a world with clean mod/resource logs |
| Production dedicated server | Same JAR reaches world-ready state without loading client classes |
| Campaign persistence | Save/reload at every chapter/phase boundary resumes one valid state and awards one diploma |
| Boss recovery | Player death, escape, dimension change, timeout, chunk unload, and admin abort leave no hazards or bars |
| Module matrix | All off, all on, and each alone behave as configured across restart; existing items/entities still load |
| Trait roulette | Every allowed entity/trait pair survives apply, save/reload, cleanup, and several minutes of ticking |
| Performance soak | Final boss plus all modules for ten minutes stays bounded in tick time, entity count, packets/logs, and save growth |
| Resource/package audit | Datagen repeat is stable; resource reload is clean; final JAR contains metadata, assets, data, license, and provenance |
| Offline handoff | After dependencies are locally installed, network-disabled play needs no key/account/service and completes the core loop |

## Sources

All confidence labels below come from the configured research confidence seam. Official pages found through web search and cross-checked against another primary source classify as **MEDIUM** in this environment.

### Fabric, Minecraft, and Build Tooling

- [Fabric for Minecraft 26.2](https://www.fabricmc.net/2026/06/15/262.html) — official release guidance; Loom/Gradle/Loader direction and 26.2 registration/render changes. **MEDIUM**
- [Fabric Docs: Porting to 26.2](https://docs.fabricmc.net/develop/porting/) — official current porting workflow. **MEDIUM**
- [Fabric Example Mod, 26.2 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2) — official current template and version pins. **MEDIUM**
- [Fabric Docs: Loom](https://docs.fabricmc.net/develop/loom/) — plugin IDs, split environment source sets, dependency packaging, remap/build behavior. **MEDIUM**
- [Fabric Docs: `fabric.mod.json`](https://docs.fabricmc.net/develop/loader/fabric-mod-json) — environment, entrypoints, dependency/version metadata, mixin sides. **MEDIUM**
- [Gradle Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html) — official Java/Gradle runtime compatibility. **MEDIUM**
- [Fabric Docs: Creating Your First Entity](https://docs.fabricmc.net/develop/entities/first-entity) — entity registration, attributes, client rendering, synchronized data, and entity persistence. **MEDIUM**
- [Fabric Docs: Networking](https://docs.fabricmc.net/develop/networking) — logical server model, payload registration, tracking, and server validation. **MEDIUM**
- [Fabric Docs: Saved Data](https://docs.fabricmc.net/develop/serialization/saved-data) — world persistence and dirty marking. **MEDIUM**
- [Fabric Docs: Data Attachments](https://docs.fabricmc.net/develop/serialization/data-attachments) — persistence/sync behavior and immutable update warning. **MEDIUM**
- [Fabric Docs: Codecs](https://docs.fabricmc.net/develop/serialization/codecs) — optional fields, defaults, and constrained serialization. **MEDIUM**
- [Fabric Docs: Data Generation Setup](https://docs.fabricmc.net/develop/data-generation/setup) — generated resources and task/source-set setup. **MEDIUM**
- [Fabric Docs: Creating Your First Item](https://docs.fabricmc.net/develop/items/first-item) — translations, textures, models, and 26.2 client-item JSON. **MEDIUM**
- [Fabric Docs: Automated Testing](https://docs.fabricmc.net/develop/automatic-testing) — Loader-aware unit tests, server/client GameTests, and CI behavior. **MEDIUM**
- [Fabric Docs: Production Run Tasks](https://docs.fabricmc.net/develop/loom/production-run-tasks) — testing remapped artifacts in production-like client/server launches. **MEDIUM**
- [Fabric Docs: Building a Mod](https://docs.fabricmc.net/develop/getting-started/building-a-mod) — wrapper build and release-JAR identification. **MEDIUM**
- [Fabric Docs: Debugging Mods](https://docs.fabricmc.net/develop/debugging) — namespaced logs and missing-resource warning workflow. **MEDIUM**

### Encounter Design and Performance

- [GDC: Crafting Epic Boss Fights in a Hurry](https://media.gdcvault.com/gdc2023/Slides/CraftingEpicBoss_Vessal_Beca.pdf) — developer-presented maneuver chains and telegraph/attack/recovery structure. **MEDIUM**
- [GDC: Boss Up](https://media.gdcvault.com/gdc2018/presentations/Keren_Itay_BossUp.pdf) — developer-presented telegraphing and attack-control guidance. **MEDIUM**
- [Minecraft: Chamber of Secrets](https://www.minecraft.net/en-us/article/chamber-secrets) — first-party interview on measuring mob capabilities, encounter space, stress, and time. **MEDIUM**

### Distribution, Assets, and Brand Safety

- [OpenAI Terms of Use](https://openai.com/policies/terms-of-use/) — output ownership between parties, non-uniqueness, and user responsibility. **MEDIUM**
- [OpenAI Design Guidelines](https://openai.com/brand/) — logo/word-mark limits and prohibition on implied endorsement/sponsorship. **MEDIUM**
- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) — mod distribution conditions, asset/brand rules, and unofficial-product disclaimer. **MEDIUM**
- [GitHub Docs: Licensing a Repository](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository) — consequences of missing licenses and repository licensing practice. **MEDIUM**

## What Might Still Be Missing

- Exact Fabric component patch pins can change after this research date; the scaffold phase must query Fabric Develop again rather than copying this document's observed example versions.
- This research does not prove compatibility with other mods or alternate renderers; v1 promises only the documented clean Fabric 26.2 profile.
- Actual attack timings, hazard caps, and performance budgets require playtest/profiling on the creator's machine; this document specifies how to bound and verify them, not fictional benchmark numbers.
- Trademark/copyright guidance is risk-reduction research, not legal advice. If the public release retains exact OpenAI/ChatGPT marks as a named character, perform a focused brand review at release time.
