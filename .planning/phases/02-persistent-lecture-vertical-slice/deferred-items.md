# Deferred Items — Phase 02

- **Plan 02-17 production victory ownership:** `ProfessorInfiniteSlidesEntity.die()` still calls the compatibility `CampaignService.victory(...)` path directly. Plan 02-17 must deauthorize or route that call so only the manager's accepted final-window `CampaignService.commitVictory(...)` result reaches `RewardService`. This file is outside Plan 02-11 ownership and was not changed here.
