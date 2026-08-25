# Requirements: Developer's Hell

**Defined:** 2026-08-25
**Core Value:** Deliver a genuinely funny, replayable boss-rush experience whose university and developer jokes become visible Minecraft mechanics rather than merely renamed items or text references.

## v1 Requirements

### Foundation

- [ ] **FND-01**: A player can install one Developer's Hell JAR with the documented Fabric 26.2 prerequisites and enter a world while the machine is offline.
- [ ] **FND-02**: A contributor can build the production JAR from a fresh checkout through the committed Gradle wrapper and a frozen Java 25/Fabric 26.2 dependency tuple.
- [ ] **FND-03**: The production mod can launch both a client world and a dedicated server without client-only classloading failures.
- [ ] **FND-04**: All stable items, entities, effects, payloads, and other content IDs remain registered regardless of module-toggle values so existing saves remain loadable.
- [ ] **FND-05**: The mod validates configuration at startup, reports actionable errors, and defaults destructive or scheduled chaos to opt-in behavior.
- [ ] **FND-06**: Versioned campaign, encounter, reward, and module state survives save, quit, reload, death, and chunk unload without duplication or regression.
- [ ] **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.

### Campaign

- [ ] **CAMP-01**: A new player can discover, craft, and use the Cursed Unpaid Internship Contract without consulting an external wiki or using an admin command.
- [ ] **CAMP-02**: Starting the Contract validates a player-selected overworld arena, creates a nearby retry checkpoint, and leaves blocks undamaged by default.
- [ ] **CAMP-03**: The player progresses in one visible sequence through Lecture, Jury, Chairman, and Codex Overdraft using recoverable chapter artifacts and current-objective text.
- [ ] **CAMP-04**: Death, escape, timeout, abort, or reload cleans encounter-owned hazards, helpers, entities, and boss bars before offering a safe Retake Form or retry action.
- [ ] **CAMP-05**: Each first-time boss completion records its checkpoint and grants its campaign artifact and practical reward exactly once, with recovery if a physical artifact is lost.
- [ ] **CAMP-06**: Earning the Definitely Legitimate Diploma unlocks boss replay without making repeat rewards a new progression gate.

### Lecture Boss

- [ ] **LECT-01**: The player can defeat Professor Infinite Slides by learning three readable acts: Slide Deck safe lanes, deterministic Surprise Quiz pads, and Attendance Check positioning.
- [ ] **LECT-02**: Defeating Professor Infinite Slides grants the Attendance Sheet and a cooldown-limited Infinite Slides Remote after a fight that supports failure, cleanup, retry, and mid-campaign persistence.

### Jury Gauntlet

- [ ] **JURY-01**: The player can clear one orchestrated Hostile Jury encounter by resolving Citation Needed evidence, Scope Creep boundaries, and the chained “But Why?” counter mechanic.
- [ ] **JURY-02**: Completing the Jury grants Signed Defense Minutes and an Evidence Binder after shared state, helpers, and shields clean up correctly on victory or failure.

### Chairman Boss

- [ ] **CHAIR-01**: The player can defeat Prof. Dr. Rejectus Maximus through Rubric Shield nodes, a bounded Minor Revisions recovery phase, and the Major Revisions acceptance-pad finale.
- [ ] **CHAIR-02**: Completing the Chairman encounter grants the Approved Revision Stamp and reusable Red Pen without deleting inventory, resetting earlier chapters, or irreversibly modifying the arena.

### Codex Finale

- [ ] **CODEX-01**: Inserting the Approved Revision Stamp into the terminal presents a skippable offline sponsor celebration in which the radiant Rich ChatGPT visibly exhausts a fictional token meter and transforms into Codex Overdraft.
- [ ] **CODEX-02**: The player can defeat Codex Overdraft through three readable acts inspired by tool queues, labelled multi-agent roles, and Context Overflow/MAX Reasoning rather than through copied branding or live AI.
- [ ] **CODEX-03**: Defeating Codex Overdraft grants the Definitely Legitimate Diploma and permanent access to the terminal's local showcase and Boss Replay page.

