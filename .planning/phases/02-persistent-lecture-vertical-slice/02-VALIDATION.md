---
phase: 02
slug: persistent-lecture-vertical-slice
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-26
---

# Phase 2 — Validation Strategy

> Nyquist contract for the first persistent, server-authoritative Contract → Professor Infinite Slides → reward loop. Automated evidence must prove safe configuration, monotonic persistence, deterministic combat, complete cleanup, and exactly-once rewards before a manual client smoke is requested.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Unit framework** | `net.fabricmc:fabric-loader-junit:0.19.3` on JUnit Platform |
| **Integrated framework** | Fabric server GameTest from Fabric API `0.158.0+26.2` |
| **Config files** | `build.gradle`; Phase 2 adds focused fixtures under `src/test/resources` only if needed |
| **JDK** | Checksum-pinned `.work/toolchain/temurin-25.0.4+7-x64`; the newly installed system JDK 25 is convenient but not the reproducibility authority |
| **Quick run command** | `./gradlew.bat test --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle` |
| **Full suite command** | `./gradlew.bat auditDirectDependencies build --offline --no-daemon --console=plain --init-script scripts/loom-resolution.init.gradle` |
| **Source/archive audit** | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath ./build/libs/developers-hell-0.1.0.jar` |
| **Estimated runtime** | Unit sample: under 90 seconds warm-cache; full build/GameTest/audit: under 6 minutes warm-cache |

---

## Sampling Rate

- **After every pure-domain task commit:** run the focused class or `test` task under the pinned JDK.
- **After every Minecraft-facing task commit:** run `test` plus a compile/build sample; never defer signature discovery to the end of a plan.
- **After every plan wave:** run the full offline build so configured server GameTests execute and the ordinary production JAR remains test-free.
- **Before `/gsd:verify-work`:** full offline build, source/archive audit, dedicated-server smoke, and the complete requirement-to-test matrix must be green.
- **Max feedback latency:** 90 seconds for pure logic; 6 minutes for the integrated phase sample.

---

## Per-Task Verification Map

The planner must replace provisional task IDs with its final IDs without dropping any row.

| Provisional Task | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command / Evidence | File Exists | Status |
|------------------|-------------|------------|-----------------|-----------|------------------------------|-------------|--------|
| 02-CFG-01 | FND-05 | T-02-01 | Missing config uses immutable safe defaults; malformed, duplicate, unknown, symlinked, or oversized input reports every actionable error, applies no partial values, and is never overwritten | unit | `DevHellConfigTest`; 75-test fresh unit report; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-STATE-01 | FND-06 | T-02-02 | Codec round-trips supported schema; future schema fails closed; progress, entitlements, reward IDs, and cooldown deadlines never regress or duplicate | unit | `CampaignCodecTest`, `CampaignReducerTest`; fresh unit report; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-GEO-01 | CAMP-02, LECT-01 | T-02-03 | Arena validation is deterministic and atomic; invalid placement consumes nothing; lane, quiz, and ring geometry remain bounded and reproducible from a logged seed | unit | `LectureGeometryTest`, `LectureStateMachineTest`; fresh unit report; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-ITEM-01 | CAMP-01, LECT-02 | T-02-04 | Contract/Retake/Remote interactions commit state before effects, reject wrong owner/desk/stale attempt, and restore the server-owned 400-tick cooldown without client authority | unit + GameTest | `ContractArenaGameTests`, `RetakeGameTests`, `RemoteGameTests`; fresh `runGameTest`; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-BOSS-01 | LECT-01 | T-02-05 | Professor identity is registered server-side, client rendering stays in `src/client`, three acts have redundant telegraphs, and only the owning player can damage during vulnerability windows | unit + GameTest | `LectureStateMachineTest`, `LectureBossGameTests`, common/client audit; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-LIFE-01 | FND-06, FND-07, LECT-02 | T-02-06 | Death, escape, timeout, dimension change, disconnect, unload, reload, abort, and victory remove every encounter-owned runtime object and converge to one safe persisted state | GameTest | `LectureLifecycleGameTests`; real ordered stop callback; zero owned-child residue; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-REWARD-01 | LECT-02 | T-02-07 | Attendance Sheet entitlement and first Remote are committed once; replayed/stale callbacks are no-ops; full inventory uses one tracked fallback and recovery never creates two Forms | unit + GameTest | `CampaignReducerTest`, `RewardGameTests`; fresh unit/GameTest reports; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-DISC-01 | CAMP-01 | — | A survival player discovers the recipe, reads bounded localized tooltips, crafts the Contract, and starts at a valid lectern without an admin command | resource + GameTest | `FoundationGameTests`; recipe/advancement/lang/model archive entries; `02-LECTURE-EVIDENCE.md` | ✅ | ✅ green |
| 02-GATE-01 | FND-07 | T-02-08 | Exact direct dependencies remain frozen, production JAR contains no GameTest/client-leak/network/API/credential residue, and a dedicated server reaches ready state | build + audit + smoke | Fresh pinned-JDK offline transaction; fail-closed audit adjudication; Phase 2 archive gate; ordered production-server clean stop; SHA-256 `3e691776…3907c` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/dev/developershell/config/DevHellConfigTest.java` — strict whole-file configuration matrix for FND-05.
- [x] `src/test/java/dev/developershell/campaign/CampaignCodecTest.java` — supported/future/corrupt schema and round-trip coverage for FND-06.
- [x] `src/test/java/dev/developershell/campaign/CampaignReducerTest.java` — monotonic progression, attempt identity, reward replay, Retake entitlement, and cooldown-deadline coverage.
- [x] `src/test/java/dev/developershell/lecture/LectureGeometryTest.java` — arena, retry search, three lanes, three quiz pads, and attendance ring bounds.
- [x] `src/test/java/dev/developershell/lecture/LectureStateMachineTest.java` — deterministic act deadlines/transitions, vulnerability, and nonlethal bounds.
- [x] Seven focused GameTest classes cover registration, interaction, lifecycle, cleanup, persistence normalization, recovery, rewards, and Remote behavior.
- [x] Resource assertions cover recipe, advancement, translation keys, placeholder models, and production archive exclusions.
- [x] The bounded Phase 2 verifier/evidence artifact records commands, raw/adjudicated exits, test reports, JAR hash, server readiness, source scan, and cleanup without private paths or personal data.

