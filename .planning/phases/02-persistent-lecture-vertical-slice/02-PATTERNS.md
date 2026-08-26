# Phase 2: Persistent Lecture Vertical Slice - Pattern Map

**Mapped:** 2026-08-26
**Baseline inspected:** `085513a30bb5`
**Local source baseline:** 23 Phase 1 implementation files plus all four Phase 1 summaries and the final clean review
**Purpose:** Route each likely Phase 2 responsibility to the closest real local analog without inventing conventions that the repository does not yet have

## Executive Finding

Phase 1 established a trustworthy build and side-safety skeleton, not a gameplay architecture. The reusable local patterns are:

- a thin common initializer and physically separate client initializer;
- unconditional stable registry identity through ID catalogs and registry providers;
- explicit snake-case serialized names;
- small immutable Java policy objects with defensive copies and fail-fast null handling;
- package-local JUnit 5 tests with exact invariant assertions;
- a separate Fabric server GameTest source set using `CustomTestMethodInvoker`;
- the Minecraft 26.2 two-JSON item/model resource path;
- exact Java/Fabric dependency and offline-build gates; and
- fail-closed PowerShell conventions for paths, processes, evidence, and cleanup.

There is no local implementation analog for configuration parsing, `SavedData`, codecs, campaign reducers, custom entities, entity attributes, renderers, boss bars, owner-bound items, commands, recipes/advancements, encounter cleanup, or exactly-once rewards. Those are establishment work for Phase 2 and must be tested as new contracts rather than copied from the foundation marker or verification harness.

## Package and Layout Convention

Keep the existing root package `dev.developershell` and extend it by responsibility. Exact class names may be consolidated during planning, but these boundaries should not be collapsed:

| Package/source set | Ownership | Closest local analog | Rule to preserve |
|---|---|---|---|
| `src/main/java/dev/developershell/registry` | Stable item/entity keys and unconditional registration | `ModItemIds`, `ModItems` | Identity exists regardless of config/module state. |
| `src/main/java/dev/developershell/module` | Existing behavior-only module policy | `ModuleId`, `ModuleGate` | Do not add world, registry, filesystem, or client dependencies. |
| `src/main/java/dev/developershell/config` | Immutable config model, validation, local-file adapter | `ModuleGate` for immutable model only | Parsing/I/O is a new adapter pattern; never put it in `ModuleGate`. |
| `src/main/java/dev/developershell/campaign` | Pure chapter/encounter state, events, reducer, reward ledger | `ModuleGate` for purity and explicit serialized values | Keep deterministic state transition logic independent of Minecraft effects. |
| `src/main/java/dev/developershell/campaign/persistence` | Minecraft 26.2 saved-data codec and world adapter | No local analog | Persist accepted transition before applying world/reward effects. |
| `src/main/java/dev/developershell/lecture` | Arena geometry, encounter schedule, attack resolution, cleanup intents | `ModuleGateTest` style for pure geometry/state | Minecraft positions/entities belong at adapter edges, not throughout reducer math. |
| `src/main/java/dev/developershell/item` | Contract, Retake Form, Sheet, and Remote behavior | `ModItems` only for construction/registration | All authoritative use checks and effects run on the logical server. |
| `src/main/java/dev/developershell/entity` | Professor entity and encounter-owned entity identity | No local analog | Entity projects persisted encounter state; it is not the campaign source of truth. |
| `src/main/java/dev/developershell/server` | Lifecycle/event adapters and service composition | `DevelopersHell` first-tick callback is only a minimal event analog | Adapters translate Fabric/vanilla callbacks into one campaign service. |
| `src/main/java/dev/developershell/command` | `/devhell` recovery/testing commands | No local analog | Commands are server-side support surfaces, not normal campaign discovery. |
| `src/client/java/dev/developershell/client` | Renderer registration only | `DevelopersHellClient` | No campaign choice, collision, damage, reward, persistence, or cleanup authority. |
| `src/test/java/dev/developershell/...` | Pure unit tests | `ModuleGateTest` | Prefer no Minecraft bootstrap and no mocking dependency. |
| `src/gametest/java/dev/developershell/gametest` | Real registry/world/lifecycle assertions | `FoundationGameTests` | Keep test code out of the production JAR. |

