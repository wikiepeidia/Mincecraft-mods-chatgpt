# Feature Landscape

**Domain:** Minecraft Java comedy anthology mod with a chaptered boss campaign
**Project:** Developer's Hell
**Researched:** 2026-08-25
**Sprint constraint:** One installable Fabric 26.2 JAR in 1–2 implementation days
**Overall confidence:** MEDIUM — project constraints are direct; feature recommendations are design synthesis grounded in official accessibility, OpenAI, and GitHub documentation

## Product Rule: A Joke Must Change Play

Developer's Hell should use a strict mechanic-first comedy test:

> setup the joke visibly → make the player choose or react → produce a mechanical consequence → land the punchline → give progress or a useful reward

A renamed sword called “Python” fails this test. A Python Pickaxe that performs capped recursive vein mining, then visibly throws a `RecursionError` and enters cooldown, passes it. Every v1 boss phase and optional module below has a trigger, readable state, player response, consequence, and completion condition.

For this sprint, “substantial boss encounter” means all of the following, not a bespoke model or huge health pool:

- A distinct entrance, boss bar, name, and chapter objective.
- Three encounter acts or phases, with at least two attacks unique to that boss.
- Every dangerous attack has a named text cue plus a visual shape/area and a distinct sound; color is never the only signal.
- At least one decision, weak-point, positioning, or interruption mechanic beyond ordinary melee damage.
- A clear fail/retry path, a persistent chapter checkpoint, a unique progression reward, and a 3–6 minute Standard-difficulty target.
- A replay path after campaign completion.

That definition lets four fights feel authored while sharing one encounter controller, vanilla-compatible entity rigs, projectiles, particles, sounds, and arena rules.

## Table Stakes

Features users need for the anthology to feel like a playable mod instead of a content dump.

| Feature | Why Expected | Complexity | V1 Contract |
|---------|--------------|------------|-------------|
| One-JAR, offline installation | The creator must keep playing after temporary AI access ends | Low | A pinned Fabric 26.2 build; no account, API key, telemetry, or network request |
| Discoverable campaign start | Players should not need wiki archaeology or commands to find the game | Low | Craft/use the Cursed Unpaid Internship Contract; advancement and journal text identify the next step |
| Persistent campaign state | Four fights need coherent progression and safe retries | Medium | Store current chapter and completed bosses per world/player; death resets only the active encounter, not the campaign |
| Four mechanically distinct encounters | This is the central promise | High once, then Medium per boss | Shared phase/telegraph/reward framework; fixed authored attacks for Lecture, Jury, Chairman, and Codex Overdraft |
| Readable combat language | Comedy is not funny when deaths feel arbitrary | Medium | Boss bar, phase label, attack title, visible target area/silhouette, audio cue, and generous wind-up for every lethal pattern |
| Retry and recovery | A lost quest item or death must not brick a world | Low | Each chapter reward is recoverable; a Retake Form/replay command resummons the current encounter after cleanup |
| Useful rewards | Progress must change play, not only rename trophies | Medium | Each boss drops a campaign key plus one practical tool/ability; the Diploma unlocks boss replay |
| Eight independent module toggles | One anthology JAR must still behave like several optional mods | Medium | Content stays registered for save safety, while configuration gates triggers and interactions; disabled content reports “module disabled” instead of corrupting saves |
| Safe-world defaults | Installing a joke mod should not immediately destroy a survival world | Low | Campaign and scheduled chaos require an item/command; boss block damage is off; random roulette/deadline events are manual by default |
| Difficulty and effects controls | Four bosses should be finishable by players who want the story and jokes | Medium | Four named presets plus telegraph timing, boss damage, dialogue duration, particle density, and flashing controls |
| Offline dialogue and objective history | Narration must remain understandable without live generation | Low | Prewritten localized strings/data; recent dialogue and current objective can be reopened in a journal/terminal |
| Configuration and personalization | Creator/company satire must be local and public-safe | Low | Fictional defaults; optional creator, sponsor, university, and company display strings; no secret-bearing output |
| Credits and asset provenance | Public release needs truthful attribution | Low | In-game Credits entry plus repository credits/license file covering code, generated art, fonts/sounds, and third-party dependencies |
| Reproducible build and release artifact | “Source on GitHub” is incomplete if nobody can install it | Low | Gradle wrapper, exact build/install instructions, tested JAR, version/compatibility statement, and known limitations |

