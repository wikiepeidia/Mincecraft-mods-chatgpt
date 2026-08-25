# Project Research Summary

**Project:** Developer's Hell  
**Domain:** Offline Minecraft Java comedy boss campaign and configurable anthology mod  
**Researched:** 2026-08-25  
**Confidence:** MEDIUM

## Executive Summary

Developer's Hell should be built as one small Fabric monolith for Minecraft Java 26.2, not as eight separate mods. Its spine is a checkpointed contract-to-diploma campaign with four substantial encounters: Professor Infinite Slides, the Hostile Jury Gauntlet, Prof. Dr. Rejectus Maximus, and The Rich ChatGPT's transformation into Codex Overdraft. The campaign is also the Graduation% AnyFAIL module; seven independent sandbox loops complete the promised eight-module anthology. Each joke must change play through a visible trigger, player response, mechanical consequence, and payoff.

The recommended implementation is Java 25 plus the official Fabric 26.2 project shape: split common/client source sets, unconditional registrations, immutable startup configuration, server-owned campaign and encounter state, Codec-backed world persistence, a small presentation-only packet surface, and vanilla/Fabric APIs before third-party libraries or Mixins. The first and non-negotiable gate is to install or select JDK 25, validate the exact 26.2 dependency tuple, and prove clean client, dedicated-server, GameTest, and remapped-JAR builds. Only then should development establish one complete Contract -> Lecture Boss -> saved completion -> reward vertical slice and reuse its encounter machinery for all later bosses.

The main risk is breadth under a hard 1–2 day sprint. Preserve the complete four-encounter path, one working loop for every named module, readable telegraphs, recovery, offline operation, and a tested distributable JAR; cut extra variants, bespoke models, dialogue volume, and decorative effects first. Other release blockers are client/server side leakage, duplicated or corrupted progress, unbounded hazards or scans, unsafe raw entity-data shuffling, and public copy that implies real OpenAI or employer sponsorship. The Rich ChatGPT, token balance, USD/company line, and sponsor role must remain clearly fictional satire with public-safe defaults and original, provenance-tracked assets.

## Key Findings

The detailed evidence is in [STACK.md](./STACK.md), [FEATURES.md](./FEATURES.md), [ARCHITECTURE.md](./ARCHITECTURE.md), and [PITFALLS.md](./PITFALLS.md).

### Recommended Stack

Use the official 26.2 Fabric example as the scaffold and freeze a verified tuple rather than mixing tutorial generations. Minecraft 26.1+ is unobfuscated, so the project should use `net.fabricmc.fabric-loom`, omit Yarn and a `mappings` dependency, and keep all build commands behind the Gradle wrapper. The source research recorded Java 21 on the workstation, which is insufficient; Phase 1 must verify both `java` and `javac` are Java 25 before Gradle work.

**Core technologies:**

- **Minecraft Java 26.2:** sole game target; 26.3 snapshots are explicitly unsupported.
- **Eclipse Temurin JDK 25:** required compile/runtime toolchain; the observed candidate pin is `25.0.4+7`.
- **Fabric Loader `0.19.3` and Fabric API `0.158.0+26.2`:** observed official example pins; recheck immediately, then freeze the tested values.
- **Fabric Loom 1.17:** try fixed `1.17.19`; if the untouched template fails, use only the official template's `1.17-SNAPSHOT` fallback and record the resolved build.
- **Gradle Wrapper `9.5.1`:** observed official 26.2 wrapper and Java 25-compatible build entry point.
- **Vanilla/Fabric facilities:** entity AI, `ServerBossEvent`, Saved Data, Codecs, synced entity fields, typed payloads, commands, datagen, particles, sounds, translations, advancements, and GameTests.
- **Asset tools:** Blockbench and pixel cleanup for only the highest-value silhouettes/icons, mono OGG audio where custom sound matters, and generated original source art committed as deterministic assets with provenance.
- **No runtime service or database:** no OpenAI SDK, HTTP content, telemetry, account, API key, remote config, or external persistence.

### Expected Features