Current Java style is tabs, K&R braces, explicit imports, `final` implementation classes, private constructors for utility/catalog classes, lower-camel descriptive test methods, and no wildcard imports. There is no established use of records, sealed types, dependency injection framework, annotations for serialization, or a service locator. Prefer simple constructors and immutable collections until a concrete need proves otherwise.

## Likely File-to-Analog Map

### Existing files to extend

| Likely file | Phase 2 responsibility | Closest existing analog | Adaptation rule |
|---|---|---|---|
| `src/main/java/dev/developershell/DevelopersHell.java` | Composition root for registries, config, server lifecycle, campaign service, and commands | Its current `ModItems.initialize()` plus `ServerTickEvents.END_SERVER_TICK` wiring | Keep it thin. Register stable content first, then construct immutable behavior/config and register adapters. Preserve the first-tick marker and do not retain a static `MinecraftServer`, `ServerLevel`, or player. |
| `src/client/java/dev/developershell/client/DevelopersHellClient.java` | Register the Professor renderer if the chosen entity requires one | Current intentionally empty client seam | Client imports remain here or below this source set. Do not move shared entity/state code into client merely to satisfy renderer construction. |
| `src/main/java/dev/developershell/registry/ModItemIds.java` | Add Contract, Retake Form, Attendance Sheet, and Infinite Slides Remote keys | `FOUNDATION_TOKEN` plus immutable `all()` catalog | Use `DevelopersHell.id("lower_snake_case")`, `ResourceKey<Item>`, and one immutable catalog containing every stable item exactly once. |
| `src/main/java/dev/developershell/registry/ModItems.java` | Unconditionally register all four items and custom item subclasses | Existing `register(key, factory, properties)` | Keep registration independent of `ModuleGate` and config. Behavior gates belong inside use callbacks/services, never around static fields or `initialize()`. |
| `src/main/resources/assets/developers_hell/lang/en_us.json` | All tooltips, objectives, placement errors, attacks, failure/retry, rewards, commands, entity name | Existing Foundation Token key | Preserve valid JSON and translation-backed user text. Do not hard-code the UI-SPEC copy in Java. Establish one custom message-key family and use it consistently. |
| `src/gametest/resources/fabric.mod.json` | Discover Phase 2 GameTest class(es) | Existing `developers_hell_test` manifest | Keep `environment: "*"`, server GameTests only, and no client GameTest entrypoint unless build config intentionally changes. Rename the display name from “Foundation Tests” if it becomes misleading. |
| `build.gradle` | Compile added sources/resources and retain test/GameTest gates | Existing split source sets, Loader JUnit, `configureTests`, production tasks | No new repository or dependency. The exact five project declarations and 145-entry Loom remainder must remain unchanged. `build` and explicit `runGameTest` remain required gates. |
| `README.md` | Describe the playable Contract-to-Lecture slice and exact verification commands | Existing player/contributor proof sections | Update claims only after implementation/UAT. Keep Java 25, Fabric 26.2, one ordinary JAR, and online-prime versus Gradle-offline versus runtime-offline distinctions. |

### New common/server modules