## Differentiators

Features that make this project memorable rather than another novelty-item pack.

| Feature | Value Proposition | Complexity | Scope Discipline |
|---------|-------------------|------------|------------------|
| Bureaucracy as combat rules | Attendance, citations, rubrics, revisions, and graduation become decisions and hazards | High | Four fixed encounters, no general quest engine |
| Ally-to-final-boss arc | The helpful Rich ChatGPT makes the betrayal land emotionally and mechanically | High | One deterministic offline story branch; no conversational AI |
| GPT-5.6 Sol-inspired “solar” spectacle | A radiant sponsor with orbiting tool runes, reasoning phases, and agent clones makes the Codex showcase visible in play | Medium | Original solar-white/gold/teal art and effects; no copied logo/UI and no endorsement claim |
| Safe Metadata Roulette | Delivers pig/Enderman-style absurdity without raw tracked-data corruption | Medium | Curated trait cards and eligible-entity allowlists, not arbitrary schema exchange |
| Developer tools with tradeoffs | Python, Git, Stack Overflow, and rubber-duck jokes produce useful powers plus recognizable failure modes | Medium | One signature loop per module in v1 |
| Fake offline agent terminal | Shows “Codex spending rich-boy tokens” after the real credit window ends | Medium | Finite prewritten agent feed and fictional token meter; zero networking |
| Replayable chaos after graduation | Campaign rewards continue to matter in an ordinary survival world | Medium | Boss selector and opt-in module events, not a new dimension/endgame progression tree |
| Fictionalized personal satire | The creator can insert private shout-outs locally without exposing real people in source | Low | Public defaults remain fictional and costs/company claims are explicitly satire |

## Bounded Campaign Loop

### Campaign spine

1. Craft or receive the **Cursed Unpaid Internship Contract**.
2. Place/use it in a clear overworld area; the mod validates space, marks a non-destructive encounter boundary, sets a nearby retry checkpoint, and starts the lecture chapter.
3. Defeat each encounter to receive a visible chapter artifact:
   `Signed Contract → Attendance Sheet → Defense Minutes → Approved Revision Stamp → Definitely Legitimate Diploma`.
4. On death or leaving the boundary, clean up encounter-only entities and offer a Retake Form. Completed chapter artifacts remain recorded even if the physical item is lost.
5. The Diploma unlocks the terminal's **Boss Replay** page and optional modifiers. Replays give cosmetic/trophy rewards, not additional campaign gates.

The campaign does not require a custom university dimension. The player chooses the arena location, bosses cannot grief blocks by default, and all fight geometry is communicated with temporary particles/display markers or disposable encounter entities.

### Encounter 1 — Professor Infinite Slides, the Lecture Boss

**Trigger and target length:** Sign the Contract at the Internship Desk; 3–4 minutes on Standard.

| Act | Observable Mechanic | Player Response | Failure/Punchline |
|-----|---------------------|-----------------|-------------------|
| 1. Slide Deck | The professor announces `NEXT SLIDE`; wide rectangular “slide” lanes sweep across the arena after floor outlines and a page-turn sound | Move through the one unfilled lane, then attack during the projector cooldown | A hit applies `Information Overload` and briefly fills the screen edge with unreadable bullet points, without hiding the telegraph |
| 2. Surprise Quiz | Three answer pads appear with both a glyph and a short label; the professor posts one deterministic absurd question | Stand on the matching pad before time expires | Wrong/no answer summons one bounded Homework add and gives `Needs More Reading`; the correct pad exposes the boss |
| 3. Attendance Check | A roll-call cue marks one attendance circle with an icon, outline, and bell | Enter the circle, then punish the professor while the Attendance beam is disabled | Missing roll call applies one `Absent` stack; the third stack triggers detention damage but never instant death |

