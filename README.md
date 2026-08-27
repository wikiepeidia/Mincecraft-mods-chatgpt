# Developer's Hell

Developer's Hell is an offline-first Fabric comedy mod for Minecraft: Java Edition. The current `0.1.0` JAR contains the complete persistent Lecture vertical slice: discover and craft the cursed Contract, sign it at a valid lectern, survive Professor Infinite Slides, recover from failure, and earn the Attendance Sheet plus Infinite Slides Remote.

## Player installation

Use this exact runtime tuple:

- Minecraft Java Edition `26.2`
- Eclipse Temurin JDK `25.0.4+7` (64-bit Windows, HotSpot)
- Fabric Installer `1.1.2` with Fabric Loader `0.19.3`
- Fabric API `0.158.0+26.2`
- Developer's Hell `0.1.0`

Install Fabric for Minecraft `26.2`, place Fabric API and the one ordinary mod JAR in the profile's `mods` directory, then launch that Fabric profile with Java 25. The verified local handoff is the Git-ignored file:

```text
dist/developers-hell-0.1.0.jar
SHA-256: 768723bb534b5e35553a9b714f23c416c838b3688791a552cdf0bc91fdebf423
```

Do not substitute a development, sources, test, or separately rebuilt JAR. The current Phase 2 verifier built from clean source commit `f68a8a404c5e1318c2c860cff08e03951b715b4b` (tree `3400035f77fd13586643450284c87d1aeca6054d`), inspected the ordinary build JAR, exercised the real dedicated-server stopping callback, and atomically promoted the JAR/evidence pair only after every automated gate passed. The earlier Phase 1 clean-checkout online/offline proof remains historical foundation evidence for hash `8d3006…ea5c8`; it is not a client-UAT claim for the current Phase 2 hash.

Once the game files and libraries are downloaded, ordinary singleplayer play is offline. This is distinct from both Gradle's cache-only `--offline` mode and the release proof's operating-system firewall isolation:

- **Online prime:** downloads the pinned Minecraft, Fabric, Loom, Gradle, and game-profile dependencies into the local caches.
- **Same-cache Gradle offline build:** rebuilds with `--offline` using only those already primed caches; it does not prove that a running game has no network access.
- **OS-isolated runtime:** starts the exact verified Java runtime while temporary outbound block rules cover both `java.exe` and `javaw.exe`; the verifier proves the rules work and removes their exact IDs afterward.

No OpenAI or ChatGPT API, account, subscription, network service, telemetry, analytics, or remote configuration is used at runtime. “The Rich ChatGPT” and all sponsor jokes are fictional parody, not claims of sponsorship, payment, affiliation, or endorsement by OpenAI or anyone else.

## Lecture campaign

1. Carry paper and an ink sac to unlock **A Suspicious Opportunity**, then craft the Cursed Unpaid Internship Contract with those two ingredients in any shapeless crafting grid.
2. Use the Contract on a lectern in the Overworld. The lectern becomes your Internship Desk only when it faces a loaded, world-border-safe arena with a solid `17x17` floor, four blocks of combat-interior headroom, and a safe Professor spawn. A rejected placement consumes nothing and gives localized repair guidance.
3. Survive the three acts: hold the named safe lane during **Slide Deck**, answer A/B/C on the matching shape pad during **Surprise Quiz**, and report to the named quadrant during **Attendance**. Each resolved telegraph opens a short projector-cooldown damage window.
4. Death, escape, timeout, dimension change, disconnect, unload, reload, abort, or server stop clears owned hazards and converges to safe persisted state. A failed attempt issues one owner/encounter-bound Retake Form; use it on the same Internship Desk. If recovery is needed, a game master can use `/devhell recover retake`.
5. Victory commits one Attendance Sheet entitlement and the first Infinite Slides Remote exactly once. The Sheet is proof of passing. The Remote projects a short bounded knockback slide, then follows a server-owned 20-second cooldown with recharge and one ready cue.

The session reads one complete strict local file at `config/developers-hell.json`. On first run it attempts to write the safe schema-v1 template. Missing, malformed, duplicate, unknown, symlinked, non-regular, or oversized input activates the complete immutable defaults; no partial values are applied and rejected bytes are not rewritten. `/devhell status` shows the accepted source, campaign/difficulty/accessibility values, schedules, and all eight module gates. Restart the game/server after changing the file because one validated snapshot is held for the session.

Automated Phase 2 evidence is green for all nine validation IDs: 88 exact unit-test receipts, 55 exact GameTest receipts, dependency/source/archive gates, equal source/build/dist hashes, and a bounded dedicated server whose log ordered `FIRST_TICK_READY -> STOPPING_CLEANUP_COMPLETE -> Stopping server -> All dimensions are saved`. This does **not** prove visual readability, fun, audio balance, motion comfort, or model rendering. No client was launched by the verifier; seven direct-client backstops remain `PENDING` in `02-LECTURE-EVIDENCE.md`.

## Contributor proof contract

Use the tracked Gradle Wrapper `9.5.1` and the checksum-bound Temurin `25.0.4+7` toolchain only. The verified Temurin archive SHA-256 is `7caab7db43bf4b94a2e6252c699e70d90084f9aa7c943cd3414761fd540937ae`; Gradle Java auto-detection and auto-download must remain disabled so another local JDK cannot be selected.

