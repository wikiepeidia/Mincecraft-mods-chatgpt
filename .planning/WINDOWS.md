---
schema_version: 1
open_count: 0
waived_count: 0
fixed_count: 6
total_count: 6
last_updated: 2026-08-26T19:27:38.589Z
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
  }
]
````