**Completion:** Reduce the boss after all three acts; receive the **Attendance Sheet** and **Infinite Slides Remote**, a cooldown-limited item that projects a short knockback wall.

**V1 cut:** Three authored quiz prompts can rotate. More questions, custom presentation screens, voice acting, and bespoke lecture-hall world generation are deferred.

**Complexity:** High for the first fight because it proves the shared phase, cue, cleanup, and checkpoint systems; later encounters reuse them.

### Encounter 2 — Hostile Jury Gauntlet

**Trigger and target length:** Submit the Attendance Sheet at the Desk; 4–5 minutes.

The Jury is one orchestrated boss encounter with a shared **Confidence** bar and three sequential jurors, not three full custom bosses.

| Juror Round | Observable Mechanic | Player Response | Failure/Punchline |
|-------------|---------------------|-----------------|-------------------|
| Citation Needed | Footnote wisps drop three Evidence pages while the juror is shielded | Collect and throw Evidence at the lectern to break the shield | Unsupported attacks ricochet as `citation needed` paper cuts |
| Scope Creep | A clearly outlined Scope boundary changes size after a warning | Stay inside the labelled boundary and knock spawned Requirements back out | Leaving scope increases Requirements and temporarily enlarges the juror |
| “But Why?” | Three slow `WHY?` projectiles arrive in a readable chain | Block/dodge all three; the final parry opens the juror's weak point | Each hit adds another louder `WHY?` and slows the next counterattack window, not an unavoidable stun-lock |

**Completion:** Empty Confidence and defeat the final juror; receive the **Signed Defense Minutes** and an **Evidence Binder** that briefly weakens one shielded hostile.

**V1 cut:** One signature rule per juror, one arena, shared health/state. Dynamic natural-language questions, many juror personalities, and actual presentation judging are deferred.

**Complexity:** Medium after the encounter framework exists.

### Encounter 3 — Prof. Dr. Rejectus Maximus, Chairman Boss

**Trigger and target length:** Submit Defense Minutes; 4–6 minutes.

| Act | Observable Mechanic | Player Response | Failure/Punchline |
|-----|---------------------|-----------------|-------------------|
| 1. Rubric Shield | Three rubric nodes labelled `Method`, `Evidence`, and `Formatting` orbit the Chairman; only the currently highlighted node is vulnerable | Dodge red-pen beams and break each highlighted criterion | Striking the wrong node stamps `Does Not Meet Rubric` and pushes the player away |
| 2. Minor Revisions | At apparent defeat the boss stamps `MINOR REVISIONS`, restores a bounded portion of health, and sends expanding redline waves with obvious gaps | Cross gaps and destroy Revision Notes before they reach the boss and heal it | Every healed note adds another comically tiny requested change |
| 3. Major Revisions | Large `REJECT` stamps mark impact zones while one pad is labelled `ACCEPTED WITH CHANGES` by text plus a checkmark | Move to the safe pad, survive the stamp, then strike the exposed Approval Seal | Missing the pad applies `One More Semester`; it damages/slows but does not delete inventory or progression |

**Completion:** Break the Approval Seal; receive the **Approved Revision Stamp**, which unlocks the sponsor's final celebration, and a reusable **Red Pen** that marks one mob for bonus damage.

**V1 cut:** Fixed rubric order and authored attack combinations. Procedural thesis topics, destructible campus scenery, and additional chairman forms are deferred.

**Complexity:** Medium after shared shields, waves, and temporary markers exist.

### Encounter 4 — The Rich ChatGPT → Codex Overdraft: The 300K-Token Abomination

**Trigger and target length:** Insert the Approved Revision Stamp into the Rich Kid Terminal. A short, skippable offline celebration drains a clearly fictional sponsorship meter to zero; 5–6 minute final fight.

**Friendly form:** The Rich ChatGPT is a radiant solar-white/gold/teal figure with a halo, glowing outline, orbiting tool runes, and prewritten sponsor dialogue. It gives the player one **Sponsored Patch** heal before transforming. The art should evoke “Sol” through an original solar motif, not copy OpenAI marks or imply endorsement.

