# Phase 1 Toolchain Evidence

This record binds the foundation scaffold to first-party artifacts and fresh local command output. Paths are recorded only as public-safe repository-relative classes; the verified JDK's canonical absolute path is intentionally omitted.

jdk_artifact_filename: OpenJDK25U-jdk_x64_windows_hotspot_25.0.4_7.zip
jdk_artifact_source: https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4_7.zip
jdk_checksum_source: https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4_7.zip.sha256.txt
jdk_official_sha256: 7caab7db43bf4b94a2e6252c699e70d90084f9aa7c943cd3414761fd540937ae
jdk_archive_sha256: 7caab7db43bf4b94a2e6252c699e70d90084f9aa7c943cd3414761fd540937ae
jdk_runtime_version: 25.0.4+7
jdk_vendor: Eclipse Adoptium
jdk_vm_vendor: Eclipse Adoptium
jdk_arch: amd64
jdk_java_sha256: 1dfe0b08636bc74b56db5e246f038cfe67c18f567053373fa601d310f29ed9da
jdk_javac_sha256: a41dbdf0740275e6941129959bb3f95fe30244694b92d7406cb87ad66523d85c
jdk_path_class: ignored-work-toolchain-child
jdk_path_sha256: 473c97bd49dd12ff8c1abf5dd67e7a4c56eccbd6a07c53d1aafdecf217622716
java_version: 25.0.4+7
javac_version: 25.0.4
gradle_java_binding: exact verified JDK only; auto-detect=false; auto-download=false

template_url: https://github.com/FabricMC/fabric-example-mod.git
template_ref: 26.2
template_commit: 34080f0b6644dd726519d578f339f8e4e50ad331
template_remote_commit: 34080f0b6644dd726519d578f339f8e4e50ad331
template_tree: a06d8cdd0b843df36bf692942efb734c77706e62
template_diff_mode: fixed-one-file
template_origin_verified: true
template_clean_before_patch: true
template_current_diff: gradle.properties

gradle_version: 9.5.1
minecraft_version: 26.2
loader_version: 0.19.3
fabric_api_version: 0.158.0+26.2
java_release: 25
loom_requested: 1.17.19
loom_selected: 1.17.19
resolved_loom_build: 1.17.19
resolved_loom_implementation_version: 1.17.19
resolved_loom_sha256: ad331736d7ee6cd5f21c45b19584b951c716ba5de8ace8662b42813d110452b8

fixed_help_command: .\gradlew.bat help --no-daemon --stacktrace --init-script ..\loom-resolution.init.gradle
fixed_help_exit: 0
fixed_help_log: .work/fixed-help.log
fixed_help_log_sha256: d1cd7fb3837dbd8b44165a95b1d297bf442ecbc07d4e6b86efb6977717121295
fixed_help_timestamp_utc: 2026-08-25T19:42:01Z
fixed_build_command: .\gradlew.bat build --no-daemon --stacktrace --init-script ..\loom-resolution.init.gradle
fixed_build_exit: 0
fixed_build_log: .work/fixed-build.log
fixed_build_log_sha256: 234c797a62da828d06e42eeb10b9e2478cbcc8035123e5edb53e96c2e62f7d20
fixed_build_timestamp_utc: 2026-08-25T19:43:07Z
fixed_resolution_command: .\gradlew.bat help --no-daemon --stacktrace --init-script ..\loom-resolution.init.gradle
fixed_resolution_exit: 0
fixed_resolution_log: .work/fixed-resolution.log
fixed_resolution_log_sha256: 19f84e990b6495e449482d4e02e9813b891d7a8604dab8c1e8488d2b68b540e6
fixed_resolution_timestamp_utc: 2026-08-25T19:44:24Z

snapshot_fallback_used: false
fixed_failure_command: not-applicable-fixed-success
fixed_failure_exit: not-applicable-fixed-success
fixed_failure_category: not-applicable-fixed-success
fixed_failure_log: not-applicable-fixed-success
fixed_failure_log_sha256: not-applicable-fixed-success
fallback_help_command: not-applicable-fixed-success
fallback_help_exit: not-applicable-fixed-success
fallback_help_log: not-applicable-fixed-success
fallback_help_log_sha256: not-applicable-fixed-success
fallback_build_command: not-applicable-fixed-success
fallback_build_exit: not-applicable-fixed-success
fallback_build_log: not-applicable-fixed-success
fallback_build_log_sha256: not-applicable-fixed-success
fallback_resolution_command: not-applicable-fixed-success
fallback_resolution_exit: not-applicable-fixed-success
fallback_resolution_log: not-applicable-fixed-success
fallback_resolution_log_sha256: not-applicable-fixed-success

wrapper_distribution: https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
wrapper_distribution_sha256: absent-in-official-template
wrapper_sha256: 497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7

proof_started_utc: 2026-08-25T19:38:01Z
proof_completed_utc: 2026-08-25T19:45:11Z
observed_anchors: help=BUILD SUCCESSFUL; build=BUILD SUCCESSFUL; resolution=BUILD SUCCESSFUL; each stream reported selected=1.17.19, resolved=1.17.19, implementation=1.17.19, and the same Loom artifact SHA-256
