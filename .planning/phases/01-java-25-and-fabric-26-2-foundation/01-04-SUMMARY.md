---
phase: 01-java-25-and-fabric-26-2-foundation
plan: 04
subsystem: release-verification
tags: [fabric-26.2, java-25, offline-runtime, reproducible-build, client-uat, firewall-isolation]

requires:
  - phase: 01-03
    provides: Committed production launchers, comprehensive audit, clean-worktree verifier, and guarded client supervisor
provides:
  - Byte-identical online and same-cache-offline ordinary JAR retained as the verified distribution
  - Production dedicated-server and client readiness proof using the exact Java 25/Fabric 26.2 tuple
  - Hashed two-client receipt with exact two-rule network isolation and zero-member cleanup
  - Public-safe accepted world-entry, Foundation Token, and normal save/exit UAT evidence
affects: [phase-2-lecture-slice, phase-6-release, distribution, runtime-verification]

actuals:
  tokens: 11561
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns: [detached-clean reproducibility, byte-identical distribution handoff, exact-rule runtime isolation, receipt-bound human UAT]

key-files:
  created:
    - .planning/phases/01-java-25-and-fabric-26-2-foundation/01-FOUNDATION-EVIDENCE.md
  modified:
    - README.md
    - scripts/verify-foundation.ps1
    - src/main/java/dev/developershell/DevelopersHell.java

key-decisions:
  - "The release handoff remains one ordinary JAR whose online, same-cache-offline, retained-distribution, and runtime-copy SHA-256 values are identical."
  - "The Foundation Token's vanilla paper/map-style icon is accepted as the Phase 1 MVP appearance; bespoke art remains release-phase polish rather than a foundation blocker."
  - "The newly installed system Temurin 25 is useful for local convenience, while reproducible project proof remains bound to the checksum-pinned Temurin 25.0.4+7 toolchain under .work."

patterns-established:
  - "Receipt-bound UAT: normalize public evidence only after a complete canonical-payload-hashed supervisor receipt, normal client exits, equal artifact hashes, and independent firewall cleanup checks."
  - "Fail-closed server shutdown: require first-tick readiness, ordered vanilla shutdown/save markers, and exact descendant-process closure."

requirements-completed: [FND-01, FND-02, FND-03]

coverage:
  - id: D1
    description: "A detached clean checkout using the pinned Java 25/Fabric 26.2 tuple produces byte-identical online and same-cache-offline ordinary JARs."
    requirement: FND-02
    verification:
      - kind: integration
        ref: "01-FOUNDATION-EVIDENCE.md#detached_online_probe, detached_offline_probe, online_jar_sha256, offline_jar_sha256"
        status: pass
      - kind: integration
        ref: "scripts/verify-foundation.ps1 -ValidateEvidence -RequireUatPass"
        status: pass
    human_judgment: false
  - id: D2
    description: "The exact distribution reaches production dedicated-server and two distinct production-client readiness states without client-only linkage, then exits cleanly."
    requirement: FND-03
    verification:
      - kind: e2e
        ref: "01-FOUNDATION-EVIDENCE.md#server_online_ready, server_isolated_ready, client_online_ready, client_isolated_ready"
        status: pass
      - kind: integration
        ref: ".work/interactive-uat-c2dd34bac9984a7bb042bd53ed7a5de5/receipt.json"
        status: pass
    human_judgment: false
  - id: D3
    description: "The installed mod enters and saves a singleplayer world, exposes the translated Foundation Token, and repeats under verified operating-system network isolation."
    requirement: FND-01
    verification:
      - kind: manual_procedural
        ref: "01-FOUNDATION-EVIDENCE.md#online_mod_list through isolated_save_exit"
        status: pass
      - kind: e2e
        ref: "01-FOUNDATION-EVIDENCE.md#client_isolation_status and client_isolation_cleanup"
        status: pass
    human_judgment: false

duration: 15h
completed: 2026-08-26
status: complete
---

# Phase 1 Plan 04: Installable Offline Artifact and Human World Proof Summary

