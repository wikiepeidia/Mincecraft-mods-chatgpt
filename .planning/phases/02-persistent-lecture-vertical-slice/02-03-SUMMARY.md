---
phase: 02-persistent-lecture-vertical-slice
plan: 03
subsystem: item-presentation
tags: [fabric-26.2, item-definitions, vanilla-assets, offline, resource-pack]

requires:
  - phase: 01-java-25-and-fabric-26-2-foundation
    provides: Minecraft 26.2 two-layer Foundation Token item-definition and generated-model convention
  - phase: 02-persistent-lecture-vertical-slice
    plan: 02
    provides: Stable Phase 2 runtime and unconditional registry initialization boundary
  - phase: 02-persistent-lecture-vertical-slice
    plan: 14
    provides: Exact unconditional Contract, Retake Form, Attendance Sheet, and Infinite Slides Remote registry IDs
provides:
  - Current Minecraft 26.2 item-definition indirection for all four stable Phase 2 items
  - Vanilla-backed generated models using map, paper, filled-map, and repeater runtime textures
  - Packaged offline presentation with no copied binary, model-source, font, shader, URL, or third-party asset
affects: [campaign-discovery, retake-flow, lecture-rewards, remote-cooldown, release-provenance]

actuals:
  tokens: 212
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns: [minecraft-26.2 item-definition indirection, vanilla runtime texture references, generated item models]

key-files:
  created:
    - src/main/resources/assets/developers_hell/items/cursed_unpaid_internship_contract.json
    - src/main/resources/assets/developers_hell/items/retake_form.json
    - src/main/resources/assets/developers_hell/items/attendance_sheet.json
    - src/main/resources/assets/developers_hell/items/infinite_slides_remote.json
    - src/main/resources/assets/developers_hell/models/item/cursed_unpaid_internship_contract.json
    - src/main/resources/assets/developers_hell/models/item/retake_form.json
    - src/main/resources/assets/developers_hell/models/item/attendance_sheet.json
    - src/main/resources/assets/developers_hell/models/item/infinite_slides_remote.json
  modified: []

key-decisions:
  - "Map the Contract to vanilla map, Retake Form to paper, Attendance Sheet to filled-map, and Infinite Slides Remote to repeater so each identity is deterministic without copied art."
  - "Mirror the proven Foundation Token minecraft:model to minecraft:item/generated chain exactly and keep gameplay, registries, dependencies, and source-set boundaries unchanged."

patterns-established:
  - "Stable item presentation: assets/developers_hell/items/<id>.json points to the same-named developers_hell:item/<id> model."
  - "License-safe placeholders: generated item models reference only verified assets/minecraft/textures/item runtime entries and never copy vanilla or community files."

requirements-completed: [FND-07, CAMP-01, LECT-02]

coverage:
  - id: D1
    description: "All four stable Phase 2 IDs resolve through the current Minecraft 26.2 item-definition plus generated-model chain."
    requirement: CAMP-01
    verification:
      - kind: integration
        ref: "Pinned Temurin 25 offline clean processResources plus exact built-path and JSON-chain assertions"
        status: pass
    human_judgment: false
  - id: D2
    description: "Contract, Retake Form, Attendance Sheet, and Infinite Slides Remote package with distinct approved vanilla runtime texture references."
    requirement: LECT-02
    verification:
      - kind: integration
        ref: "Pinned offline jar task plus exact eight-entry archive inspection"
        status: pass
    human_judgment: false
  - id: D3
    description: "The presentation adds no binary asset, external URL, credential marker, dependency, registry change, or client/common code."
    requirement: FND-07
    verification:
      - kind: other
        ref: "Added-file extension scan, URL/credential scan, git diff inspection, and local 26.2 client-JAR texture lookup"
        status: pass
    human_judgment: false

duration: 4min
completed: 2026-08-26
status: complete
---

# Phase 2 Plan 03: Survival Discovery and Vanilla-Backed Items Summary

**Four stable campaign items now use packaged Minecraft 26.2 render chains backed entirely by verified vanilla runtime textures.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-08-26T18:11:33Z
- **Completed:** 2026-08-26T18:14:41Z
- **Tasks:** 1
- **Files modified:** 8

## Accomplishments

- Added exact current item-definition JSON for the Contract, Retake Form, Attendance Sheet, and Infinite Slides Remote.
- Added matching generated item models using vanilla `map`, `paper`, `filled_map`, and `repeater` texture references.
- Proved all eight resources parse, copy, resolve, and package offline without adding copied or third-party assets.

## Task Commits

Each task was committed atomically:

1. **Task 1: Give every stable Phase 2 item a current vanilla-backed render chain** - `679b1dc` (feat)

The summary and sequential tracking state are recorded in the plan closeout commit.

## Files Created/Modified

- `src/main/resources/assets/developers_hell/items/cursed_unpaid_internship_contract.json` - Contract item-definition indirection.
- `src/main/resources/assets/developers_hell/items/retake_form.json` - Retake Form item-definition indirection.
- `src/main/resources/assets/developers_hell/items/attendance_sheet.json` - Attendance Sheet item-definition indirection.
- `src/main/resources/assets/developers_hell/items/infinite_slides_remote.json` - Remote item-definition indirection.
- `src/main/resources/assets/developers_hell/models/item/cursed_unpaid_internship_contract.json` - Vanilla map-backed Contract model.
- `src/main/resources/assets/developers_hell/models/item/retake_form.json` - Vanilla paper-backed Retake Form model.
- `src/main/resources/assets/developers_hell/models/item/attendance_sheet.json` - Vanilla filled-map-backed Attendance Sheet model.
- `src/main/resources/assets/developers_hell/models/item/infinite_slides_remote.json` - Vanilla repeater-backed Remote model.

## Verification Evidence

- `gradlew.bat clean processResources --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle` passed with checksum-pinned Temurin 25.0.4+7 and Loom 1.17.19.
- Every expected source and `build/resources/main` JSON parsed successfully and every item definition pointed to its same-named model.
- `gradlew.bat jar --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle` passed; `build/libs/developers-hell-0.1.0.jar` contained all eight exact entries.
- The local Minecraft 26.2 client JAR contains each referenced vanilla texture and `minecraft:item/generated`; extension and content scans found no copied binary, URL, API, credential, or stub marker.

## Decisions Made

- Used map/paper/filled-map/repeater to make the four placeholders distinguishable while preserving the accepted vanilla look.
- Referenced vanilla resources in place; no texture was extracted, generated, downloaded, or copied into the repository.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration or runtime network access is required.

## Next Phase Readiness

- Survival discovery and later reward/retry plans can now reference all four stable items without missing-model presentation.
- Plan 02-16 remains the declared owner of item-name, Contract tooltip, advancement, and recipe localization; this plan intentionally changed only its eight resource-chain files.
- No blockers or security-relevant trust-boundary changes were introduced.

## Self-Check: PASSED

- All eight declared resource files and this summary exist on disk.
- Task commit `679b1dc` exists in repository history.
- Required status, requirement coverage, verification evidence, and actuals metadata are present.

---
*Phase: 02-persistent-lecture-vertical-slice*
*Completed: 2026-08-26*