| Act | Observable Mechanic | Player Response | Failure/Punchline |
|-----|---------------------|-----------------|-------------------|
| 1. Programmatic Tool Calling | The boss schedules a visible queue of three tool calls such as `SHELL`, `SEARCH`, and `APPLY PATCH`; the active call_id crystal and target area share a glyph | Read the queue, dodge the attack, then break the matching call_id crystal to interrupt | Breaking the wrong call “returns an error” and launches a bounded error projectile |
| 2. Multi-Agent Review | Three labelled clones appear: Builder repairs, Reviewer shields, Executor attacks; their role is shown by icon, shape, and name | Prioritize Builder/Reviewer, then damage the original while the team is desynchronized | Ignoring roles lets the team complete a fake pull request and restore a capped shield |
| 3. Context Overflow / MAX Reasoning | A token ring contracts while Cache Fragments spawn; depositing fragments at the terminal opens the core, followed by one long telegraphed solar beam | Gather/deposit enough fragments, use cover lanes, and strike the exposed core | Overflow triggers `COMPACTION`, clearing adds and dealing survivable damage rather than wiping the arena |

**Completion:** Defeat the exposed core; receive the **Definitely Legitimate Diploma** and permanent terminal access to Boss Replay and the offline agent showcase.

**Satire boundary:** “300K tokens,” invoices, and any company budget lines are fictional game resources/jokes. V1 does not display a real USD conversion, assert a real employer spend, query current prices, or make any network request. Official OpenAI documentation currently describes GPT-5.6 Sol as the frontier/flagship tier for complex professional work and highlights tool calling, multi-agent coordination, and multiple reasoning levels; those ideas inspire the phase names only.

**V1 cut:** Three acts, three clone roles, one prewritten transformation. Conversational AI, arbitrary tool execution, generated dialogue, full cinematics, and a custom animated model are deferred.

**Complexity:** High; this is the polish target and showcase encounter.

## Eight Toggleable Modules: Exact V1 Loops

Each module must be independently useful. No module may require another optional module to be enabled; shared items have vanilla or campaign acquisition fallbacks.

| Module | Trigger → Play → Consequence → Reward | V1 Content Boundary | Complexity | Dependencies |
|--------|---------------------------------------|---------------------|------------|--------------|
| **Graduation% AnyFAIL** | Use Contract → complete four checkpointed encounters → failures produce Retake Forms and a visible attempt counter → earn Diploma and replay | The campaign above; one fixed route, one arena at a time, no branching story | High | Config, campaign state, encounter controller, dialogue/cues |
| **Metadata Roulette** | Manually use the Roulette Remote or enable a timed interval → one eligible loaded mob receives a visible curated Trait Card such as Teleporter, Oinker, Intern, Lecturer, Giant, or Coward → adapt to the changed behavior → defeat it for a Debug Fragment | At least six safe trait cards; bounded active count; bosses, pets, named mobs, villagers, mounts, and modded entities excluded by default; never exchange raw tracked metadata | Medium | Trait-card interface, entity allowlist, persistence/cleanup, config |
| **Python Tools** | Spend XP with the `pip` Wand to install one temporary “package” buff → incompatible second package spawns a visible Dependency Conflict → use a `venv` Flask to isolate/clear it; Python Pickaxe performs capped connected-ore mining then throws `RecursionError` cooldown | Three items, four package effects, one conflict type, strict block-count cap; no embedded Python runtime or script execution | Medium | Items/effects, cooldowns, safe block traversal |
| **Codex Rich Kid Terminal** | Place/use terminal → select Sponsor Feed, Run Agents, or Boss Replay → three prewritten agents visibly process a fake task while a fictional token meter falls → completion grants a small Debug Fragment/temporary buff and records a humorous log | One compact screen or chat-driven menu, finite local dialogue pool, deterministic fake progress; also owns final-boss trigger/replay | Medium–High | Local data, GUI/menu, campaign state, final encounter |
| **Git Happens** | Use Commit Anchor to save position/health/hunger → later Revert to that state → the commit is consumed and a Merge Conflict duplicate of one nearby hostile appears → resolve it for a cooldown reduction | Never snapshot inventory, blocks, entities, dimensions, or files; one commit slot per player and long cooldown prevents duplication/exploits | Medium | Player state serialization, safe teleport validation, duplicate allowlist |
| **Stack Overflow Totem** | Hold in offhand → lethal damage consumes it and always prevents death → draw one of four clearly named “answers” with a safe side effect (`Accepted`, `Deprecated`, `Duplicate`, `Wrong Language`) → survive and see the answer toast | Four outcomes; every outcome saves the player, while downside severity varies; no internet/browser content | Low | Damage callback, status effects, localization |
| **Rubber Duck Engineering** | Crouch-use/hold the Duck for two seconds → the player “explains” the current problem → duck marks the recent damage source/nearest hostile, or reveals the next boss weak point and attack name → gain a short Focus buff after acting on the hint | One item, target detection, boss hint hook, prewritten quips, cooldown; no speech recognition or chat analysis | Low–Medium | Target query, encounter hint interface, outline/effect |
| **Three-Day Deadline** | Manually start a 180-second sprint → complete three tasks drawn from bounded kill/mine/craft templates shown under a Deadline bar → finish to gain `Shipped` and a Debug Fragment; fail to spawn one Compiler Error and short Technical Debt | Manual by default; one active deadline; safe task allowlist; time multiplier/off switch; no real-world clock, background service, or punishment while logged out | Medium | Objective tracker, boss-bar UI, reward/error spawn, config |

