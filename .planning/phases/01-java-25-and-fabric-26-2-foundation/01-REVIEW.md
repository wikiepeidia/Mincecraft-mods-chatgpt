---
phase: 01-java-25-and-fabric-26-2-foundation
reviewed: 2026-08-26T12:18:25Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - build.gradle
  - gradle.properties
  - gradle/wrapper/gradle-wrapper.properties
  - gradlew
  - gradlew.bat
  - README.md
  - scripts/audit-foundation.ps1
  - scripts/loom-resolution.init.gradle
  - scripts/verify-foundation.ps1
  - settings.gradle
  - src/client/java/dev/developershell/client/DevelopersHellClient.java
  - src/gametest/java/dev/developershell/gametest/FoundationGameTests.java
  - src/gametest/resources/fabric.mod.json
  - src/main/java/dev/developershell/DevelopersHell.java
  - src/main/java/dev/developershell/module/ModuleGate.java
  - src/main/java/dev/developershell/module/ModuleId.java
  - src/main/java/dev/developershell/registry/ModItemIds.java
  - src/main/java/dev/developershell/registry/ModItems.java
  - src/main/resources/assets/developers_hell/items/foundation_token.json
  - src/main/resources/assets/developers_hell/lang/en_us.json
  - src/main/resources/assets/developers_hell/models/item/foundation_token.json
  - src/main/resources/fabric.mod.json
  - src/test/java/dev/developershell/module/ModuleGateTest.java
findings:
  critical: 2
  warning: 2
  info: 0
  total: 4
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-08-26T12:18:25Z
**Depth:** standard
**Files Reviewed:** 23
**Status:** issues_found

## Summary

The Fabric 26.2 registry, source-set split, module gate, GameTest, metadata, and vanilla Foundation Token resource chain are internally consistent. The release verifier is not safe to ship unchanged, however: firewall query failures can be mistaken for successful cleanup, and the documented automation cancellation message is silently ignored. Two additional verification gaps can strand Gradle descendants or bypass the claimed direct-dependency allowlist.

## Critical Issues

### CR-01: Firewall lookup failures are treated as proof that rules are absent

**Classification:** BLOCKER

**File:** `scripts/verify-foundation.ps1:1179-1196`

**Issue:** `Assert-WindowsFirewallControl`, `Test-ExactFirewallRuleExists`, and `Get-ExactFirewallGroupCount` all query firewall policy stores with `-ErrorAction SilentlyContinue`. A provider, permission, service, or policy-store error therefore produces the same `$null`/zero result as “rule absent.” Cleanup uses those helpers to decide whether removal is needed and to set `java_rule_absent`, `javaw_rule_absent`, `member_count_after`, and `cleanup_status = PASS` at lines 1315-1336. Final receipt/evidence validation reuses the same helpers at lines 2275-2278 and 2394-2397, so it is not an independent fail-closed check. A transient query failure can skip removal and still certify that temporary outbound-block rules are gone.

**Fix:** Query both policy stores with `-ErrorAction Stop`, catch only the specific “no matching instance” condition as absence, and propagate every other provider error. Remove rules based on the already-recorded `$javaCreated`/`$javawCreated` ownership flags rather than a fallible pre-removal existence probe, then strictly re-query both exact names and the exact group in both stores.

```powershell
function Get-ExactRuleStrict([string] $Name, [string] $Store) {
    try {
        return @(Get-NetFirewallRule -Name $Name -PolicyStore $Store -ErrorAction Stop)
    } catch {
        if ($_.FullyQualifiedErrorId -like 'CmdletizationQuery_NotFound*') { return @() }
        throw
    }
}

if ($javaCreated) {
    Remove-NetFirewallRule -Name $javaRuleId -PolicyStore PersistentStore -ErrorAction Stop
}
if ((Get-ExactRuleStrict $javaRuleId 'PersistentStore').Count -ne 0 -or
    (Get-ExactRuleStrict $javaRuleId 'ActiveStore').Count -ne 0) {
    throw 'Exact Java firewall rule remains after cleanup'
}
```

### CR-02: The UAT cancellation protocol silently ignores the automation command

**Classification:** BLOCKER

**File:** `scripts/verify-foundation.ps1:1926-1932`

**Issue:** The supervisor recognizes cancellation only when `control.json` contains `action: "CANCEL"`. Initialization also accepts any non-null JSON object that has no `action` property at lines 2053-2066. Phase 1's launch/timeout contract writes `command: "RUN"` and later `command: "CANCEL"`; both objects therefore pass initialization and the cancellation request is ignored. `Wait-ForHumanClientExit` can then continue for up to 21,600 seconds. If cancellation is requested during the isolated session, the Java/javaw block rules remain active until the human exits or the six-hour timeout finally reaches cleanup.

**Fix:** Define one strict control schema and use it for every producer and consumer. Reject missing, duplicate, or unknown action fields, bind the command to the current `session_id`, and add a self-check that starts a supervised wait, writes the canonical cancellation object, and proves prompt terminal failure plus firewall cleanup.

```powershell
function Read-ControlAction([string] $Path, [string] $ExpectedSessionId) {
    $control = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -ErrorAction Stop
    if ([string]$control.session_id -cne $ExpectedSessionId -or
        [string]$control.action -notin @('WAIT', 'CANCEL')) {
        throw 'Interactive UAT control schema/session is invalid'
    }
    return [string]$control.action
}
```

## Warnings

### WR-01: Timed-out wrapper commands kill only cmd.exe and can strand Gradle/Java descendants

**Classification:** WARNING

**File:** `scripts/verify-foundation.ps1:307-338`

**Issue:** `Invoke-BatchCapture` launches `gradlew.bat` through `cmd.exe`, but its timeout path calls only `$process.Kill()` and ignores any kill error. Gradle's wrapper/single-use daemon Java descendants are not captured, waited for, or terminated. `Invoke-ProbedBuild` uses this helper for the detached online/offline builds, so a timeout can leave Java running against the disposable worktree while cleanup attempts to remove that worktree and container. `Invoke-NativeCapture` has the same unchecked root-only timeout pattern at lines 252-290.

**Fix:** Reuse a scoped PID/start-time/executable/command-line process-tree owner for all bounded wrapper launches. On timeout, terminate the validated root with its descendants, wait for every captured identity to disappear, dispose redirected streams/processes in `finally`, and report cleanup failure separately from the original timeout.

### WR-02: Named Loom configuration exemptions bypass the exact dependency allowlist

**Classification:** WARNING

**File:** `build.gradle:117-132`

**Issue:** `auditDirectDependencies` excludes every dependency on ten configurations solely because the configuration name appears in `loomOwnedConfigurations`. It never verifies that those dependencies were actually injected by Loom or that their coordinates match an expected plugin-owned baseline. An accidental declaration added to a skipped configuration such as `loaderLibraries`, `minecraftClientLibraries`, or `minecraftServerLibraries` is invisible to the five-coordinate comparison, even though those configurations can affect launch/runtime classpaths. The comprehensive PowerShell audit invokes this task and treats its marker as exact proof, so it inherits the bypass.

**Fix:** Inspect all nonempty declarable configurations. Model each permitted Loom injection as an exact configuration/type/coordinate-or-canonical-file tuple, subtract only those verified entries, and fail on every remainder. Do not exempt a whole configuration by name.

---

_Reviewed: 2026-08-26T12:18:25Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