| Likely file/module | Closest local analog | Pattern to use | Pattern status |
|---|---|---|---|
| `config/DevelopersHellConfig.java` | `ModuleGate` | Final immutable value object; bounded values; defensive collections; explicit defaults; no I/O | Partial analog; schema/versioning is new. |
| `config/DevelopersHellConfigLoader.java` | PowerShell validators are only conceptual fail-closed analogs | Read one local file, validate the complete document, aggregate path-specific issues, apply all-safe defaults on any invalid document, and never overwrite invalid input | No Java/local-config analog. |
| `campaign/CampaignChapter.java`, `LectureAttemptState.java`, `FailureReason.java` | `ModuleId` | Explicit stable serialized names; never persist `Enum.name()` implicitly | Strong enum analog. |
| `campaign/EncounterRef.java`, `CampaignState.java`, `RewardLedger.java` | `ModuleGate` | Immutable values, owner UUID plus encounter UUID, monotonic chapter/milestones, optional active reference, no world side effects | Partial analog; state schema is new. |
| `campaign/CampaignEvent.java`, `CampaignReducer.java` | No local reducer | Pure transition function. Reject or no-op stale, duplicate, wrong-owner, and wrong-encounter events; return explicit effect intents rather than executing them | New required pattern. |
| `campaign/CampaignService.java` | No local service | Load state, compute transition, persist it, then execute bounded effects. One public entry per callback family prevents reward/cleanup logic from being duplicated across events | New required pattern. |
| `campaign/persistence/CampaignSavedData.java` and codec helpers | No local persistence | Versioned `SavedDataType` plus compile-checked `ValueInput`/`ValueOutput`; decode conservatively; convert in-flight attempts to safe retake on load; never regress progress | New required pattern; exact 26.2 APIs must come from generated sources/research. |
| `lecture/ArenaLayout.java` | `ModuleGate` purity | Immutable origin/facing/local-axis and integer boundary/interior description, with no mutation | Partial pure-value analog. |
| `lecture/ArenaValidator.java` | No world validator | Pure geometry calculations separated from a server-world probe adapter; return a typed rejection reason instead of player-facing English | New required pattern. |
| `lecture/LectureAct.java`, `LectureSchedule.java`, `LectureResolution.java` | `ModuleId` plus `ModuleGateTest` | Deterministic seed/index choices, bounded tick durations/damage, explicit lane/pad/quadrant identities | Partial enum/test analog; scheduling is new. |
| `lecture/LectureEncounterController.java` | No boss controller | Server-authoritative state machine; every timer/attack callback carries owner and encounter UUID; cleanup cancels all pending presentation/effects | New required pattern. |
| `lecture/LecturePresentation.java` | No presentation service | Own one `ServerBossEvent`, action-bar/system messages, bounded particles, and vanilla sounds. Update only on transitions/whole-second changes and remove all presentation on every exit path | New required pattern. |
| `lecture/RetakeService.java` | No entitlement pattern | State-first exactly-one entitlement; inventory-first delivery; one tracked owner-bound fallback; successful retry consumes only after validation/transition succeeds | New required pattern. |
| `item/CursedInternshipContractItem.java` | `ModItems` construction only | Server validates lectern, Overworld, arena, retry point, and inactive state; rejected use consumes/spawns/persists nothing | New item-interaction pattern. |
| `item/RetakeFormItem.java` | No local analog | Bind use to owner, failed attempt, and matching desk; state remains authoritative over item-carried identity | New owner-bound item pattern. |
| `item/AttendanceSheetItem.java` | Foundation Token is only a static-item analog | Durable proof item whose loss permits recovery from persisted entitlement without replaying first rewards | New recovery pattern. |
| `item/InfiniteSlidesRemoteItem.java` | No local active item | Server-owned 400-tick cooldown, bounded effect, native cooldown overlay, persisted remaining ticks, and edge-triggered ready cue | New cooldown/rejoin pattern. |
| `registry/ModEntityIds.java`, `registry/ModEntities.java` | `ModItemIds`, `ModItems` structurally | Dedicated stable key/catalog plus unconditional registration/attributes | Structural analog only; exact entity APIs are new. |
| `entity/ProfessorInfiniteSlidesEntity.java` | No local entity | Store only synchronization/ownership identity needed by the encounter; reject/discard orphan loads; delegate campaign outcomes to service | New required pattern. |
| `server/CampaignLifecycle.java` | Current server-tick marker only | Register typed server/player lifecycle callbacks once and translate death, disconnect, dimension change, unload, stop, and tick into campaign events | New required pattern. Do not scan all players/entities every tick. |
| `command/DevHellCommands.java` | No local command | Fabric Command API v2, server-only source, explicit permission/owner scope, deterministic status/reset/abort/recovery behavior, localized responses | New required pattern. |

