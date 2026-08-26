---
phase: 02-persistent-lecture-vertical-slice
plan: 16
subsystem: campaign-discovery
tags: [fabric-26.2, survival-crafting, advancement, recipe, localization, gametest]

requires:
  - phase: 02-persistent-lecture-vertical-slice
    plan: 03
    provides: Minecraft 26.2 Contract item-definition and vanilla-backed model chain
  - phase: 02-persistent-lecture-vertical-slice
    plans: [01, 14, 15]
    provides: Registered Contract, server-authoritative lectern transaction, stable campaign state, and retained Professor tracer
provides:
  - Paper-and-ink survival recipe unlocked by the local A Suspicious Opportunity advancement
  - Three-line translated Contract instructions and non-consuming wrong-target lectern guidance
  - Runtime proof that discovered ingredients craft the exact registered Contract and feed the retained valid lectern tracer
affects: [phase-2-verification, campaign-start, lecture-retry, release-uat]

actuals:
  tokens: 3169
  tasks: 1
  commits: 3

tech-stack:
  added: []
  patterns: [singular Minecraft 26.2 data paths, local recipe reward advancement, handled server-authoritative wrong-target interaction]

key-files:
  created:
    - src/main/resources/data/developers_hell/recipe/cursed_unpaid_internship_contract.json
    - src/main/resources/data/developers_hell/advancement/a_suspicious_opportunity.json
  modified:
    - src/main/java/dev/developershell/item/CursedInternshipContractItem.java
    - src/main/resources/assets/developers_hell/lang/en_us.json
    - src/gametest/java/dev/developershell/gametest/FoundationGameTests.java

key-decisions:
  - "Use one local root advancement whose inventory criterion requires paper and ink together, then reward exactly the namespaced Contract recipe."
  - "Handle a held Contract on the wrong block with client SUCCESS and server SUCCESS_SERVER so Fabric sends the packet, cancels fallthrough, and emits one server-localized direction without consumption."
  - "Keep discovery proof runtime-free and make the retained lifecycle tracer start from recipe-assembled output, avoiding concurrent static encounter-count races."

patterns-established:
  - "Command-free discovery: a local inventory_changed advancement unlocks the exact singular-path recipe and no admin command participates in the normal path."
  - "Guided failure: wrong-target item use is handled on both sides, but only the logical server emits copy and only CampaignService may consume after accepted materialization."
  - "Transformed tracer: the production RecipeManager assembles the stack used by the full valid-desk lifecycle test."

requirements-completed: [FND-07, CAMP-01, LECT-02]

coverage:
  - id: D1
    description: "Paper plus ink completes A Suspicious Opportunity, unlocks the exact recipe, and crafts one registered Contract."
    requirement: CAMP-01
    verification:
      - kind: e2e
        ref: "src/gametest/java/dev/developershell/gametest/FoundationGameTests.java#contractDiscoveryCraftingAndGuidanceAreSurvivalReachable"
        status: pass
    human_judgment: false
  - id: D2
    description: "The Contract exposes exactly three translated instructions; wrong-target use emits localized lectern guidance while consuming, persisting, and spawning nothing."
    requirement: CAMP-01
    verification:
      - kind: e2e
        ref: "FoundationGameTests#contractDiscoveryCraftingAndGuidanceAreSurvivalReachable tooltip and wrong-target assertions"
        status: pass
    human_judgment: false
  - id: D3
    description: "Recipe, advancement, translations, item definition, and model package under current singular Minecraft 26.2 paths with no GameTest leakage."
    requirement: FND-07
    verification:
      - kind: integration
        ref: "Pinned offline processResources, jar, exact archive entry inspection, and plural-path rejection"
        status: pass
    human_judgment: false
  - id: D4
    description: "A recipe-produced Contract still reaches the retained server tracer, consumes once only after acceptance, and completes the existing Professor reward lifecycle."
    requirement: LECT-02
    verification:
      - kind: e2e
        ref: "src/gametest/java/dev/developershell/gametest/FoundationGameTests.java#contractStartsSlideWindowAndCommitsFirstReward"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 16: Command-Free Contract Discovery Summary

**Paper-and-ink discovery now unlocks and crafts the registered Contract, whose localized tooltip and handled wrong-target guidance lead directly into the server-authoritative Lecture tracer.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-08-26T18:47:45Z
- **Completed:** 2026-08-26T18:58:59Z
- **Tasks:** 1
- **Files modified:** 5

## Accomplishments

- Added a local `A Suspicious Opportunity` advancement that requires both paper and ink and rewards the exact Contract recipe.
- Added the singular 26.2 shapeless recipe for one Contract, four stable Phase 2 item names, the exact three-line Contract tooltip, and localized wrong-target direction.
- Proved discovery, recipe-book unlock, production recipe assembly, wrong-target non-mutation, and the recipe-produced valid lectern lifecycle in a clean server GameTest world.

## Requirements (Copied Verbatim)

