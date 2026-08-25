---
phase: 1
slug: java-25-and-fabric-26-2-foundation
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-25
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for the Java 25 and Fabric 26.2 walking skeleton.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Fabric Loader JUnit `0.19.3` on JUnit Platform plus Fabric API server GameTest |
| **Config file** | `build.gradle` and `src/gametest/resources/fabric.mod.json` — Wave 0 creates them |
| **Quick run command** | `.\gradlew.bat test --no-daemon` |
| **Full suite command** | `.\gradlew.bat build --no-daemon` |
| **Offline suite command** | `.\gradlew.bat --offline clean build --no-daemon` after a successful online prime |
| **Estimated runtime** | ~30–120 seconds quick and ~2–10 minutes full after dependencies are cached |

---

## Sampling Rate

- **After the scaffold task:** Run `.\gradlew.bat help --no-daemon --stacktrace`, then `.\gradlew.bat build --no-daemon --stacktrace`.
- **After every later task commit:** Run `.\gradlew.bat test --no-daemon`.
- **After every plan wave:** Run `.\gradlew.bat build --no-daemon` so server GameTests execute.
- **After common/client boundary changes:** Run `rg -n "net\.minecraft\.client|com\.mojang\.blaze3d" src/main`; zero matches are required.
- **Before `/gsd:verify-work`:** The full suite, archive inspection, online/offline hash comparison, production client smoke, and production server smoke must pass.
- **Max automated feedback latency:** 120 seconds for the warm quick suite; long dependency primes and interactive runtime smokes are phase gates, not per-task loops.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | FND-02 | T-01-01 | Only pinned first-party build inputs and the committed wrapper are used | build integration | `.\gradlew.bat help --no-daemon --stacktrace` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | FND-02 | The frozen tuple compiles on Java 25 without machine-local paths | build integration | `.\gradlew.bat build --no-daemon --stacktrace` | ❌ W0 | ⬜ pending |
| 01-02-01 | 02 | 2 | FND-04 | Module gates never change the stable registry catalog | unit | `.\gradlew.bat test --tests "*ModuleGateTest" --no-daemon` | ❌ W0 | ⬜ pending |
| 01-02-02 | 02 | 2 | FND-04 | `developers_hell:foundation_token` exists in the live registry | server GameTest | `.\gradlew.bat build --no-daemon` | ❌ W0 | ⬜ pending |
| 01-02-03 | 02 | 2 | FND-03 | Common sources contain no physical-client references | static audit | `rg -n "net\.minecraft\.client|com\.mojang\.blaze3d" src/main` returns no matches | ❌ W0 | ⬜ pending |
| 01-03-01 | 03 | 3 | FND-01, FND-02 | The expected JAR contains metadata, common/client classes, and resources | archive integration | `jar --list --file build\libs\developers-hell-0.1.0.jar` plus required-entry assertions | ❌ W0 | ⬜ pending |
| 01-03-02 | 03 | 3 | FND-02 | Primed online and cached offline builds produce the same SHA-256 | reproducibility | `.\gradlew.bat --offline clean build --no-daemon` plus `Get-FileHash` comparison | ❌ W0 | ⬜ pending |
| 01-03-03 | 03 | 3 | FND-03 | Production server reaches ready state with no client classloading failure | runtime smoke | `.\gradlew.bat runProductionServer --no-daemon` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Select a complete Eclipse Temurin JDK `25.0.4+7`; both `java` and `javac` plus the wrapper JVM must report major 25.
- [ ] Add the Gradle `9.5.1` wrapper and root build files with Minecraft `26.2`, Loader `0.19.3`, Fabric API `0.158.0+26.2`, Installer `1.1.2`, and Loom `1.17.19` first.
- [ ] If and only if the untouched fixed-Loom scaffold fails, capture the failure, switch only Loom to official `1.17-SNAPSHOT`, and record the resolved version.
- [ ] Add Loader JUnit configuration and `src/test/java/dev/developershell/module/ModuleGateTest.java`.
- [ ] Add Fabric GameTest configuration, `src/gametest/java/dev/developershell/gametest/FoundationGameTests.java`, and test-mod metadata.
- [ ] Register `runProductionClient` and `runProductionServer` with separate directories and Java 25 launchers; add Fabric API to `productionRuntimeMods`.
- [ ] Add a PowerShell evidence/check script for archive entries and online/offline SHA-256 equality.
- [ ] Merge official ignore rules for `.gradle/`, `build/`, `run/`, IDE state, dumps, recordings, and machine-local settings.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Built production client enters and cleanly exits a singleplayer world | FND-01, FND-03 | A Windows Minecraft GUI cannot be credibly verified by a forced timeout | Prime dependencies; run `.\gradlew.bat runProductionClient --no-daemon`; confirm Developer's Hell `0.1.0` loads; create and enter a world; save and quit normally; repeat from the primed directory with Gradle offline/network disabled. |
| Built production server reaches ready state and stops cleanly | FND-03 | The authoritative success line and clean stdin shutdown require observing a live process | In `run/production-server`, accept the EULA; run `.\gradlew.bat runProductionServer --no-daemon`; confirm the mod loads and the server reaches `Done`; reject client-class linkage errors; type `stop`; repeat offline from the primed directory. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verification or explicit Wave 0 dependencies.
- [ ] Sampling continuity: no three consecutive implementation tasks lack automated verification.
- [ ] Wave 0 covers every missing test/configuration reference.
- [ ] No watch-mode flags are used.
- [ ] Warm quick-suite feedback latency is under 120 seconds.
- [ ] The online/offline JAR hashes match and archive assertions pass.
- [ ] Production client and dedicated-server manual gates are recorded.
- [ ] `nyquist_compliant: true` and `wave_0_complete: true` are set only after evidence exists.

**Approval:** pending