The Homework add does not automatically justify another custom entity. Prefer one bounded vanilla-compatible server-owned helper if it can carry owner/encounter identity safely; otherwise establish a second unconditional entity registration using the same new entity pattern. Do not use ordinary mob loot for first-victory rewards.

### New client module

| Likely file | Closest local analog | Rule |
|---|---|---|
| `src/client/java/dev/developershell/client/render/ProfessorInfiniteSlidesRenderer.java` | None; only the empty `DevelopersHellClient` seam exists | Choose the smallest compile-proven vanilla-compatible renderer/silhouette. Reference vanilla runtime resources rather than copying textures. Rendering reads synchronized state only and never advances the fight. |

If the selected vanilla-compatible entity can use an existing renderer registration without a custom class, omit this file. No custom HUD, screen, shader, packet loop, model source, PNG, or OGG belongs in Phase 2.

## Initialization and Dependency Direction

The existing initializer establishes the following order and should evolve deliberately:

1. Register all stable item and entity identities unconditionally.
2. Load and validate the immutable local configuration; construct the existing `ModuleGate` from its final accepted/defaulted values.
3. Construct server-side campaign/persistence/lecture services without a live server or level singleton.
4. Register lifecycle adapters, item behavior dependencies, and `/devhell` commands.
5. Retain the existing bounded first-server-tick log marker and normal SLF4J initialization log.

Dependency direction should be `Fabric/vanilla callback -> server adapter -> campaign service -> pure reducer -> persisted state -> effect adapter`. Pure campaign/config/geometry classes must not import client classes, Fabric events, registries, filesystem APIs, clocks, or non-seeded randomness. Registry providers must not import `ModuleGate` or config. Client code may depend on stable entity types and synchronized presentation state, never the reverse.

Do not wrap initialization in a blanket catch. Duplicate/malformed registrations should fail startup visibly. Config validation is the one specified recovery boundary: log all sanitized issues and use the complete public-safe default snapshot, without partially applying or rewriting invalid input.

## Pure Domain and JUnit Pattern

`ModuleGate` and `ModuleGateTest` are the best local templates for Phase 2's reducer, geometry, config model, cooldown math, and deterministic choices:

- constructors/factories reject nulls immediately;
- mutable inputs are copied into immutable snapshots;
- enums expose explicit serialized strings;
- query methods do not mutate state;
- tests are package-local `final` classes using JUnit Jupiter;
- test names describe the invariant in lower camel case;
- assertions cover all enum values, exact ordering/names, nulls, defensive copy, and immutability;
- no test framework or mocking library is added; use small in-memory fakes or pure effect-intent values; and
- random scenarios always take and report a seed.

Likely focused unit-test classes are:

- `config/DevelopersHellConfigTest.java` — defaults, aggregate errors, invalid-file no-overwrite contract, ranges, and all eight module keys;
- `campaign/CampaignReducerTest.java` — monotonic transitions, duplicate/stale/wrong-owner events, failure reasons, load-to-retake, and exactly-once reward intents;
- `campaign/CampaignCodecTest.java` — round trips, version field, missing/unknown/malformed values, and safe in-flight recovery;
- `lecture/ArenaLayoutTest.java` — exact 17x17/15x15 offsets, facing transforms, lane coverage, pads, quadrants, and retry bounds;
- `lecture/LectureScheduleTest.java` — deterministic choices, timer/damage/add caps, nonlethal third absence, and reduced-effects invariance; and
- `item/InfiniteSlidesRemoteCooldownTest.java` — 400 ticks, ceiling-rounded feedback, rejoin restoration, and one ready edge.

Keep tests that require registries, `ServerLevel`, entities, saved data, inventory, or item cooldowns in GameTest instead of bootstrapping half of Minecraft inside ordinary JUnit.

## Fabric GameTest Pattern

