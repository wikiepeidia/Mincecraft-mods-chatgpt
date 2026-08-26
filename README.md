# Developer's Hell

Developer's Hell is an offline-first Fabric comedy mod for Minecraft: Java Edition. The current `0.1.0` foundation build registers the Foundation Token and provides the stable base for the university/developer boss campaign.

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
SHA-256: 8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8
```

Do not substitute a development, sources, test, or separately rebuilt JAR. The clean-checkout online build, the same-cache Gradle offline build, and the retained `dist` copy all produced that identical SHA-256.

Once the game files and libraries are downloaded, ordinary singleplayer play is offline. This is distinct from both Gradle's cache-only `--offline` mode and the release proof's operating-system firewall isolation:

- **Online prime:** downloads the pinned Minecraft, Fabric, Loom, Gradle, and game-profile dependencies into the local caches.
- **Same-cache Gradle offline build:** rebuilds with `--offline` using only those already primed caches; it does not prove that a running game has no network access.
- **OS-isolated runtime:** starts the exact verified Java runtime while temporary outbound block rules cover both `java.exe` and `javaw.exe`; the verifier proves the rules work and removes their exact IDs afterward.

No OpenAI or ChatGPT API, account, subscription, network service, telemetry, analytics, or remote configuration is used at runtime. “The Rich ChatGPT” and all sponsor jokes are fictional parody, not claims of sponsorship, payment, affiliation, or endorsement by OpenAI or anyone else.

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

The authoritative release proof is the committed harness, not a build performed in a dirty working tree:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-foundation.ps1 -PrimeAndCompare -RunServerSmoke -ClientPreflight -ValidateEvidence -DistributionPath .\dist\developers-hell-0.1.0.jar -EvidencePath .\.planning\phases\01-java-25-and-fabric-26-2-foundation\01-FOUNDATION-EVIDENCE.md
```

That harness verifies itself against committed `HEAD`, creates a registered detached clean worktree at the same commit, proves the required manifest is tracked and clean, performs fresh online and same-cache-offline builds with the Loom probe, compares archive entries and hashes, copies only the verified bytes to `dist`, and removes only its exact guarded worktree registration. It also runs the comprehensive audit before and after the production client/server checks.

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