**One checksum-bound ordinary JAR now has clean-checkout online/offline parity, production server and client proof, exact two-rule runtime isolation, and accepted world-entry UAT backed by a hashed supervisor receipt.**

## Performance

- **Duration:** 15h (includes the blocking-human checkpoint and workday pause)
- **Started:** 2026-08-25T21:03:51Z
- **Completed:** 2026-08-26T12:05:00Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- Produced `dist/developers-hell-0.1.0.jar` as the exact `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8` ordinary JAR, equal to both detached online and same-cache-offline builds.
- Proved the exact distribution on production dedicated-server and client paths, including two distinct normally exiting client processes and no client-only linkage on the server.
- Proved reachable-then-blocked Java runtime networking with exactly two scoped rules, complete receipt hashing, rule absence, zero group membership, and restoration of the original disabled Public firewall profile.
- Finalized all eight public-safe UAT markers after the user accepted the visual checkpoint and the machine receipt independently authenticated readiness, isolation, artifact identity, normal exits, and cleanup.

## Task Commits

1. **Task 1: Prove the ordinary JAR, same-cache rebuild, comprehensive offline surface, and both production sides** - `5b2ee34`, `143e355`, `4cdf4de` (fix, fix, docs)
2. **Task 2: Observe production-client install, world entry, save/quit, and isolated repeat** - blocking-human checkpoint; no commit
3. **Task 3: Finalize, validate, and commit the human UAT evidence** - `34e162d` (docs)

**Plan metadata:** committed separately with this summary and sequential tracking updates.

## Files Created/Modified

- `.planning/phases/01-java-25-and-fabric-26-2-foundation/01-FOUNDATION-EVIDENCE.md` - Records normalized provenance, tuple, build parity, production runtime, receipt, isolation, cleanup, and accepted UAT markers.
- `README.md` - Documents the exact player tuple, one-JAR handoff, contributor build/audit contract, offline distinctions, and bounded two-session client procedure.
- `scripts/verify-foundation.ps1` - Uses PowerShell-version-safe atomic replacement and a first-tick/ordered-save production-server shutdown protocol.
- `src/main/java/dev/developershell/DevelopersHell.java` - Emits the bounded first-server-tick readiness marker consumed by production smoke supervision.

## Decisions Made

- Kept the installable handoff byte-identical to the clean detached build rather than rebuilding or substituting another JAR after UAT.
- Retained the checksum-pinned project JDK as authoritative even though `java` and `javac` now resolve to system Temurin `25.0.4.1`; the user-level `JAVA_HOME` also points to that system JDK.
- Accepted the Foundation Token's vanilla paper/map-style model for the MVP. This is cosmetic debt, not a missing registry/model or functional failure.
- Treated the user's explicit overall acceptance and continuation instruction as the human checkpoint result; only the machine receipt supplied process, artifact, firewall, and cleanup facts.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Made atomic receipt/status replacement work in Windows PowerShell and PowerShell 7**
- **Found during:** Task 1 production harness execution
- **Issue:** Passing `$null` to `File.Replace` selected an incompatible overload on the installed PowerShell runtimes.
- **Fix:** Passed the CLR null-string sentinel at all three atomic replacement sites.
- **Files modified:** `scripts/verify-foundation.ps1`
- **Verification:** Atomic write self-tests and the complete guarded client receipt passed.
- **Committed in:** `5b2ee34`

**2. [Rule 1 - Bug] Prevented premature production-server termination**
- **Found during:** Task 1 production server smoke
- **Issue:** The harness could send `stop` immediately after vanilla readiness and then terminate only the command-shell root before server descendants finished saving.
- **Fix:** Added a server-first-tick marker, flushed a graceful stop command, required ordered shutdown/save markers, validated exact descendant closure, and kept cleanup scoped to the captured process tree.
- **Files modified:** `scripts/verify-foundation.ps1`, `src/main/java/dev/developershell/DevelopersHell.java`
- **Verification:** Sequential online/isolated server smokes and missing-marker cleanup tests passed; the final evidence validator retained both clean-stop markers.
- **Committed in:** `143e355`

