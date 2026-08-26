---
phase: 02
slug: persistent-lecture-vertical-slice
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 02-CFG-01 | FND-05 | T-02-01 | Missing config uses immutable safe defaults; malformed, duplicate, unknown, symlinked, or oversized input reports every actionable error, applies no partial values, and is never overwritten | unit | `DevHellConfigTest`; source assertion for a fixed 64 KiB limit and all-or-default result | ❌ W0 | ⬜ pending |
| 02-STATE-01 | FND-06 | T-02-02 | Codec round-trips supported schema; future schema fails closed; progress, entitlements, reward IDs, and cooldown deadlines never regress or duplicate | unit | `CampaignCodecTest` and `CampaignReducerTest` | ❌ W0 | ⬜ pending |
| 02-GEO-01 | CAMP-02, LECT-01 | T-02-03 | Arena validation is deterministic and atomic; invalid placement consumes nothing; lane, quiz, and ring geometry remain bounded and reproducible from a logged seed | unit | `LectureGeometryTest` and `LectureStateMachineTest` | ❌ W0 | ⬜ pending |
| 02-ITEM-01 | CAMP-01, LECT-02 | T-02-04 | Contract/Retake/Remote interactions commit state before effects, reject wrong owner/desk/stale attempt, and restore the server-owned 400-tick cooldown without client authority | unit + GameTest | Item transaction tests plus `LectureGameTests` recovery/cooldown cases | ❌ W0 | ⬜ pending |
| 02-BOSS-01 | LECT-01 | T-02-05 | Professor identity is registered server-side, client rendering stays in `src/client`, three acts have redundant telegraphs, and only the owning player can damage during vulnerability windows | unit + GameTest | State-machine tests, client/common import scan, real spawn/act transition GameTest | ❌ W0 | ⬜ pending |
| 02-LIFE-01 | FND-06, FND-07, LECT-02 | T-02-06 | Death, escape, timeout, dimension change, disconnect, unload, reload, abort, and victory remove every encounter-owned runtime object and converge to one safe persisted state | GameTest | `LectureGameTests` lifecycle matrix; stale entity/orphan rejection assertions | ❌ W0 | ⬜ pending |
| 02-REWARD-01 | LECT-02 | T-02-07 | Attendance Sheet entitlement and first Remote are committed once; replayed/stale callbacks are no-ops; full inventory uses one tracked fallback and recovery never creates two Forms | unit + GameTest | Reducer replay tests plus inventory-full/recovery/victory GameTests | ❌ W0 | ⬜ pending |
| 02-DISC-01 | CAMP-01 | — | A survival player discovers the recipe, reads bounded localized tooltips, crafts the Contract, and starts at a valid lectern without an admin command | resource + GameTest | Recipe/advancement/lang/model assertions and valid-desk GameTest | ❌ W0 | ⬜ pending |
| 02-GATE-01 | FND-07 | T-02-08 | Exact direct dependencies remain frozen, production JAR contains no GameTest/client-leak/network/API/credential residue, and a dedicated server reaches ready state | build + audit + smoke | Full suite, `audit-foundation.ps1`, bounded `runServer` smoke | ✅ base / ❌ Phase 2 evidence | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/dev/developershell/config/DevHellConfigTest.java` — strict whole-file configuration matrix for FND-05.
- [ ] `src/test/java/dev/developershell/campaign/CampaignCodecTest.java` — supported/future/corrupt schema and round-trip coverage for FND-06.
- [ ] `src/test/java/dev/developershell/campaign/CampaignReducerTest.java` — monotonic progression, attempt identity, reward replay, Retake entitlement, and cooldown-deadline coverage.
- [ ] `src/test/java/dev/developershell/lecture/LectureGeometryTest.java` — arena, retry search, three lanes, three quiz pads, and attendance ring bounds.
- [ ] `src/test/java/dev/developershell/lecture/LectureStateMachineTest.java` — deterministic act deadlines/transitions, vulnerability, and nonlethal bounds.
- [ ] `src/gametest/java/dev/developershell/gametest/LectureGameTests.java` — real registration, interaction, lifecycle, cleanup, persistence normalization, recovery, and victory/replay cases.
- [ ] Resource assertions cover recipe, advancement, translation keys, placeholder models, and production archive exclusions.
- [ ] A bounded Phase 2 verification script/evidence artifact records exact commands, exits, test reports, JAR hash, server readiness, source scan, and cleanup without private paths or personal data.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Boss/action copy, shape telegraphs, subtitles, and placeholder items remain understandable at small/normal/large GUI scales and one narrow window | LECT-01, CAMP-01 | Native rendering, text clipping, audio balance, and cognitive readability are not proven by server GameTests | Use an isolated launcher profile only after automated gates pass; complete one valid fight and inspect each act at the declared scales |
| Full survival loop feels playable: discover/craft Contract, fail once, recover Retake, win, use Remote, observe cooldown/ready state | CAMP-01, CAMP-02, LECT-01, LECT-02 | Fun, pacing, and visible feedback require human observation | Run the exact distribution JAR in the isolated Fabric 26.2 profile; record pass/fail only, with no personal/employer text |

The accepted vanilla paper/map-style placeholder art is not a visual failure. Manual verification checks readability and state feedback, not bespoke texture quality.

---

## Validation Sign-Off

- [ ] Every final plan task maps to at least one automated row or an explicit manual backstop.
- [ ] Sampling continuity: no three consecutive implementation tasks lack an automated sample.
- [ ] All Wave 0 test paths exist before the corresponding production behavior is declared green.
- [ ] No watch-mode flags, background clients, or unbounded servers are used in automated verification.
- [ ] FND-05 through LECT-02 all have both a named test and an observable acceptance condition.
- [ ] Failure/reload cleanup is tested before any manual client UAT.
- [ ] Exactly-once rewards are proven with duplicate, stale, wrong-owner, and inventory-full cases.
- [ ] Common source contains no client imports; runtime contains no HTTP/OpenAI/API/telemetry code or secrets.
- [ ] Full suite and audit run under the checksum-pinned Java 25 toolchain and finish within the bounded feedback window.
- [ ] Manual backstops are reported honestly; they are not inferred from compilation or logs.
- [ ] `nyquist_compliant: true` is set only after all final plan rows are reconciled and green.

**Approval:** pending
