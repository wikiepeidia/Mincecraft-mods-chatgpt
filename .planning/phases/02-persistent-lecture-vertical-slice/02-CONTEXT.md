# Phase 2: Persistent Lecture Vertical Slice - Context

**Gathered:** 2026-08-26
**Status:** Ready for planning
**Mode:** Autonomous smart discuss; user previously accepted all recommended choices

<domain>
## Phase Boundary

Deliver the complete, discoverable Contract-to-Lecture vertical slice: safe startup configuration, the craftable Cursed Unpaid Internship Contract, a validated non-destructive overworld arena and retry point, the three-act Professor Infinite Slides fight, save-safe failure and cleanup, and idempotent first-victory rewards. Jury, Chairman, Codex, the wider objective journal, bespoke art, and the six developer-tool sandbox loops remain outside this phase.

</domain>

<decisions>
## Implementation Decisions

### Safe Startup Configuration and Module Gates
- Load one immutable, versioned local configuration at startup without adding a runtime library. Preserve all eight stable `ModuleId` keys and construct the existing immutable `ModuleGate`; registration remains unconditional and toggles control behavior only.
- Campaign play is enabled by default. Metadata Roulette and Three-Day Deadline scheduling remain manual/off, boss block damage remains false, difficulty defaults to Standard, reduced flashing defaults on, and all caps/timers are bounded.
- Validate the complete file before applying it. Report every invalid path, rejected value, and allowed range; fail closed to public-safe defaults rather than partially enabling risky behavior. Do not overwrite an invalid user file.
- Keep all gameplay offline and server-authoritative. Configuration contains no API key, network endpoint, personal/employer identity, or claim about real spending.

### Contract Discovery, Internship Desk, and Arena
- Unlock the Contract recipe through a small local advancement when the player obtains obvious ingredients such as paper and ink. Localized tooltip text explains that it starts Lecture 1 and must be used on a lectern facing clear Overworld ground; commands are recovery/testing tools, not the normal discovery path.
- A lectern is the Internship Desk. Its clicked position and facing establish the player-selected arena origin and local forward/right axes. Validate an approximately 17x17 boundary with a 15x15 combat interior, solid floor, four blocks of headroom, loaded chunks, world-border safety, and the exact Overworld dimension.
- Invalid placement produces a specific localized reason, consumes no Contract, writes no campaign state, and spawns nothing. The fight never edits or destroys blocks by default and does not use block-damaging explosions.
- Find and persist a bounded nearby safe retry position in campaign state without replacing the player's bed or vanilla respawn point. Retake Form interaction at the same Internship Desk safely starts a later attempt when no encounter is active.

### Professor Infinite Slides Fight and Readability
- Use a stable custom boss entity with the simplest compile-proven vanilla renderer/silhouette; no new bitmap, model, OGG, shader, custom HUD, or screen is required in this phase. The entity, attacks, damage, particles, sounds, boss bar, and messages are owned by common/server code; client code is renderer registration only if needed.
- Present the fight through a `ServerBossEvent`, action-bar instructions/countdowns, low-frequency system chat, bounded server particles, and distinct vanilla sounds/subtitles. Every harmful pattern must communicate attack name, text instruction, geometric outline, non-color-only symbol/direction, wind-up, and recovery/damage window.
- Act 1, Slide Deck: split the 15x15 interior into LEFT/CENTER/RIGHT five-block lanes, deterministically choose one safe lane, telegraph for about five seconds with safe outline plus danger X shapes, then resolve server-side position and open a short Projector Cooldown damage window.
- Act 2, Surprise Quiz: show one localized joke prompt and three fixed A/B/C pads using square/circle/diamond identities. Select the correct pad deterministically from encounter seed and quiz index, allow about eight seconds, and make wrong/no answers bounded rather than lethal.
- Act 3, Attendance Check: choose one deterministic quadrant circle, announce FRONT/BACK plus LEFT/RIGHT, ring a bell, allow about six seconds, and track ABSENT 1/3 through 3/3. The third miss may cause bounded detention damage but never an instant kill; success opens the final damage window.
- Reduced-effects mode removes ambient density but retains essential corners/segments, boss/action text, shape identity, and sound. Never use forced camera shake, nausea, full-screen flashes, rapid glow toggling, or strobing.