### Safe Metadata Roulette contract

The funny pig/Enderman case should be authored as compatible traits, for example:

- **Pig + Teleporter:** on being hurt, attempts a bounded Enderman-like teleport and emits a teleport cue.
- **Enderman + Oinker:** becomes non-hostile until attacked, uses pig sounds, and flees with a speed cap.

Traits are behavioral adapters owned by the mod. They do not copy entity data tracker indices, brain memories, passengers, inventories, NBT blobs, dimensions, or renderer/model metadata. The action bar announces `Pig rolled TELEPORTER` so the change is observable rather than mysterious.

## Configuration, Safety, and Replay

| Setting/Control | Recommended Default | V1 Behavior |
|-----------------|---------------------|-------------|
| `campaign.enabled` | `true` | Registers and enables Contract progression |
| Eight `modules.<name>.enabled` flags | `true` | Items remain safe in saves; disabling stops triggers/use effects and explains why |
| `metadata_roulette.mode` | `manual` | No random mutation until player opts in; optional interval and active-entity cap |
| `three_day_deadline.mode` | `manual` | Never starts a deadline merely because time passes |
| `difficulty` | `standard` | `story`, `relaxed`, `standard`, and `intense` tune damage, add count, and telegraph windows |
| `telegraph_time_multiplier` | `1.0` | May be increased independently of enemy health/damage |
| `dialogue_duration` and `objective_history` | Long / enabled | Critical text remains available in journal/terminal; no forced rapid auto-advance |
| `reduced_effects` | `false` | Reduces particle count and removes nonessential motion; essential hazard outlines remain |
| `reduced_flashing` | `true` | Avoids rapid/full-screen flashes; no lethal signal depends on flashing |
| `boss_block_damage` | `false` | Encounters do not grief the chosen arena by default |
| `creator`, `sponsor`, `university`, `company` | Fictional placeholders | Optional local display strings only; public source never assumes real names |
| `satire_budget_line` | Clearly fictional copy | No verified company/pricing assertion and no automatic currency calculation |

Runtime gating should be save-safe: registered items/entities are not dynamically removed. Turning a module off stops scheduled events and causes its active item to return a localized disabled message. Configuration validation should reject impossible intervals/caps early and log the effective values without logging personal data.

Required recovery commands are narrow and documented:

- `/devhell status` — current chapter, active encounter, and module states.
- `/devhell retry` — clean and restart only the current chapter.
- `/devhell replay <boss>` — requires Diploma unless cheats/admin override.
- `/devhell module <name> status` — reports effective configuration; mutations can remain config-file-only if an in-game editor threatens the sprint.

## Accessibility and Readability Requirements

These are v1 gameplay requirements, not post-launch polish:

