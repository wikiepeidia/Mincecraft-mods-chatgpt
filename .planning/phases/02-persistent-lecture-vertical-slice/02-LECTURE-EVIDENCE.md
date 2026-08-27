# Phase 2 Lecture Evidence

Machine-produced public-safe facts for the exact fresh ordinary JAR. Automated PASS never stands in for client rendering, readability, audio, motion, model, or playability observation.

<!-- DEVELOPERS_HELL_PHASE2_EVIDENCE_V1_BEGIN -->
evidence_schema: developers_hell_phase2_v1
evidence_timestamp_utc: 2026-08-27T05:05:33.9333279Z
source_object_format: sha1
source_commit: 494ccf0773611c1e8061b64eefdae9e11aa45f12
source_tree: 4267ce1eb009444b4a039aebfe53017267e0b669
source_worktree_status: CLEAN
java_runtime: Eclipse Temurin 25.0.4+7 checksum-bound
gradle_command: gradlew.bat pinned-jvm --offline clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle
gradle_transaction_exit: 0
gradle_log_sha256: 3078ed98dd343f28fe86dfaad11cdb18578e46e0269d5796f6d024d7f8eb35c1
foundation_audit_command: powershell.exe scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath build/libs/developers-hell-0.1.0.jar
foundation_audit_exit: 0
foundation_audit_status: PASS
foundation_audit_log_sha256: 2adff4c2c74e7e0624d3d52c4c18b8f4a769f62e32999e0b97f6ad5520403368
test_manifest_sha256: 8839bafe8ddcd5b4e89faf84b6cb419d97ab97864296ccf23b3ea4cae78ad5b5
unit_test_report_files: 8
unit_receipt_count: 88
unit_receipt_failures: 0
unit_receipt_errors: 0
unit_receipt_skipped: 0
unit_receipt_sha256: 1a763e159409837ccf8da3682789363c547ffc9713bee56536f389aaa7227ba0
gametest_report_files: 1
gametest_receipt_count: 47
gametest_receipt_failures: 0
gametest_receipt_errors: 0
gametest_receipt_skipped: 0
gametest_receipt_sha256: 37d2309a8891b89b01bccf9f25014c836a606d563396e2533b4c6c50e6090546
ordinary_jar_size: 327029
ordinary_jar_entries: 198
ordinary_jar_entries_sha256: 005db33dda716cf7e94d0bce32eaafec7ef9a2e826d126c15b8cd4f1f0efda90
production_server_profile: local_automated_loopback_offline_no_query_no_rcon_no_resource_pack
previous_distribution_sha256: 3e691776e6bb0f1371eedb341cc5874fc107bd254769e8bc0abb5fffb783907c
source_jar_sha256: cd114cab56f6d697aaa0400373dc87b709b778c8952e753685dd815953e494a0
build_jar_sha256: cd114cab56f6d697aaa0400373dc87b709b778c8952e753685dd815953e494a0
distribution_sha256: cd114cab56f6d697aaa0400373dc87b709b778c8952e753685dd815953e494a0
hash_equality_status: PASS
hash_equality_detail: source_build_distribution_sha256_equal
source_archive_audit_status: PASS
source_archive_audit_detail: dependency_source_archive_policy
phase2_archive_audit_status: PASS
phase2_archive_audit_detail: production_contract_present_forbidden_residue_absent
server_ready_status: PASS
server_ready_detail: DEVELOPERS_HELL_SERVER_FIRST_TICK_READY
server_stop_cleanup_status: PASS
server_stop_cleanup_detail: DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE
server_ordered_shutdown_status: PASS
server_ordered_shutdown_detail: first_tick_then_cleanup_then_stop_then_all_dimensions_saved
production_server_exit: 0
clean_exit_status: PASS
clean_exit_detail: production_server_exit_zero
owned_server_root_pid: 25296
owned_child_count: 7
owned_child_cleanup_status: PASS
owned_child_cleanup_detail: zero_owned_child_residue
production_server_log_sha256: 033b97e2e07c7e5d577bf6b1c040fd1d3ae23076984597c75fbaccab2489a91b
<!-- DEVELOPERS_HELL_PHASE2_EVIDENCE_V1_END -->

## Automated validation rows

| Automated ID | Measured receipt group | Receipt | Status |
|---|---|---|---|
| 02-CFG-01 | strict config defaults, rejection, and redaction receipt | unit=17; gametest=0; gates=none; receipt_sha256=b3d643de6abb852b2ef3c374e065641a378120f1339fdc51a007dea9eb4cceb0 | PASS |
| 02-STATE-01 | monotonic replay-safe persistence receipt | unit=32; gametest=0; gates=none; receipt_sha256=1bfbf2b42278eda41f93f07afffe0bfed11616646b3a45cd55f7c155872310c7 | PASS |
| 02-GEO-01 | bounded deterministic arena and attack geometry receipt | unit=11; gametest=13; gates=none; receipt_sha256=34f6585a572f5d33a90eeb44d313c1b65f640920c1e3496cc7bbc39d133d23d1 | PASS |
| 02-ITEM-01 | contract, retake, and remote transaction receipt | unit=5; gametest=22; gates=none; receipt_sha256=47cfca1dba9fb89486e0729bb6bf7cff7c5ca122d0415a7aa13120992103991c | PASS |
| 02-BOSS-01 | boss identity, acts, damage, and vulnerability receipt | unit=15; gametest=5; gates=none; receipt_sha256=0b3f026999ead27682b14481b9cf8100641d6dbe0c8953c489fc9190ff90a04b | PASS |
| 02-LIFE-01 | terminal, reload, orphan, and server-stop cleanup receipt | unit=0; gametest=3; gates=none; receipt_sha256=9f16259fb0c0c883d13d674e48b4365191f0cea159c0cb65b9eb038f4fa29bfb | PASS |
| 02-REWARD-01 | exactly-once reward fallback and recovery receipt | unit=22; gametest=14; gates=none; receipt_sha256=d0ce8f8e783a298813f97de4de352967ee4c8c94b1ca9f1a579b42a63fe5219c | PASS |
| 02-DISC-01 | survival discovery and registered foundation receipt | unit=0; gametest=2; gates=none; receipt_sha256=83ba27daa4aacce7c807bcff0633ef22578fed5c2e25047b4700b724e1bfea12 | PASS |
| 02-GATE-01 | module, vanilla harness, build, audit, archive, and server receipt | unit=8; gametest=1; gates=gradle_transaction,foundation_audit,source_archive,phase2_archive,production_server; receipt_sha256=f5bfb7b59419c4a240fc31a3ce58fdca7fe55266610bdf61895616c8973be03c | PASS |

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