**Must have (table stakes):**

- One offline, installable Fabric 26.2 JAR with exact prerequisites, wrapper-based build instructions, source, license, credits, and a tested release artifact.
- A discoverable Cursed Unpaid Internship Contract start and persistent path through all four encounters to the Definitely Legitimate Diploma and boss replay.
- Four substantial encounters, each with three acts, at least two distinctive attacks, a non-damage decision or interruption mechanic, readable boss state, a retry path, and a useful unique reward.
- Exactly eight independent one-loop modules: Graduation% AnyFAIL, Metadata Roulette, Python Tools, Codex Rich Kid Terminal, Git Happens, Stack Overflow Totem, Rubber Duck Engineering, and Three-Day Deadline.
- Safe module toggles that gate behavior rather than registry identity; all-off, all-on, and one-at-a-time configurations must remain loadable.
- Persistent checkpoints, idempotent rewards, encounter cleanup, `/devhell status` and retry/replay recovery, and no world-grief default.
- Prewritten offline dialogue and objective history plus redundant text/shape/sound telegraphs, Story-to-Intense difficulty controls, longer telegraph options, reduced effects, and reduced flashing.
- Fictional public defaults and optional local creator, sponsor, university, and company strings that never leak secrets or present real sponsorship/cost claims as facts.

**Should have (differentiators):**

- Bureaucracy expressed as combat rules: slide lanes, quiz pads, citation/evidence mechanics, scope creep, rubric shields, revisions, and rejection stamps.
- A coherent ally-to-boss arc with original solar-white/gold/teal spectacle, fake agent roles, tool-call queues, token overflow, and a scripted local terminal.
- Curated trait roulette that produces recognizable pig/Enderman-style absurdity while remaining reversible and save-safe.
- Developer tools with useful powers and bounded failure jokes: recursion cooldowns, dependency conflicts, merge conflicts, dubious totem answers, and rubber-duck hints.
- Replay after graduation and enough contextual variants to stay funny without building a general quest or scripting engine.

**Defer to v2+:**

- Live AI, generated dialogue, browser, shell, real Git/Python execution, telemetry, update checks, or any network dependency.
- A campus dimension, procedural university, branching story, deep quest engine, voice acting, cinematics, or bespoke skeletal rig for every boss.
- Arbitrary raw metadata/NBT/brain interchange, unsupported modded-entity roulette, and inventory/world rollback.
- Full multiplayer balance, broad mod compatibility, custom config GUI, extensive localization, and eight separately packaged JARs.

### Architecture Approach

Build a coherent monolith with vertical feature packages around a narrow shared kernel. Configuration is machine-local and immutable until restart; gameplay state is server-owned and persisted; presentation state is client-only and reconstructible. Register every stable item/entity/block/payload unconditionally, then gate triggers and effects through one eight-entry module catalog. Use a pure campaign reducer plus imperative, idempotent effects; a single encounter coordinator and telegraph -> attack -> recovery scheduler; whitelisted Java attack primitives with JSON only for safe tuning/dialogue; and small event-driven packets for cues rather than per-tick animation traffic.

**Major components:**

1. **Bootstrap, registries, config, and module gate** — establish stable IDs, validate public-safe defaults, and install enabled behavior hooks without changing registered content.
2. **Campaign reducer/service and Saved Data** — own monotonic chapter transitions, encounter references, checkpoints, token state, schema version, and exactly-once rewards.
3. **Encounter coordinator and shared boss framework** — own one active encounter, server-side phases, deterministic attack scheduling, boss bars, bounded helpers, save/reload recovery, abort, and cleanup.
4. **Content repository and dialogue service** — atomically validate immutable local JSON snapshots and retain built-in fallbacks for campaign-critical definitions.
5. **Eight vertical module packages** — expose one bounded signature loop each while sharing items/effects, objectives, cues, cooldowns, and safe persistence primitives.
6. **Networking and client presentation** — accept only validated terminal requests; render synced cues, HUD, entities, particles, sounds, terminal UI, and the solar finale without gameplay authority or raw OpenGL.
7. **Unit tests, GameTests, and smoke seams** — validate reducers and bounds quickly, then prove actual lifecycle, persistence, dedicated-server safety, resources, and release packaging.