**3. [Rule 3 - Blocking] Restored the ignored distribution handoff before final validation**
- **Found during:** Task 3 final receipt validation
- **Issue:** The ignored `dist` copy was absent while the byte-identical verified ordinary JAR remained in `build/libs`.
- **Fix:** Copied only the candidate whose SHA-256 exactly matched Task 1 evidence and the receipt's pre/post distribution/runtime hashes.
- **Files modified:** `dist/developers-hell-0.1.0.jar` (ignored distribution artifact)
- **Verification:** Rehash returned `8d3006dca37b3987ccf949f8881ecc5fc56f20d78d0af665f2b11b71c77ea5c8`; unchanged `-ValidateEvidence -RequireUatPass` exited zero.
- **Committed in:** Not committed by design; `dist/` is the ignored player handoff.

### User-Authorized Checkpoint Interpretation

- The planned eight-line PASS form was not returned verbatim. The user instead explicitly accepted the loaded model—including the vanilla map/paper-style icon—and instructed autonomous work to continue.
- Per that user override, the eight bounded checkpoint fields were normalized to PASS. No free-form response was copied into evidence, and every process, distribution, isolation, receipt, and cleanup claim still came from the authenticated machine receipt and unchanged validator.

---

**Total deviations:** 3 auto-fixed (2 Rule 1, 1 Rule 3) plus one transparent user-authorized checkpoint interpretation.
**Impact on plan:** The fixes strengthened portability, graceful shutdown, and artifact continuity without changing the frozen platform tuple or expanding gameplay scope.

## Issues Encountered

- The host's Public firewall profile was disabled, so exact application rules would not have enforced isolation. After explicit user approval, a temporary elevated guard enabled only that profile for the supervised proof and restored both configured and effective states to disabled in `finally`.
- A concurrent external commit advanced `main` and `origin/main` after Task 3. It was preserved untouched and excluded from this plan's implementation claims.

## Accepted Cosmetic Debt

- The Foundation Token deliberately uses the current vanilla paper/map-style model. The user accepted it for Phase 1; original item art can replace it during the asset/release phase without changing the stable item ID.

## Java 25 Environment Update

- `java --version` now reports Eclipse Temurin `25.0.4.1+1` and `javac --version` reports `25.0.4.1` from the system installation.
- The user-level `JAVA_HOME` points to the system JDK. The current long-lived agent process inherited an older process-level value, so verification explicitly selected the project toolchain instead of trusting inherited environment state.
- Project reproducibility remains proven by the locally retained, checksum-bound Eclipse Temurin `25.0.4+7` archive and executable hashes recorded in foundation evidence.

## Verification

- `scripts/verify-foundation.ps1 -ValidateEvidence -RequireUatPass ...` - PASS.
- Canonical receipt payload SHA-256 recomputation - PASS (`28caa498d6eb0619e37f99cb1e5a5aa211f47ff0b9a2f626502bdf9341754436`).
- Exact interactive firewall rule absence and group membership zero - PASS.
- Public firewall PersistentStore/ActiveStore restoration to disabled/disabled - PASS.
- Distribution SHA-256 equality against evidence and receipt before/after values - PASS.

## User Setup Required

Use a separate Fabric `26.2` launcher profile with Java 25, Fabric Loader `0.19.3`, Fabric API `0.158.0+26.2`, and the one retained `dist/developers-hell-0.1.0.jar`. No account, API key, remote service, or runtime network connection is required by the mod.

## Next Phase Readiness

- Phase 1's exact platform, unconditional stable registry, module gate, production launch paths, and offline evidence are ready for the persistent Lecture vertical slice.
- The vanilla Foundation Token icon is accepted cosmetic debt and does not block Phase 2.
- No firewall rule, Minecraft test process, or guarded client supervisor remains active.

## Self-Check: PASSED

- All four Task 1/Task 3 implementation commits exist.
- The finalized evidence, verified distribution, README, harness, and first-tick marker files exist.
- Coverage metadata classifies all three deliverables as already proven, with no schema errors.
- The retained distribution SHA-256 still equals the clean-build and guarded-receipt value.

---
*Phase: 01-java-25-and-fabric-26-2-foundation*
*Completed: 2026-08-26*