`FoundationGameTests` is the exact local framework analog:

- implement `CustomTestMethodInvoker`;
- annotate public methods with Fabric's `@GameTest`;
- accept `GameTestHelper`;
- use helper assertions and call `context.succeed()` only after the complete lifecycle assertion; and
- keep the test mod under the existing `gametest` source set and metadata.

Create a separate `LectureGameTests` class rather than turning the one-method foundation registry test into a campaign monolith, subject to compile-checking how multiple invokers are declared in the 26.2 test manifest. Required in-runtime families include invalid/valid Contract activation, block preservation, boss/bar ownership, every cleanup exit, reload-to-retake, orphan rejection, Retake inventory/fallback recovery, first-victory reward idempotency, Sheet recovery, and Remote cooldown/rejoin behavior.

There is no local structure-template, tick-scheduling, fake-player, save/reload, or multi-test fixture pattern. Phase 2 must establish these against Fabric 26.2 generated sources and official GameTest examples. Do not treat compilation or test discovery as proof: Phase 1's accepted pattern is fail-first named failure followed by a fresh wrapper-owned `runGameTest` pass.

## Resource and Data Naming

Use the established namespace and lower-snake-case stable IDs everywhere:

- `DevelopersHell.id("<id>")` is the sole namespaced-ID factory in Java.
- Item translation keys retain vanilla form: `item.developers_hell.<id>`.
- Entity translation should use vanilla form: `entity.developers_hell.professor_infinite_slides`.
- Choose one namespaced family for custom messages/tooltips (for example `message.developers_hell.lecture.*`) and freeze it; no local family exists yet.
- User-facing copy belongs in `assets/developers_hell/lang/en_us.json` and is emitted with translatable components.

Each new item follows the current Minecraft 26.2 two-file pattern:

```text
assets/developers_hell/items/<id>.json
assets/developers_hell/models/item/<id>.json
```

Reference vanilla item textures/models for `cursed_unpaid_internship_contract`, `retake_form`, `attendance_sheet`, and `infinite_slides_remote`; do not copy Minecraft or community assets into the repository. Phase 2 intentionally adds no `sounds.json` because its cues use vanilla sound events and built-in subtitles.

Data-pack content uses the singular 26.2 paths specified by Phase 2 context:

```text
src/main/resources/data/developers_hell/recipe/cursed_unpaid_internship_contract.json
src/main/resources/data/developers_hell/advancement/a_suspicious_opportunity.json
```

There is no local recipe/advancement JSON analog. Their exact 26.2 schema and any GameTest structure/resource paths must be compile/run-checked rather than inferred from older plural-directory tutorials.

## Build, Offline, and Archive Verification

`build.gradle`, `gradle.properties`, and the wrapper are established and should normally remain structurally unchanged for Phase 2:

- Minecraft `26.2`, Java `25`, Loader `0.19.3`, Fabric API `0.158.0+26.2`, Loom `1.17.19`, and Gradle `9.5.1` stay pinned.
- Use the existing Fabric API umbrella; do not add Gson, Jackson, Cloth Config, Mod Menu, GeckoLib, a database, HTTP client, or test/mocking dependency.
- Keep `splitEnvironmentSourceSets()` and client/common physical separation.
- `auditDirectDependencies` must continue to observe the exact five project declarations and the complete 145-entry Loom injection multiset/hash.
- Use only `gradlew.bat` with the one explicit checksum-bound Java installation, auto-detection disabled, and auto-download disabled.
- Required automated gates remain `test`, wrapper-owned `runGameTest`, `clean build`, same-cache `--offline clean build`, and the comprehensive source/dependency/archive audit.

`scripts/audit-foundation.ps1` is reusable as a baseline because it recursively scans all `src/main` and `src/client` text for networking, telemetry, secrets, and client leakage, audits exact dependencies/repositories, and rejects test output in the ordinary JAR. Its required archive-entry list is foundation-only, so a PASS does not prove that Phase 2 classes/data are packaged correctly. Add a Phase 2 archive contract in a new phase-specific verifier or intentionally generalize the audit with explicit phase contracts; do not weaken the foundation assertions.

