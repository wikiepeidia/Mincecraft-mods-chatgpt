---
schema_version: 1
open_count: 0
waived_count: 0
fixed_count: 3
total_count: 3
last_updated: 2026-08-26T17:09:10.764Z
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
  }
]
````