### Module Independence

- [ ] **MOD-01**: Graduation% AnyFAIL and the seven sandbox modules can each be enabled or disabled independently, while all-off, all-on, and one-at-a-time configurations load safely across restart and cannot block the core campaign.

### Metadata Roulette

- [ ] **META-01**: The player can opt in to apply at least six announced Trait Cards—including Teleporter and Oinker—to compatible vanilla mobs through an allowlist that excludes bosses, pets, named mobs, villagers, mounts, and modded entities by default.
- [ ] **META-02**: Trait Cards are bounded, reversible, deterministic enough to debug, safe across save/reload and entity unload, and removable through namespaced cleanup without copying raw tracked metadata, brains, inventories, passengers, or NBT blobs.

### Python Tools

- [ ] **PYTH-01**: The player can spend XP with a pip Wand to install one of four temporary package effects, see an incompatible install create a visible Dependency Conflict, and clear or isolate it with a venv Flask.
- [ ] **PYTH-02**: The Python Pickaxe can mine a strictly capped connected ore set before entering a visible RecursionError cooldown, without running Python code or causing unbounded block traversal.

### Codex Rich Kid Terminal

- [ ] **TERM-01**: The player can use a compact terminal interface to open Sponsor Feed, Run Agents, and Boss Replay, with three prewritten labelled agents processing deterministic fake tasks against a fictional token meter.
- [ ] **TERM-02**: Terminal runs remain completely local, record a humorous readable log, grant only bounded in-game rewards, and never access an API, account, shell, browser, source file, or network service.

### Git Happens

- [ ] **GIT-01**: The player can create one Commit Anchor containing safe position, health, and hunger state, then consume it to Revert through validated teleport and restoration.
- [ ] **GIT-02**: Revert never snapshots inventory, blocks, entities, dimensions, or files, and produces one bounded Merge Conflict hostile plus a cooldown rather than a duplication path.

### Stack Overflow Totem

- [ ] **TOTM-01**: Holding a Stack Overflow Totem in the offhand prevents otherwise lethal damage, consumes the item, and clearly displays the selected answer outcome.
- [ ] **TOTM-02**: Accepted, Deprecated, Duplicate, and Wrong Language outcomes all save the player while applying distinct bounded side effects without opening or querying the internet.

### Rubber Duck Engineering

- [ ] **DUCK-01**: Crouch-using the Rubber Duck identifies a recent damage source or nearby hostile, or reveals the active boss's next weak point and attack name through the shared hint interface.
- [ ] **DUCK-02**: Acting on a duck hint can grant a short Focus effect, while prewritten quips and a cooldown prevent spam and require no microphone, speech recognition, or chat analysis.

### Three-Day Deadline

- [ ] **DEAD-01**: The player can manually start one 180-second sprint containing three visible bounded kill, mine, or craft objectives under a Deadline bar.
- [ ] **DEAD-02**: Completing a sprint grants Shipped and a Debug Fragment, while failure creates only one bounded Compiler Error and short Technical Debt effect; no deadline advances while logged out or follows a real-world clock.

### Accessibility and Readability

- [ ] **ACC-01**: Every harmful boss pattern communicates its attack name, affected shape/area or icon, distinct sound/caption, wind-up, and recovery without relying on color alone.
- [ ] **ACC-02**: Story, Relaxed, Standard, and Intense presets can tune damage, add count, hint strength, and telegraph time without removing campaign jokes or rewards.
- [ ] **ACC-03**: Reduced-effects and reduced-flashing controls remove decorative intensity while preserving hazard geometry; v1 uses no forced camera shake or rapid full-screen strobe.
- [ ] **ACC-04**: All critical dialogue and non-speech cues have readable on-screen text, and the player can reopen the current objective and recent important lines from the journal or terminal.

### Offline Satire and Assets

