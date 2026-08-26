---
schema_version: 1
open_count: 0
waived_count: 0
fixed_count: 14
total_count: 14
last_updated: 2026-08-26T21:05:24.751Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 02 | deviation | src/main/java/dev/developershell/item/CursedInternshipContractItem.java |  | Fabric pre-block callback required for Contract use on an empty lectern | fixed |  | 2026-08-26T17:08:39.981Z | 2026-08-26T17:09:05.619Z |
| 2 | 02 | deviation | src/main/java/dev/developershell/campaign/CampaignSavedData.java |  | Removed stale invalid mapping import during compile gate | fixed |  | 2026-08-26T17:08:40.346Z | 2026-08-26T17:09:10.361Z |
| 3 | 02 | deviation | src/main/java/dev/developershell/campaign/CampaignService.java |  | Replaced deprecated solid-floor predicate with upward-face sturdiness | fixed |  | 2026-08-26T17:08:40.728Z | 2026-08-26T17:09:10.764Z |
| 4 | 02 | deviation | src/main/java/dev/developershell/campaign/CampaignSavedData.java | 155 | Preserve mismatched UUID map-key records while marking schema-1 data read-only. | fixed |  | 2026-08-26T18:42:52.733Z | 2026-08-26T18:43:17.572Z |
| 5 | 02 | deviation | src/main/java/dev/developershell/lecture/LectureEncounterManager.java |  | Scoped the deterministic timeout GameTest seam per encounter to avoid advancing concurrent runtimes | fixed |  | 2026-08-26T19:27:11.372Z | 2026-08-26T19:27:34.311Z |
| 6 | 02 | deviation | src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java |  | Delayed the server-wide stop seam test until concurrent runtime tests finish | fixed |  | 2026-08-26T19:27:11.777Z | 2026-08-26T19:27:38.589Z |
| 7 | 02 | deviation | src/main/java/dev/developershell/campaign/CampaignService.java |  | Routed accepted Contract geometry through CampaignService and DevelopersHellRuntime to eliminate duplicate validation and preserve state-first atomic starts | fixed |  | 2026-08-26T19:58:18.510Z | 2026-08-26T19:58:24.067Z |
| 8 | 02 | deviation | src/main/java/dev/developershell/campaign/CampaignService.java |  | Retained the legacy server-side start signature as a thin adapter to the sole ArenaValidator for lifecycle GameTest compatibility | fixed |  | 2026-08-26T19:58:18.859Z | 2026-08-26T19:58:24.406Z |
| 9 | 02 | deviation | src/main/java/dev/developershell/lecture/ArenaValidator.java |  | Added explicit entity occupancy to Professor spawn-capacity preflight because block collision alone permits overlapping spawns | fixed |  | 2026-08-26T19:58:19.238Z | 2026-08-26T19:58:24.758Z |
| 10 | 02 | deviation | src/main/java/dev/developershell/campaign/CampaignSavedData.java |  | Persisted optional schema-v1 failed encounter and fallback reservation UUIDs for reload-safe exactly-one Retake authority | fixed |  | 2026-08-26T20:34:29.591Z | 2026-08-26T20:34:37.748Z |
| 11 | 02 | deviation | src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java |  | Routed persistent lifecycle GameTest retries from Contract setup through keyed RetakeService after the clean gate exposed the stale test contract | fixed |  | 2026-08-26T20:34:30.170Z | 2026-08-26T20:34:38.436Z |
| 12 | 02 | deviation | src/main/java/dev/developershell/lecture/RetakeService.java |  | Compensated failed retry runtime starts and cleaned reserved or post-spawn fallback crash windows state-first | fixed |  | 2026-08-26T20:34:30.623Z | 2026-08-26T20:34:38.897Z |
| 13 | 02 | deviation | src/main/java/dev/developershell/server/DevelopersHellRuntime.java |  | Approved lifecycle bridge invokes RetakeService only after an accepted persisted reconciliation intent | fixed |  | 2026-08-26T21:04:59.081Z | 2026-08-26T21:05:24.438Z |
| 14 | 02 | deviation | src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java |  | Approved retained lifecycle expectations include issued copy only when reconciliation materializes a Form | fixed |  | 2026-08-26T21:04:59.387Z | 2026-08-26T21:05:24.751Z |