### Critical Pitfalls

1. **Wrong 26.2 toolchain or guessed APIs** — require JDK 25 first, scaffold from the official 26.2 example, validate Loom/Gradle/API together, and freeze the passing tuple before gameplay code.
2. **Broken server/entity/persistence lifecycle** — enforce the common/client source boundary, keep mechanics server-authoritative, persist schema and encounter IDs, make transitions/rewards idempotent, and test death, unload, reload, escape, timeout, and abort.
3. **Breadth before a playable vertical slice** — complete and repeatedly test Contract -> Lecture -> reward before parallel boss/module work; reuse one maneuver, cue, cleanup, and reward framework.
4. **Unsafe or unbounded chaos** — replace raw metadata transfer with compatible reversible Trait Cards; cap scans, adds, hazards, particles, packets, recursion, and objective work; log deterministic seeds and provide namespaced cleanup.
5. **Unreadable or unreleasable spectacle** — every lethal move needs text, shape, sound, wind-up, recovery, and a timeout; inspect the final JAR/resources; use original assets with provenance and explicit no-sponsorship/unofficial-product language.

## Implications for Roadmap

The roadmap should use six tightly time-boxed phases. A phase is complete only when its runnable exit evidence passes; written code or generated assets alone do not count. The total scope remains a hard 1–2 day sprint.

### Phase 1: Java 25 and Fabric 26.2 Foundation

**Rationale:** Every other task depends on a real, side-safe, reproducible 26.2 development environment; this retires the highest uncertainty before feature work.  
**Delivers:** JDK 25 validation; official 26.2 scaffold; frozen known-good Minecraft/Loader/API/Loom/Gradle tuple; wrapper; Groovy build; no Yarn; split source sets; common/client entrypoints; stable namespace and unconditional registries; minimal config defaults; production client and dedicated-server launches; trivial unit/GameTest; remapped JAR; primed offline build.  
**Addresses:** one-JAR installation, offline boundary, reproducible build, save-safe content identity.  
**Avoids:** mixed toolchain generations, client classes on servers, missing wrapper/resources, and spending the sprint on speculative APIs.  
**Exit evidence:** `java` and `javac` report 25; wrapper `help` and `build` pass; client and server reach ready state; one GameTest passes; `--offline build` succeeds after dependencies are cached.

### Phase 2: State Kernel and Lecture Vertical Slice

**Rationale:** One end-to-end fight proves the architecture, persistence, readability, and recovery contracts before the remaining bosses multiply failure paths.  
**Delivers:** immutable validated config; exactly eight module IDs/gates; campaign reducer/service; Codec-backed Saved Data; local content/dialogue schemas; encounter coordinator; shared boss bar and telegraph -> attack -> recovery scheduler; bounded attack primitives; Cursed Contract; complete three-act Professor Infinite Slides fight; Attendance Sheet and Slides Remote; status/retry/debug seams.  
**Addresses:** campaign discovery, persistent progress, Graduation% AnyFAIL's first checkpoint, readable combat, recovery, useful reward, offline dialogue.  
**Avoids:** static progress, duplicate rewards, double ticking, orphan entities/bars, phase softlocks, and four bespoke boss engines.  
**Exit evidence:** the Lecture can be started, saved mid-fight, resumed safely, failed, retried, aborted, defeated once, and rewarded once in both automated and manual checks.

### Phase 3: Complete Four-Encounter Campaign