- **Redundant cues:** Every harmful boss pattern uses at least three channels: a short attack name, a visual area/silhouette/icon, and a distinct sound. Color always has a glyph, shape, text label, or motion-pattern counterpart.
- **Captions and dialogue:** All spoken/announcer content exists as on-screen text with speaker identity. Important non-speech sounds have text equivalents such as `[projector charging]`. The journal/terminal preserves the current objective and recent critical lines.
- **Legible overlays:** Prefer Minecraft's existing font and UI scaling. Put attack names away from boss bars/objectives, use shadow/opaque backing where supported, and never cover the crosshair or safe-zone marker with a joke paragraph.
- **Timing:** Story/Relaxed modes lengthen attack wind-ups and quiz/dialogue windows. Narrative text can be advanced manually or reread; it does not vanish before the player can reasonably read it.
- **Difficulty without mockery:** Presets use descriptive names (`Story`, `Relaxed`, `Standard`, `Intense`) and can change at any time. Boss damage, add count, telegraph time, and hint strength are independently tunable where practical.
- **Effects safety:** V1 has no forced camera shake. Avoid rapid full-screen flashes and saturated-red strobing; reduced-effects mode removes decorative particles while retaining hazard geometry.
- **Failure fairness:** No instant inventory deletion, irreversible world edits, long runback, or secret one-hit joke. Each encounter has a nearby retry and persistent chapter checkpoint.
- **Opt-in chaos:** Scheduled roulette and deadlines default to manual. A player must deliberately activate world-chaos systems.

Official Xbox accessibility guidance supports multiple sensory channels for critical cues, captions for important dialogue/sounds, non-color signifiers, adjustable difficulty components, controllable text timing, reduced motion, and photosensitivity-safe effects. Full platform-grade accessibility is beyond a two-day mod sprint, but the requirements above capture the highest-value barriers in this combat-heavy scope.

## Credits, Installability, and GitHub Showcase

V1 is not complete until another person can understand and install it without this chat history.

### In-game

- Terminal/Credits page names the creator, configurable fictional sponsor/company/university, code assistant contribution, asset provenance, licenses, and the statement: **“Offline fictional parody; no OpenAI API connection or endorsement.”**
- “300K tokens” is identified as a fictional in-game sponsorship meter. Any `$15 million / 3,000 employees` line is optional satire text, not a verified factual claim.
- The Rich ChatGPT uses original solar-themed art/effects. Do not copy OpenAI logos, proprietary UI, or imply that GPT-5.6 Sol itself is executing inside Minecraft.

### Repository and release

- Root README: one-sentence pitch, compatibility matrix, Fabric/Fabric API prerequisites, exact install steps, campaign start, module table, config location/example, build command, troubleshooting, and known limitations.
- Showcase media: at minimum one clean screenshot of the solar sponsor/final boss and one campaign/module montage image or short GIF; use an isolated test world with fictional defaults.
- Build: committed Gradle wrapper, pinned versions, clean-build command, and artifact path. A fresh checkout must build without downloading secrets or private files.
- Release: tagged version with the tested JAR, release notes, Minecraft/Fabric compatibility, dependency list, checksum if cheap, and a warning that 26.3 snapshots are unsupported.
- Licensing: detectable source license plus credits/provenance for every generated or third-party asset and dependency. Generated assets should be identified honestly; only original or license-compatible material ships.
- Hygiene: no API keys, local personalization, employer identity, caches, test-world saves, or generated secret-bearing configuration in Git.

GitHub's official documentation recommends a README that explains purpose, usefulness, getting started, support, and maintainers; a detectable license clarifies reuse; Releases package notes and binary assets. Those items are therefore release criteria, not optional community polish.

## Anti-Features