````json
[
  {
    "id": 1,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/item/CursedInternshipContractItem.java",
    "line": null,
    "description": "Fabric pre-block callback required for Contract use on an empty lectern",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T17:08:39.981Z",
    "resolved_at": "2026-08-26T17:09:05.619Z"
  },
  {
    "id": 2,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/campaign/CampaignSavedData.java",
    "line": null,
    "description": "Removed stale invalid mapping import during compile gate",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T17:08:40.346Z",
    "resolved_at": "2026-08-26T17:09:10.361Z"
  },
  {
    "id": 3,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/campaign/CampaignService.java",
    "line": null,
    "description": "Replaced deprecated solid-floor predicate with upward-face sturdiness",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T17:08:40.728Z",
    "resolved_at": "2026-08-26T17:09:10.764Z"
  },
  {
    "id": 4,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/campaign/CampaignSavedData.java",
    "line": 155,
    "description": "Preserve mismatched UUID map-key records while marking schema-1 data read-only.",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T18:42:52.733Z",
    "resolved_at": "2026-08-26T18:43:17.572Z"
  },
  {
    "id": 5,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/lecture/LectureEncounterManager.java",
    "line": null,
    "description": "Scoped the deterministic timeout GameTest seam per encounter to avoid advancing concurrent runtimes",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T19:27:11.372Z",
    "resolved_at": "2026-08-26T19:27:34.311Z"
  },
  {
    "id": 6,
    "kind": "deviation",
    "phase": "02",
    "file": "src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java",
    "line": null,
    "description": "Delayed the server-wide stop seam test until concurrent runtime tests finish",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T19:27:11.777Z",
    "resolved_at": "2026-08-26T19:27:38.589Z"
  },
  {
    "id": 7,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/campaign/CampaignService.java",
    "line": null,
    "description": "Routed accepted Contract geometry through CampaignService and DevelopersHellRuntime to eliminate duplicate validation and preserve state-first atomic starts",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T19:58:18.510Z",
    "resolved_at": "2026-08-26T19:58:24.067Z"
  },
  {
    "id": 8,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/campaign/CampaignService.java",
    "line": null,
    "description": "Retained the legacy server-side start signature as a thin adapter to the sole ArenaValidator for lifecycle GameTest compatibility",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T19:58:18.859Z",
    "resolved_at": "2026-08-26T19:58:24.406Z"
  },
  {
    "id": 9,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/lecture/ArenaValidator.java",
    "line": null,
    "description": "Added explicit entity occupancy to Professor spawn-capacity preflight because block collision alone permits overlapping spawns",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T19:58:19.238Z",
    "resolved_at": "2026-08-26T19:58:24.758Z"
  },
  {
    "id": 10,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/campaign/CampaignSavedData.java",
    "line": null,
    "description": "Persisted optional schema-v1 failed encounter and fallback reservation UUIDs for reload-safe exactly-one Retake authority",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T20:34:29.591Z",
    "resolved_at": "2026-08-26T20:34:37.748Z"
  },
  {
    "id": 11,
    "kind": "deviation",
    "phase": "02",
    "file": "src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java",
    "line": null,
    "description": "Routed persistent lifecycle GameTest retries from Contract setup through keyed RetakeService after the clean gate exposed the stale test contract",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T20:34:30.170Z",
    "resolved_at": "2026-08-26T20:34:38.436Z"
  },
  {
    "id": 12,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/lecture/RetakeService.java",
    "line": null,
    "description": "Compensated failed retry runtime starts and cleaned reserved or post-spawn fallback crash windows state-first",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T20:34:30.623Z",
    "resolved_at": "2026-08-26T20:34:38.897Z"
  },
  {
    "id": 13,
    "kind": "deviation",
    "phase": "02",
    "file": "src/main/java/dev/developershell/server/DevelopersHellRuntime.java",
    "line": null,
    "description": "Approved lifecycle bridge invokes RetakeService only after an accepted persisted reconciliation intent",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T21:04:59.081Z",
    "resolved_at": "2026-08-26T21:05:24.438Z"
  },
  {
    "id": 14,
    "kind": "deviation",
    "phase": "02",
    "file": "src/gametest/java/dev/developershell/gametest/LectureLifecycleGameTests.java",
    "line": null,
    "description": "Approved retained lifecycle expectations include issued copy only when reconciliation materializes a Form",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-08-26T21:04:59.387Z",
    "resolved_at": "2026-08-26T21:05:24.751Z"
  }
]
````
