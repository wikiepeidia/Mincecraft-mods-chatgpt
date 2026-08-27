---
gsd_state_version: 1.0
current_phase: 02
current_phase_name: Persistent Lecture Vertical Slice
status: verifying
stopped_at: Completed 02-13-PLAN.md
last_updated: "2026-08-27T00:55:39.822Z"
last_activity: 2026-08-26
last_activity_desc: Phase 02 execution started
state_head: 6e27e46a01220c7a65074e172d7912c88a8c69fd
progress:
  total_phases: 6
  completed_phases: 1
  total_plans: 22
  completed_plans: 22
  percent: 17
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-08-26)

**Core value:** Deliver a genuinely funny, replayable boss-rush experience whose university and developer jokes become visible Minecraft mechanics rather than merely renamed items or text references.
**Current focus:** Phase 02 — Persistent Lecture Vertical Slice

## Current Position

Phase: 02 (Persistent Lecture Vertical Slice) — EXECUTING
Plan: 18 of 18
Status: Phase complete — ready for verification
Last activity: 2026-08-27 - Completed quick task 260827-u3w: offline Python tools with 199 unit tests

Progress: [████████████████████] 4/4 plans ([██░░░░░░░░] 17%)

## Performance Metrics

**Velocity:**

- Total plans completed: 4
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 4 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 01 P01 | 17 min | 2 tasks | 14 files |
| Phase 01 P02 | 9 min | 1 tasks | 8 files |
| Phase 01 P03 | 29 min | 1 tasks | 6 files |
| Phase 01 P04 | 15h | 3 tasks | 4 files |
| Phase 02 P01 | 20min | 1 tasks | 12 files |
| Phase 02 P14 | 14min | 1 tasks | 7 files |
| Phase 02 P15 | 8min | 1 tasks | 6 files |
| Phase 02 P02 | 22 min | 2 tasks | 9 files |
| Phase 02 P03 | 4min | 1 tasks | 8 files |
| Phase 02 P04 | 25min | 2 tasks | 8 files |
| Phase 02 P16 | 12min | 1 tasks | 5 files |
| Phase 02 P05 | 22min | 2 tasks | 8 files |
| Phase 02-persistent-lecture-vertical-slice P06 | 27min | 2 tasks | 11 files |
| Phase 02-persistent-lecture-vertical-slice P07 | 32min | 2 tasks | 9 files |
| Phase 02 P08 | 23min | 2 tasks | 11 files |
| Phase 02-persistent-lecture-vertical-slice P09 | 16min | 2 tasks | 7 files |
| Phase 02-persistent-lecture-vertical-slice P10 | 29min | 2 tasks | 12 files |
| Phase 02-persistent-lecture-vertical-slice P11 | 9min | 1 tasks | 4 files |
| Phase 02 P17 | 24min | 1 tasks | 10 files |
| Phase 02 P12 | 14min | 1 tasks | 5 files |
| Phase 02 P18 | 32min | 1 tasks | 6 files |
| Phase 02 P13 | 72min | 2 tasks | 5 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Prove persistence, cleanup, retry, and exactly-once rewards with the complete Lecture vertical slice before adding later bosses.
- [Phase 01]: Keep the exact Fabric 26.2 tuple: checksum-pinned Temurin 25.0.4+7, Loader 0.19.3, Fabric API 0.158.0+26.2, Loom 1.17.19, and Gradle 9.5.1.
- [Phase 01]: Stable content registration is unconditional and independent of the eight immutable behavior gates so later toggle changes cannot remove saved IDs.
- [Phase 01]: Preserve the one ordinary JAR and fail-closed offline/runtime proof contract; online, same-cache-offline, distribution, and runtime-copy SHA-256 values remain identical.
- [Phase 01]: The vanilla paper/map-style Foundation Token appearance is accepted MVP cosmetic debt; bespoke art remains later release polish.
- [Phase 02]: Use Fabric UseBlockCallback as the normal Contract-to-lectern entrypoint because Minecraft 26.2 consumes empty-lectern handling before Item.useOn.
- [Phase 02]: Decode unsupported schema numbers into read-only campaign state so computeIfAbsent cannot replace future data after a Codec error.
- [Phase 02]: Match both owner UUID and active encounter UUID, and dirty the ledger before spawning, shrinking, cleanup, boss-bar changes, or inventory grants.
- [Phase 02]: Keep the Professor as a no-loot Vindicator-derived server identity while the encounter manager owns the bounded runtime and owner-only ServerBossEvent.
- [Phase 02]: Plan 02-14: PlayerCampaignState and ProfessorInfiniteSlidesEntity are the sole state-owning final types; existing tracer consumers use zero-state compatibility views.
- [Phase 02]: Plan 02-14: Schema-v1 optional fields preserve tracer saves, while encoded owner and UUID map-key disagreement makes campaign state read-only.
- [Phase 02]: Plan 02-14: Professor damage requires matching attacker, live runtime participant, saved owner, and active encounter before CampaignService may commit rewards.
- [Phase 02]: Plan 02-15: Capture one validated Standard LectureRules value per runtime so cadence and cue ceilings cannot change during an encounter.
- [Phase 02]: Plan 02-15: Keep gameplay and presentation decisions on the logical server; the client only binds the stable Professor to VindicatorRenderer.
- [Phase 02]: Accept only one complete strict schema-v1 document; any validation failure activates complete immutable defaults without rewriting rejected bytes.
- [Phase 02]: Bridge retained static campaign and lecture utilities with scoped runtime adapters that drive the real Contract and tick paths.
- [Phase 02]: Keep /devhell status read-only and reserve game-master-gated mutation children for their owning recovery plan.
- [Phase 02]: Plan 02-03: Use vanilla map, paper, filled-map, and repeater runtime textures for the four stable Phase 2 item placeholders without copied art.
- [Phase 02]: Plan 02-03: Mirror the Minecraft 26.2 Foundation Token item-definition/model chain and leave gameplay, registries, dependencies, and source-set boundaries unchanged.
- [Phase 02]: Decode strict schema 1 first, then retain future or malformed documents as explicit read-only Dynamic values.
- [Phase 02]: Compose flat MapCodec groups to preserve established schema-1 field names beyond the 16-field builder limit.
- [Phase 02]: Record encounter materialization failure as ABORT so attempt counters never roll back.
- [Phase 02]: Persist replacement and mark SavedData dirty before CampaignService dispatches any effect.
- [Phase 02]: Plan 02-16: Require paper and ink together in one local advancement, then reward exactly the namespaced Contract recipe.
- [Phase 02]: Plan 02-16: Handle wrong-target Contract use with Fabric SUCCESS/SUCCESS_SERVER semantics, server-only localized guidance, and no consumption or persistence.
- [Phase 02]: Plan 02-16: Feed recipe-assembled output into the retained lifecycle tracer and keep the separate discovery GameTest runtime-free.
- [Phase 02]: Fabric lifecycle callbacks submit closed CampaignEvent values; CampaignService.apply remains the only durable mutation path.
- [Phase 02]: Live runtime exits require exact owner, encounter, Professor UUID, and attempt identity before cleanup.
- [Phase 02]: Disk-loaded Professors normalize matching ACTIVE progress to safe Retake and never resume combat.
- [Phase 02]: The real SERVER_STOPPING callback and in-process GameTest share one server-wide state-first handler.
- [Phase 02]: Plan 02-06: Four-block headroom applies only to the 15x15 combat interior; the full 17x17 boundary remains solid, loaded, and border-safe.
- [Phase 02]: Plan 02-06: Contract validation produces one immutable Accepted geometry value consumed unchanged by runtime and CampaignService.
- [Phase 02]: Plan 02-06: Retry scans a duplicate-free behind-and-beside Chebyshev wedge from radius two through five, beginning at L-2F.
- [Phase 02]: Plan 02-06: Professor spawn capacity includes type availability, border/block collision, and exact-AABB entity occupancy before START persistence.
- [Phase 02]: Plan 02-07: Retake identity is owner UUID plus exact failed encounter UUID; attemptCount is progression, not identity authority.
- [Phase 02]: Plan 02-07: Optional schema-v1 reservation and committed fallback UUIDs are mutually exclusive and preserve future-schema read-only behavior.
- [Phase 02]: Plan 02-07: RetakeService is the sole state-first adapter for reconciliation, recovery, and commit-before-consume retry.
- [Phase 02]: Plan 02-07: Failed retry runtime start compensates with a new keyed ABORT entitlement before replacing old physical state.
- [Phase 02]: Plan 02-08: Encode the exact owner plus failed-encounter RetakeKey in vanilla CUSTOM_DATA; physical Forms never manufacture authority.
- [Phase 02]: Plan 02-08: Reject stale fallback chunk copies and clear only matching reserved/materialized UUIDs before replacement.
- [Phase 02]: Plan 02-08: Revalidate owner, saved Desk, state, and complete arena; persist and start the retry before consuming the Form.
- [Phase 02]: Plan 02-09: Preserve the seven-argument LectureRules construction surface while exposing exact Standard combat tuning through compatible methods.
- [Phase 02]: Plan 02-09: Derive all combat choices solely from encounter UUID, attempt, act, cycle, and quiz index while keeping reduced effects semantic-free.
- [Phase 02]: Plan 02-09: Keep CampaignService as the sole victory transaction and conjunct its authority with current manager participant, owner, window, and act-floor admission.
- [Phase 02]: Lecture presentation remains a one-way owner-only projection of recorded server state with bounded redundant semantic cues.
- [Phase 02]: Homework adds are ephemeral exact-owner/exact-encounter helpers with one-active cap, no loot, bounded lifetime, and fail-closed reload behavior.
- [Phase 02]: LectureRules keeps its seven-value constructor and equality contract while configured sessions store all accepted combat tuning and reduced-effects mode.
- [Phase 02]: Use the persisted ACTIVE encounter reference as the first-terminal-wins latch; no campaign schema field is added.
- [Phase 02]: Expose CampaignService.commitVictory as the accepted transition handoff while retaining the boolean compatibility wrapper until Plan 02-17.
- [Phase 02]: Route Terminal, NormalizeReload, and Victory through one sealed EncounterTerminal admission gate.
- [Phase 02]: Only LectureEncounterManager may turn admitted final-window damage into commitVictory and pass its accepted matching transition to RewardService.
- [Phase 02]: Keep CampaignService.victory callable for source compatibility but deprecate it as a false-returning no-op with no persistence or effects.
- [Phase 02]: Bind every Attendance Sheet to owner UUID and sheetRecoverySequence, incrementing that persisted generation before restoring a missing Sheet.
- [Phase 02]: Preserve Retake priority at the Desk and reuse campaign schema 1 rather than adding a physical-item ledger.
- [Phase 02]: Plan 02-12: Reuse the frozen optional schema-v1 Remote deadline and notice fields/events; no persistence widening or version bump.
- [Phase 02]: Plan 02-12: Route held Remote use through Fabric UseItemCallback before vanilla cooldown rejection while leaving the client PASS-only and the persisted server deadline authoritative.
- [Phase 02]: Plan 02-12: Dispatch the capped six-block/six-target slide only from the accepted post-setDirty cooldown intent.
- [Phase 02]: Plan 02-18: Project one native cooldown group from the first present production Remote; matching stacks share it without duplicate packets.
- [Phase 02]: Plan 02-18: Leave an elapsed ready edge pending while the Remote is absent or a critical owner action bar is active.
- [Phase 02]: Plan 02-18: Reconcile Remote readiness after encounter runtime ticks so just-closed fights release action-bar priority.
- [Phase 02]: Plan 02-18: Preserve lifecycle orphan rejection and prove critical priority with a real ACTIVE encounter.
- [Phase 02]: Bind verifier-owned root and descendants by PID, UTC start ticks, executable, parent edge, and command anchors before cleanup.
- [Phase 02]: Keep raw foundation audit exit 1 visible; accept only the pinned ConfigIssue sanitizer denylist finding after independent source and archive scans.
- [Phase 02]: Accept the advancement telemetry field only when JSON parsing proves the exact boolean false opt-out.
- [Phase 02]: Promote dist atomically only after fresh build, tests, audits, archive contract, real-server ordered shutdown, and zero owned residue.

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 5]: Every advertised entity/trait pairing and its disable/restore policy needs explicit GameTest proof.
- [Phase 6]: Public naming, parody copy, performance caps, and generated-asset provenance remain release gates.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260827-s61 | Emergency playable Jury, Chairman, sponsor, and Codex boss rush with a verified Fabric 26.2 JAR | 2026-08-27 | e36eb26 | [260827-s61-emergency-playable-boss-rush-add-a-bound](./quick/260827-s61-emergency-playable-boss-rush-add-a-bound/) |
| 260827-u3w | Offline pip Wand, venv Flask, and bounded Python Pickaxe with a verified Fabric 26.2 JAR | 2026-08-27 | 8291061 | [260827-u3w-implement-the-deadline-python-tools-modu](./quick/260827-u3w-implement-the-deadline-python-tools-modu/) |

## Deferred Items

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-08-27T00:55:39.690Z
Stopped at: Completed 02-13-PLAN.md
Resume file: None
