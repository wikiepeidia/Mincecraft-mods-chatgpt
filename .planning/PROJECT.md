# Developer's Hell

## What This Is

Developer's Hell is a single configurable Minecraft: Java Edition anthology mod that turns developer life, university bureaucracy, and expiring AI credits into a playable comedy campaign. The player signs a cursed unpaid-internship contract, becomes trapped in a fictional university, fights increasingly absurd lecture and thesis-defense bosses, and eventually faces the radiant sponsor-turned-final-boss, The Rich ChatGPT.

Alongside the campaign, the same JAR provides eight independently toggleable chaos modules for continued survival play. It is built primarily for the creator to install and enjoy offline after their temporary ChatGPT access expires, with complete source published in the existing GitHub repository.

## Core Value

Deliver a genuinely funny, replayable boss-rush experience whose university and developer jokes become visible Minecraft mechanics rather than merely renamed items or text references.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Ship one installable Fabric mod JAR for Minecraft Java 26.2 that works without ChatGPT, an API key, or any network service.
- [ ] Provide a chaptered Developer's Hell campaign initiated by a Cursed Unpaid Internship Contract and completed by earning a Definitely Legitimate Diploma.
- [ ] Implement substantial, multi-phase boss encounters for Professor Infinite Slides, a hostile Jury Gauntlet, Prof. Dr. Rejectus Maximus as the Chairman Boss, and Codex Overdraft: The 300K-Token Abomination.
- [ ] Make The Rich ChatGPT a radiant ChatGPT 5.6 Sol-inspired sponsor and narrator who helps the player before its token balance reaches zero and it transforms into the final boss.
- [ ] Provide eight independently toggleable comedy modules: Graduation% AnyFAIL, Metadata Roulette, Python Tools, Codex Rich Kid Terminal, Git Happens, Stack Overflow Totem, Rubber Duck Engineering, and Three-Day Deadline.
- [ ] Make boss attacks readable and mechanically distinct through boss bars, telegraphs, phases, dialogue, audiovisual effects, and unique rewards.
- [ ] Include a safe chaos system that shuffles compatible mob behaviors, attributes, sounds, scale, aggression, effects, and loot without performing crash-prone arbitrary raw metadata swaps.
- [ ] Include enough pre-generated dialogue, jokes, textures, sounds, and configuration to remain entertaining offline.
- [ ] Publish readable source, build instructions, credits, licenses, and a distributable JAR through the existing GitHub repository.
- [ ] Provide configurable creator, sponsor, fictional university, and fictional company shout-outs without requiring real personal or employer identities in public source.

### Out of Scope

- Live ChatGPT/Codex API calls — the mod must remain fully playable after the creator's temporary AI access ends.
- Arbitrary raw entity metadata interchange — incompatible tracked-data schemas are likely to crash or corrupt gameplay; curated compatible trait shuffling produces the intended joke safely.
- Real lecturers, jurors, universities, employers, or coworkers encoded by default — public releases use fictional satire and optional local configuration.
- A custom campus dimension or large procedural world-generation system for v1 — the 1–2 day deadline prioritizes fights and replayable mechanics over environment infrastructure.
- Guaranteed compatibility with every mod or loader — v1 targets a tested Fabric 26.2 installation.
- Production-grade multiplayer balancing — v1 is singleplayer-first, with server-safe implementation where practical.

## Context

- The repository currently contains only an initial README, license, and Java-oriented `.gitignore`; implementation begins from a clean greenfield state.
- The creator has a large temporary ChatGPT/Codex allowance that expires in roughly three days and wants to convert it into a mod they can keep playing afterward.
- The central joke draws from the creator's experience with internships, difficult university lectures, hostile thesis juries, delayed graduation, developer tooling, and corporate AI-credit allocation.
- The creator requested 5–10 mods' worth of material. The selected packaging is one anthology JAR with eight toggleable modules so shared systems and testing effort produce a more polished result.
- The creator requested a GitHub-visible Codex showcase. The showcase is represented through an in-game terminal, fake agent activity, token meters, narration, credits, and the final-boss transformation rather than a live service dependency.
- A user-supplied corporate joke describes USD 15 million being divided across 3,000 employees. Treat this as configurable satire, not a verified factual or pricing claim.
- Textures and other original bitmap assets may be generated with the available image-generation workflow, then cleaned and converted into Minecraft-appropriate pixel assets. Code-native effects and procedural assets should be preferred where they communicate the joke better.

## Constraints

- **Timeline**: Produce the playable project within a hard 1–2 day implementation sprint — prioritize a complete vertical campaign slice and bounded modules.
- **Game target**: Minecraft Java 26.2 — latest stable release confirmed during initialization; do not target the changing 26.3 snapshots.
- **Mod loader**: Fabric with its official 26.2 toolchain — best current documentation and shortest path for this sprint.
- **Runtime**: JDK 25 and current Fabric 26.2 generator/template pins — recheck exact patch versions immediately before scaffolding.
- **Packaging**: One JAR with eight configurable modules — minimizes duplicated setup while preserving mod-like independent toggles.
- **Operation**: Offline-first — no remote API, account, subscription, or network requirement at runtime.
- **Scope**: Singleplayer-first — protect campaign completeness and stability before optional multiplayer polish.
- **Distribution**: GitHub-ready source plus reproducible build and distributable artifact — the result must remain usable after the AI sprint.
- **Privacy**: Fictional/public-safe defaults with optional local customization — do not invent or expose personal or employer information.
- **Assets**: Original or license-compatible assets only — record provenance and avoid unlicensed Minecraft community assets.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Name the project Developer's Hell | Captures the combined university, internship, developer-tooling, and AI-credit premise | — Pending |
| Target Minecraft Java 26.2 with Fabric and JDK 25 | 26.2 is stable while 26.3 is still in snapshots; Fabric has current official 26.2 tooling and documentation | — Pending |
| Package eight modules in one configurable anthology JAR | Shares infrastructure and testing effort while providing the requested breadth | — Pending |
| Build a chaptered boss campaign plus optional sandbox chaos | Gives the jokes a memorable progression while preserving replayability after completion | — Pending |
| Make The Rich ChatGPT an ally who becomes the final boss | Creates a coherent arc from AI sponsorship and token excess to zero-credit hallucination catastrophe | — Pending |
| Keep all runtime content offline | Ensures the mod survives the temporary ChatGPT credit window | — Pending |
| Use curated trait shuffling instead of raw metadata swapping | Preserves surprising cross-mob behavior without predictable crashes from incompatible schemas | — Pending |
| Fictionalize institutions and make shout-outs configurable | Keeps the public repository funny without exposing or targeting real people or organizations | — Pending |
| Use generated original art with pixel cleanup when custom textures add value | The creator has no time to author textures, while bespoke visuals are important to the boss spectacle | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-25 after initialization*
