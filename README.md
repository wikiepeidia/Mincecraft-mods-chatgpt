# Developer's Hell

Developer's Hell is an offline-first Fabric comedy mod for Minecraft: Java Edition. Its campaign keeps the existing cursed Contract and Professor Infinite Slides path, then adds a bounded direct-start boss rush through the Jury Gauntlet, the Chairman, a fictional radiant sponsor countdown, and Codex Overdraft. The deadline build uses vanilla-rendered mobs and icons plus local scripted mechanics so the joke remains playable without accounts, services, downloads, or admin setup at runtime.

## Player installation

Use this exact runtime tuple:

- Minecraft Java Edition `26.2`
- Eclipse Temurin JDK `25.0.4+7` (64-bit Windows, HotSpot)
- Fabric Installer `1.1.2` with Fabric Loader `0.19.3`
- Fabric API `0.158.0+26.2`
- Developer's Hell `0.1.0`

Install Fabric for Minecraft `26.2`, place both Fabric API `0.158.0+26.2` and the one ordinary Developer's Hell JAR in that Fabric 26.2 profile's `mods` directory, then launch the profile with Java 25. Keep this profile separate from installations for other Minecraft, Fabric, Forge, or OptiFine versions.

The verified deadline release is:

```text
dist/developers-hell-0.1.0.jar
Size: 408,578 bytes
SHA-256: c3bd2b6e025b2d8b3c7cb056b31981c719416fb8a7270341fb1b3e737ec6d392
```

Its clean Java 25 offline gate passed 106 unit tests, 70 GameTests, the direct-dependency audit, the production build, and a bounded dedicated-server ready/clean-stop smoke. Manual client visual/readability UAT remains pending.

Install only the ordinary remapped release JAR recorded by the final build. Do not substitute a development, sources, test, or arbitrary local rebuild. Earlier Phase 1 and Phase 2 hashes remain historical foundation/Lecture evidence; neither is the checksum for this deadline boss-rush build.

Once the game files and libraries are downloaded, ordinary singleplayer play is offline. This is distinct from both Gradle's cache-only `--offline` mode and the release proof's operating-system firewall isolation:

- **Online prime:** downloads the pinned Minecraft, Fabric, Loom, Gradle, and game-profile dependencies into the local caches.
- **Same-cache Gradle offline build:** rebuilds with `--offline` using only those already primed caches; it does not prove that a running game has no network access.
- **OS-isolated runtime:** starts the exact verified Java runtime while temporary outbound block rules cover both `java.exe` and `javaw.exe`; the verifier proves the rules work and removes their exact IDs afterward.

No OpenAI or ChatGPT API, account, subscription, network service, telemetry, analytics, or remote configuration is used at runtime. “The Rich ChatGPT” and all sponsor jokes are fictional parody, not claims of sponsorship, payment, affiliation, or endorsement by OpenAI or anyone else.

## Deadline boss rush

The boss rush can be played without reconstructing the Lecture arena or changing config files:

```text
/devhell bossrush start
/devhell bossrush status
/devhell bossrush abort
```

- `start` begins or resumes the next first-clear checkpoint and can take a player directly to the Jury.
- `status` reports the saved boss-rush checkpoint and active stage.
- `abort` ends the player's active encounter and returns it to a safe saved checkpoint.
- After earning the Diploma, `/devhell bossrush replay <jury|chairman|codex>` replays an encounter without granting progression rewards again.

The bounded sequence is Jury, Chairman, the local fictional-sponsor countdown, and Codex. First clears award five deliberately vanilla-icon artifacts:

- **Signed Defense Minutes** and **Evidence Binder** from the Jury chapter.
- **Approved Revision Stamp** and **Red Pen** from the Chairman chapter.
- **Definitely Legitimate Diploma** for graduating after Codex.

The original Contract/Lecture route remains available and independent of the direct boss-rush start.

## Lecture campaign

1. Carry paper and an ink sac to unlock **A Suspicious Opportunity**, then craft the Cursed Unpaid Internship Contract with those two ingredients in any shapeless crafting grid.
2. Use the Contract on a lectern in the Overworld. The lectern becomes your Internship Desk only when it faces a loaded, world-border-safe arena with a solid `17x17` floor, four blocks of combat-interior headroom, and a safe Professor spawn. A rejected placement consumes nothing and gives localized repair guidance.
3. Survive the three acts: hold the named safe lane during **Slide Deck**, answer A/B/C on the matching shape pad during **Surprise Quiz**, and report to the named quadrant during **Attendance**. Each resolved telegraph opens a short projector-cooldown damage window.
4. Death, escape, timeout, dimension change, disconnect, unload, reload, abort, or server stop clears owned hazards and converges to safe persisted state. A failed attempt issues one owner/encounter-bound Retake Form; use it on the same Internship Desk. If recovery is needed, a game master can use `/devhell recover retake`.
5. Victory commits one Attendance Sheet entitlement and the first Infinite Slides Remote exactly once. The Sheet is proof of passing. The Remote projects a short bounded knockback slide, then follows a server-owned 20-second cooldown with recharge and one ready cue.

The session reads one complete strict local file at `config/developers-hell.json`. On first run it attempts to write the safe schema-v1 template. Missing, malformed, duplicate, unknown, symlinked, non-regular, or oversized input activates the complete immutable defaults; no partial values are applied and rejected bytes are not rewritten. `/devhell status` shows the accepted source, campaign/difficulty/accessibility values, schedules, and all eight module gates. Restart the game/server after changing the file because one validated snapshot is held for the session.

The Phase 2 evidence files document the earlier Lecture-only automated baseline. They do **not** validate the new deadline boss rush, prove visual readability, fun, audio balance, motion comfort, or model rendering, and they must not be quoted as the deadline build result.

## Known deadline limitations

This build intentionally defers the Python, terminal, Git, Totem, Duck, and Deadline chaos modules; Metadata Roulette; custom models, textures, sounds, GUI, cinematics, and other custom assets. It also does not claim exhaustive handling of exotic reward-custody or crash-window paths. Manual visual client UAT for encounter readability, rendering, comfort, and fun remains pending until a human completes it.

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