The committed Loom probe is mandatory. It must select and mechanically resolve Loom `1.17.19` with artifact SHA-256 `ad331736d7ee6cd5f21c45b19584b951c716ba5de8ace8662b42813d110452b8`. With the verified archive extracted to the ignored toolchain location, the wrapper contract is:

```powershell
$jdkRoot = (Resolve-Path '.work/toolchain/temurin-25.0.4+7-x64').Path
$env:JAVA_HOME = $jdkRoot
$env:Path = "$(Join-Path $jdkRoot 'bin');$env:Path"
$gradleJdk = @("-Dorg.gradle.java.installations.paths=$jdkRoot", '-Dorg.gradle.java.installations.auto-detect=false', '-Dorg.gradle.java.installations.auto-download=false')

.\gradlew.bat @gradleJdk help --no-daemon --stacktrace --init-script scripts/loom-resolution.init.gradle
.\gradlew.bat @gradleJdk test runGameTest --no-daemon --stacktrace --init-script scripts/loom-resolution.init.gradle
.\gradlew.bat @gradleJdk clean build --no-daemon --stacktrace --init-script scripts/loom-resolution.init.gradle
.\gradlew.bat @gradleJdk --offline clean build --no-daemon --stacktrace --init-script scripts/loom-resolution.init.gradle
```

`build` includes the Java unit tests and the wrapper-owned server `runGameTest` proof; the focused command above makes both gates explicit. A successful compile or test discovery alone is not accepted. The comprehensive source/dependency/archive audit is:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\audit-foundation.ps1 -SourceAndDependencies -JarPath .\build\libs\developers-hell-0.1.0.jar
```

The production JAR must contain exactly one renamed root license, `LICENSE_developers-hell`. It must not contain unit-test output, `dev/developershell/gametest/**`, `FoundationGameTests*.class`, `ModuleGateTest*.class`, the `developers_hell_test` identity, or a `fabric-gametest` entrypoint.

Phase 1's clean-worktree online/offline and OS-isolated client machinery remains the historical foundation proof:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-foundation.ps1 -PrimeAndCompare -RunServerSmoke -ClientPreflight -ValidateEvidence -DistributionPath .\dist\developers-hell-0.1.0.jar -EvidencePath .\.planning\phases\01-java-25-and-fabric-26-2-foundation\01-FOUNDATION-EVIDENCE.md
```

That harness verifies itself against committed `HEAD`, creates a registered detached clean worktree at the same commit, proves the required manifest is tracked and clean, performs fresh online and same-cache-offline builds with the Loom probe, compares archive entries and hashes, copies only the verified bytes to `dist`, and removes only its exact guarded worktree registration. It also runs the comprehensive audit before and after the production client/server checks.

The current Phase 2 artifact/evidence transaction is:

```powershell
$jdkRoot = (Resolve-Path '.work/toolchain/temurin-25.0.4+7-x64').Path
$env:JAVA_HOME = $jdkRoot
$env:Path = "$(Join-Path $jdkRoot 'bin');$env:Path"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lecture.ps1 -SelfCheck
pwsh -NoProfile -File .\scripts\verify-lecture.ps1 -SelfCheck
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lecture.ps1 -Verify -EvidencePath .\.planning\phases\02-persistent-lecture-vertical-slice\02-LECTURE-EVIDENCE.md -DistributionPath .\dist\developers-hell-0.1.0.jar
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lecture.ps1 -ValidateEvidence -EvidencePath .\.planning\phases\02-persistent-lecture-vertical-slice\02-LECTURE-EVIDENCE.md -DistributionPath .\dist\developers-hell-0.1.0.jar
```

`-Verify` runs the pinned-JDK same-cache offline `clean test runGameTest auditDirectDependencies build` transaction, captures the raw foundation audit result and its single pinned safe-sanitizer false positive honestly, applies an independent operational network/API scan and the Phase 2 archive contract, then supervises only its exact child process tree. The ignored smoke profile is loopback-only with online mode, query, RCON, status, and resource-pack URL disabled. The verifier requires the real stop-cleanup callback, clean exit, and zero owned PID/start-time residue before it replaces `dist` atomically.

## Two-session client check

Plan 04 automation owns the machinery. It starts one hidden supervisor, launches both visible Minecraft sessions, performs the transition from the online session to the two-rule OS-isolated session, removes the exact firewall rules in `finally`, and writes a hashed receipt under the ignored `.work` directory. The checkpoint user does not run commands, edit firewall rules, or prepare profiles.

In each visible session, the human only observes and then exits Minecraft normally:

1. `online_mod_list`: Developer's Hell `0.1.0` and Fabric API appear.
2. `online_world_entry`: a singleplayer world can be created or entered.
3. `online_token`: `/give @s developers_hell:foundation_token` shows the translated item and model.
4. `online_save_exit`: save/quit reaches the title screen, then the client exits normally.
5. `isolated_mod_list`: the same mod-list observation passes in the automatically launched isolated session.
6. `isolated_world_entry`: the isolated session enters a singleplayer world.
7. `isolated_token`: the Foundation Token command, translation, and model pass while isolated.
8. `isolated_save_exit`: save/quit and normal client exit pass while isolated.

Only the finalizer combines those eight observations with the machine receipt, rechecks the artifact and firewall-cleanup facts, and changes the public foundation evidence from `PENDING` to a validated result.

## License

Developer's Hell is released under [The Unlicense](LICENSE).