**Rationale:** The contract-to-diploma path is the core value and must be complete before optional breadth. All encounters now compose the proven scheduler and primitives.  
**Delivers:** Hostile Jury Gauntlet with Evidence/Scope/Why rules; Chairman Boss with rubric, minor revisions, and acceptance mechanics; radiant sponsor interlude; minimal Codex Rich Kid Terminal trigger; three-act Codex Overdraft finale; token depletion transformation; all chapter artifacts and useful rewards; Diploma; boss replay; shortened full-campaign smoke path.  
**Addresses:** all four promised bosses, ally-to-final-boss arc, Graduation% AnyFAIL's complete loop, terminal/campaign integration, replay payoff.  
**Avoids:** phase skips, bespoke AI proliferation, duplicated rewards, chapter deadlocks, and a repository full of disconnected showcases.  
**Exit evidence:** one new world reaches exactly one Diploma through Lecture -> Jury -> Chairman -> Codex Overdraft, and save/reload works at each chapter boundary.

### Phase 4: Six Developer-Tool Sandbox Loops

**Rationale:** These modules mostly reuse verified item, effect, cooldown, objective, dialogue, and cue infrastructure and are lower risk than entity trait manipulation.  
**Delivers:** Python Tools; the full fake-agent/token-feed loop for Codex Rich Kid Terminal; Git Happens; Stack Overflow Totem; Rubber Duck Engineering; and Three-Day Deadline. Each receives one bounded signature loop, a documented acquisition/debug path, an independent toggle, inert disabled behavior, and focused tests. Graduation% AnyFAIL is already complete, bringing the demonstrated count to seven modules.  
**Addresses:** developer satire, independent anthology play, offline Codex showcase, repeatable post-campaign entertainment.  
**Avoids:** real code/file execution, inventory/world rollback, hidden global scans, always-on punishment, unsafe client requests, and cross-module dependencies.  
**Exit evidence:** each loop can be demonstrated in under five minutes; all-off, all-on, and each-alone configurations load across restart without blocking the campaign.

### Phase 5: Safe Metadata Roulette

**Rationale:** This is the riskiest optional seam and belongs after campaign stability, shared module gating, persistence, and cleanup exist.  
**Delivers:** at least six visible curated Trait Cards; compatible vanilla-entity allowlists; exclusions for bosses, pets, named mobs, villagers, mounts, and modded entities by default; bounded active leases; deterministic seeds; save/reload policy; operator cleanup; manual default plus optional capped scheduling. This completes the eighth module loop.  
**Addresses:** the requested pig/Enderman-style shuffle while preserving world safety.  
**Avoids:** tracked-data serializer corruption, copied brains/goals/NBT, extreme attributes, retry storms, permanent broken mobs, and global per-tick iteration.  
**Exit evidence:** every allowed entity/trait pair survives application, several minutes of behavior, save/reload, module disable policy, death/unload, and namespaced cleanup without errors.

### Phase 6: Offline Content, Accessibility, and Release Hardening

**Rationale:** Final art and joke volume should polish proven mechanics, while production packaging and adversarial testing determine whether the mod remains usable after the credit window ends.  
**Delivers:** concise localized dialogue; objective history; original boss/item/terminal textures and highest-value sounds; solar finale treatment; credits and `ASSET_PROVENANCE.md`; reduced-effects/flashing and difficulty checks; README/install/config/troubleshooting/known-limitations documentation; clean production JAR, checksum, compatibility tuple, showcase media, and GitHub-ready release materials.  
**Addresses:** offline entertainment, readable spectacle, public attribution, installability, and creator showcase.  
**Avoids:** missing resources, unlicensed or logo-like assets, false OpenAI/company sponsorship, private-name leakage, FPS/TPS collapse, save corruption, and shipping a dev/sources JAR.  
**Exit evidence:** clean checkout build; production client/server smoke; resource/JAR audit; save fixture and recovery matrix; ten-minute final-boss-plus-modules soak; fresh-profile network-disabled start-to-Diploma run.

### Phase Ordering Rationale