---

## Manual-Only Verifications

| Backstop ID | Behavior | Requirement | Status |
|-------------|----------|-------------|--------|
| MANUAL-UI-01 | Boss/action copy at small, normal, and large GUI scales plus a narrow window | LECT-01, CAMP-01 | PENDING |
| MANUAL-I18N-02 | Held-out long localization wrapping for quiz and fixed-budget boss/action strings | LECT-01 | PENDING |
| MANUAL-EFFECTS-03 | Normal and reduced-effects lane, pad, and ring geometry equivalence | LECT-01 | PENDING |
| MANUAL-ACCESS-04 | Muted audio and minimal particles retain enough text/stable-shape cues to complete every act | LECT-01 | PENDING |
| MANUAL-MOTION-05 | No camera shake, nausea, full-screen flash, strobe, or stale post-cleanup marker | LECT-01, LECT-02 | PENDING |
| MANUAL-MODELS-06 | Vanilla-backed items and Professor/Homework silhouettes render without missing models | CAMP-01, LECT-01 | PENDING |
| MANUAL-REMOTE-07 | Remote overlay, 20-second tooltip, recharge line, and ready cue remain recognizable without hiding boss instructions | LECT-02 | PENDING |

The accepted vanilla paper/map-style placeholder art is not a visual failure. Manual verification checks readability and state feedback, not bespoke texture quality.

---

## Validation Sign-Off

- [x] Every final plan task maps to at least one automated row or an explicit manual backstop.
- [x] Sampling continuity: no three consecutive implementation tasks lack an automated sample.
- [x] All Wave 0 test paths exist before the corresponding production behavior is declared green.
- [x] No watch-mode flags, background clients, or unbounded servers are used in automated verification.
- [x] FND-05 through LECT-02 all have both a named test and an observable acceptance condition.
- [x] Failure/reload cleanup is tested before any manual client UAT.
- [x] Exactly-once rewards are proven with duplicate, stale, wrong-owner, and inventory-full cases.
- [x] Common source contains no client imports; runtime contains no operational HTTP/OpenAI/API/telemetry code or secrets.
- [x] Full suite and audit adjudication ran under the checksum-pinned Java 25 toolchain and finished within the bounded feedback window.
- [x] Manual backstops are reported honestly as seven PENDING rows; none is inferred from compilation or logs.
- [x] `nyquist_compliant: true` was set only after all nine automated rows reconciled green.

**Automated approval:** complete. **Direct-client UAT:** seven backstops PENDING.