`scripts/verify-foundation.ps1` is evidence-bound to the Phase 1 manifest, evidence file, and accepted distribution hash. Preserve it as the Phase 1 proof. It is an implementation analog for safe automation, not the place to overwrite Phase 1 evidence with a new gameplay JAR. Phase 2 needs a separate `scripts/verify-lecture.ps1` (or an explicitly versioned generic verifier) if clean-checkout parity, lifecycle smoke, or phase-specific evidence must be automated.

Do not claim the Phase 1 `dist` hash remains current after Phase 2 code changes. Produce and identify a new ordinary JAR only after the Phase 2 build/GameTest/audit gates pass. Gradle `--offline` still means cache-only dependency resolution, not operating-system network isolation; runtime offline claims require source/archive audit and an appropriate production runtime check.

## PowerShell Convention

Any Phase 2 script should adapt the safety properties of `audit-foundation.ps1` and `verify-foundation.ps1`, not copy their phase-specific size or firewall machinery:

- `[CmdletBinding()]`, an explicit parameter surface, `Set-StrictMode -Version Latest`, and `$ErrorActionPreference = 'Stop'`;
- repository root derived from `$PSScriptRoot`, with `-LiteralPath`, canonical-path containment, reparse-point checks, and exact leaf validation before writes/deletes;
- no `$HOME`, broad glob, global Java kill, host-wide CIM inventory, group-wide firewall removal, or unvalidated recursive cleanup;
- wrapper/native commands captured with numeric exit codes, bounded timeouts, and exact PID/start/executable/command ownership when processes are launched;
- UTF-8, atomic evidence/status writes and deterministic canonical serialization where hashes matter;
- redact repository/home/temp paths, URL credentials, tokens, authorization strings, and machine-specific data from public evidence;
- aggregate independent verification failures where later checks are still safe, but fail closed on uncertainty;
- cleanup in `finally`, preserving both the primary error and cleanup error;
- deterministic `-SelfCheck` coverage for destructive/path/process primitives before real runtime use; and
- PowerShell 5.1 plus PowerShell 7 parser/self-check compatibility.

The Phase 2 verifier should stay gameplay-focused: unit/GameTest results, archive contents, clean server lifecycle, idempotent cleanup/rewards, and public-safe evidence. It should not duplicate the elevated firewall supervisor unless Phase 2 explicitly requires a fresh operating-system isolation proof.

## Fabric and Vanilla API Routing

| Capability | Existing local use | Phase 2 routing |
|---|---|---|
| Common/client entrypoints | `ModInitializer`, `ClientModInitializer` | Continue as the only startup roots. |
| Server tick event | One `ServerTickEvents.END_SERVER_TICK` marker | Register bounded campaign scheduling through a server adapter; never put unbounded scans or dialogue on every tick. |
| Item registry | `BuiltInRegistries.ITEM` plus `Registry.register` | Extend existing provider/factory pattern. |
| Entity registry/default attributes | None | Establish from official 26.2 generated sources; initialize unconditionally. |
| Entity renderer registry | None | Client source set only; stable entity type may cross the boundary, renderer classes may not. |
| Commands | None | Fabric Command API v2 in common/server code; exact 26.2 signature must be compile-checked. |
| GameTest | `CustomTestMethodInvoker`, `@GameTest`, `GameTestHelper` | Reuse exactly and add real lecture lifecycle coverage. |
| Boss bar/action/chat | None | Vanilla `ServerBossEvent` and `ServerPlayer` text surfaces from common/server code. |
| Particles/sounds | None | Server-targeted vanilla particles/sounds with hard caps and transition-scoped emission. |
| Saved data/codecs | None | Establish `SavedDataType` and `ValueInput`/`ValueOutput` pattern from 26.2 sources; no database. |
| Tooltip/item use/cooldown | None | Common item methods plus server-owned validation/effects; compile-check current signatures. |
| Player/lifecycle events | None | Select official Fabric/vanilla events for death, disconnect, dimension change, respawn, unload, and stop; all forward to one campaign service. |