- JDK 25 and the exact Fabric tuple precede all implementation because the current research environment could not compile-test 26.2 APIs.
- The state/config kernel and one full Lecture slice precede content breadth so every later encounter inherits tested persistence, cues, cleanup, and reward semantics.
- The complete four-boss campaign precedes optional modules because the Diploma path is the product's core value and the strictest sprint stopping rule.
- The terminal is split deliberately: its minimal block/trigger/replay contract ships with the finale, while its standalone fake-agent loop is completed with the other developer tools.
- Low- and medium-risk item/objective modules precede Metadata Roulette; trait compatibility and restoration receive their own validation window.
- Placeholder vanilla assets are acceptable throughout earlier phases, but missing gameplay cues are not. Custom art, dialogue volume, policy review, and packaging come only after the mechanics are end-to-end.
- If time slips, reduce outcomes, dialogue variants, custom sounds, models, and replay modifiers. Do not cut a boss completion, any of the eight signature loops, telegraphs, recovery, persistence, offline operation, or release verification.

### Research Flags

**Needs targeted research or compile spikes:**

- **Phase 1:** requery the official Fabric 26.2 template, validate Java 25, and resolve fixed Loom `1.17.19` versus the template's moving `1.17-SNAPSHOT` before freezing the tuple.
- **Phase 2:** confirm exact unobfuscated 26.2 symbols and DSLs for entity registration/attributes, Saved Data Codecs, typed payloads, resource reload, HUD access, and server GameTests in generated sources.
- **Phase 3:** run a short supported-rendering spike for emissive layers, HUD placement, and the Vulkan-capable 26.2 renderer path; never import raw OpenGL patterns from older tutorials.
- **Phase 5:** perform deeper research and GameTest prototyping for reversible goal/aggression adapters, supported scale/sound changes, lease persistence, compatibility predicates, and safe cleanup.
- **Phase 6:** recheck current Minecraft/OpenAI brand guidance if the public release retains exact ChatGPT/GPT-5.6 naming; this is a release copy/asset review, not evidence of sponsorship.

**Standard patterns after Phase 1 validation:**

- **Phase 2 campaign reducer and persistence:** documented pure-state and Saved Data/Codec patterns, with implementation proof supplied by tests.
- **Phase 4 item/effect/objective modules:** ordinary registered items, status effects, cooldowns, bounded server events, and existing campaign hint hooks; skip broad research unless a compile error exposes a 26.2 change.
- **Phase 6 build/package checks:** documented wrapper, production-run, resource, GitHub README/release, and license practices; prioritize execution over more ecosystem research.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | Official Mojang, Fabric, Gradle, and Adoptium sources agree on the 26.2/Java 25 direction, but the proposed exact tuple and Loom fixed pin were not compiled because the research environment reported Java 21. |
| Features | HIGH for scope; MEDIUM for balance | The user directly selected the concept, four encounters, ally-to-boss arc, and one-JAR anthology. Exact timings, difficulty, and content counts still require playtesting. |
| Architecture | MEDIUM | Server ownership, split source sets, Saved Data, Codecs, typed payloads, and stable registries are documented patterns; exact 26.2 method names and rendering seams need a live compile. |
| Pitfalls | MEDIUM | Failure modes are supported by official platform docs and established encounter practice, but performance caps, save recovery, and public-brand treatment need implementation evidence. |

**Overall confidence:** MEDIUM. The product and dependency order are clear; feasibility depends on passing Phase 1 quickly and enforcing the ruthless MVP cut line.

### Gaps to Address

- **JDK/toolchain proof:** verify JDK 25 selection and the complete dependency tuple on this workstation before writing content code.
- **Loom pin:** determine whether `1.17.19` builds the untouched official template; otherwise record the exact resolved `1.17-SNAPSHOT` build without changing unrelated pins.
- **26.2 API surface:** compile tiny seams for registries/datagen, HUD/render states, typed networking, resource reload, and GameTest task names rather than guessing from older Yarn tutorials.
- **Encounter tuning:** establish real telegraph windows, damage, add/hazard caps, arena radius, and 3–6 minute targets through short playtests; do not invent benchmark numbers.
- **Metadata trait compatibility:** prove every advertised entity/trait pair and the disable/restore policy; unsupported combinations should be absent, not probabilistically attempted.
- **Performance budget:** profile the final encounter with all modules for ten minutes and set caps from measured entity, packet, tick, FPS, log, and save-growth behavior.
- **Asset scope:** decide after the Lecture slice whether any boss needs a bespoke rig; default to shared vanilla-compatible silhouettes, palette variants, particles, and original icons.
- **Public naming and satire:** label token/USD/company lines as fictional; use placeholders by default; state no OpenAI, ChatGPT, Mojang, Microsoft, university, or employer sponsorship/endorsement; perform focused brand review if exact marks remain.
- **Compatibility boundary:** v1 guarantees only the documented clean Fabric 26.2 profile and singleplayer balance; broader mod/render/multiplayer compatibility is unproven and should be stated as such.

