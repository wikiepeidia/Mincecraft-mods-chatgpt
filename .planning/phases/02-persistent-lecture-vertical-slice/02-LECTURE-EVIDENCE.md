# Phase 2 Lecture Evidence

Machine-produced public-safe facts for the exact fresh ordinary JAR. Automated PASS never stands in for client rendering, readability, audio, motion, model, or playability observation.

evidence_timestamp_utc: 2026-08-27T00:49:13.2120892Z
java_runtime: Eclipse Temurin 25.0.4+7 checksum-bound
gradle_command: gradlew.bat pinned-jvm --offline clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle
gradle_transaction_exit: 0
gradle_log_sha256: 43c9554093ebc5a655ae3e968bde69d60e7935db820243f5d4c7f6f87cbcb6bc
foundation_audit_command: powershell.exe scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath build/libs/developers-hell-0.1.0.jar
foundation_audit_exit: 1
foundation_audit_adjudication_exit: 0
foundation_audit_adjudication: PASS - one exact ConfigIssue.sanitizeRejectedValue denylist literal at pinned source hash; no other raw finding
foundation_audit_log_sha256: b99b8cc391d2a1882731252c419acd73bfa20a01ccdb1fc33562fd7b0a1eb615
unit_test_report_files: 7
unit_tests: 75
unit_failures: 0
unit_errors: 0
unit_skipped: 0
gametest_anchors_executed_by_runGameTest: 31
ordinary_jar_size: 285256
ordinary_jar_entries: 179
ordinary_jar_entries_sha256: 229be629ce89bf182d693efa4812fff6217a6a8d421fc6de113c65b1b94d5780
production_server_profile: local automated smoke; loopback; online-mode=false; query=false; rcon=false; no resource-pack URL
previous_distribution_sha256: 8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8
source_jar_sha256: 3e691776e6bb0f1371eedb341cc5874fc107bd254769e8bc0abb5fffb783907c
build_jar_sha256: 3e691776e6bb0f1371eedb341cc5874fc107bd254769e8bc0abb5fffb783907c
distribution_sha256: 3e691776e6bb0f1371eedb341cc5874fc107bd254769e8bc0abb5fffb783907c
hash_equality: source/build/dist hashes equal
source_archive_audit: PASS - dependency/source/archive policy
phase2_archive_audit: PASS - stable items/entities/recipe/advancement/lang/models/classes present; test/client-link/network/API/telemetry/credential residue absent; one license
server_ready: PASS - DEVELOPERS_HELL_SERVER_FIRST_TICK_READY
server_stop_cleanup_callback: PASS - DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE
server_ordered_shutdown: PASS - FIRST_TICK_READY -> STOPPING_CLEANUP_COMPLETE -> Stopping server -> All dimensions are saved
production_server_exit: 0
clean_exit: PASS
owned_server_root_pid: 9644
owned_child_count: 7
owned_child_cleanup: PASS - clean; zero owned child residue
production_server_log_sha256: ef30859c27e3ed8b6e52db7c94f2ea13401a3786cdadd9598a713f973ce848db

## Automated validation rows

| Automated ID | Existing green evidence | Status |
|---|---|---|
| 02-CFG-01 | DevHellConfigTest strict whole-file defaults/rejection matrix | PASS |
| 02-STATE-01 | CampaignCodecTest and CampaignReducerTest monotonic/replay-safe persistence | PASS |
| 02-GEO-01 | LectureGeometryTest and LectureStateMachineTest bounded deterministic geometry | PASS |
| 02-ITEM-01 | ContractArenaGameTests, RetakeGameTests, RemoteGameTests transaction/cooldown cases | PASS |
| 02-BOSS-01 | LectureStateMachineTest and LectureBossGameTests identity/acts/vulnerability | PASS |
| 02-LIFE-01 | LectureLifecycleGameTests terminal/reload/orphan/server-stop cleanup matrix | PASS |
| 02-REWARD-01 | CampaignReducerTest and RewardGameTests exactly-once/fallback/recovery cases | PASS |
| 02-DISC-01 | FoundationGameTests recipe/advancement/localization/valid-desk discovery | PASS |
| 02-GATE-01 | Fresh offline build, dependency/source/archive audit, and ordered dedicated-server clean stop | PASS |

## Direct-client backstops

These rows require a visible isolated Fabric 26.2 client run of this exact hash. No client was launched by this verifier.

| Manual backstop ID | Direct observation still required | Status |
|---|---|---|
| MANUAL-UI-01 | Small/normal/large GUI scale and narrow-window boss/action clipping or overlap | PENDING |
| MANUAL-I18N-02 | Held-out long localization wrapping for quiz plus fixed-budget boss/action strings | PENDING |
| MANUAL-EFFECTS-03 | Normal/reduced-effects lane, pad, and ring geometry equivalence | PENDING |
| MANUAL-ACCESS-04 | Muted audio/minimal particles preserve text and stable-shape completion cues | PENDING |
| MANUAL-MOTION-05 | No camera shake, nausea, full-screen flash, strobe, or stale cleanup marker | PENDING |
| MANUAL-MODELS-06 | Accepted vanilla-backed items and Professor/Homework silhouettes have no missing model | PENDING |
| MANUAL-REMOTE-07 | Remote overlay, 20-second tooltip, recharge line, and ready cue stay recognizable without covering boss instructions | PENDING |