Avoid Mixins unless generated-source inspection proves no Fabric event or vanilla subclass seam exists. Do not add raw OpenGL, custom networking, display-entity mutation, client-authoritative collision, or per-tick presentation packets.

## Missing Patterns That Phase 2 Must Establish

These are not implementation details that can be deferred without an explicit plan task:

1. **Config schema and parser:** version field, unknown/missing fields, aggregate validation errors, safe-default fallback, sanitized logging, and invalid-file no-overwrite behavior.
2. **Campaign reducer/effect boundary:** immutable state/event types, stale-event no-ops, state-first persistence, and effect-intent execution ordering.
3. **Minecraft 26.2 persistence codec:** `SavedDataType`, `ValueInput`/`ValueOutput`, per-player storage, schema evolution, corrupt/old state handling, and reload-to-retake conversion.
4. **Owner-bound encounter identity:** owner UUID plus encounter UUID carried through boss, helpers, Retake fallback, callbacks, and cleanup.
5. **Custom entity lifecycle:** registration, attributes, synchronization, save/load behavior, orphan rejection, death callback, and dedicated-server safety.
6. **Client renderer seam:** exact vanilla-compatible superclass/renderer and vanilla resource reference, with no common-side client linkage.
7. **Boss presentation cleanup:** one participant-scoped boss bar plus bounded action/chat/particles/sounds removed on every success/failure/unload path.
8. **Arena/world adapter:** loaded chunks, border, floor/headroom, Overworld, facing axes, retry position, and zero block mutation on rejection or combat.
9. **Exactly-once rewards and Retake recovery:** ledger semantics, inventory-full fallback, destroyed/despawned fallback recovery, artifact restoration, and no duplicate Remote.
10. **Persistent Remote cooldown:** server-tick time base, death/rejoin restoration, native overlay synchronization, and one ready edge.
11. **Command convention:** permission model, owner/world scoping, recovery semantics, and localized output.
12. **Data-pack resources:** singular 26.2 recipe/advancement schema and any GameTest structure fixtures.
13. **Phase 2 verification/evidence:** package assertions for all new classes/data, deterministic GameTests, ordinary-JAR production smoke, and an honest new artifact/hash handoff without rewriting Phase 1 proof.

## Anti-Patterns to Reject During Planning

- Gating item/entity registration on config or `ModuleGate`.
- Storing active campaign truth only in an entity, boss bar, static map, item NBT/component, or client state.
- Applying rewards before the persisted transition/ledger is accepted.
- Resuming an in-flight attack after save/reload instead of cleanup-to-retake.
- Deriving saved names from Java enum constants.
- Calling `Random`/wall clock directly inside deterministic domain logic.
- Hard-coded English player copy in Java.
- Client imports under `src/main` or client decisions sent back as authoritative results.
- Adding a library to solve JSON, animation, UI, persistence, or mocking during this phase.
- Per-tick global player/entity scans, chat, particles, sounds, config reads, or disk writes.
- Ordinary entity loot for Attendance Sheet/Remote first-victory grants.
- Broad process/firewall cleanup or reusing the Phase 1 accepted evidence/hash for a changed JAR.

## Planner Handoff

The safest plan split follows the dependency graph:

1. pure config/campaign/geometry contracts and JUnit tests;
2. stable items/entities/resources and compile-checked Minecraft adapters;
3. saved-data/service/lifecycle and retry/reward idempotency;
4. Contract arena activation plus real GameTests;
5. three-act encounter/presentation plus cleanup GameTests;
6. Remote cooldown, recovery commands, archive/offline gates, and bounded client UAT.

The planner may combine files, but it should not combine these proof boundaries. The first implementation task should compile-check the exact Minecraft/Fabric 26.2 persistence, entity, renderer, item, event, and GameTest signatures before committing a broad gameplay design around remembered older APIs.