## Sources

All external evidence below is from official or first-party sources unless marked secondary. The source research classified web-retrieved official evidence as MEDIUM until implementation validation.

### Project authority

- [PROJECT.md](../PROJECT.md) — approved concept, constraints, four encounters, eight-module packaging, offline requirement, satire boundary, and 1–2 day sprint.
- [STACK.md](./STACK.md) — exact candidate pins, bootstrap commands, APIs, asset toolchain, and unresolved 26.2 seams.
- [FEATURES.md](./FEATURES.md) — bounded encounter/module contracts, accessibility, cut order, and acceptance checks.
- [ARCHITECTURE.md](./ARCHITECTURE.md) — component boundaries, state ownership, data flow, build order, and research flags.
- [PITFALLS.md](./PITFALLS.md) — release blockers, recovery rules, validation matrix, and MVP-safe shortcuts.

### Primary platform and tooling sources

- [Minecraft Java Edition 26.2 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2) — stable target and resource/data formats.
- [Fabric for Minecraft 26.2](https://www.fabricmc.net/2026/06/15/262.html) — Java, Loom/Gradle, mappings, registration, and rendering changes.
- [Official Fabric 26.2 example mod](https://github.com/FabricMC/fabric-example-mod/tree/26.2) — scaffold, wrapper, dependencies, source-set shape, and metadata.
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/) — build plugin, split environments, remapping/build tasks, and dependency behavior.
- [Fabric project structure](https://docs.fabricmc.net/develop/getting-started/project-structure) and [networking](https://docs.fabricmc.net/develop/networking) — sided entrypoints, logical-server authority, payload registration, and validation.
- [Fabric Saved Data](https://docs.fabricmc.net/develop/serialization/saved-data) and [Codecs](https://docs.fabricmc.net/develop/serialization/codecs) — persistent campaign state and bounded data parsing.
- [Fabric custom entities](https://docs.fabricmc.net/develop/entities/first-entity) and [automated testing](https://docs.fabricmc.net/develop/automatic-testing) — entity lifecycle/registration and unit/GameTest strategy.
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html) — Java 25 runtime compatibility.
- [OpenAI design guidelines](https://openai.com/brand/) and [Terms of Use](https://openai.com/policies/terms-of-use/) — no implied sponsorship and output-use responsibility.
- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) — mod distribution, asset/brand rules, and unofficial-product treatment.
- [Xbox Accessibility Guidelines](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/) — redundant cues, captions, timing, difficulty, reduced motion, and photosensitivity practices.
- [GitHub README guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes), [Releases](https://docs.github.com/en/repositories/releasing-projects-on-github), and [repository licensing](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/adding-a-license-to-a-repository) — public handoff criteria.

### Secondary encounter-design sources

- [GDC: Crafting Epic Boss Fights in a Hurry](https://media.gdcvault.com/gdc2023/Slides/CraftingEpicBoss_Vessal_Beca.pdf) — reusable maneuver chains and telegraph/attack/recovery structure.
- [GDC: Boss Up](https://media.gdcvault.com/gdc2018/presentations/Keren_Itay_BossUp.pdf) — attack communication and player-response design.
- [Minecraft: Chamber of Secrets](https://www.minecraft.net/en-us/article/chamber-secrets) — first-party encounter-space, stress, time, and mob-capability considerations.

---
*Research completed: 2026-08-25*  
*Ready for roadmap: yes*