Features to explicitly not build during this milestone.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Live ChatGPT/Codex/API integration | Breaks offline use, introduces keys/cost/network failure, and consumes the sprint | Finite local dialogue, deterministic agent feed, fictional token counter |
| Eight separately packaged JARs | Duplicates setup, config, testing, and release work | One JAR with independent behavior gates |
| Arbitrary raw metadata/NBT/brain swapping | Incompatible schemas can crash or corrupt entities/worlds | Curated, typed Trait Cards on an allowlist |
| A custom campus dimension or procedural university | World generation would consume the deadline without improving combat | Player-chosen overworld arena and temporary non-grief markers |
| Bespoke model, animation set, and soundtrack for every boss | Asset/render/audio pipelines threaten campaign completion | Reuse vanilla-compatible rigs and sounds; prioritize four readable skins/icons and the solar finale |
| Dynamic natural-language exams or thesis judging | Requires content generation and creates unclear/unfair answers | Small authored prompt set with deterministic icons/labels |
| Real file-system Git, shell, Python, browser, or code execution | Serious security and portability risk; not needed for the joke | Minecraft-only state machines that parody commands |
| Inventory/world rollback for Git Revert | Creates duplication, corruption, and cross-mod compatibility risk | Restore only safe player state/position with validation and a consumed checkpoint |
| Always-on random punishment | Makes ordinary survival saves exhausting and hides cause/effect | Manual/default-off scheduling, announcements, caps, cooldowns |
| Real lecturers, jurors, school, employer, or coworker names by default | Privacy, harassment, and public-release risk | Fictional defaults plus optional local strings |
| Real USD/token/company-cost claims | Prices and private budget claims can be wrong or change | Clearly labelled absurd fictional invoice; no automatic conversion |
| Production multiplayer parity | Synchronization and balancing would consume the sprint | Singleplayer-first; server-safe state ownership where cheap |
| Full custom config GUI | Adds UI/dependency work with little gameplay value | One readable config file plus status/retry commands; terminal UI only where it serves play |
| Huge dialogue corpus before mechanics work | More jokes cannot rescue flat combat | Ship short contextual lines tied to phase transitions, attacks, failures, and rewards |
| Renamed vanilla items with no behavior | Violates the core value | Cut them or attach a bounded mechanical loop |

## Feature Dependencies

```text
Fabric scaffold + registries + saved data
    ├── Configuration validation + module gates ───────────────→ all campaign/modules
    ├── Localized dialogue/objective history ─────────────────→ all bosses + terminal
    ├── Telegraph/effect primitives ──────────────────────────→ encounter controller
    │       └── phase/checkpoint/cleanup/reward controller
    │              ├── Professor Infinite Slides
    │              ├── Jury Gauntlet
    │              ├── Chairman Rejectus
    │              └── Sponsor state → Codex Overdraft → Diploma → replay
    ├── Typed Trait Card interface + entity allowlist ────────→ Metadata Roulette
    ├── Item/effect/cooldown primitives ──────────────────────→ Python, Git, Totem, Duck
    └── Objective tracker + shared boss-bar UI ───────────────→ Three-Day Deadline

Asset/localization pipeline
    ├── boss/item/terminal textures + cue sounds
    ├── in-game credits/provenance
    └── README screenshots + release packaging
```

Optional modules must remain leaves: Metadata Roulette must not be required for Python Tools, the Terminal, or the campaign; disabling any one module cannot block the Diploma.

## Ruthless MVP Recommendation

### P0 — must ship

1. Clean offline Fabric 26.2 launch, configuration validation, module gates, and recovery commands.
2. Shared encounter controller with telegraphs, phases, cleanup, checkpoints, rewards, and difficulty values.
3. All four encounters at the “substantial” definition above. Preserve boss count and unique decisions before adding more attacks.
4. Exactly one complete signature loop for each of the eight modules. Metadata gets six curated traits; Python gets three tools/four buffs; Terminal gets three fake agents; every other module gets the bounded loop above.
5. Diploma-based replay, prewritten offline dialogue/objective history, safe-world defaults, and accessibility controls.
6. Focused original assets: readable boss skins/overlays, progression-item icons, terminal art, and the radiant solar finale. Use vanilla sounds/rigs and procedural particles where adequate.
7. Fresh-checkout build, installable JAR, README, credits/provenance, source license, screenshot/GIF, and known limitations.

### P1 — only after the complete path passes