- [ ] **OFFL-01**: All dialogue, objectives, fake agent activity, token accounting, and boss behavior are finite local content that works with networking disabled.
- [ ] **OFFL-02**: Public defaults use fictional creator, sponsor, university, lecturer, company, and budget strings, with optional personal values remaining local and excluded from Git.
- [ ] **OFFL-03**: In-game credits identify the project as an offline fictional parody with no OpenAI API connection, official sponsorship, endorsement, or factual price/company-spend assertion.
- [ ] **OFFL-04**: Shipped textures, sounds, fonts, and other assets are original or license-compatible, record provenance, and use an original solar-white/gold/teal motif rather than copied OpenAI logos or proprietary UI.

### Release and Showcase

- [ ] **REL-01**: The repository README explains the pitch, exact compatibility tuple, prerequisites, install/build steps, campaign start, module table, configuration, troubleshooting, and known limitations.
- [ ] **REL-02**: The repository includes a detectable source license, dependency credits, generated-asset disclosure, and an asset-provenance record.
- [ ] **REL-03**: A clean production JAR is smoke-tested in a fresh Fabric 26.2 profile and dedicated server, then published with release notes, dependencies, compatibility warning, and checksum.
- [ ] **REL-04**: A network-disabled fresh-profile run can progress from Contract to Diploma, and the final boss plus enabled modules can survive a timed soak without unbounded errors, entities, particles, packets, or tick cost.
- [ ] **REL-05**: The GitHub showcase includes at least one clear image of the radiant sponsor/final boss and one campaign-or-module montage made with fictional public-safe defaults.

## v2 Requirements

### Expanded World and Presentation

- **WORLD-01**: Player can enter a custom university dimension or generated campus with campaign-specific structures.
- **PRES-01**: Bosses can use bespoke skeletal models, advanced animations, cinematics, voice acting, and a larger original soundtrack.
- **LOC-01**: Players can select additional complete language packs beyond the initial language file.

### Expanded Systems

- **MULTI-01**: Multiple players can complete and replay the campaign with production-grade synchronization and encounter balancing.
- **MODX-01**: Each anthology module can provide additional traits, items, outcomes, objectives, dialogue, and replay modifiers after its signature loop is validated.
- **COMP-01**: Metadata Roulette can support explicitly tested modded entities and broader modpack compatibility adapters.
- **CONF-01**: Player can edit the complete configuration through a custom in-game GUI.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Live ChatGPT/Codex or another AI service | Breaks offline use and adds API keys, cost, accounts, and network failure modes |
| Real shell, browser, Git, Python, or source-file execution | Security and portability risk with no need for the gameplay joke |
| Arbitrary raw metadata, Brain, DataTracker, inventory, passenger, or NBT interchange | Incompatible schemas can crash entities or corrupt worlds |
| Real people, institutions, employers, or private budget claims in public defaults | Privacy, harassment, accuracy, and unwanted disclosure risk |
| Minecraft 26.3 snapshots | Moving APIs and data formats are unsuitable for the sprint and tested release target |
| Eight separately packaged JARs | Duplicates setup, configuration, testing, and release work |
| Full inventory, entity, dimension, or world rollback for Git Revert | Creates duplication, corruption, and compatibility hazards |
| Procedural natural-language exams or AI-generated dialogue at runtime | Makes outcomes unclear and reintroduces the prohibited online dependency |

## Definition of Done

- Every v1 requirement is implemented, verified, and mapped to exactly one roadmap phase.
- Automated tests, production client/server smoke checks, save/reload recovery checks, and the network-disabled Contract-to-Diploma run pass.
- The tested production JAR, documentation, credits, provenance, and showcase media are present in the GitHub-ready repository.
- No secret, real personal/employer identity, API key, cache, test world, or private configuration is committed.

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|

**Coverage:**
- v1 requirements: 50 total
- Mapped to phases: 0
- Unmapped: 50 ⚠️

---
*Requirements defined: 2026-08-25*
*Last updated: 2026-08-25 after initial definition*