### Persistence, Cleanup, Retry, and Exactly-Once Rewards
- Store versioned, monotonic per-player campaign progress in logical-server world saved data. Keep chapter state separate from an optional active `EncounterRef`; at most one active encounter exists per player and callbacks must match owner plus encounter UUID.
- Persist the transition before applying world/reward effects. Duplicate, stale, wrong-owner, repeated-death, unload, and rejoin callbacks become idempotent no-ops and cannot regress chapter, attempt count, milestones, entitlements, or issued rewards.
- On save/reload or chunk unload, prefer deterministic cleanup to resuming an in-flight cast: clear the active attempt, discard or reject orphan encounter-owned entities when they load, remove boss bars/hazards, cancel telegraphs, preserve chapter/checkpoint/attempt count/reward ledger, and offer a safe Retake Form.
- Death, escape, timeout, dimension change, disconnect, abort, or server stop first records the failed attempt and clears the active reference, then removes bars and bounded encounter-owned objects. Cleanup-triggered unload events cannot apply a second failure.
- Attendance Sheet is a durable recoverable entitlement once Lecture is passed. Infinite Slides Remote is a practical reward issued only on the first matching victory, never through ordinary entity loot, and has a visible 20-second cooldown. Losing the Sheet allows recovery without duplicating the Remote or progression.
- Automated acceptance must cover immutable config/defaults/errors, reducer transitions and mismatched events, codec round trips/schema handling, deterministic geometry/timers/damage bounds, all failure reasons, reload-to-retake cleanup, orphan rejection, exactly-once victory/rewards, Contract arena lifecycle, block preservation, and Remote cooldown through unit tests plus real Fabric GameTests.

### Claude's Discretion
- Choose the exact vanilla boss renderer/entity superclass and placeholder item model references after compile-checking the Minecraft 26.2 signatures; prefer the smallest side-safe implementation.
- Tune bounded damage, health, particle density, recovery duration, arena search radius, and joke wording while preserving the decisions and readability constraints above.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DevelopersHell.id(...)` is the only namespaced-ID factory.
- `ModItemIds` owns stable `ResourceKey<Item>` identities and an immutable catalog; extend it for Contract, Retake Form, Attendance Sheet, and Infinite Slides Remote.
- `ModItems.register(...)` already follows the Minecraft 26.2 `Item.Properties.setId(key)` registration pattern.
- `ModuleId` and immutable `ModuleGate` already define the eight behavior-only gates.
- `FoundationGameTests` supplies the current `CustomTestMethodInvoker` GameTest pattern; Loader JUnit and the separate `gametest` source set already exist.

### Established Patterns
- Common/client entrypoints are physically separated; no client imports may enter common campaign/entity/config code.
- Stable registries initialize independently of behavior toggles. Resource identity is namespaced and save-safe.
- The direct-dependency audit permits only the exact Phase 1 tuple, so Phase 2 adds no Gson/Jackson/config/UI dependency.
- Current item resources require both `assets/developers_hell/items/<id>.json` and `assets/developers_hell/models/item/<id>.json`; vanilla runtime textures may be referenced without copying them.
- Minecraft 26.2 uses `Identifier`, singular data paths such as `data/developers_hell/recipe/`, `SavedDataType`, `ValueInput`/`ValueOutput`, typed server events, and unobfuscated names rather than Yarn.

### Integration Points
- `DevelopersHell.onInitialize()` wires configuration, stable registries, campaign service, lifecycle adapters, and commands on the logical server side.
- `DevelopersHellClient` remains limited to a vanilla-compatible boss renderer registration if the custom entity requires it.
- New campaign/config/boss/item/registry/server packages connect through the existing entrypoints; no remote service or database is introduced.
- Localized tooltip, objective, telegraph, failure, retry, and reward copy belongs in `assets/developers_hell/lang/en_us.json` rather than hard-coded user-facing text.

</code_context>

<specifics>
## Specific Ideas

- Advancement title: `A Suspicious Opportunity`; description: `Craft the contract. It has absolutely no benefits.`
- Start copy: `Contract signed. The lectern is now your Internship Desk.` and `Objective: Pass Professor Infinite Slides.`
- Boss bars: `Professor Infinite Slides | Act 1/3`, `Act 2/3`, and `Act 3/3`.
- Short action-bar examples: `NEXT SLIDE [5] - SAFE: LEFT`, `SURPRISE QUIZ [8] - PICK A / B / C`, `ATTENDANCE [6] - ENTER FRONT-RIGHT RING`, and `PROJECTOR COOLDOWN - ATTACK!`.
- The accepted paper/map-style placeholder appearance is intentional MVP cosmetic debt, not a blocker.

</specifics>

<deferred>
## Deferred Ideas

- Bespoke professor model/texture, generated art, custom sounds, custom HUD, screen-edge overload overlay, and other cosmetic polish belong in the release/showcase phase unless spare time remains after the complete campaign.
- Hostile Jury, Chairman, Rich ChatGPT/Codex Overdraft, Diploma, objective journal, showcase, and boss replay belong in Phase 3.
- Python tools, fake terminal agents, Git revert, Stack Overflow Totem, Rubber Duck, Deadline mode, and Metadata Roulette remain in Phases 4–5.

</deferred>