- Additional dialogue variants and quiz prompts.
- Extra Roulette traits/package buffs/terminal fake tasks.
- One alternate attack per boss and replay modifiers.
- Additional custom sound effects, particles, and item textures.
- Automated release workflow or broader compatibility smoke tests.

### Deferred to a later milestone

- Campus dimension/world generation, quests outside the fixed route, branching endings.
- Custom voice acting, cinematic camera work, bespoke skeletal models/animations.
- Live AI, user-written Python, real shell/Git execution, online leaderboards or telemetry.
- Deep versions of all eight modules, full multiplayer balancing, localization beyond the initial language file.
- Arbitrary modded-entity trait support or cross-mod metadata compatibility.

### Cut order if the sprint slips

Cut in this order: extra jokes → extra art/sounds → extra variants → optional replay modifiers → one nonessential attack from a boss. Do **not** cut recovery/installability, telegraphs, the four encounter completions, the eight signature loops, the offline boundary, or the final Diploma/replay payoff.

The scope is plausible in two focused days only if implementation aggressively reuses one encounter state machine, one cue system, one objective/boss-bar renderer, vanilla-compatible entity bases, and data-driven dialogue/attack tables. Building each boss or module as its own framework will miss the deadline.

## V1 Acceptance Checks

- A fresh Fabric 26.2 client can install the published JAR and reach a world with networking disabled.
- A new player can discover the Contract, complete all four encounters, receive the Diploma, and replay a boss.
- Each encounter visibly demonstrates three acts, two unique attacks, a non-damage decision, readable cues, retry, and a unique reward.
- Each of the eight module toggles can be disabled independently without load failure, missing-registry corruption, or blocked campaign progression.
- Each enabled module's signature loop can be demonstrated in under five minutes with documented items/commands.
- Story difficulty and longer telegraphs allow completion without changing the jokes; critical attacks remain understandable with game audio muted and without relying on color alone.
- Metadata Roulette never applies to excluded entities and never copies raw tracked metadata/NBT/brains.
- Git Revert cannot duplicate inventory or restore world blocks.
- Public defaults contain no real lecturer, school, employer, coworker, private configuration, API key, or factual company-spend assertion.
- README build/install instructions succeed from a fresh checkout; the release has a tested JAR, compatibility notes, credits, licenses, and showcase media.

## Sources

### Project authority

- `.planning/PROJECT.md` — approved concept, scope, constraints, and active requirements. **Confidence: HIGH** (direct project source).

### Official OpenAI documentation

- [GPT-5.6 Sol model page](https://developers.openai.com/api/docs/models/gpt-5.6-sol) — official identity, frontier positioning, reasoning settings, context/tool capabilities. **Confidence: MEDIUM** (direct official page retrieved through web tooling).
- [OpenAI model guidance for GPT-5.6](https://developers.openai.com/api/docs/guides/latest-model) — flagship alias, programmatic tool calling, multi-agent, token-efficiency, and reasoning guidance. **Confidence: MEDIUM** (direct official page retrieved through web tooling).

### Official accessibility guidance

- [Xbox Accessibility Guideline 101: Text display](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/101)
- [Xbox Accessibility Guideline 103: Additional channels for cues](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/103)
- [Xbox Accessibility Guideline 104: Subtitles and captions](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/104)
- [Xbox Accessibility Guideline 108: Game difficulty options](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/108)
- [Xbox Accessibility Guideline 116: Time limits](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/116)
- [Xbox Accessibility Guideline 117: Visual distractions and motion](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/117)
- [Xbox Accessibility Guideline 118: Photosensitivity](https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/118)

Accessibility sources are official Microsoft guidance and were cross-checked across cue, caption, timing, difficulty, motion, and photosensitivity topics. **Confidence: MEDIUM** (official pages, web retrieval; implementation choices are project-specific synthesis).

### Official GitHub documentation

- [About README files](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes)
- [Releasing projects on GitHub](https://docs.github.com/en/repositories/releasing-projects-on-github)
- [Adding a license to a repository](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/adding-a-license-to-a-repository)

GitHub sources support the repository/release criteria. **Confidence: MEDIUM** (official pages retrieved through web tooling).