- **FND-07**: Automated unit tests and Fabric GameTests cover state transitions, bounds, persistence, and at least one real encounter lifecycle before release.
- **CAMP-01**: A new player can discover, craft, and use the Cursed Unpaid Internship Contract without consulting an external wiki or using an admin command.
- **LECT-02**: Defeating Professor Infinite Slides grants the Attendance Sheet and a cooldown-limited Infinite Slides Remote after a fight that supports failure, cleanup, retry, and mid-campaign persistence.

## Task Commits

The TDD task was committed as its required RED and GREEN outcomes:

1. **RED — Specify Contract discovery, crafting, guidance, and transformed tracer behavior** - `e991c7c` (test)
2. **GREEN — Make the Contract discoverable and craftable in survival** - `18b273b` (feat)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `src/main/resources/data/developers_hell/recipe/cursed_unpaid_internship_contract.json` - One paper-plus-ink shapeless recipe producing the stable Contract ID.
- `src/main/resources/data/developers_hell/advancement/a_suspicious_opportunity.json` - Local ingredient discovery, translated toast, and exact recipe reward.
- `src/main/java/dev/developershell/item/CursedInternshipContractItem.java` - Three translated tooltip components and handled server-only wrong-target direction while retaining the real lectern transaction.
- `src/main/resources/assets/developers_hell/lang/en_us.json` - Discovery copy, stable item names, exact tooltip lines, and wrong-target guidance.
- `src/gametest/java/dev/developershell/gametest/FoundationGameTests.java` - Ingredient, advancement, recipe, crafting, tooltip, wrong-target, and recipe-produced valid tracer proof.

## Decisions Made

- Made the discovery advancement an independent local root so paper and ink are the only prerequisite; it does not depend on an admin command, recipe root, or another progression tab.
- Used the exact Fabric callback result semantics: client `SUCCESS` sends the block-use packet, server `SUCCESS_SERVER` cancels further handling after one localized response, and the stack is never shrunk on this path.
- Reused the existing full lifecycle tracer with recipe-assembled output instead of starting a second runtime in the discovery GameTest, preserving deterministic global runtime assertions.

## Verification

- Pinned Temurin `25.0.4+7`, Loom `1.17.19`, and offline Gradle gate `test processResources runGameTest jar` - PASS.
- Java unit tests - 37 passed, 0 failed, 0 errors, 0 skipped.
- Fresh generated server world - `4 GAME TESTS COMPLETE`; `All 4 required tests passed` - PASS.
- Phase 1 regression surface - `ModuleGateTest` and live Foundation Token GameTest passed inside the same final unit/GameTest gate.
- Production archive - exact singular recipe and advancement, Contract item/model/lang/class entries present; plural paths and GameTest classes absent - PASS.
- Direct dependency audit - exact five approved declarations and Loom injection baseline `145/a3fef1ae...` - PASS.
- Built candidate SHA-256 `c58cbdd46f0031858d491d237bb35b10bdaf58e0d0dcb8bdf4d4747bcca11655`; retained `dist/developers-hell-0.1.0.jar` remained `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8` - PASS.
- Exact translated copy, JSON parsing, stub/skip marker scan, and runtime URL/API/socket/telemetry scan - PASS.

## Threat Mitigations

- **T-02-DISC-01:** Exact runtime recipe/advancement keys, one recipe reward, singular archive paths, recipe-manager assembly, and registered output are all asserted.
- **T-02-DISC-02:** All added copy is fixed, fictional, local, translation-backed, and contains no identity, endpoint, price, credential, or private claim.
- No new endpoint, filesystem access, schema trust boundary, dependency, client authority, runtime network, or unplanned threat surface was introduced.

## TDD Gate Compliance

- RED commit `e991c7c` compiled, then failed from a clean GameTest world because the exact advancement and recipe were absent.
- GREEN commit `18b273b` supplied only the production behavior/resources required to make the same assertions pass.
- No refactor commit was needed after GREEN.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Clean GameTest startup produced the expected first-run warnings for absent transient `server.properties`, `eula.txt`, and client-resource output, then loaded normally and passed every required test.
- Generated GameTest worlds were moved aside under ignored `build/run/` paths before RED and GREEN, ensuring fresh persistent state without deleting prior diagnostic evidence.

## User Setup Required

None - discovery, crafting, copy, and activation are local and require no account, credential, external service, runtime network, or admin command.

## Next Phase Readiness

- CAMP-01 is reachable through ordinary survival ingredients, recipe discovery, crafting, tooltip guidance, and the retained Contract-to-Lecture transaction.
- Later retry/reward and phase-verification plans can rely on the exact stable item names and transformed tracer.
- No visible client was launched. The accepted vanilla-backed placeholder appearance remains intentional MVP cosmetic debt; later human GUI-scale/readability smoke remains a release-phase backstop, not a Plan 16 blocker.

## Self-Check: PASSED

- All five declared task files and this canonical summary exist on disk.
- RED commit `e991c7c` and GREEN commit `18b273b` exist in repository history with no tracked-file deletion.
- Requirement coverage, actuals, TDD compliance, threat evidence, verification results, and `status: complete` are present.
- No stub, TODO, FIXME, skipped test, unrun verification, unknown coverage, or deferred blocker remains.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-26*
