[CmdletBinding()]
param(
    [switch] $SelfCheck,
    [switch] $PrimeAndCompare,
    [switch] $RunServerSmoke,
    [switch] $ClientPreflight,
    [switch] $SuperviseInteractiveUat,
    [switch] $ValidateEvidence,
    [switch] $RequireUatPass,
    [string] $EvidencePath,
    [string] $SessionDirectory,
    [string] $SessionPointerPath,
    [string] $SessionReceiptPath,
    [string] $DistributionPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$script:ExpectedDistributionName = 'developers-hell-0.1.0.jar'
$script:ExpectedJdkDirectoryName = 'temurin-25.0.4+7-x64'
$script:ExpectedRuntimeVersion = '25.0.4+7'
$script:ExpectedMinecraftVersion = '26.2'
$script:ExpectedLoaderVersion = '0.19.3'
$script:ExpectedInstallerVersion = '1.1.2'
$script:ExpectedFabricApiVersion = '0.158.0+26.2'
$script:ExpectedModId = 'developers_hell'
$script:ExpectedModVersion = '0.1.0'
$script:ProbeHost = 'www.minecraft.net'
$script:ProbePort = 443
$script:RepositoryRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$script:PhaseDirectory = Join-Path $script:RepositoryRoot '.planning\phases\01-java-25-and-fabric-26-2-foundation'
$script:ToolchainEvidencePath = Join-Path $script:PhaseDirectory '01-TOOLCHAIN-EVIDENCE.md'
$script:DefaultEvidencePath = Join-Path $script:PhaseDirectory '01-FOUNDATION-EVIDENCE.md'
$script:DefaultDistributionPath = Join-Path $script:RepositoryRoot 'dist\developers-hell-0.1.0.jar'
$script:DefaultSessionPointerPath = Join-Path $script:RepositoryRoot '.work\interactive-uat-active.json'
$script:AuditScriptPath = Join-Path $script:RepositoryRoot 'scripts\audit-foundation.ps1'
$script:LoomProbeRelativePath = 'scripts\loom-resolution.init.gradle'
$script:EvidenceLines = [System.Collections.Generic.List[string]]::new()
$script:LastIsolationRecord = $null

function Write-Pass {
    param([Parameter(Mandatory)][string] $Message)
    Write-Host "PASS: $Message"
    $script:EvidenceLines.Add("PASS: $Message")
}

function Write-Detail {
    param([Parameter(Mandatory)][string] $Message)
    Write-Host "INFO: $Message"
    $script:EvidenceLines.Add("INFO: $Message")
}

function Throw-Failure {
    param([Parameter(Mandatory)][string] $Message)
    $script:EvidenceLines.Add("FAIL: $Message")
    throw $Message
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string] $LiteralPath)
    if (-not (Test-Path -LiteralPath $LiteralPath -PathType Leaf)) {
        Throw-Failure "File required for SHA-256 is missing: $LiteralPath"
    }
    return (Get-FileHash -LiteralPath $LiteralPath -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-StringSha256 {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Value)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Get-EvidenceMarker {
    param(
        [Parameter(Mandatory)][string] $Text,
        [Parameter(Mandatory)][string] $Name,
        [switch] $Optional
    )
    $matches = [regex]::Matches($Text, '(?m)^' + [regex]::Escape($Name) + ':\s*(.+?)\s*$')
    if ($matches.Count -eq 0) {
        if ($Optional) { return $null }
        Throw-Failure "Evidence marker is missing: $Name"
    }
    if ($matches.Count -ne 1) { Throw-Failure "Evidence marker appears more than once: $Name" }
    return $matches[0].Groups[1].Value.Trim()
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)] $Expected,
        [Parameter(Mandatory)][string] $Label
    )
    if ([string]$Actual -cne [string]$Expected) {
        Throw-Failure "$Label mismatch (expected '$Expected', observed '$Actual')"
    }
}

function Assert-PathInside {
    param(
        [Parameter(Mandatory)][string] $Child,
        [Parameter(Mandatory)][string] $Parent,
        [Parameter(Mandatory)][string] $Label,
        [switch] $AllowEqual
    )
    $childFull = [System.IO.Path]::GetFullPath($Child).TrimEnd('\')
    $parentFull = [System.IO.Path]::GetFullPath($Parent).TrimEnd('\')
    $comparison = [StringComparison]::OrdinalIgnoreCase
    if ($AllowEqual -and $childFull.Equals($parentFull, $comparison)) { return }
    if (-not $childFull.StartsWith($parentFull + '\', $comparison)) {
        Throw-Failure "$Label is outside its allowed root"
    }
}

function Assert-LeafPath {
    param(
        [Parameter(Mandatory)][string] $Candidate,
        [Parameter(Mandatory)][string] $ProtectedRoot,
        [Parameter(Mandatory)][string] $Label
    )
    $candidateFull = [System.IO.Path]::GetFullPath($Candidate).TrimEnd('\')
    $rootFull = [System.IO.Path]::GetFullPath($ProtectedRoot).TrimEnd('\')
    $comparison = [StringComparison]::OrdinalIgnoreCase
    if ($candidateFull.Equals($rootFull, $comparison) -or $rootFull.StartsWith($candidateFull + '\', $comparison)) {
        Throw-Failure "$Label equals or is an ancestor of a protected root"
    }
}

if (-not ('DevelopersHell.FinalPath' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace DevelopersHell {
    public static class FinalPath {
        private const uint FILE_SHARE_READ = 0x00000001;
        private const uint FILE_SHARE_WRITE = 0x00000002;
        private const uint FILE_SHARE_DELETE = 0x00000004;
        private const uint OPEN_EXISTING = 3;
        private const uint FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern SafeFileHandle CreateFile(
            string fileName,
            uint desiredAccess,
            uint shareMode,
            IntPtr securityAttributes,
            uint creationDisposition,
            uint flagsAndAttributes,
            IntPtr templateFile);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern uint GetFinalPathNameByHandle(
            SafeFileHandle file,
            StringBuilder path,
            uint capacity,
            uint flags);

        public static string Resolve(string path) {
            using (SafeFileHandle handle = CreateFile(
                path,
                0,
                FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                IntPtr.Zero,
                OPEN_EXISTING,
                FILE_FLAG_BACKUP_SEMANTICS,
                IntPtr.Zero)) {
                if (handle.IsInvalid) {
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "CreateFile failed for canonical-path resolution");
                }
                var buffer = new StringBuilder(32768);
                uint length = GetFinalPathNameByHandle(handle, buffer, (uint)buffer.Capacity, 0);
                if (length == 0 || length >= buffer.Capacity) {
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "GetFinalPathNameByHandle failed");
                }
                string result = buffer.ToString();
                if (result.StartsWith(@"\\?\UNC\", StringComparison.OrdinalIgnoreCase)) {
                    return @"\\" + result.Substring(8);
                }
                if (result.StartsWith(@"\\?\", StringComparison.OrdinalIgnoreCase)) {
                    return result.Substring(4);
                }
                return result;
            }
        }
    }
}
'@
}

function Resolve-CanonicalPath {
    param(
        [Parameter(Mandatory)][string] $LiteralPath,
        [switch] $AllowMissingLeaf
    )
    $full = [System.IO.Path]::GetFullPath($LiteralPath)
    if (Test-Path -LiteralPath $full) {
        if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
            $resolved = [DevelopersHell.FinalPath]::Resolve($full)
        }
        else {
            $resolved = (Resolve-Path -LiteralPath $full).Path
        }
        return [System.IO.Path]::GetFullPath($resolved).TrimEnd('\')
    }
    if (-not $AllowMissingLeaf) {
        Throw-Failure "Path does not exist for canonical resolution: $LiteralPath"
    }
    $parent = Split-Path -Parent $full
    $leaf = Split-Path -Leaf $full
    if ([string]::IsNullOrWhiteSpace($parent) -or [string]::IsNullOrWhiteSpace($leaf)) {
        Throw-Failure "Missing path has no safe parent/leaf decomposition: $LiteralPath"
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Resolve-CanonicalPath -LiteralPath $parent -AllowMissingLeaf) $leaf)).TrimEnd('\')
}

function Test-SamePath {
    param(
        [Parameter(Mandatory)][string] $Left,
        [Parameter(Mandatory)][string] $Right
    )
    return (Resolve-CanonicalPath -LiteralPath $Left).Equals(
        (Resolve-CanonicalPath -LiteralPath $Right),
        [StringComparison]::OrdinalIgnoreCase)
}

function Assert-NoReparsePoint {
    param([Parameter(Mandatory)][string] $LiteralPath)
    $item = Get-Item -LiteralPath $LiteralPath -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        Throw-Failure "Reparse points are forbidden for owned verification paths: $LiteralPath"
    }
}

function ConvertTo-NativeArgument {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Argument)
    if ($Argument -notmatch '[\s"]') { return $Argument }
    return '"' + ([regex]::Replace($Argument, '(\\*)"', '$1$1\"') -replace '(\\+)$', '$1$1') + '"'
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)][string] $FilePath,
        [Parameter(Mandatory)][string[]] $ArgumentList,
        [string] $WorkingDirectory = $script:RepositoryRoot,
        [switch] $AllowFailure,
        [int] $TimeoutSeconds = 900
    )
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $FilePath
    $start.Arguments = (($ArgumentList | ForEach-Object { ConvertTo-NativeArgument -Argument ([string]$_) }) -join ' ')
    $start.WorkingDirectory = $WorkingDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { Throw-Failure "Failed to start native command: $FilePath" }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        try { $process.Kill() } catch { }
        Throw-Failure "Native command timed out after $TimeoutSeconds seconds: $FilePath"
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exitCode = $process.ExitCode
    $process.Dispose()
    $result = [pscustomobject]@{
        ExitCode = $exitCode
        StdOut = $stdout
        StdErr = $stderr
        Combined = (($stdout.TrimEnd(), $stderr.TrimEnd()) | Where-Object { $_ -ne '' }) -join [Environment]::NewLine
        Command = "$FilePath $($start.Arguments)"
    }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        Throw-Failure "Native command failed with exit $exitCode`: $($result.Command)`n$($result.Combined)"
    }
    return $result
}

function Get-CmdBatchArguments {
    param(
        [Parameter(Mandatory)][string] $BatchPath,
        [Parameter(Mandatory)][string[]] $ArgumentList
    )
    $all = @($BatchPath) + @($ArgumentList)
    foreach ($value in $all) {
        if ([string]$value -match '[\r\n"&|<>^%!]') { Throw-Failure 'Batch path/argument contains a cmd.exe metacharacter that cannot be passed safely' }
    }
    $quotedArguments = ($ArgumentList | ForEach-Object { '"' + [string]$_ + '"' }) -join ' '
    return '/d /s /c ""' + $BatchPath + '" ' + $quotedArguments + '"'
}

function Invoke-BatchCapture {
    param(
        [Parameter(Mandatory)][string] $BatchPath,
        [Parameter(Mandatory)][string[]] $ArgumentList,
        [Parameter(Mandatory)][string] $WorkingDirectory,
        [switch] $AllowFailure,
        [int] $TimeoutSeconds = 900
    )
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $env:ComSpec
    $start.Arguments = Get-CmdBatchArguments -BatchPath $BatchPath -ArgumentList $ArgumentList
    $start.WorkingDirectory = $WorkingDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { Throw-Failure "Failed to start batch command: $BatchPath" }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        try { $process.Kill() } catch { }
        Throw-Failure "Batch command timed out after $TimeoutSeconds seconds: $BatchPath"
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exitCode = $process.ExitCode
    $process.Dispose()
    $combined = (($stdout.TrimEnd(),$stderr.TrimEnd()) | Where-Object { $_ }) -join "`n"
    if (-not $AllowFailure -and $exitCode -ne 0) { Throw-Failure "Batch command failed with exit $exitCode`: $BatchPath`n$combined" }
    return [pscustomobject]@{ ExitCode=$exitCode; StdOut=$stdout; StdErr=$stderr; Combined=$combined; Command="$BatchPath $($ArgumentList -join ' ')" }
}

function Invoke-Git {
    param(
        [Parameter(Mandatory)][string[]] $Arguments,
        [string] $WorkingDirectory = $script:RepositoryRoot,
        [switch] $AllowFailure
    )
    $git = (Get-Command git.exe -ErrorAction SilentlyContinue)
    if (-not $git) { $git = Get-Command git -ErrorAction Stop }
    return Invoke-NativeCapture -FilePath $git.Source -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -AllowFailure:$AllowFailure
}

function Get-WorktreePorcelainBytes {
    param([Parameter(Mandatory)][string] $RepositoryRoot)
    $git = (Get-Command git.exe -ErrorAction SilentlyContinue)
    if (-not $git) { $git = Get-Command git -ErrorAction Stop }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $git.Source
    $start.Arguments = ((@('-C', $RepositoryRoot, 'worktree', 'list', '--porcelain', '-z') | ForEach-Object { ConvertTo-NativeArgument ([string]$_) }) -join ' ')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { Throw-Failure 'Could not query Git worktree registry' }
    $memory = [IO.MemoryStream]::new()
    $copyTask = $process.StandardOutput.BaseStream.CopyToAsync($memory)
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit(30000)) {
        try { $process.Kill() } catch { }
        Throw-Failure 'git worktree list timed out'
    }
    [void]$copyTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exit = $process.ExitCode
    $process.Dispose()
    if ($exit -ne 0) { Throw-Failure "git worktree list failed with exit $exit`: $stderr" }
    return $memory.ToArray()
}

function ConvertFrom-WorktreePorcelain {
    param([Parameter(Mandatory)][byte[]] $Bytes)
    $text = [Text.Encoding]::UTF8.GetString($Bytes)
    $records = [System.Collections.Generic.List[object]]::new()
    $current = [ordered]@{}
    foreach ($field in $text.Split([char]0)) {
        if ($field.Length -eq 0) {
            if ($current.Count -gt 0) {
                $records.Add([pscustomobject]$current)
                $current = [ordered]@{}
            }
            continue
        }
        $space = $field.IndexOf(' ')
        if ($space -lt 0) { $current[$field] = $true }
        else { $current[$field.Substring(0, $space)] = $field.Substring($space + 1) }
    }
    if ($current.Count -gt 0) { $records.Add([pscustomobject]$current) }
    return @($records)
}

function Write-JsonAtomic {
    param(
        [Parameter(Mandatory)][string] $LiteralPath,
        [Parameter(Mandatory)] $Value
    )
    $target = [IO.Path]::GetFullPath($LiteralPath)
    $parent = Split-Path -Parent $target
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        [void](New-Item -ItemType Directory -Path $parent)
    }
    $temporary = Join-Path $parent ('.' + (Split-Path -Leaf $target) + '.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($temporary, (($Value | ConvertTo-Json -Depth 20) + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
        if (Test-Path -LiteralPath $target -PathType Leaf) { [IO.File]::Replace($temporary, $target, $null, $true) }
        else { [IO.File]::Move($temporary, $target) }
    }
    finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Resolve-RepositoryPath {
    param(
        [string] $Path,
        [Parameter(Mandatory)][AllowEmptyString()][string] $Default,
        [switch] $AllowMissingLeaf
    )
    $candidate = if ([string]::IsNullOrWhiteSpace($Path)) { $Default } elseif ([IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $script:RepositoryRoot $Path }
    return Resolve-CanonicalPath -LiteralPath $candidate -AllowMissingLeaf:$AllowMissingLeaf
}

function Get-ToolchainContract {
    if (-not (Test-Path -LiteralPath $script:ToolchainEvidencePath -PathType Leaf)) {
        Throw-Failure 'Toolchain evidence is missing'
    }
    $text = Get-Content -LiteralPath $script:ToolchainEvidencePath -Raw
    $required = @(
        'jdk_artifact_filename', 'jdk_artifact_source', 'jdk_checksum_source',
        'jdk_official_sha256', 'jdk_archive_sha256', 'jdk_runtime_version',
        'jdk_vendor', 'jdk_vm_vendor', 'jdk_arch', 'jdk_java_sha256',
        'jdk_javac_sha256', 'jdk_path_class', 'jdk_path_sha256', 'java_version',
        'javac_version', 'gradle_java_binding', 'template_url', 'template_ref',
        'template_commit', 'template_remote_commit', 'template_tree',
        'template_diff_mode', 'template_origin_verified', 'template_clean_before_patch',
        'template_current_diff', 'gradle_version', 'minecraft_version',
        'loader_version', 'fabric_api_version', 'java_release', 'loom_requested',
        'loom_selected', 'resolved_loom_build', 'resolved_loom_implementation_version',
        'resolved_loom_sha256', 'fixed_help_command', 'fixed_help_exit',
        'fixed_help_log_sha256', 'fixed_build_command', 'fixed_build_exit',
        'fixed_build_log_sha256', 'fixed_resolution_command', 'fixed_resolution_exit',
        'fixed_resolution_log_sha256', 'snapshot_fallback_used',
        'fixed_failure_command', 'fixed_failure_exit', 'fixed_failure_category', 'fixed_failure_log', 'fixed_failure_log_sha256',
        'fallback_help_command', 'fallback_help_exit', 'fallback_help_log', 'fallback_help_log_sha256',
        'fallback_build_command', 'fallback_build_exit', 'fallback_build_log', 'fallback_build_log_sha256',
        'fallback_resolution_command', 'fallback_resolution_exit', 'fallback_resolution_log', 'fallback_resolution_log_sha256',
        'wrapper_distribution', 'wrapper_distribution_sha256', 'wrapper_sha256',
        'proof_started_utc', 'proof_completed_utc', 'observed_anchors'
    )
    $values = [ordered]@{}
    foreach ($name in $required) { $values[$name] = Get-EvidenceMarker -Text $text -Name $name }
    Assert-Equal $values.jdk_runtime_version $script:ExpectedRuntimeVersion 'JDK runtime version evidence'
    Assert-Equal $values.java_version $script:ExpectedRuntimeVersion 'Java version evidence'
    Assert-Equal $values.javac_version '25.0.4' 'Javac version evidence'
    Assert-Equal $values.jdk_vendor 'Eclipse Adoptium' 'JDK vendor evidence'
    Assert-Equal $values.jdk_vm_vendor 'Eclipse Adoptium' 'JVM vendor evidence'
    if ($values.jdk_arch -notin @('amd64', 'x86_64')) { Throw-Failure 'JDK architecture evidence is not x64' }
    Assert-Equal $values.jdk_path_class 'ignored-work-toolchain-child' 'JDK path class'
    Assert-Equal $values.gradle_version '9.5.1' 'Gradle version evidence'
    Assert-Equal $values.minecraft_version $script:ExpectedMinecraftVersion 'Minecraft version evidence'
    Assert-Equal $values.loader_version $script:ExpectedLoaderVersion 'Loader version evidence'
    Assert-Equal $values.fabric_api_version $script:ExpectedFabricApiVersion 'Fabric API version evidence'
    Assert-Equal $values.java_release '25' 'Java release evidence'
    Assert-Equal $values.loom_selected $values.loom_requested 'Selected/requested Loom evidence'
    Assert-Equal $values.resolved_loom_build $values.resolved_loom_implementation_version 'Resolved Loom implementation evidence'
    if ($values.loom_selected -eq '1.17-SNAPSHOT' -and $values.resolved_loom_build -eq '1.17-SNAPSHOT') {
        Throw-Failure 'Loom snapshot alias was not resolved to a concrete build'
    }
    foreach ($hashName in @('jdk_official_sha256','jdk_archive_sha256','jdk_java_sha256','jdk_javac_sha256','jdk_path_sha256','resolved_loom_sha256','fixed_help_log_sha256','fixed_build_log_sha256','fixed_resolution_log_sha256','wrapper_sha256')) {
        if ($values[$hashName] -notmatch '^[0-9a-fA-F]{64}$') { Throw-Failure "Invalid SHA-256 marker: $hashName" }
    }
    Assert-Equal $values.jdk_official_sha256 $values.jdk_archive_sha256 'Official/downloaded JDK archive SHA-256'
    if ($values.template_commit -notmatch '^[0-9a-f]{40}$' -or $values.template_remote_commit -notmatch '^[0-9a-f]{40}$' -or $values.template_tree -notmatch '^[0-9a-f]{40}$') {
        Throw-Failure 'Template provenance contains an invalid Git object ID'
    }
    $proofStarted = [DateTimeOffset]::MinValue
    $proofCompleted = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse($values.proof_started_utc, [ref]$proofStarted) -or
        -not [DateTimeOffset]::TryParse($values.proof_completed_utc, [ref]$proofCompleted) -or
        $proofCompleted -lt $proofStarted) { Throw-Failure 'Toolchain proof timestamps are invalid or out of order' }
    Assert-Equal $values.template_commit $values.template_remote_commit 'Template local/remote commit'
    Assert-Equal $values.template_ref '26.2' 'Template ref'
    Assert-Equal $values.template_origin_verified 'true' 'Template origin verification'
    Assert-Equal $values.template_clean_before_patch 'true' 'Template pristine verification'
    Assert-Equal $values.template_current_diff 'gradle.properties' 'Template fixed-pin diff path'
    if ($values.snapshot_fallback_used -notin @('true','false')) { Throw-Failure 'snapshot_fallback_used is not boolean' }
    if ($values.snapshot_fallback_used -eq 'false') {
        foreach ($exitName in @('fixed_help_exit','fixed_build_exit','fixed_resolution_exit')) { Assert-Equal $values[$exitName] '0' $exitName }
        foreach ($name in @('fixed_failure_command','fixed_failure_exit','fixed_failure_category','fixed_failure_log','fixed_failure_log_sha256','fallback_help_command','fallback_help_exit','fallback_help_log','fallback_help_log_sha256','fallback_build_command','fallback_build_exit','fallback_build_log','fallback_build_log_sha256','fallback_resolution_command','fallback_resolution_exit','fallback_resolution_log','fallback_resolution_log_sha256')) {
            Assert-Equal $values[$name] 'not-applicable-fixed-success' "Fixed-success fallback marker $name"
        }
    }
    else {
        Assert-Equal $values.loom_selected '1.17-SNAPSHOT' 'Snapshot fallback selected Loom spelling'
        if ($values.fixed_failure_exit -notmatch '^\d+$' -or [int]$values.fixed_failure_exit -eq 0) { Throw-Failure 'Snapshot fallback lacks a nonzero fixed-pin failure exit' }
        if ($values.fixed_failure_category -notin @('plugin-resolution','minecraft-setup')) { Throw-Failure 'Snapshot fallback fixed failure category is ineligible' }
        if ($values.fixed_failure_log_sha256 -notmatch '^[0-9a-fA-F]{64}$') { Throw-Failure 'Snapshot fallback fixed failure log SHA-256 is invalid' }
        foreach ($prefix in @('fallback_help','fallback_build','fallback_resolution')) {
            Assert-Equal $values["${prefix}_exit"] '0' "$prefix exit"
            if ($values["${prefix}_log_sha256"] -notmatch '^[0-9a-fA-F]{64}$') { Throw-Failure "$prefix log SHA-256 is invalid" }
            if ([string]::IsNullOrWhiteSpace($values["${prefix}_command"])) { Throw-Failure "$prefix command is blank" }
        }
    }
    return [pscustomobject]@{ Text = $text; Values = [pscustomobject]$values }
}

function Select-VerifiedJdk {
    param([Parameter(Mandatory)] $Toolchain)
    $candidate = Join-Path $script:RepositoryRoot ".work\toolchain\$($script:ExpectedJdkDirectoryName)"
    $jdkRoot = Resolve-CanonicalPath -LiteralPath $candidate
    Assert-NoReparsePoint -LiteralPath $jdkRoot
    $java = Join-Path $jdkRoot 'bin\java.exe'
    $javaw = Join-Path $jdkRoot 'bin\javaw.exe'
    $javac = Join-Path $jdkRoot 'bin\javac.exe'
    foreach ($path in @($java, $javaw, $javac)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Throw-Failure "Verified JDK executable missing: $path" }
        Assert-NoReparsePoint -LiteralPath $path
    }
    Assert-Equal (Get-Sha256 $java) $Toolchain.Values.jdk_java_sha256 'java.exe SHA-256'
    Assert-Equal (Get-Sha256 $javac) $Toolchain.Values.jdk_javac_sha256 'javac.exe SHA-256'
    $pathHash = Get-StringSha256 $jdkRoot.ToLowerInvariant()
    Assert-Equal $pathHash $Toolchain.Values.jdk_path_sha256 'Verified JDK canonical path SHA-256'

    $properties = Invoke-NativeCapture -FilePath $java -ArgumentList @('-XshowSettings:properties','-version') -WorkingDirectory $script:RepositoryRoot
    $propertyText = $properties.Combined
    function Read-JavaProperty([string] $Name) {
        $match = [regex]::Match($propertyText, '(?m)^\s*' + [regex]::Escape($Name) + '\s*=\s*(.+?)\s*$')
        if (-not $match.Success) { Throw-Failure "Exact Java property missing: $Name" }
        return $match.Groups[1].Value.Trim()
    }
    $runtime = (Read-JavaProperty 'java.runtime.version') -replace '-LTS$', ''
    Assert-Equal $runtime $script:ExpectedRuntimeVersion 'Selected Java runtime'
    $vendor = Read-JavaProperty 'java.vendor'
    $vmVendor = Read-JavaProperty 'java.vm.vendor'
    if ($vendor -notmatch '(?i)(Eclipse Adoptium|Temurin)' -or $vmVendor -notmatch '(?i)(Eclipse Adoptium|Temurin)') {
        Throw-Failure 'Selected JDK is not Eclipse Adoptium/Temurin'
    }
    $arch = Read-JavaProperty 'os.arch'
    if ($arch -notin @('amd64','x86_64')) { Throw-Failure "Selected JDK architecture is not x64: $arch" }
    $reportedHome = Resolve-CanonicalPath -LiteralPath (Read-JavaProperty 'java.home')
    if (-not $reportedHome.Equals($jdkRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Throw-Failure 'Selected java.home is not the checksum-bound JDK root'
    }
    $env:JAVA_HOME = $jdkRoot
    $env:Path = "$(Join-Path $jdkRoot 'bin');$env:Path"
    Write-Pass 'checksum-bound Eclipse Temurin 25.0.4+7 selected'
    return [pscustomobject]@{
        Root = $jdkRoot
        Java = $java
        Javaw = $javaw
        Javac = $javac
        JavaSha256 = Get-Sha256 $java
        JavawSha256 = Get-Sha256 $javaw
        JavacSha256 = Get-Sha256 $javac
        PathSha256 = $pathHash
        RuntimeVersion = $runtime
        Vendor = $vendor
        VmVendor = $vmVendor
        Architecture = $arch
    }
}

function Get-GradleJvmArguments {
    param([Parameter(Mandatory)] $Jdk)
    return @(
        "-Dorg.gradle.java.installations.paths=$($Jdk.Root)",
        '-Dorg.gradle.java.installations.auto-detect=false',
        '-Dorg.gradle.java.installations.auto-download=false'
    )
}

function Invoke-FoundationAudit {
    param(
        [Parameter(Mandatory)][string] $Root,
        [string] $JarPath,
        [string] $AuditEvidencePath
    )
    $audit = Join-Path $Root 'scripts\audit-foundation.ps1'
    if (-not (Test-Path -LiteralPath $audit -PathType Leaf)) { Throw-Failure 'Comprehensive audit script is missing' }
    $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$audit,'-SourceAndDependencies')
    if (-not [string]::IsNullOrWhiteSpace($JarPath)) { $arguments += @('-JarPath',$JarPath) }
    if (-not [string]::IsNullOrWhiteSpace($AuditEvidencePath)) { $arguments += @('-EvidencePath',$AuditEvidencePath) }
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $result = Invoke-NativeCapture -FilePath $powershell -ArgumentList $arguments -WorkingDirectory $Root -AllowFailure
    if ($result.ExitCode -ne 0) { Throw-Failure "Comprehensive foundation audit failed with exit $($result.ExitCode)`n$($result.Combined)" }
    Write-Pass 'comprehensive source/dependency/archive audit'
    return $result
}

function Set-EvidenceMarkers {
    param(
        [Parameter(Mandatory)][string] $LiteralPath,
        [Parameter(Mandatory)][System.Collections.IDictionary] $Markers
    )
    $target = [IO.Path]::GetFullPath($LiteralPath)
    $parent = Split-Path -Parent $target
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { [void](New-Item -ItemType Directory -Path $parent) }
    $text = if (Test-Path -LiteralPath $target -PathType Leaf) {
        Get-Content -LiteralPath $target -Raw
    }
    else {
        "# Phase 1 Foundation Evidence`r`n`r`nMachine-produced, public-safe verification markers. Runtime client observations remain PENDING until the blocking-human UAT is finalized.`r`n`r`n"
    }
    foreach ($entry in $Markers.GetEnumerator()) {
        $name = [string]$entry.Key
        $value = [string]$entry.Value
        if ([string]::IsNullOrWhiteSpace($value)) { Throw-Failure "Refusing to write blank evidence marker: $name" }
        if ($name -notmatch '^[a-z0-9_]+$' -or $value -match '[\r\n]') { Throw-Failure "Unsafe evidence marker: $name" }
        $pattern = '(?m)^' + [regex]::Escape($name) + ':\s*.*$'
        $replacement = "$name`: $value"
        if ([regex]::IsMatch($text, $pattern)) { $text = [regex]::Replace($text, $pattern, $replacement) }
        else { $text = $text.TrimEnd() + "`r`n$replacement`r`n" }
    }
    if ($text -match '(?i)(?:C:\\Users\\[^\\\r\n]+|/Users/[^/\r\n]+|password\s*:|api[_-]?key\s*:|secret\s*:|employer\s*:|school\s*:|sponsor(?:ed|ship)?\s*:)') {
        Throw-Failure 'Evidence contains a private path, credential label, or unsupported factual identity/sponsorship claim'
    }
    $temporary = Join-Path $parent ('.foundation-evidence-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($temporary, $text.TrimEnd() + "`r`n", [Text.UTF8Encoding]::new($false))
        if (Test-Path -LiteralPath $target -PathType Leaf) { [IO.File]::Replace($temporary, $target, $null, $true) }
        else { [IO.File]::Move($temporary, $target) }
    }
    finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

$script:TrackedManifest = @(
    'settings.gradle',
    'gradle.properties',
    'build.gradle',
    'gradlew',
    'gradlew.bat',
    'gradle/wrapper/gradle-wrapper.jar',
    'gradle/wrapper/gradle-wrapper.properties',
    'LICENSE',
    '.planning/phases/01-java-25-and-fabric-26-2-foundation/01-TOOLCHAIN-EVIDENCE.md',
    'src/main/java/dev/developershell/DevelopersHell.java',
    'src/client/java/dev/developershell/client/DevelopersHellClient.java',
    'src/main/java/dev/developershell/registry/ModItemIds.java',
    'src/main/java/dev/developershell/registry/ModItems.java',
    'src/main/java/dev/developershell/module/ModuleId.java',
    'src/main/java/dev/developershell/module/ModuleGate.java',
    'src/test/java/dev/developershell/module/ModuleGateTest.java',
    'src/main/resources/fabric.mod.json',
    'src/main/resources/assets/developers_hell/lang/en_us.json',
    'src/main/resources/assets/developers_hell/items/foundation_token.json',
    'src/main/resources/assets/developers_hell/models/item/foundation_token.json',
    'src/gametest/java/dev/developershell/gametest/FoundationGameTests.java',
    'src/gametest/resources/fabric.mod.json',
    'scripts/loom-resolution.init.gradle',
    'scripts/audit-foundation.ps1',
    'scripts/verify-foundation.ps1'
)

function Assert-TrackedManifest {
    param([Parameter(Mandatory)][string] $WorktreeRoot)
    $status = Invoke-Git -Arguments @('-C',$WorktreeRoot,'status','--porcelain') -WorkingDirectory $WorktreeRoot
    if (-not [string]::IsNullOrWhiteSpace($status.StdOut)) {
        Throw-Failure "Detached verification worktree is not clean: $($status.StdOut)"
    }
    foreach ($relative in $script:TrackedManifest) {
        $result = Invoke-Git -Arguments @('-C',$WorktreeRoot,'ls-files','--error-unmatch','--',$relative) -WorkingDirectory $WorktreeRoot -AllowFailure
        if ($result.ExitCode -ne 0 -or $result.StdOut.Trim() -cne $relative) {
            Throw-Failure "Required clean-checkout input is not tracked exactly: $relative"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $WorktreeRoot ($relative -replace '/', '\')))) {
            Throw-Failure "Tracked clean-checkout input is absent from disk: $relative"
        }
    }
    Write-Pass "tracked clean-checkout manifest ($($script:TrackedManifest.Count) paths)"
}

function Get-ExactWorktreeRecord {
    param(
        [Parameter(Mandatory)][byte[]] $PorcelainBytes,
        [Parameter(Mandatory)][string] $ExpectedPath,
        [Parameter(Mandatory)][string] $ExpectedHead
    )
    $matches = @()
    foreach ($record in (ConvertFrom-WorktreePorcelain -Bytes $PorcelainBytes)) {
        if (-not $record.PSObject.Properties['worktree']) { continue }
        $recordPath = Resolve-CanonicalPath -LiteralPath ([string]$record.worktree) -AllowMissingLeaf
        if ($recordPath.Equals($ExpectedPath, [StringComparison]::OrdinalIgnoreCase)) { $matches += $record }
    }
    if ($matches.Count -ne 1) { Throw-Failure "Expected exactly one registered worktree record, observed $($matches.Count)" }
    $match = $matches[0]
    if (-not $match.PSObject.Properties['HEAD'] -or ([string]$match.HEAD).ToLowerInvariant() -cne $ExpectedHead.ToLowerInvariant()) {
        Throw-Failure 'Registered worktree HEAD does not match the expected detached commit'
    }
    if (-not $match.PSObject.Properties['detached'] -or $match.detached -ne $true) {
        Throw-Failure 'Registered verification worktree is not marked detached'
    }
    return $match
}

function Get-LoomProbeValue {
    param(
        [Parameter(Mandatory)][string] $Text,
        [Parameter(Mandatory)][string] $Name
    )
    $matches = [regex]::Matches($Text, '(?m)^DEVELOPERS_HELL_' + [regex]::Escape($Name) + '=(.+?)\s*$')
    if ($matches.Count -ne 1) { Throw-Failure "Expected one Loom probe marker $Name, observed $($matches.Count)" }
    $value = $matches[0].Groups[1].Value.Trim()
    if ([string]::IsNullOrWhiteSpace($value)) { Throw-Failure "Loom probe marker is blank: $Name" }
    return $value
}

function Invoke-ProbedBuild {
    param(
        [Parameter(Mandatory)][string] $WorktreeRoot,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)][ValidateSet('online','offline')][string] $Mode
    )
    $wrapper = Join-Path $WorktreeRoot 'gradlew.bat'
    $probe = Join-Path $WorktreeRoot $script:LoomProbeRelativePath
    if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf) -or -not (Test-Path -LiteralPath $probe -PathType Leaf)) {
        Throw-Failure 'Committed wrapper or Loom probe is missing in detached worktree'
    }
    $logDirectory = Join-Path $WorktreeRoot '.work'
    if (-not (Test-Path -LiteralPath $logDirectory -PathType Container)) { [void](New-Item -ItemType Directory -Path $logDirectory) }
    $logPath = Join-Path $logDirectory "foundation-$Mode-build.log"
    Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue
    $arguments = @(Get-GradleJvmArguments -Jdk $Jdk)
    if ($Mode -eq 'offline') { $arguments += '--offline' }
    $arguments += @('clean','build','--no-daemon','--stacktrace','--init-script',$probe)
    $result = Invoke-BatchCapture -BatchPath $wrapper -ArgumentList $arguments -WorkingDirectory $WorktreeRoot -AllowFailure -TimeoutSeconds 1800
    [IO.File]::WriteAllText($logPath, $result.Combined + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
    if ($result.ExitCode -ne 0) { Throw-Failure "$Mode detached build failed with exit $($result.ExitCode); log is $logPath" }
    if ($result.Combined -notmatch '(?m)^BUILD SUCCESSFUL') { Throw-Failure "$Mode detached build lacks BUILD SUCCESSFUL marker" }
    $selected = Get-LoomProbeValue -Text $result.Combined -Name 'LOOM_SELECTED'
    $resolved = Get-LoomProbeValue -Text $result.Combined -Name 'LOOM_RESOLVED'
    $implementation = Get-LoomProbeValue -Text $result.Combined -Name 'LOOM_IMPLEMENTATION'
    $artifactSha = (Get-LoomProbeValue -Text $result.Combined -Name 'LOOM_ARTIFACT_SHA256').ToLowerInvariant()
    Assert-Equal $selected $Toolchain.Values.loom_selected "$Mode configured Loom"
    Assert-Equal $resolved $Toolchain.Values.resolved_loom_build "$Mode resolved Loom"
    Assert-Equal $implementation $Toolchain.Values.resolved_loom_implementation_version "$Mode Loom implementation"
    Assert-Equal $artifactSha $Toolchain.Values.resolved_loom_sha256 "$Mode Loom artifact SHA-256"
    if ($selected -eq '1.17-SNAPSHOT' -and $resolved -eq '1.17-SNAPSHOT') { Throw-Failure "$Mode build retained an unresolved Loom snapshot alias" }
    $productionJars = @(Get-ChildItem -LiteralPath (Join-Path $WorktreeRoot 'build\libs') -File -Filter '*.jar' | Where-Object { $_.Name -notmatch '-(?:sources|javadoc)\.jar$' })
    if ($productionJars.Count -ne 1 -or $productionJars[0].Name -cne $script:ExpectedDistributionName) {
        Throw-Failure "$Mode build did not produce exactly one ordinary $($script:ExpectedDistributionName) archive"
    }
    Write-Pass "$Mode detached clean build and frozen Loom probe"
    return [pscustomobject]@{
        Mode = $Mode
        ExitCode = $result.ExitCode
        LogPath = $logPath
        LogSha256 = Get-Sha256 $logPath
        SelectedLoom = $selected
        ResolvedLoom = $resolved
        LoomArtifactSha256 = $artifactSha
        JarPath = $productionJars[0].FullName
        CommandMarker = if ($Mode -eq 'offline') { 'gradlew.bat --offline clean build --no-daemon --stacktrace --init-script scripts/loom-resolution.init.gradle' } else { 'gradlew.bat clean build --no-daemon --stacktrace --init-script scripts/loom-resolution.init.gradle' }
    }
}

function Get-ProductionArchiveContract {
    param([Parameter(Mandatory)][string] $JarPath)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entries = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\','/') } | Sort-Object)
        $required = @(
            'fabric.mod.json',
            'LICENSE_developers-hell',
            'dev/developershell/DevelopersHell.class',
            'dev/developershell/client/DevelopersHellClient.class',
            'dev/developershell/registry/ModItemIds.class',
            'dev/developershell/registry/ModItems.class',
            'dev/developershell/module/ModuleId.class',
            'dev/developershell/module/ModuleGate.class',
            'assets/developers_hell/lang/en_us.json',
            'assets/developers_hell/items/foundation_token.json',
            'assets/developers_hell/models/item/foundation_token.json'
        )
        foreach ($entry in $required) {
            if ($entries -cnotcontains $entry) { Throw-Failure "Production archive entry missing: $entry" }
        }
        if (@($entries | Where-Object { $_ -ceq 'fabric.mod.json' }).Count -ne 1) { Throw-Failure 'Production archive must contain exactly one root fabric.mod.json' }
        if (@($entries | Where-Object { $_ -match '^LICENSE(?:_|$)' }).Count -ne 1 -or $entries -cnotcontains 'LICENSE_developers-hell') {
            Throw-Failure 'Production archive must contain exactly one LICENSE_developers-hell and no unrenamed/duplicate license'
        }
        $forbidden = @($entries | Where-Object {
            $_ -match '(?i)(^|/)(?:src/(?:test|gametest)|test-results|reports/tests|run|world|logs|eula\.txt|\.work|\.jdk|jdk)/' -or
            $_ -match '(?i)^dev/developershell/gametest/' -or
            $_ -match '(?i)(?:FoundationGameTests|ModuleGateTest).*\.class$' -or
            $_ -match '(?i)^(?:com/openai|okhttp3|retrofit2|io/sentry|com/mixpanel|com/amplitude|com/segment)/' -or
            $_ -match '(?i)(?:api[_-]?key|credentials?|secret|password|remote[-_/]?config)'
        })
        if ($forbidden.Count -gt 0) { Throw-Failure "Forbidden production archive entries: $($forbidden -join ', ')" }
        $manifestEntry = $archive.GetEntry('fabric.mod.json')
        $reader = [IO.StreamReader]::new($manifestEntry.Open(), [Text.Encoding]::UTF8, $true)
        try { $manifestText = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $manifest = $manifestText | ConvertFrom-Json
        Assert-Equal $manifest.id $script:ExpectedModId 'Production metadata mod ID'
        if ($manifestText -match '(?i)developers_hell_test|fabric-gametest') { Throw-Failure 'Production metadata contains GameTest identity/entrypoint' }
        if (-not $manifest.entrypoints.main -or -not $manifest.entrypoints.client) { Throw-Failure 'Production metadata lacks common/client entrypoints' }
        $allText = $manifestText
        foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '\.(?:json|txt|properties|xml|mf)$' }) {
            $entryReader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8, $true)
            try { $allText += "`n" + $entryReader.ReadToEnd() } finally { $entryReader.Dispose() }
        }
        if ($allText -match '(?i)developers_hell_test|fabric-gametest') { Throw-Failure 'Production archive text contains GameTest metadata' }
        Write-Pass 'ordinary production archive inclusions, license, and test exclusions'
        return [pscustomobject]@{
            Entries = $entries
            EntriesSha256 = Get-StringSha256 ($entries -join "`n")
            Sha256 = Get-Sha256 $JarPath
            Size = (Get-Item -LiteralPath $JarPath).Length
        }
    }
    finally {
        $archive.Dispose()
    }
}

function Copy-FileAtomically {
    param(
        [Parameter(Mandatory)][string] $Source,
        [Parameter(Mandatory)][string] $Destination
    )
    $parent = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { [void](New-Item -ItemType Directory -Path $parent) }
    $temporary = Join-Path $parent ('.' + (Split-Path -Leaf $Destination) + '.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::Copy($Source, $temporary, $false)
        Assert-Equal (Get-Sha256 $temporary) (Get-Sha256 $Source) 'Atomic copy staging SHA-256'
        if (Test-Path -LiteralPath $Destination -PathType Leaf) { [IO.File]::Replace($temporary, $Destination, $null, $true) }
        else { [IO.File]::Move($temporary, $Destination) }
    }
    finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Assert-SingleDistributionArtifact {
    param(
        [Parameter(Mandatory)][string] $DistributionFile,
        [switch] $AllowAbsent
    )
    $directory = Split-Path -Parent $DistributionFile
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        if ($AllowAbsent) { return }
        Throw-Failure 'Distribution directory is missing'
    }
    $jars = @(Get-ChildItem -LiteralPath $directory -File -Filter '*.jar')
    if ($AllowAbsent) {
        if (@($jars | Where-Object { $_.Name -cne $script:ExpectedDistributionName }).Count -ne 0) { Throw-Failure 'Unexpected JAR exists beside the declared distribution path' }
        if ($jars.Count -gt 1) { Throw-Failure 'More than one distribution JAR exists' }
        return
    }
    if ($jars.Count -ne 1 -or $jars[0].Name -cne $script:ExpectedDistributionName -or
        -not (Resolve-CanonicalPath -LiteralPath $jars[0].FullName).Equals((Resolve-CanonicalPath -LiteralPath $DistributionFile),[StringComparison]::OrdinalIgnoreCase)) {
        Throw-Failure 'Distribution directory does not contain exactly one declared production JAR'
    }
}

function Assert-RepositoryBuildContract {
    param(
        [Parameter(Mandatory)][string] $Root,
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)] $Jdk
    )
    $propertiesText = Get-Content -LiteralPath (Join-Path $Root 'gradle.properties') -Raw
    $expectedProperties = [ordered]@{
        minecraft_version = $script:ExpectedMinecraftVersion
        loader_version = $script:ExpectedLoaderVersion
        fabric_api_version = $script:ExpectedFabricApiVersion
        fabric_installer_version = $script:ExpectedInstallerVersion
        mod_version = $script:ExpectedModVersion
        archives_base_name = 'developers-hell'
        loom_version = $Toolchain.Values.loom_selected
    }
    foreach ($entry in $expectedProperties.GetEnumerator()) {
        $match = [regex]::Match($propertiesText, '(?m)^' + [regex]::Escape([string]$entry.Key) + '=(.+?)\s*$')
        if (-not $match.Success) { Throw-Failure "Frozen Gradle property missing: $($entry.Key)" }
        Assert-Equal $match.Groups[1].Value.Trim() $entry.Value "Frozen Gradle property $($entry.Key)"
    }
    $buildSurface = (Get-Content -LiteralPath (Join-Path $Root 'build.gradle') -Raw) + "`n" +
        (Get-Content -LiteralPath (Join-Path $Root 'settings.gradle') -Raw)
    if ($buildSurface -match '(?i)net\.fabricmc\s*:\s*yarn|\bmappings\s+["'']|id\s+["'']net\.fabricmc\.fabric-loom-remap["'']|(?:dependsOn|tasks\.(?:named|register))[^\r\n]*["'']remapJar["'']') {
        Throw-Failure 'Forbidden Yarn/mappings/legacy remap surface found in the frozen build'
    }
    $wrapperProperties = Get-Content -LiteralPath (Join-Path $Root 'gradle\wrapper\gradle-wrapper.properties') -Raw
    if ($wrapperProperties -notmatch '(?m)^distributionUrl=https\\://services\.gradle\.org/distributions/gradle-9\.5\.1-bin\.zip\s*$') {
        Throw-Failure 'Wrapper distribution is not exact Gradle 9.5.1'
    }
    Assert-Equal (Get-Sha256 (Join-Path $Root 'gradle\wrapper\gradle-wrapper.jar')) $Toolchain.Values.wrapper_sha256 'Wrapper JAR SHA-256'

    $wrapper = Join-Path $Root 'gradlew.bat'
    $arguments = @(Get-GradleJvmArguments -Jdk $Jdk) + @('--version','--no-daemon')
    $versionResult = Invoke-BatchCapture -BatchPath $wrapper -ArgumentList $arguments -WorkingDirectory $Root -TimeoutSeconds 300
    $versionMatch = [regex]::Match($versionResult.Combined, '(?m)^Gradle\s+([0-9.]+)\s*$')
    if (-not $versionMatch.Success) { Throw-Failure 'Gradle wrapper did not report a parseable version' }
    Assert-Equal $versionMatch.Groups[1].Value $Toolchain.Values.gradle_version 'Gradle wrapper runtime version'
    if ($versionResult.Combined -notmatch '(?m)^Launcher JVM:\s+25\.0\.4') { Throw-Failure 'Gradle launcher JVM is not exact Java 25.0.4' }
    Write-Pass 'frozen Gradle/Fabric tuple and ordinary no-remap build contract'
}

function Invoke-PrimeAndCompareMode {
    param(
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $EvidenceFile,
        [Parameter(Mandatory)][string] $DistributionFile
    )
    if ((Split-Path -Leaf $DistributionFile) -cne $script:ExpectedDistributionName -or
        -not $DistributionFile.Equals((Resolve-CanonicalPath -LiteralPath $script:DefaultDistributionPath -AllowMissingLeaf), [StringComparison]::OrdinalIgnoreCase)) {
        Throw-Failure 'DistributionPath must be exactly repository-relative dist/developers-hell-0.1.0.jar'
    }
    $headScript = Invoke-Git -Arguments @('-C',$script:RepositoryRoot,'cat-file','-e','HEAD:scripts/verify-foundation.ps1') -AllowFailure
    if ($headScript.ExitCode -ne 0) { Throw-Failure 'Verification harness is not committed at HEAD' }
    $scriptDiff = Invoke-Git -Arguments @('-C',$script:RepositoryRoot,'diff','--quiet','HEAD','--','scripts/verify-foundation.ps1') -AllowFailure
    if ($scriptDiff.ExitCode -ne 0) { Throw-Failure 'Verification harness differs from committed HEAD' }

    [void](Invoke-FoundationAudit -Root $script:RepositoryRoot)
    $repository = Resolve-CanonicalPath -LiteralPath $script:RepositoryRoot
    $tempRoot = Resolve-CanonicalPath -LiteralPath ([IO.Path]::GetTempPath())
    $homeRoot = Resolve-CanonicalPath -LiteralPath ([Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile))
    $head = (Invoke-Git -Arguments @('-C',$repository,'rev-parse','HEAD')).StdOut.Trim().ToLowerInvariant()
    if ($head -notmatch '^[0-9a-f]{40}$') { Throw-Failure 'Repository HEAD is not a full Git object ID' }
    $preBytes = Get-WorktreePorcelainBytes -RepositoryRoot $repository
    $containerName = 'developers-hell-' + [guid]::NewGuid().ToString('N')
    if ($containerName -notmatch '^developers-hell-[0-9a-f]{32}$') { Throw-Failure 'Owned temp container name is invalid' }
    $containerCandidate = Join-Path $tempRoot $containerName
    if (Test-Path -LiteralPath $containerCandidate) { Throw-Failure 'Owned temp container unexpectedly pre-exists' }
    $worktreeCandidate = Join-Path $containerCandidate 'worktree'
    foreach ($protected in @($tempRoot,$repository,$homeRoot)) {
        Assert-LeafPath -Candidate $containerCandidate -ProtectedRoot $protected -Label 'Temp container'
        Assert-LeafPath -Candidate $worktreeCandidate -ProtectedRoot $protected -Label 'Detached worktree'
    }
    [void](New-Item -ItemType Directory -Path $containerCandidate)
    $container = Resolve-CanonicalPath -LiteralPath $containerCandidate
    Assert-NoReparsePoint -LiteralPath $container
    $containerParent = Resolve-CanonicalPath -LiteralPath (Split-Path -Parent $container)
    if (-not $containerParent.Equals($tempRoot, [StringComparison]::OrdinalIgnoreCase)) { Throw-Failure 'Owned temp container is not a direct canonical child of OS temp' }
    $worktree = Resolve-CanonicalPath -LiteralPath $worktreeCandidate -AllowMissingLeaf
    Assert-PathInside -Child $worktree -Parent $container -Label 'Detached worktree'
    if ((Split-Path -Leaf $worktree) -cne 'worktree') { Throw-Failure 'Detached worktree leaf is not exact' }
    $onlinePreservedJar = Join-Path $container 'online-developers-hell-0.1.0.jar'
    $registered = $false
    $worktreeRemoved = $false
    $registryRestored = $false
    $containerRemoved = $false
    $online = $null
    $offline = $null
    $onlineArchive = $null
    $offlineArchive = $null
    try {
        $add = Invoke-Git -Arguments @('-C',$repository,'worktree','add','--detach',$worktree,'HEAD') -WorkingDirectory $repository -AllowFailure
        if ($add.ExitCode -ne 0) { Throw-Failure "git worktree add failed with exit $($add.ExitCode): $($add.Combined)" }
        $worktree = Resolve-CanonicalPath -LiteralPath $worktree
        Assert-NoReparsePoint -LiteralPath $worktree
        $currentBytes = Get-WorktreePorcelainBytes -RepositoryRoot $repository
        [void](Get-ExactWorktreeRecord -PorcelainBytes $currentBytes -ExpectedPath $worktree -ExpectedHead $head)
        $registered = $true
        Write-Pass 'exact detached HEAD worktree registered'
        Assert-TrackedManifest -WorktreeRoot $worktree
        Assert-RepositoryBuildContract -Root $worktree -Toolchain $Toolchain -Jdk $Jdk

        $online = Invoke-ProbedBuild -WorktreeRoot $worktree -Jdk $Jdk -Toolchain $Toolchain -Mode online
        $onlineArchive = Get-ProductionArchiveContract -JarPath $online.JarPath
        $auditEvidence = Join-Path $worktree '.work\foundation-online-audit.log'
        [void](Invoke-FoundationAudit -Root $worktree -JarPath $online.JarPath -AuditEvidencePath $auditEvidence)
        Copy-FileAtomically -Source $online.JarPath -Destination $onlinePreservedJar
        Assert-Equal (Get-Sha256 $onlinePreservedJar) $onlineArchive.Sha256 'Preserved online archive SHA-256'

        $offline = Invoke-ProbedBuild -WorktreeRoot $worktree -Jdk $Jdk -Toolchain $Toolchain -Mode offline
        $offlineArchive = Get-ProductionArchiveContract -JarPath $offline.JarPath
        [void](Invoke-FoundationAudit -Root $worktree -JarPath $offline.JarPath)
        Assert-Equal $offlineArchive.Sha256 $onlineArchive.Sha256 'Online/offline production JAR SHA-256'
        Assert-Equal $offlineArchive.EntriesSha256 $onlineArchive.EntriesSha256 'Online/offline archive entry list'
        Assert-SingleDistributionArtifact -DistributionFile $DistributionFile -AllowAbsent
        Copy-FileAtomically -Source $offline.JarPath -Destination $DistributionFile
        Assert-SingleDistributionArtifact -DistributionFile $DistributionFile
        $distributionHash = Get-Sha256 $DistributionFile
        Assert-Equal $distributionHash $onlineArchive.Sha256 'Online/distribution JAR SHA-256'
        Assert-Equal $distributionHash $offlineArchive.Sha256 'Offline/distribution JAR SHA-256'
        [void](Get-ProductionArchiveContract -JarPath $DistributionFile)
        Write-Pass 'online/offline/distribution SHA-256 equality'
    }
    finally {
        if (Test-Path -LiteralPath $container -PathType Container) {
            $guardedContainer = Resolve-CanonicalPath -LiteralPath $container
            if (-not $guardedContainer.Equals($container, [StringComparison]::OrdinalIgnoreCase)) { Throw-Failure 'Owned temp container canonical identity changed before cleanup' }
            Assert-PathInside -Child $worktree -Parent $guardedContainer -Label 'Cleanup worktree path'
            Assert-LeafPath -Candidate $guardedContainer -ProtectedRoot $tempRoot -Label 'Cleanup container'
            Assert-LeafPath -Candidate $guardedContainer -ProtectedRoot $repository -Label 'Cleanup container'
            Assert-LeafPath -Candidate $guardedContainer -ProtectedRoot $homeRoot -Label 'Cleanup container'
            if ($registered) {
                $beforeRemove = Get-WorktreePorcelainBytes -RepositoryRoot $repository
                [void](Get-ExactWorktreeRecord -PorcelainBytes $beforeRemove -ExpectedPath $worktree -ExpectedHead $head)
                $remove = Invoke-Git -Arguments @('-C',$repository,'worktree','remove','--force','--',$worktree) -WorkingDirectory $repository -AllowFailure
                if ($remove.ExitCode -ne 0) { Throw-Failure "Exact git worktree removal failed with exit $($remove.ExitCode): $($remove.Combined)" }
                if (Test-Path -LiteralPath $worktree) { Throw-Failure 'Exact worktree path remains after Git removal' }
                $postBytes = Get-WorktreePorcelainBytes -RepositoryRoot $repository
                $postRecords = @(ConvertFrom-WorktreePorcelain -Bytes $postBytes | Where-Object {
                    $_.PSObject.Properties['worktree'] -and ([IO.Path]::GetFullPath([string]$_.worktree)).Equals($worktree, [StringComparison]::OrdinalIgnoreCase)
                })
                if ($postRecords.Count -ne 0) { Throw-Failure 'Exact worktree registration remains after removal' }
                $preBase64 = [Convert]::ToBase64String($preBytes)
                $postBase64 = [Convert]::ToBase64String($postBytes)
                if ($preBase64 -cne $postBase64) { Throw-Failure 'Pre-existing Git worktree registration bytes changed during cleanup' }
                $worktreeRemoved = $true
                $registryRestored = $true
            }
            elseif (Test-Path -LiteralPath $worktree) {
                $unregisteredTarget = Resolve-CanonicalPath -LiteralPath $worktree
                Assert-NoReparsePoint -LiteralPath $unregisteredTarget
                Assert-PathInside -Child $unregisteredTarget -Parent $guardedContainer -Label 'Unregistered cleanup target'
                if (@(Get-ChildItem -LiteralPath $unregisteredTarget -Force).Count -ne 0) {
                    Throw-Failure 'Unregistered nonempty worktree target requires manual inspection; refusing recursive cleanup'
                }
                [IO.Directory]::Delete($unregisteredTarget, $false)
            }
            if (Test-Path -LiteralPath $onlinePreservedJar -PathType Leaf) { Remove-Item -LiteralPath $onlinePreservedJar -Force }
            $remaining = @(Get-ChildItem -LiteralPath $guardedContainer -Force)
            if ($remaining.Count -ne 0) { Throw-Failure 'Owned temp container is not empty after exact worktree/sibling cleanup' }
            [IO.Directory]::Delete($guardedContainer, $false)
            if (Test-Path -LiteralPath $guardedContainer) { Throw-Failure 'Owned temp container remains after non-recursive removal' }
            $containerRemoved = $true
        }
        foreach ($root in @($repository,$homeRoot,$tempRoot)) {
            if (-not (Test-Path -LiteralPath $root -PathType Container)) { Throw-Failure 'A protected root disappeared during worktree cleanup' }
        }
    }
    if (-not ($registered -and $worktreeRemoved -and $registryRestored -and $containerRemoved)) {
        Throw-Failure 'Clean-worktree transaction did not complete every registration/cleanup marker'
    }
    $distributionHash = Get-Sha256 $DistributionFile
    $markers = [ordered]@{
        evidence_timestamp_utc = [DateTime]::UtcNow.ToString('o')
        os_version = ([Environment]::OSVersion.VersionString -replace '\s+','_')
        jdk_artifact_filename = $Toolchain.Values.jdk_artifact_filename
        jdk_artifact_source = $Toolchain.Values.jdk_artifact_source
        jdk_checksum_source = $Toolchain.Values.jdk_checksum_source
        jdk_official_sha256 = $Toolchain.Values.jdk_official_sha256.ToLowerInvariant()
        jdk_artifact_sha256 = $Toolchain.Values.jdk_archive_sha256.ToLowerInvariant()
        jdk_runtime_version = $Jdk.RuntimeVersion
        jdk_vendor = 'Eclipse_Adoptium'
        jdk_vm_vendor = 'Eclipse_Adoptium'
        jdk_arch = $Jdk.Architecture
        jdk_java_sha256 = $Jdk.JavaSha256
        jdk_javaw_sha256 = $Jdk.JavawSha256
        jdk_javac_sha256 = $Jdk.JavacSha256
        jdk_path_sha256 = $Jdk.PathSha256
        gradle_version = $Toolchain.Values.gradle_version
        wrapper_distribution = $Toolchain.Values.wrapper_distribution
        wrapper_distribution_sha256 = $Toolchain.Values.wrapper_distribution_sha256
        wrapper_sha256 = $Toolchain.Values.wrapper_sha256.ToLowerInvariant()
        minecraft_version = $script:ExpectedMinecraftVersion
        loader_version = $script:ExpectedLoaderVersion
        fabric_api_version = $script:ExpectedFabricApiVersion
        fabric_installer_version = $script:ExpectedInstallerVersion
        java_release = '25'
        template_url = $Toolchain.Values.template_url
        template_ref = $Toolchain.Values.template_ref
        template_commit = $Toolchain.Values.template_commit
        template_remote_commit = $Toolchain.Values.template_remote_commit
        template_tree = $Toolchain.Values.template_tree
        template_origin_verified = $Toolchain.Values.template_origin_verified
        template_clean_before_patch = $Toolchain.Values.template_clean_before_patch
        template_diff_mode = $Toolchain.Values.template_diff_mode
        template_current_diff = $Toolchain.Values.template_current_diff
        snapshot_fallback_used = $Toolchain.Values.snapshot_fallback_used
        loom_requested = $Toolchain.Values.loom_requested
        loom_selected = $Toolchain.Values.loom_selected
        resolved_loom_build = $Toolchain.Values.resolved_loom_build
        resolved_loom_sha256 = $Toolchain.Values.resolved_loom_sha256.ToLowerInvariant()
        pristine_fixed_help_exit = $Toolchain.Values.fixed_help_exit
        pristine_fixed_help_log_sha256 = $Toolchain.Values.fixed_help_log_sha256
        pristine_fixed_build_exit = $Toolchain.Values.fixed_build_exit
        pristine_fixed_build_log_sha256 = $Toolchain.Values.fixed_build_log_sha256
        pristine_fixed_resolution_exit = $Toolchain.Values.fixed_resolution_exit
        pristine_fixed_resolution_log_sha256 = $Toolchain.Values.fixed_resolution_log_sha256
        toolchain_proof_started_utc = $Toolchain.Values.proof_started_utc
        toolchain_proof_completed_utc = $Toolchain.Values.proof_completed_utc
        detached_head = $head
        detached_online_probe = 'PASS'
        detached_online_command = ($online.CommandMarker -replace ' ','_')
        detached_online_exit = [string]$online.ExitCode
        detached_online_log_sha256 = $online.LogSha256
        detached_offline_probe = 'PASS'
        detached_offline_command = ($offline.CommandMarker -replace ' ','_')
        detached_offline_exit = [string]$offline.ExitCode
        detached_offline_log_sha256 = $offline.LogSha256
        clean_checkout_status = 'PASS'
        tracked_manifest = 'PASS'
        temp_child_valid = 'PASS'
        worktree_registered = 'PASS'
        worktree_removed = 'PASS'
        worktree_registry_restored = 'PASS'
        temp_container_removed = 'PASS'
        repository_root_preserved = 'PASS'
        home_root_preserved = 'PASS'
        temp_root_preserved = 'PASS'
        direct_dependency_audit = 'PASS'
        comprehensive_audit = 'PASS'
        ordinary_jar_required_entries = 'PASS'
        ordinary_jar_license = 'PASS'
        ordinary_jar_test_exclusions = 'PASS'
        ordinary_jar_path = 'dist/developers-hell-0.1.0.jar'
        ordinary_jar_size = [string]$offlineArchive.Size
        ordinary_jar_entries_sha256 = $offlineArchive.EntriesSha256
        online_jar_sha256 = $onlineArchive.Sha256
        offline_jar_sha256 = $offlineArchive.Sha256
        distribution_sha256 = $distributionHash
        distribution_path = 'dist/developers-hell-0.1.0.jar'
        gradle_cache_mode = 'online_prime_then_same_cache_offline'
        server_profile_id = 'production-server'
        client_profile_id = 'production-client'
        server_online_ready = 'PENDING'
        server_online_clean_stop = 'PENDING'
        server_isolated_ready = 'PENDING'
        server_isolated_clean_stop = 'PENDING'
        client_preflight = 'PENDING'
        client_isolation_status = 'PENDING'
        client_session_id = 'PENDING'
        client_session_evidence_id = 'PENDING'
        client_supervisor_exit = 'PENDING'
        client_online_ready = 'PENDING'
        client_online_exit = 'PENDING'
        client_isolated_ready = 'PENDING'
        client_isolated_exit = 'PENDING'
        client_isolation_group_id = 'PENDING'
        client_isolation_java_rule_id = 'PENDING'
        client_isolation_javaw_rule_id = 'PENDING'
        client_isolation_java_program_sha256 = 'PENDING'
        client_isolation_javaw_program_sha256 = 'PENDING'
        client_isolation_membership_active = 'PENDING'
        client_isolation_probe_reachable = 'PENDING'
        client_isolation_probe_blocked = 'PENDING'
        client_isolation_java_rule_absent = 'PENDING'
        client_isolation_javaw_rule_absent = 'PENDING'
        client_isolation_membership_after = 'PENDING'
        client_isolation_cleanup = 'PENDING'
        client_distribution_sha256_before = 'PENDING'
        client_distribution_sha256_after = 'PENDING'
        client_runtime_copy_sha256_before = 'PENDING'
        client_runtime_copy_sha256_after = 'PENDING'
        client_receipt_payload_sha256 = 'PENDING'
        online_mod_list = 'PENDING'
        online_world_entry = 'PENDING'
        online_token = 'PENDING'
        online_save_exit = 'PENDING'
        isolated_mod_list = 'PENDING'
        isolated_world_entry = 'PENDING'
        isolated_token = 'PENDING'
        isolated_save_exit = 'PENDING'
        uat_status = 'PENDING'
    }
    Set-EvidenceMarkers -LiteralPath $EvidenceFile -Markers $markers
    Write-Pass 'clean-checkout artifact proof and public-safe pending evidence written'
}

function Assert-WindowsFirewallControl {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { Throw-Failure 'Windows Defender Firewall isolation requires Windows' }
    foreach ($command in @('Get-NetFirewallRule','Get-NetFirewallApplicationFilter','New-NetFirewallRule','Remove-NetFirewallRule')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { Throw-Failure "Windows Defender Firewall cmdlet unavailable: $command" }
    }
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        Throw-Failure 'Administrative control is required for exact two-rule firewall isolation'
    }
    $controlProbeName = 'DevelopersHell.Foundation.ControlProbe.' + [guid]::NewGuid().ToString('N')
    try { [void](Get-NetFirewallRule -Name $controlProbeName -PolicyStore ActiveStore -ErrorAction SilentlyContinue) }
    catch { Throw-Failure "Windows Defender Firewall ActiveStore is unavailable: $($_.Exception.Message)" }
    Write-Pass 'elevated Windows Defender Firewall control available'
}

function Test-ExactFirewallRuleExists {
    param([Parameter(Mandatory)][string] $Name)
    if (Get-NetFirewallRule -Name $Name -PolicyStore PersistentStore -ErrorAction SilentlyContinue) { return $true }
    if (Get-NetFirewallRule -Name $Name -PolicyStore ActiveStore -ErrorAction SilentlyContinue) { return $true }
    return $false
}

function Get-ExactFirewallGroupCount {
    param(
        [Parameter(Mandatory)][string] $Group,
        [ValidateSet('ActiveStore','PersistentStore')][string] $PolicyStore = 'ActiveStore'
    )
    return @(Get-NetFirewallRule -Group $Group -PolicyStore $PolicyStore -ErrorAction SilentlyContinue).Count
}

function Invoke-ExactJavaNetworkProbe {
    param(
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][bool] $ExpectReachable
    )
    $probeRoot = Join-Path $script:RepositoryRoot ('.work\network-probe-' + [guid]::NewGuid().ToString('N'))
    $probeFile = Join-Path $probeRoot 'NetworkProbe.java'
    if (Test-Path -LiteralPath $probeRoot) { Throw-Failure 'Network probe directory unexpectedly pre-exists' }
    [void](New-Item -ItemType Directory -Path $probeRoot)
    try {
        Assert-NoReparsePoint -LiteralPath $probeRoot
        $source = @'
import java.net.InetSocketAddress;
import java.net.Socket;

final class NetworkProbe {
    public static void main(String[] args) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(args[0], Integer.parseInt(args[1])), 4000);
            System.out.println("REACHABLE");
            System.exit(0);
        } catch (Exception expected) {
            System.out.println("BLOCKED");
            System.exit(7);
        }
    }
}
'@
        [IO.File]::WriteAllText($probeFile, $source, [Text.UTF8Encoding]::new($false))
        $result = Invoke-NativeCapture -FilePath $Jdk.Java -ArgumentList @($probeFile,$script:ProbeHost,[string]$script:ProbePort) -WorkingDirectory $probeRoot -AllowFailure -TimeoutSeconds 15
        if ($ExpectReachable) {
            if ($result.ExitCode -ne 0 -or $result.StdOut -notmatch '(?m)^REACHABLE\s*$') { Throw-Failure 'Exact-Java pre-isolation network probe was not reachable' }
            return 'PASS'
        }
        if ($result.ExitCode -eq 0 -or $result.StdOut -notmatch '(?m)^BLOCKED\s*$') { Throw-Failure 'Exact-Java probe remained reachable under firewall isolation' }
        return 'BLOCKED'
    }
    finally {
        if (Test-Path -LiteralPath $probeFile) { Remove-Item -LiteralPath $probeFile -Force }
        if (Test-Path -LiteralPath $probeRoot) {
            if (@(Get-ChildItem -LiteralPath $probeRoot -Force).Count -ne 0) { Throw-Failure 'Network probe directory is not empty at cleanup' }
            [IO.Directory]::Delete($probeRoot, $false)
        }
    }
}

function Invoke-WithFirewallIsolation {
    param(
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][scriptblock] $Operation
    )
    $script:LastIsolationRecord = $null
    Assert-WindowsFirewallControl
    $suffix = [guid]::NewGuid().ToString('N')
    $groupId = "DevelopersHell.Foundation.$suffix"
    $javaRuleId = "DevelopersHell.Foundation.Java.$suffix"
    $javawRuleId = "DevelopersHell.Foundation.Javaw.$suffix"
    if ($javaRuleId -eq $javawRuleId) { Throw-Failure 'Firewall rule IDs collided' }
    if (Test-ExactFirewallRuleExists -Name $javaRuleId) { Throw-Failure 'Fresh Java firewall rule ID already exists' }
    if (Test-ExactFirewallRuleExists -Name $javawRuleId) { Throw-Failure 'Fresh Javaw firewall rule ID already exists' }
    if ((Get-ExactFirewallGroupCount -Group $groupId -PolicyStore ActiveStore) -ne 0 -or (Get-ExactFirewallGroupCount -Group $groupId -PolicyStore PersistentStore) -ne 0) { Throw-Failure 'Fresh firewall group already contains rules' }
    $probeOnline = Invoke-ExactJavaNetworkProbe -Jdk $Jdk -ExpectReachable $true
    $javaCreated = $false
    $javawCreated = $false
    $operationResult = $null
    $primaryError = $null
    $cleanupError = $null
    $record = [ordered]@{
        group_id = $groupId
        java_rule_id = $javaRuleId
        javaw_rule_id = $javawRuleId
        java_program_sha256 = $Jdk.JavaSha256
        javaw_program_sha256 = $Jdk.JavawSha256
        member_count_active = 0
        member_count_after = -1
        java_rule_absent = $false
        javaw_rule_absent = $false
        probe_online = $probeOnline
        probe_isolated = 'NOT_RUN'
        cleanup_status = 'FAIL'
    }
    try {
        [void](New-NetFirewallRule -Name $javaRuleId -DisplayName $javaRuleId -Group $groupId -Direction Outbound -Action Block -Program $Jdk.Java -Profile Any -Enabled True -PolicyStore PersistentStore -ErrorAction Stop)
        $javaCreated = $true
        [void](New-NetFirewallRule -Name $javawRuleId -DisplayName $javawRuleId -Group $groupId -Direction Outbound -Action Block -Program $Jdk.Javaw -Profile Any -Enabled True -PolicyStore PersistentStore -ErrorAction Stop)
        $javawCreated = $true
        $activationDeadline = [DateTime]::UtcNow.AddSeconds(10)
        while ([DateTime]::UtcNow -lt $activationDeadline -and
            ((Get-ExactFirewallGroupCount -Group $groupId -PolicyStore ActiveStore) -ne 2 -or (Get-ExactFirewallGroupCount -Group $groupId -PolicyStore PersistentStore) -ne 2)) {
            Start-Sleep -Milliseconds 250
        }
        $members = @(Get-NetFirewallRule -Group $groupId -PolicyStore ActiveStore -ErrorAction Stop)
        $persistentMembers = Get-ExactFirewallGroupCount -Group $groupId -PolicyStore PersistentStore
        if ($members.Count -ne 2 -or $persistentMembers -ne 2) { Throw-Failure "Firewall group membership is active=$($members.Count), persistent=$persistentMembers; expected exactly 2 in both stores" }
        $memberNames = @($members | ForEach-Object Name)
        if ($memberNames -notcontains $javaRuleId -or $memberNames -notcontains $javawRuleId) { Throw-Failure 'Firewall group does not contain both exact rule IDs' }
        foreach ($rule in $members) {
            if ($rule.Direction -ne 'Outbound' -or $rule.Action -ne 'Block' -or $rule.Enabled -ne 'True') { Throw-Failure "Firewall rule has unexpected state: $($rule.Name)" }
            $applications = @(Get-NetFirewallApplicationFilter -AssociatedNetFirewallRule $rule -ErrorAction Stop)
            if ($applications.Count -ne 1) { Throw-Failure "Firewall rule has an ambiguous application filter: $($rule.Name)" }
            $application = $applications[0]
            $expectedProgram = if ($rule.Name -eq $javaRuleId) { $Jdk.Java } else { $Jdk.Javaw }
            if (-not ([IO.Path]::GetFullPath([string]$application.Program)).Equals([IO.Path]::GetFullPath($expectedProgram), [StringComparison]::OrdinalIgnoreCase)) {
                Throw-Failure "Firewall application path mismatch for $($rule.Name)"
            }
        }
        $record.member_count_active = 2
        $record.probe_isolated = Invoke-ExactJavaNetworkProbe -Jdk $Jdk -ExpectReachable $false
        $script:LastIsolationRecord = $record
        $operationResult = & $Operation ([pscustomobject]$record)
    }
    catch {
        $primaryError = $_
    }
    finally {
        try {
            if (Test-ExactFirewallRuleExists -Name $javaRuleId) {
                Remove-NetFirewallRule -Name $javaRuleId -PolicyStore PersistentStore -ErrorAction Stop
            }
            if (Test-ExactFirewallRuleExists -Name $javawRuleId) {
                Remove-NetFirewallRule -Name $javawRuleId -PolicyStore PersistentStore -ErrorAction Stop
            }
            $cleanupDeadline = [DateTime]::UtcNow.AddSeconds(10)
            while ([DateTime]::UtcNow -lt $cleanupDeadline -and
                ((Test-ExactFirewallRuleExists -Name $javaRuleId) -or (Test-ExactFirewallRuleExists -Name $javawRuleId) -or
                (Get-ExactFirewallGroupCount -Group $groupId -PolicyStore ActiveStore) -ne 0 -or (Get-ExactFirewallGroupCount -Group $groupId -PolicyStore PersistentStore) -ne 0)) {
                Start-Sleep -Milliseconds 250
            }
            $record.java_rule_absent = -not (Test-ExactFirewallRuleExists -Name $javaRuleId)
            $record.javaw_rule_absent = -not (Test-ExactFirewallRuleExists -Name $javawRuleId)
            $activeAfter = Get-ExactFirewallGroupCount -Group $groupId -PolicyStore ActiveStore
            $persistentAfter = Get-ExactFirewallGroupCount -Group $groupId -PolicyStore PersistentStore
            $record.member_count_after = $activeAfter
            if (-not $record.java_rule_absent -or -not $record.javaw_rule_absent -or $activeAfter -ne 0 -or $persistentAfter -ne 0) {
                Throw-Failure 'Exact firewall-rule cleanup could not be proven'
            }
            $record.cleanup_status = 'PASS'
            $script:LastIsolationRecord = $record
        }
        catch { $cleanupError = $_ }
    }
    if ($cleanupError) { Throw-Failure "Firewall cleanup failed: $($cleanupError.Exception.Message)" }
    if ($primaryError) { throw $primaryError }
    Write-Pass 'exact two-rule Java/javaw firewall isolation and cleanup'
    return [pscustomobject]@{ Operation = $operationResult; Isolation = [pscustomobject]$record }
}

function Get-ValidatedDistribution {
    param(
        [Parameter(Mandatory)][string] $DistributionFile,
        [Parameter(Mandatory)][string] $EvidenceFile
    )
    if (-not (Test-Path -LiteralPath $DistributionFile -PathType Leaf)) { Throw-Failure 'Verified distribution JAR is missing' }
    Assert-SingleDistributionArtifact -DistributionFile $DistributionFile
    if ((Split-Path -Leaf $DistributionFile) -cne $script:ExpectedDistributionName) { Throw-Failure 'Runtime distribution filename is not exact' }
    $expectedPath = Resolve-CanonicalPath -LiteralPath $script:DefaultDistributionPath
    $actualPath = Resolve-CanonicalPath -LiteralPath $DistributionFile
    if (-not $actualPath.Equals($expectedPath, [StringComparison]::OrdinalIgnoreCase)) { Throw-Failure 'Runtime distribution is not the declared repository dist artifact' }
    if (-not (Test-Path -LiteralPath $EvidenceFile -PathType Leaf)) { Throw-Failure 'Foundation evidence is missing before runtime launch' }
    $evidence = Get-Content -LiteralPath $EvidenceFile -Raw
    $expectedHash = (Get-EvidenceMarker -Text $evidence -Name 'distribution_sha256').ToLowerInvariant()
    if ($expectedHash -notmatch '^[0-9a-f]{64}$') { Throw-Failure 'Evidence distribution SHA-256 is invalid' }
    $actualHash = Get-Sha256 $actualPath
    Assert-Equal $actualHash $expectedHash 'Runtime distribution/evidence SHA-256'
    [void](Get-ProductionArchiveContract -JarPath $actualPath)
    return [pscustomobject]@{ Path = $actualPath; Sha256 = $actualHash; EvidenceText = $evidence }
}

function Reset-OwnedRuntimeProfile {
    param([Parameter(Mandatory)][ValidateSet('production-server','production-client')][string] $ProfileName)
    $runRootCandidate = Join-Path $script:RepositoryRoot 'run'
    if (-not (Test-Path -LiteralPath $runRootCandidate -PathType Container)) { [void](New-Item -ItemType Directory -Path $runRootCandidate) }
    $runRoot = Resolve-CanonicalPath -LiteralPath $runRootCandidate
    Assert-NoReparsePoint -LiteralPath $runRoot
    $profileCandidate = Join-Path $runRoot $ProfileName
    if (Test-Path -LiteralPath $profileCandidate) {
        $profile = Resolve-CanonicalPath -LiteralPath $profileCandidate
        Assert-NoReparsePoint -LiteralPath $profile
        Assert-PathInside -Child $profile -Parent $runRoot -Label 'Runtime profile'
        if ((Split-Path -Leaf $profile) -cne $ProfileName) { Throw-Failure 'Runtime profile leaf mismatch' }
        [IO.Directory]::Delete($profile, $true)
    }
    [void](New-Item -ItemType Directory -Path $profileCandidate)
    $created = Resolve-CanonicalPath -LiteralPath $profileCandidate
    Assert-NoReparsePoint -LiteralPath $created
    Assert-PathInside -Child $created -Parent $runRoot -Label 'Runtime profile'
    return $created
}

function Install-DistributionRuntimeCopy {
    param([Parameter(Mandatory)] $Distribution)
    $buildLibs = Join-Path $script:RepositoryRoot 'build\libs'
    if (-not (Test-Path -LiteralPath $buildLibs -PathType Container)) { [void](New-Item -ItemType Directory -Path $buildLibs) }
    $runtimeCopy = Join-Path $buildLibs $script:ExpectedDistributionName
    Copy-FileAtomically -Source $Distribution.Path -Destination $runtimeCopy
    Assert-Equal (Get-Sha256 $runtimeCopy) $Distribution.Sha256 'Production runtime-copy SHA-256'
    return (Resolve-CanonicalPath -LiteralPath $runtimeCopy)
}

function Start-GradleRuntime {
    param(
        [Parameter(Mandatory)][ValidateSet('runProductionServer','runProductionClient')][string] $TaskName,
        [Parameter(Mandatory)] $Jdk,
        [switch] $Offline
    )
    $wrapper = Join-Path $script:RepositoryRoot 'gradlew.bat'
    $arguments = @(Get-GradleJvmArguments -Jdk $Jdk)
    if ($Offline) { $arguments += '--offline' }
    $arguments += @($TaskName,'-x','jar','--no-daemon','--info','--stacktrace')
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $env:ComSpec
    $start.Arguments = Get-CmdBatchArguments -BatchPath $wrapper -ArgumentList $arguments
    $start.WorkingDirectory = $script:RepositoryRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { Throw-Failure "Could not start Gradle runtime task $TaskName" }
    return [pscustomobject]@{
        Process = $process
        StdOutTask = $process.StandardOutput.ReadToEndAsync()
        StdErrTask = $process.StandardError.ReadToEndAsync()
        TaskName = $TaskName
        Offline = [bool]$Offline
    }
}

function Complete-GradleRuntime {
    param(
        [Parameter(Mandatory)] $Runtime,
        [int] $TimeoutSeconds = 120
    )
    if (-not $Runtime.Process.WaitForExit($TimeoutSeconds * 1000)) {
        try { $Runtime.Process.Kill() } catch { }
        Throw-Failure "$($Runtime.TaskName) did not terminate inside $TimeoutSeconds seconds"
    }
    $stdout = $Runtime.StdOutTask.GetAwaiter().GetResult()
    $stderr = $Runtime.StdErrTask.GetAwaiter().GetResult()
    $exit = $Runtime.Process.ExitCode
    return [pscustomobject]@{ ExitCode = $exit; StdOut = $stdout; StdErr = $stderr; Combined = (($stdout.TrimEnd(),$stderr.TrimEnd()) | Where-Object { $_ }) -join "`n" }
}

function Wait-ForRuntimeLog {
    param(
        [Parameter(Mandatory)] $Runtime,
        [Parameter(Mandatory)][string] $LogPath,
        [Parameter(Mandatory)][string[]] $RequiredPatterns,
        [Parameter(Mandatory)][int] $TimeoutSeconds,
        [scriptblock] $AdditionalCheck
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Runtime.Process.HasExited) { Throw-Failure "$($Runtime.TaskName) exited before readiness" }
        $text = if (Test-Path -LiteralPath $LogPath -PathType Leaf) { Get-Content -LiteralPath $LogPath -Raw -ErrorAction SilentlyContinue } else { '' }
        $all = $true
        foreach ($pattern in $RequiredPatterns) { if ($text -notmatch $pattern) { $all = $false; break } }
        if ($all) {
            if (-not $AdditionalCheck -or (& $AdditionalCheck)) { return $text }
        }
        Start-Sleep -Milliseconds 500
    }
    Throw-Failure "$($Runtime.TaskName) did not reach its bounded readiness markers"
}

function Get-ClientProcessInfo {
    param(
        [Parameter(Mandatory)][int] $RootProcessId,
        [Parameter(Mandatory)] $Jdk
    )
    if (-not (Get-Command Get-CimInstance -ErrorAction SilentlyContinue)) { Throw-Failure 'CIM process inspection is unavailable' }
    $rows = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    $descendantIds = [System.Collections.Generic.HashSet[int]]::new()
    [void]$descendantIds.Add($RootProcessId)
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($row in $rows) {
            if ($descendantIds.Contains([int]$row.ParentProcessId) -and -not $descendantIds.Contains([int]$row.ProcessId)) {
                [void]$descendantIds.Add([int]$row.ProcessId)
                $changed = $true
            }
        }
    }
    $candidates = @($rows | Where-Object {
        $descendantIds.Contains([int]$_.ProcessId) -and
        [int]$_.ProcessId -ne $RootProcessId -and
        [string]$_.CommandLine -match '(?i)(KnotClient|net\.minecraft\.client\.main\.Main)' -and
        -not [string]::IsNullOrWhiteSpace([string]$_.ExecutablePath)
    })
    if ($candidates.Count -eq 0) { return $null }
    if ($candidates.Count -ne 1) { Throw-Failure "Expected one production Minecraft client process, observed $($candidates.Count)" }
    $executable = Resolve-CanonicalPath -LiteralPath ([string]$candidates[0].ExecutablePath)
    $validExecutables = @((Resolve-CanonicalPath $Jdk.Java),(Resolve-CanonicalPath $Jdk.Javaw))
    if (-not @($validExecutables | Where-Object { $_.Equals($executable,[StringComparison]::OrdinalIgnoreCase) }).Count) {
        Throw-Failure 'Production Minecraft client executable is outside the exact verified JDK'
    }
    return [pscustomobject]@{ Pid = [int]$candidates[0].ProcessId; Executable = $executable; CommandLine = [string]$candidates[0].CommandLine }
}

function Invoke-ServerSession {
    param(
        [Parameter(Mandatory)] $Distribution,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $RuntimeCopy,
        [switch] $Offline
    )
    Assert-Equal (Get-Sha256 $Distribution.Path) $Distribution.Sha256 'Server prelaunch distribution SHA-256'
    Assert-Equal (Get-Sha256 $RuntimeCopy) $Distribution.Sha256 'Server prelaunch runtime-copy SHA-256'
    $logPath = Join-Path $script:RepositoryRoot 'run\production-server\logs\latest.log'
    Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue
    $runtime = Start-GradleRuntime -TaskName runProductionServer -Jdk $Jdk -Offline:$Offline
    try {
        $logText = Wait-ForRuntimeLog -Runtime $runtime -LogPath $logPath -RequiredPatterns @(
            '(?i)Developer''s Hell foundation initialized',
            '(?i)Done \([^)]+\)! For help'
        ) -TimeoutSeconds 300
        if ($logText -match '(?i)(NoClassDefFoundError|ClassNotFoundException|net\.minecraft\.client|com\.mojang\.blaze3d|crash report)') {
            Throw-Failure 'Production server log contains linkage/crash markers'
        }
        $runtime.Process.StandardInput.WriteLine('stop')
        $runtime.Process.StandardInput.Flush()
        $runtime.Process.StandardInput.Close()
        $complete = Complete-GradleRuntime -Runtime $runtime -TimeoutSeconds 120
        if ($complete.ExitCode -ne 0) { Throw-Failure "Production server exited nonzero: $($complete.ExitCode)" }
        if (($complete.Combined -replace '\\','/') -notmatch [regex]::Escape($script:ExpectedDistributionName)) {
            Throw-Failure 'Production server launch output does not name the exact runtime JAR'
        }
        $runtime.Process.Dispose()
    }
    catch {
        Stop-RuntimeAfterFailure -Runtime $runtime -ClientInfo $null
        throw
    }
    Assert-Equal (Get-Sha256 $RuntimeCopy) $Distribution.Sha256 'Server postlaunch runtime-copy SHA-256'
    Assert-Equal (Get-Sha256 $Distribution.Path) $Distribution.Sha256 'Server postlaunch distribution SHA-256'
    return [pscustomobject]@{ Ready = $true; CleanStop = $true; ExitCode = 0 }
}

function Invoke-RunServerSmokeMode {
    param(
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $EvidenceFile,
        [Parameter(Mandatory)][string] $DistributionFile
    )
    $distribution = Get-ValidatedDistribution -DistributionFile $DistributionFile -EvidenceFile $EvidenceFile
    $profile = Reset-OwnedRuntimeProfile -ProfileName production-server
    [IO.File]::WriteAllText((Join-Path $profile 'eula.txt'), "eula=true`r`n", [Text.UTF8Encoding]::new($false))
    $runtimeCopy = Install-DistributionRuntimeCopy -Distribution $distribution
    $online = Invoke-ServerSession -Distribution $distribution -Jdk $Jdk -RuntimeCopy $runtimeCopy
    $isolatedEnvelope = Invoke-WithFirewallIsolation -Jdk $Jdk -Operation {
        param($Isolation)
        Invoke-ServerSession -Distribution $distribution -Jdk $Jdk -RuntimeCopy $runtimeCopy -Offline
    }
    $isolated = $isolatedEnvelope.Operation
    $isolation = $isolatedEnvelope.Isolation
    $markers = [ordered]@{
        server_distribution_sha256_before = $distribution.Sha256
        server_runtime_copy_sha256_before = $distribution.Sha256
        server_online_ready = if ($online.Ready) { 'PASS' } else { 'FAIL' }
        server_online_clean_stop = if ($online.CleanStop) { 'PASS' } else { 'FAIL' }
        server_isolated_ready = if ($isolated.Ready) { 'PASS' } else { 'FAIL' }
        server_isolated_clean_stop = if ($isolated.CleanStop) { 'PASS' } else { 'FAIL' }
        server_runtime_copy_sha256_after = Get-Sha256 $runtimeCopy
        server_distribution_sha256_after = Get-Sha256 $distribution.Path
        server_isolation_group_id = $isolation.group_id
        server_isolation_java_rule_id = $isolation.java_rule_id
        server_isolation_javaw_rule_id = $isolation.javaw_rule_id
        server_isolation_java_program_sha256 = $isolation.java_program_sha256
        server_isolation_javaw_program_sha256 = $isolation.javaw_program_sha256
        server_isolation_membership_active = [string]$isolation.member_count_active
        server_isolation_probe_reachable = $isolation.probe_online
        server_isolation_probe_blocked = $isolation.probe_isolated
        server_isolation_java_rule_absent = if ($isolation.java_rule_absent) { 'PASS' } else { 'FAIL' }
        server_isolation_javaw_rule_absent = if ($isolation.javaw_rule_absent) { 'PASS' } else { 'FAIL' }
        server_isolation_membership_after = [string]$isolation.member_count_after
        server_isolation_cleanup = $isolation.cleanup_status
    }
    Set-EvidenceMarkers -LiteralPath $EvidenceFile -Markers $markers
    Write-Pass 'online and exactly-two-rule isolated production server smoke'
}

function Wait-ForClientReady {
    param(
        [Parameter(Mandatory)] $Runtime,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $LogPath,
        [int] $TimeoutSeconds = 420
    )
    $detected = $null
    $logText = Wait-ForRuntimeLog -Runtime $Runtime -LogPath $LogPath -RequiredPatterns @(
        '(?i)Developer''s Hell foundation initialized',
        '(?i)(Sound engine started|Created:\s+.*atlas|OpenAL initialized|Narrator library)'
    ) -TimeoutSeconds $TimeoutSeconds -AdditionalCheck {
        $script:CandidateClient = Get-ClientProcessInfo -RootProcessId $Runtime.Process.Id -Jdk $Jdk
        return $null -ne $script:CandidateClient
    }
    $detected = $script:CandidateClient
    Remove-Variable -Scope Script -Name CandidateClient -ErrorAction SilentlyContinue
    if (-not $detected -or -not (Get-Process -Id $detected.Pid -ErrorAction SilentlyContinue)) { Throw-Failure 'Ready client PID is not live' }
    if ($logText -match '(?i)(NoClassDefFoundError|ClassNotFoundException|crash report|Failed to start Minecraft)') {
        Throw-Failure 'Production client log contains linkage/crash markers'
    }
    return $detected
}

function Stop-RuntimeAfterFailure {
    param(
        [Parameter(Mandatory)] $Runtime,
        $ClientInfo
    )
    if ($ClientInfo) {
        try {
            $client = [Diagnostics.Process]::GetProcessById([int]$ClientInfo.Pid)
            [void]$client.CloseMainWindow()
            if (-not $client.WaitForExit(15000)) { $client.Kill() }
            $client.Dispose()
        }
        catch { }
    }
    try {
        if (-not $Runtime.Process.HasExited) {
            $Runtime.Process.Kill()
            [void]$Runtime.Process.WaitForExit(15000)
        }
    }
    catch { }
    try { $Runtime.Process.Dispose() } catch { }
}

function Wait-ForHumanClientExit {
    param(
        [Parameter(Mandatory)] $Runtime,
        [Parameter(Mandatory)] $ClientInfo,
        [Parameter(Mandatory)][string] $ControlPath,
        [int] $TimeoutSeconds = 21600
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $clientProcess = [Diagnostics.Process]::GetProcessById([int]$ClientInfo.Pid)
    try {
        while ([DateTime]::UtcNow -lt $deadline) {
            if (Test-Path -LiteralPath $ControlPath -PathType Leaf) {
                $controlText = Get-Content -LiteralPath $ControlPath -Raw -ErrorAction Stop
                if (-not [string]::IsNullOrWhiteSpace($controlText)) {
                    try { $control = $controlText | ConvertFrom-Json } catch { Throw-Failure 'Interactive UAT control.json is invalid JSON' }
                    if ($null -eq $control) { Throw-Failure 'Interactive UAT control.json cannot contain JSON null' }
                    if ($control.PSObject.Properties['action'] -and [string]$control.action -eq 'CANCEL') { Throw-Failure 'Interactive UAT was cancelled by automation control' }
                }
            }
            if ($clientProcess.HasExited) {
                $complete = Complete-GradleRuntime -Runtime $Runtime -TimeoutSeconds 120
                if ($complete.ExitCode -ne 0) { Throw-Failure "Production client did not exit normally (Gradle exit $($complete.ExitCode))" }
                if (($complete.Combined -replace '\\','/') -notmatch [regex]::Escape($script:ExpectedDistributionName)) {
                    Throw-Failure 'Production client launch output does not name the exact runtime JAR'
                }
                $Runtime.Process.Dispose()
                return [pscustomobject]@{ Exit = 'NORMAL'; ExitCode = 0 }
            }
            if ($Runtime.Process.HasExited) { Throw-Failure 'Gradle runtime exited while the expected client PID was still live' }
            Start-Sleep -Milliseconds 750
        }
        Throw-Failure 'Human client session exceeded the bounded supervision window'
    }
    finally {
        $clientProcess.Dispose()
    }
}

function Invoke-ClientPreflightMode {
    param(
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $EvidenceFile,
        [Parameter(Mandatory)][string] $DistributionFile
    )
    $distribution = Get-ValidatedDistribution -DistributionFile $DistributionFile -EvidenceFile $EvidenceFile
    [void](Reset-OwnedRuntimeProfile -ProfileName production-client)
    $runtimeCopy = Install-DistributionRuntimeCopy -Distribution $distribution
    $logPath = Join-Path $script:RepositoryRoot 'run\production-client\logs\latest.log'
    Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue
    $runtime = Start-GradleRuntime -TaskName runProductionClient -Jdk $Jdk
    $client = $null
    try {
        $client = Wait-ForClientReady -Runtime $runtime -Jdk $Jdk -LogPath $logPath
        $clientProcess = [Diagnostics.Process]::GetProcessById($client.Pid)
        try {
            try { [void]$clientProcess.WaitForInputIdle(30000) } catch { }
            if (-not $clientProcess.CloseMainWindow()) { Throw-Failure 'Production client preflight could not request a window close' }
            if (-not $clientProcess.WaitForExit(120000)) { Throw-Failure 'Production client preflight did not close inside the bounded window' }
        }
        finally { $clientProcess.Dispose() }
        $complete = Complete-GradleRuntime -Runtime $runtime -TimeoutSeconds 120
        if ($complete.ExitCode -ne 0) { Throw-Failure "Production client preflight Gradle task exited $($complete.ExitCode)" }
        if (($complete.Combined -replace '\\','/') -notmatch [regex]::Escape($script:ExpectedDistributionName)) { Throw-Failure 'Client preflight launch output does not name the exact runtime JAR' }
        $runtime.Process.Dispose()
    }
    catch {
        Stop-RuntimeAfterFailure -Runtime $runtime -ClientInfo $client
        throw
    }
    Assert-Equal (Get-Sha256 $runtimeCopy) $distribution.Sha256 'Client-preflight runtime-copy SHA-256'
    Assert-Equal (Get-Sha256 $distribution.Path) $distribution.Sha256 'Client-preflight distribution SHA-256'
    Set-EvidenceMarkers -LiteralPath $EvidenceFile -Markers ([ordered]@{
        client_preflight = 'PASS'
        client_entrypoints = 'PASS'
        client_menu_ready = 'PASS'
        client_profile_id = 'production-client'
        client_distribution_sha256_before = $distribution.Sha256
        client_runtime_copy_sha256_before = $distribution.Sha256
        client_runtime_copy_sha256_after = Get-Sha256 $runtimeCopy
        client_distribution_sha256_after = Get-Sha256 $distribution.Path
    })
    Write-Pass 'online production-client profile preflight and menu readiness'
}

function Get-SanitizedFailure {
    param([Parameter(Mandatory)][string] $Message)
    $safe = $Message -replace [regex]::Escape($script:RepositoryRoot), '<repository>'
    $userProfileRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    if (-not [string]::IsNullOrWhiteSpace($userProfileRoot)) { $safe = $safe -replace [regex]::Escape($userProfileRoot), '<home>' }
    $safe = $safe -replace '[\r\n]+',' '
    if ($safe.Length -gt 300) { $safe = $safe.Substring(0,300) }
    return $safe
}

function Assert-InteractiveSessionPath {
    param([Parameter(Mandatory)][string] $SessionPath)
    $workRootCandidate = Join-Path $script:RepositoryRoot '.work'
    if (-not (Test-Path -LiteralPath $workRootCandidate -PathType Container)) { [void](New-Item -ItemType Directory -Path $workRootCandidate) }
    $workRoot = Resolve-CanonicalPath -LiteralPath $workRootCandidate
    $session = Resolve-CanonicalPath -LiteralPath $SessionPath
    Assert-NoReparsePoint -LiteralPath $session
    Assert-PathInside -Child $session -Parent $workRoot -Label 'Interactive UAT session'
    if ((Split-Path -Parent $session) -cne $workRoot -or (Split-Path -Leaf $session) -notmatch '^interactive-uat-[0-9a-f]{32}$') {
        Throw-Failure 'Interactive UAT session must be a direct canonical GUID child under .work'
    }
    return $session
}

function Invoke-SuperviseInteractiveUatMode {
    param(
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $EvidenceFile,
        [Parameter(Mandatory)][string] $DistributionFile,
        [Parameter(Mandatory)][string] $SessionPath,
        [Parameter(Mandatory)][string] $PointerPath,
        [Parameter(Mandatory)][string] $ReceiptPath
    )
    $script:LastIsolationRecord = $null
    Assert-WindowsFirewallControl
    $distribution = Get-ValidatedDistribution -DistributionFile $DistributionFile -EvidenceFile $EvidenceFile
    $evidenceText = Get-Content -LiteralPath $EvidenceFile -Raw
    foreach ($marker in @('server_online_ready','server_online_clean_stop','server_isolated_ready','server_isolated_clean_stop','client_preflight')) {
        Assert-Equal (Get-EvidenceMarker -Text $evidenceText -Name $marker) 'PASS' "Interactive prerequisite $marker"
    }
    $session = Assert-InteractiveSessionPath -SessionPath $SessionPath
    $sessionId = (Split-Path -Leaf $session).Substring('interactive-uat-'.Length)
    $expectedPointer = Resolve-CanonicalPath -LiteralPath $script:DefaultSessionPointerPath -AllowMissingLeaf
    $pointer = Resolve-CanonicalPath -LiteralPath $PointerPath -AllowMissingLeaf
    if (-not $pointer.Equals($expectedPointer,[StringComparison]::OrdinalIgnoreCase)) { Throw-Failure 'Interactive UAT pointer path is not exact' }
    if (Test-Path -LiteralPath $pointer) { Throw-Failure 'Interactive UAT pointer already exists' }
    $statusPath = Join-Path $session 'status.json'
    $supervisorLogPath = Join-Path $session 'supervisor.log'
    $controlPath = Join-Path $session 'control.json'
    $expectedReceipt = Resolve-CanonicalPath -LiteralPath (Join-Path $session 'receipt.json') -AllowMissingLeaf
    if (-not (Resolve-CanonicalPath -LiteralPath $ReceiptPath -AllowMissingLeaf).Equals($expectedReceipt,[StringComparison]::OrdinalIgnoreCase)) { Throw-Failure 'Interactive receipt path escaped the unique session' }
    $existing = @(Get-ChildItem -LiteralPath $session -Force)
    if (@($existing | Where-Object { $_.Name -ne 'control.json' }).Count -ne 0) { Throw-Failure 'Interactive UAT session was reused or contains terminal state' }
    if (-not (Test-Path -LiteralPath $controlPath -PathType Leaf)) {
        Write-JsonAtomic -LiteralPath $controlPath -Value ([ordered]@{ action = 'WAIT' })
    }
    else {
        $controlText = Get-Content -LiteralPath $controlPath -Raw
        if ([string]::IsNullOrWhiteSpace($controlText)) {
            Write-JsonAtomic -LiteralPath $controlPath -Value ([ordered]@{ action = 'WAIT' })
        }
        else {
            $control = $controlText | ConvertFrom-Json
            if ($null -eq $control) { Throw-Failure 'Interactive control JSON cannot be null' }
            if ($control.PSObject.Properties['action'] -and [string]$control.action -notin @('WAIT','CONTINUE')) { Throw-Failure 'Interactive control has a non-initial action' }
        }
    }
    $profile = Resolve-CanonicalPath -LiteralPath (Join-Path $script:RepositoryRoot 'run\production-client')
    Assert-NoReparsePoint -LiteralPath $profile
    $runtimeCopy = Install-DistributionRuntimeCopy -Distribution $distribution
    $evidenceId = Get-Sha256 $EvidenceFile
    $started = [DateTime]::UtcNow
    $onlineRuntime = $null
    $isolatedRuntime = $null
    $onlineClient = $null
    $isolatedClient = $null
    $onlineExit = $null
    $isolatedExit = $null
    $isolationRecord = $null
    $terminalStatus = 'FAILED'
    $failure = $null
    $onlineReadyAt = $null
    $isolatedReadyAt = $null
    $distributionBefore = Get-Sha256 $distribution.Path
    $runtimeCopyBefore = Get-Sha256 $runtimeCopy
    [IO.File]::WriteAllText($supervisorLogPath, "state=STARTING`r`n", [Text.UTF8Encoding]::new($false))
    Write-JsonAtomic -LiteralPath $pointer -Value ([ordered]@{
        session_id = $sessionId
        session_directory = $session
        status_path = $statusPath
        receipt_path = $expectedReceipt
        evidence_path = $EvidenceFile
        distribution_path = $distribution.Path
        supervisor_pid = $PID
    })
    Write-JsonAtomic -LiteralPath $statusPath -Value ([ordered]@{ state='STARTING'; session_id=$sessionId; supervisor_pid=$PID; updated_utc=[DateTime]::UtcNow.ToString('o') })
    try {
        Remove-Item -LiteralPath (Join-Path $profile 'logs\latest.log') -Force -ErrorAction SilentlyContinue
        $onlineRuntime = Start-GradleRuntime -TaskName runProductionClient -Jdk $Jdk
        $onlineClient = Wait-ForClientReady -Runtime $onlineRuntime -Jdk $Jdk -LogPath (Join-Path $profile 'logs\latest.log')
        if ($onlineClient.Pid -eq $PID -or $onlineClient.Pid -eq $onlineRuntime.Process.Id) { Throw-Failure 'Online client PID is not distinct from supervisor/wrapper' }
        $onlineReadyAt = [DateTime]::UtcNow.ToString('o')
        Write-JsonAtomic -LiteralPath $statusPath -Value ([ordered]@{
            state='ONLINE_READY'; session_id=$sessionId; profile_id='production-client'; distribution_sha256=$distribution.Sha256;
            supervisor_pid=$PID; client_pid=$onlineClient.Pid; menu_ready=$true; updated_utc=$onlineReadyAt
        })
        [IO.File]::AppendAllText($supervisorLogPath, "state=ONLINE_READY`r`n", [Text.UTF8Encoding]::new($false))
        $onlineExit = Wait-ForHumanClientExit -Runtime $onlineRuntime -ClientInfo $onlineClient -ControlPath $controlPath
        [IO.File]::AppendAllText($supervisorLogPath, "state=ONLINE_EXIT_NORMAL`r`n", [Text.UTF8Encoding]::new($false))
        Assert-Equal (Get-Sha256 $distribution.Path) $distribution.Sha256 'Post-online-UAT distribution SHA-256'
        Assert-Equal (Get-Sha256 $runtimeCopy) $distribution.Sha256 'Post-online-UAT runtime-copy SHA-256'
        Write-JsonAtomic -LiteralPath $statusPath -Value ([ordered]@{ state='ISOLATION_STARTING'; session_id=$sessionId; supervisor_pid=$PID; updated_utc=[DateTime]::UtcNow.ToString('o') })
        $isolationEnvelope = Invoke-WithFirewallIsolation -Jdk $Jdk -Operation {
            param($Isolation)
            Remove-Item -LiteralPath (Join-Path $profile 'logs\latest.log') -Force -ErrorAction SilentlyContinue
            $script:IsolatedRuntimeForCleanup = Start-GradleRuntime -TaskName runProductionClient -Jdk $Jdk -Offline
            $script:IsolatedClientForCleanup = $null
            try {
                $script:IsolatedClientForCleanup = Wait-ForClientReady -Runtime $script:IsolatedRuntimeForCleanup -Jdk $Jdk -LogPath (Join-Path $profile 'logs\latest.log')
                if ($script:IsolatedClientForCleanup.Pid -eq $onlineClient.Pid -or $script:IsolatedClientForCleanup.Pid -eq $PID -or $script:IsolatedClientForCleanup.Pid -eq $script:IsolatedRuntimeForCleanup.Process.Id) {
                    Throw-Failure 'Isolated client PID is not a new distinct client process'
                }
                $script:IsolatedReadyAt = [DateTime]::UtcNow.ToString('o')
                Write-JsonAtomic -LiteralPath $statusPath -Value ([ordered]@{
                    state='ISOLATED_READY'; session_id=$sessionId; profile_id='production-client'; distribution_sha256=$distribution.Sha256;
                    supervisor_pid=$PID; client_pid=$script:IsolatedClientForCleanup.Pid; menu_ready=$true; firewall_group_id=$Isolation.group_id;
                    firewall_membership=$Isolation.member_count_active; updated_utc=$script:IsolatedReadyAt
                })
                [IO.File]::AppendAllText($supervisorLogPath, "state=ISOLATED_READY`r`n", [Text.UTF8Encoding]::new($false))
                $result = Wait-ForHumanClientExit -Runtime $script:IsolatedRuntimeForCleanup -ClientInfo $script:IsolatedClientForCleanup -ControlPath $controlPath
                [IO.File]::AppendAllText($supervisorLogPath, "state=ISOLATED_EXIT_NORMAL`r`n", [Text.UTF8Encoding]::new($false))
                return [pscustomobject]@{ Exit=$result.Exit; ExitCode=$result.ExitCode; Pid=$script:IsolatedClientForCleanup.Pid; ReadyAt=$script:IsolatedReadyAt }
            }
            catch {
                if ($script:IsolatedRuntimeForCleanup) { Stop-RuntimeAfterFailure -Runtime $script:IsolatedRuntimeForCleanup -ClientInfo $script:IsolatedClientForCleanup }
                throw
            }
            finally {
                Remove-Variable -Scope Script -Name IsolatedRuntimeForCleanup -ErrorAction SilentlyContinue
                Remove-Variable -Scope Script -Name IsolatedClientForCleanup -ErrorAction SilentlyContinue
            }
        }
        $isolationRecord = $isolationEnvelope.Isolation
        $isolatedExit = $isolationEnvelope.Operation
        $isolatedClient = [pscustomobject]@{ Pid = $isolatedExit.Pid }
        $isolatedReadyAt = $isolatedExit.ReadyAt
        Assert-Equal (Get-Sha256 $distribution.Path) $distribution.Sha256 'Post-isolated-UAT distribution SHA-256'
        Assert-Equal (Get-Sha256 $runtimeCopy) $distribution.Sha256 'Post-isolated-UAT runtime-copy SHA-256'
        $terminalStatus = 'COMPLETE'
    }
    catch {
        $failure = Get-SanitizedFailure -Message $_.Exception.Message
        if ($onlineRuntime -and -not $onlineRuntime.Process.HasExited) { Stop-RuntimeAfterFailure -Runtime $onlineRuntime -ClientInfo $onlineClient }
        $isolationRecord = $script:LastIsolationRecord
    }
    finally {
        $finished = [DateTime]::UtcNow
        $distributionAfter = if (Test-Path -LiteralPath $distribution.Path -PathType Leaf) { Get-Sha256 $distribution.Path } else { 'missing' }
        $runtimeCopyAfter = if (Test-Path -LiteralPath $runtimeCopy -PathType Leaf) { Get-Sha256 $runtimeCopy } else { 'missing' }
        $firewall = if ($isolationRecord) {
            [ordered]@{
                group_id=$isolationRecord.group_id; java_rule_id=$isolationRecord.java_rule_id; javaw_rule_id=$isolationRecord.javaw_rule_id;
                java_program_sha256=$isolationRecord.java_program_sha256; javaw_program_sha256=$isolationRecord.javaw_program_sha256;
                member_count_active=$isolationRecord.member_count_active; member_count_after=$isolationRecord.member_count_after;
                java_rule_absent=[bool]$isolationRecord.java_rule_absent; javaw_rule_absent=[bool]$isolationRecord.javaw_rule_absent
            }
        } else {
            [ordered]@{ group_id='NOT_CREATED'; java_rule_id='NOT_CREATED'; javaw_rule_id='NOT_CREATED'; java_program_sha256=$Jdk.JavaSha256; javaw_program_sha256=$Jdk.JavawSha256; member_count_active=0; member_count_after=0; java_rule_absent=$true; javaw_rule_absent=$true }
        }
        $payload = [ordered]@{
            status=$terminalStatus; session_id=$sessionId; evidence_id=$evidenceId; profile_id='production-client'; supervisor_pid=$PID; supervisor_exit=if($terminalStatus -eq 'COMPLETE'){0}else{1};
            started_utc=$started.ToString('o'); finished_utc=$finished.ToString('o');
            online=[ordered]@{ ready=($null -ne $onlineReadyAt); ready_utc=$onlineReadyAt; pid=if($onlineClient){$onlineClient.Pid}else{0}; exit=if($onlineExit){$onlineExit.Exit}else{'FAILED'} };
            isolated=[ordered]@{ ready=($null -ne $isolatedReadyAt); ready_utc=$isolatedReadyAt; pid=if($isolatedClient){$isolatedClient.Pid}else{0}; exit=if($isolatedExit){$isolatedExit.Exit}else{'FAILED'} };
            firewall=$firewall;
            probe=[ordered]@{ online=if($isolationRecord){$isolationRecord.probe_online}else{'NOT_RUN'}; isolated=if($isolationRecord){$isolationRecord.probe_isolated}else{'NOT_RUN'} };
            distribution=[ordered]@{ path='dist/developers-hell-0.1.0.jar'; sha256_before=$distributionBefore; sha256_after=$distributionAfter };
            runtime_copy=[ordered]@{ sha256_before=$runtimeCopyBefore; sha256_after=$runtimeCopyAfter };
            cleanup_status=if($isolationRecord -and $isolationRecord.cleanup_status -eq 'PASS'){'PASS'}elseif(-not $isolationRecord){'PASS'}else{'FAIL'};
            failure=if($failure){$failure}else{''}
        }
        $payloadJson = $payload | ConvertTo-Json -Compress -Depth 20
        $receipt = [ordered]@{ payload=$payload; receipt_payload_sha256=Get-StringSha256 $payloadJson }
        Write-JsonAtomic -LiteralPath $expectedReceipt -Value $receipt
        Write-JsonAtomic -LiteralPath $statusPath -Value ([ordered]@{
            state=$terminalStatus; session_id=$sessionId; supervisor_pid=$PID; receipt_path=$expectedReceipt;
            cleanup_status=$payload.cleanup_status; updated_utc=$finished.ToString('o')
        })
        [IO.File]::AppendAllText($supervisorLogPath, "state=$terminalStatus`r`ncleanup=$($payload.cleanup_status)`r`n", [Text.UTF8Encoding]::new($false))
    }
    if ($terminalStatus -ne 'COMPLETE') { Throw-Failure "Interactive UAT supervisor failed: $failure" }
    Write-Pass 'two visible production clients supervised through exact isolation and hashed receipt'
}

function Assert-EvidencePassMarker {
    param(
        [Parameter(Mandatory)][string] $Text,
        [Parameter(Mandatory)][string] $Name
    )
    Assert-Equal (Get-EvidenceMarker -Text $Text -Name $Name) 'PASS' "Evidence marker $Name"
}

function Assert-ReceiptContract {
    param(
        [Parameter(Mandatory)][string] $ReceiptFile,
        [Parameter(Mandatory)][string] $EvidenceText,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $DistributionHash
    )
    $receiptCanonical = Resolve-CanonicalPath -LiteralPath $ReceiptFile
    $session = Assert-InteractiveSessionPath -SessionPath (Split-Path -Parent $receiptCanonical)
    if ((Split-Path -Leaf $receiptCanonical) -cne 'receipt.json') { Throw-Failure 'Interactive receipt filename is not exact' }
    try { $receipt = Get-Content -LiteralPath $receiptCanonical -Raw | ConvertFrom-Json } catch { Throw-Failure 'Interactive receipt is invalid JSON' }
    if (-not $receipt.PSObject.Properties['payload'] -or -not $receipt.PSObject.Properties['receipt_payload_sha256']) { Throw-Failure 'Interactive receipt lacks payload/hash' }
    $payloadJson = $receipt.payload | ConvertTo-Json -Compress -Depth 20
    $payloadHash = Get-StringSha256 $payloadJson
    Assert-Equal $payloadHash ([string]$receipt.receipt_payload_sha256).ToLowerInvariant() 'Interactive receipt canonical payload SHA-256'
    Assert-Equal ([string]$receipt.payload.status) 'COMPLETE' 'Interactive receipt status'
    Assert-Equal ([string]$receipt.payload.supervisor_exit) '0' 'Interactive supervisor exit'
    Assert-Equal ([string]$receipt.payload.profile_id) 'production-client' 'Interactive receipt profile ID'
    Assert-Equal ([string]$receipt.payload.distribution.path) 'dist/developers-hell-0.1.0.jar' 'Interactive receipt distribution path'
    if ($receipt.payload.online.ready -ne $true -or $receipt.payload.isolated.ready -ne $true) { Throw-Failure 'Both interactive clients did not reach readiness' }
    Assert-Equal ([string]$receipt.payload.online.exit) 'NORMAL' 'Online client exit'
    Assert-Equal ([string]$receipt.payload.isolated.exit) 'NORMAL' 'Isolated client exit'
    if ([int]$receipt.payload.online.pid -le 0 -or [int]$receipt.payload.isolated.pid -le 0 -or [int]$receipt.payload.online.pid -eq [int]$receipt.payload.isolated.pid) {
        Throw-Failure 'Interactive client PIDs are missing or not distinct'
    }
    Assert-Equal ([string]$receipt.payload.firewall.member_count_active) '2' 'Interactive firewall active membership'
    Assert-Equal ([string]$receipt.payload.firewall.member_count_after) '0' 'Interactive firewall final membership'
    if ($receipt.payload.firewall.java_rule_absent -ne $true -or $receipt.payload.firewall.javaw_rule_absent -ne $true) { Throw-Failure 'Interactive receipt does not prove both rules absent' }
    Assert-Equal ([string]$receipt.payload.cleanup_status) 'PASS' 'Interactive cleanup status'
    Assert-Equal ([string]$receipt.payload.probe.online) 'PASS' 'Interactive reachable probe'
    Assert-Equal ([string]$receipt.payload.probe.isolated) 'BLOCKED' 'Interactive blocked probe'
    $group = [string]$receipt.payload.firewall.group_id
    $javaRule = [string]$receipt.payload.firewall.java_rule_id
    $javawRule = [string]$receipt.payload.firewall.javaw_rule_id
    if ([string]::IsNullOrWhiteSpace($group) -or [string]::IsNullOrWhiteSpace($javaRule) -or [string]::IsNullOrWhiteSpace($javawRule) -or $javaRule -eq $javawRule) {
        Throw-Failure 'Interactive firewall identities are invalid'
    }
    Assert-Equal ([string]$receipt.payload.firewall.java_program_sha256).ToLowerInvariant() $Jdk.JavaSha256 'Interactive java.exe program SHA-256'
    Assert-Equal ([string]$receipt.payload.firewall.javaw_program_sha256).ToLowerInvariant() $Jdk.JavawSha256 'Interactive javaw.exe program SHA-256'
    Assert-Equal ([string]$receipt.payload.distribution.sha256_before).ToLowerInvariant() $DistributionHash 'Interactive distribution prelaunch SHA-256'
    Assert-Equal ([string]$receipt.payload.distribution.sha256_after).ToLowerInvariant() $DistributionHash 'Interactive distribution postlaunch SHA-256'
    Assert-Equal ([string]$receipt.payload.runtime_copy.sha256_before).ToLowerInvariant() $DistributionHash 'Interactive runtime-copy prelaunch SHA-256'
    Assert-Equal ([string]$receipt.payload.runtime_copy.sha256_after).ToLowerInvariant() $DistributionHash 'Interactive runtime-copy postlaunch SHA-256'
    Assert-Equal ([string]$receipt.payload.session_id) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_session_id') 'Interactive evidence/receipt session ID'
    Assert-Equal ([string]$receipt.payload.evidence_id) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_session_evidence_id') 'Interactive evidence/receipt evidence ID'
    Assert-Equal $payloadHash (Get-EvidenceMarker -Text $EvidenceText -Name 'client_receipt_payload_sha256') 'Interactive evidence/receipt payload SHA-256'
    Assert-Equal ([string]$receipt.payload.supervisor_exit) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_supervisor_exit') 'Interactive evidence/receipt supervisor exit'
    Assert-Equal $group (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_group_id') 'Interactive evidence/receipt firewall group'
    Assert-Equal $javaRule (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_java_rule_id') 'Interactive evidence/receipt Java rule'
    Assert-Equal $javawRule (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_javaw_rule_id') 'Interactive evidence/receipt Javaw rule'
    Assert-Equal ([string]$receipt.payload.firewall.member_count_active) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_membership_active') 'Interactive evidence/receipt active membership'
    Assert-Equal ([string]$receipt.payload.firewall.member_count_after) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_membership_after') 'Interactive evidence/receipt final membership'
    Assert-Equal ([string]$receipt.payload.firewall.java_program_sha256) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_java_program_sha256') 'Interactive evidence/receipt java.exe hash'
    Assert-Equal ([string]$receipt.payload.firewall.javaw_program_sha256) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_javaw_program_sha256') 'Interactive evidence/receipt javaw.exe hash'
    Assert-Equal ([string]$receipt.payload.probe.online) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_probe_reachable') 'Interactive evidence/receipt reachable probe'
    Assert-Equal ([string]$receipt.payload.probe.isolated) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_isolation_probe_blocked') 'Interactive evidence/receipt blocked probe'
    Assert-Equal ([string]$receipt.payload.distribution.sha256_before) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_distribution_sha256_before') 'Interactive evidence/receipt distribution before'
    Assert-Equal ([string]$receipt.payload.distribution.sha256_after) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_distribution_sha256_after') 'Interactive evidence/receipt distribution after'
    Assert-Equal ([string]$receipt.payload.runtime_copy.sha256_before) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_runtime_copy_sha256_before') 'Interactive evidence/receipt runtime copy before'
    Assert-Equal ([string]$receipt.payload.runtime_copy.sha256_after) (Get-EvidenceMarker -Text $EvidenceText -Name 'client_runtime_copy_sha256_after') 'Interactive evidence/receipt runtime copy after'
    if ([string]$receipt.payload.session_id -notmatch '^[0-9a-f]{32}$') { Throw-Failure 'Interactive receipt session ID is not a GUID suffix' }
    if ((Split-Path -Leaf $session) -cne ('interactive-uat-' + [string]$receipt.payload.session_id)) { Throw-Failure 'Interactive receipt session ID does not match its guarded directory' }
    $evidenceTimestamp = [DateTimeOffset]::MinValue
    $startedTimestamp = [DateTimeOffset]::MinValue
    $finishedTimestamp = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse((Get-EvidenceMarker -Text $EvidenceText -Name 'evidence_timestamp_utc'), [ref]$evidenceTimestamp) -or
        -not [DateTimeOffset]::TryParse([string]$receipt.payload.started_utc, [ref]$startedTimestamp) -or
        -not [DateTimeOffset]::TryParse([string]$receipt.payload.finished_utc, [ref]$finishedTimestamp) -or
        $startedTimestamp -lt $evidenceTimestamp -or $finishedTimestamp -lt $startedTimestamp -or
        ($finishedTimestamp - $startedTimestamp).TotalHours -gt 48) {
        Throw-Failure 'Interactive receipt timestamps are invalid, stale, or out of order'
    }
    if (-not (Get-Command Get-NetFirewallRule -ErrorAction SilentlyContinue)) { Throw-Failure 'Cannot independently verify receipt firewall cleanup' }
    if (Test-ExactFirewallRuleExists -Name $javaRule) { Throw-Failure 'Interactive Java firewall rule still exists' }
    if (Test-ExactFirewallRuleExists -Name $javawRule) { Throw-Failure 'Interactive Javaw firewall rule still exists' }
    if ((Get-ExactFirewallGroupCount -Group $group -PolicyStore ActiveStore) -ne 0 -or (Get-ExactFirewallGroupCount -Group $group -PolicyStore PersistentStore) -ne 0) { Throw-Failure 'Interactive firewall group still contains rules' }
    return [pscustomobject]@{ Receipt = $receipt; Session = $session; PayloadSha256 = $payloadHash }
}

function Invoke-ValidateEvidenceMode {
    param(
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $EvidenceFile,
        [Parameter(Mandatory)][string] $DistributionFile,
        [switch] $RequirePass,
        [string] $ReceiptFile
    )
    if (-not (Test-Path -LiteralPath $EvidenceFile -PathType Leaf)) { Throw-Failure "Foundation evidence does not exist: $EvidenceFile" }
    $text = Get-Content -LiteralPath $EvidenceFile -Raw
    if ([string]::IsNullOrWhiteSpace($text)) { Throw-Failure 'Foundation evidence is empty' }
    if ($text -match '(?i)(?:C:\\Users\\[^\\\r\n]+|/Users/[^/\r\n]+|password\s*:|api[_-]?key\s*:|secret\s*:|employer\s*:|school\s*:|sponsor(?:ed|ship)?\s*:)') {
        Throw-Failure 'Foundation evidence contains private or unsupported factual content'
    }
    $timestampText = Get-EvidenceMarker -Text $text -Name 'evidence_timestamp_utc'
    $timestamp = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse($timestampText, [ref]$timestamp)) { Throw-Failure 'Evidence timestamp is not ISO-8601' }
    foreach ($name in @('os_version','server_profile_id','client_profile_id')) {
        if ([string]::IsNullOrWhiteSpace((Get-EvidenceMarker -Text $text -Name $name))) { Throw-Failure "Evidence marker is blank: $name" }
    }
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_artifact_sha256') $Toolchain.Values.jdk_archive_sha256 'Evidence JDK archive SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_official_sha256') $Toolchain.Values.jdk_official_sha256 'Evidence official JDK archive SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_artifact_filename') $Toolchain.Values.jdk_artifact_filename 'Evidence JDK artifact filename'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_artifact_source') $Toolchain.Values.jdk_artifact_source 'Evidence JDK artifact source'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_checksum_source') $Toolchain.Values.jdk_checksum_source 'Evidence JDK checksum source'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_runtime_version') $Jdk.RuntimeVersion 'Evidence JDK runtime'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_vendor') 'Eclipse_Adoptium' 'Evidence JDK vendor'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_vm_vendor') 'Eclipse_Adoptium' 'Evidence JVM vendor'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_arch') $Jdk.Architecture 'Evidence JDK architecture'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_java_sha256') $Jdk.JavaSha256 'Evidence java.exe SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_javaw_sha256') $Jdk.JavawSha256 'Evidence javaw.exe SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_javac_sha256') $Jdk.JavacSha256 'Evidence javac.exe SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'jdk_path_sha256') $Jdk.PathSha256 'Evidence JDK path SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'gradle_version') $Toolchain.Values.gradle_version 'Evidence Gradle version'
    foreach ($pair in @(
        @('minecraft_version',$script:ExpectedMinecraftVersion),
        @('loader_version',$script:ExpectedLoaderVersion),
        @('fabric_api_version',$script:ExpectedFabricApiVersion),
        @('fabric_installer_version',$script:ExpectedInstallerVersion),
        @('java_release','25'),
        @('template_ref',$Toolchain.Values.template_ref),
        @('template_url',$Toolchain.Values.template_url),
        @('template_commit',$Toolchain.Values.template_commit),
        @('template_remote_commit',$Toolchain.Values.template_remote_commit),
        @('template_tree',$Toolchain.Values.template_tree),
        @('template_origin_verified',$Toolchain.Values.template_origin_verified),
        @('template_clean_before_patch',$Toolchain.Values.template_clean_before_patch),
        @('template_diff_mode',$Toolchain.Values.template_diff_mode),
        @('template_current_diff',$Toolchain.Values.template_current_diff),
        @('snapshot_fallback_used',$Toolchain.Values.snapshot_fallback_used),
        @('loom_requested',$Toolchain.Values.loom_requested),
        @('loom_selected',$Toolchain.Values.loom_selected),
        @('resolved_loom_build',$Toolchain.Values.resolved_loom_build),
        @('resolved_loom_sha256',$Toolchain.Values.resolved_loom_sha256),
        @('wrapper_distribution',$Toolchain.Values.wrapper_distribution),
        @('wrapper_distribution_sha256',$Toolchain.Values.wrapper_distribution_sha256),
        @('wrapper_sha256',$Toolchain.Values.wrapper_sha256),
        @('distribution_path','dist/developers-hell-0.1.0.jar'),
        @('ordinary_jar_path','dist/developers-hell-0.1.0.jar'),
        @('gradle_cache_mode','online_prime_then_same_cache_offline')
    )) { Assert-Equal (Get-EvidenceMarker -Text $text -Name $pair[0]) $pair[1] "Evidence $($pair[0])" }
    foreach ($name in @(
        'detached_online_probe','detached_offline_probe','clean_checkout_status','tracked_manifest','temp_child_valid',
        'worktree_registered','worktree_removed','worktree_registry_restored','temp_container_removed','repository_root_preserved',
        'home_root_preserved','temp_root_preserved','direct_dependency_audit','comprehensive_audit','ordinary_jar_required_entries',
        'ordinary_jar_license','ordinary_jar_test_exclusions','server_online_ready','server_online_clean_stop','server_isolated_ready',
        'server_isolated_clean_stop','server_isolation_java_rule_absent','server_isolation_javaw_rule_absent','server_isolation_cleanup',
        'client_preflight','client_entrypoints','client_menu_ready'
    )) { Assert-EvidencePassMarker -Text $text -Name $name }
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'detached_online_exit') '0' 'Detached online build exit'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'detached_offline_exit') '0' 'Detached offline build exit'
    foreach ($pair in @(
        @('pristine_fixed_help_exit',$Toolchain.Values.fixed_help_exit),
        @('pristine_fixed_help_log_sha256',$Toolchain.Values.fixed_help_log_sha256),
        @('pristine_fixed_build_exit',$Toolchain.Values.fixed_build_exit),
        @('pristine_fixed_build_log_sha256',$Toolchain.Values.fixed_build_log_sha256),
        @('pristine_fixed_resolution_exit',$Toolchain.Values.fixed_resolution_exit),
        @('pristine_fixed_resolution_log_sha256',$Toolchain.Values.fixed_resolution_log_sha256),
        @('toolchain_proof_started_utc',$Toolchain.Values.proof_started_utc),
        @('toolchain_proof_completed_utc',$Toolchain.Values.proof_completed_utc)
    )) { Assert-Equal (Get-EvidenceMarker -Text $text -Name $pair[0]) $pair[1] "Evidence $($pair[0])" }
    if ((Get-EvidenceMarker -Text $text -Name 'detached_head') -notmatch '^[0-9a-f]{40}$') { Throw-Failure 'Invalid detached HEAD marker' }
    foreach ($name in @('detached_online_log_sha256','detached_offline_log_sha256','ordinary_jar_entries_sha256')) {
        if ((Get-EvidenceMarker -Text $text -Name $name) -notmatch '^[0-9a-f]{64}$') { Throw-Failure "Invalid SHA marker: $name" }
    }
    foreach ($name in @('detached_online_command','detached_offline_command')) {
        $commandMarker = Get-EvidenceMarker -Text $text -Name $name
        if ([string]::IsNullOrWhiteSpace($commandMarker) -or $commandMarker -match '(?i)(?:C:\\Users\\|/Users/)') { Throw-Failure "Invalid or private command marker: $name" }
    }
    $jarSize = 0L
    if (-not [long]::TryParse((Get-EvidenceMarker -Text $text -Name 'ordinary_jar_size'), [ref]$jarSize) -or $jarSize -le 0) { Throw-Failure 'Ordinary JAR size marker is invalid' }
    $onlineHash = (Get-EvidenceMarker -Text $text -Name 'online_jar_sha256').ToLowerInvariant()
    $offlineHash = (Get-EvidenceMarker -Text $text -Name 'offline_jar_sha256').ToLowerInvariant()
    $distributionHash = (Get-EvidenceMarker -Text $text -Name 'distribution_sha256').ToLowerInvariant()
    if ($distributionHash -notmatch '^[0-9a-f]{64}$') { Throw-Failure 'Evidence distribution SHA-256 is invalid' }
    Assert-Equal $onlineHash $distributionHash 'Online/distribution evidence SHA-256'
    Assert-Equal $offlineHash $distributionHash 'Offline/distribution evidence SHA-256'
    $distribution = Get-ValidatedDistribution -DistributionFile $DistributionFile -EvidenceFile $EvidenceFile
    foreach ($name in @('server_distribution_sha256_before','server_runtime_copy_sha256_before','server_runtime_copy_sha256_after','server_distribution_sha256_after','client_distribution_sha256_before','client_runtime_copy_sha256_before','client_runtime_copy_sha256_after','client_distribution_sha256_after')) {
        Assert-Equal (Get-EvidenceMarker -Text $text -Name $name) $distributionHash "Evidence runtime hash $name"
    }
    $serverGroup = Get-EvidenceMarker -Text $text -Name 'server_isolation_group_id'
    $serverJavaRule = Get-EvidenceMarker -Text $text -Name 'server_isolation_java_rule_id'
    $serverJavawRule = Get-EvidenceMarker -Text $text -Name 'server_isolation_javaw_rule_id'
    if ([string]::IsNullOrWhiteSpace($serverGroup) -or $serverJavaRule -eq $serverJavawRule) { Throw-Failure 'Server firewall identity markers are invalid' }
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'server_isolation_java_program_sha256') $Jdk.JavaSha256 'Server firewall java.exe SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'server_isolation_javaw_program_sha256') $Jdk.JavawSha256 'Server firewall javaw.exe SHA-256'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'server_isolation_membership_active') '2' 'Server firewall active membership'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'server_isolation_membership_after') '0' 'Server firewall final membership'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'server_isolation_probe_reachable') 'PASS' 'Server reachable probe'
    Assert-Equal (Get-EvidenceMarker -Text $text -Name 'server_isolation_probe_blocked') 'BLOCKED' 'Server blocked probe'
    if (Get-Command Get-NetFirewallRule -ErrorAction SilentlyContinue) {
        if (Test-ExactFirewallRuleExists -Name $serverJavaRule) { Throw-Failure 'Server Java firewall rule still exists' }
        if (Test-ExactFirewallRuleExists -Name $serverJavawRule) { Throw-Failure 'Server Javaw firewall rule still exists' }
        if ((Get-ExactFirewallGroupCount -Group $serverGroup -PolicyStore ActiveStore) -ne 0 -or (Get-ExactFirewallGroupCount -Group $serverGroup -PolicyStore PersistentStore) -ne 0) { Throw-Failure 'Server firewall group still contains rules' }
    }
    else { Throw-Failure 'Cannot independently validate server firewall cleanup' }

    if ($RequirePass) {
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'uat_status') 'PASS' 'Final UAT status'
        foreach ($name in @('online_mod_list','online_world_entry','online_token','online_save_exit','isolated_mod_list','isolated_world_entry','isolated_token','isolated_save_exit')) {
            Assert-EvidencePassMarker -Text $text -Name $name
        }
        Assert-EvidencePassMarker -Text $text -Name 'client_isolation_status'
        Assert-EvidencePassMarker -Text $text -Name 'client_online_ready'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_online_exit') 'NORMAL' 'Evidence online client exit'
        Assert-EvidencePassMarker -Text $text -Name 'client_isolated_ready'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_isolated_exit') 'NORMAL' 'Evidence isolated client exit'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_supervisor_exit') '0' 'Evidence supervisor exit'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_isolation_membership_active') '2' 'Evidence client firewall active membership'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_isolation_membership_after') '0' 'Evidence client firewall final membership'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_isolation_probe_reachable') 'PASS' 'Evidence client reachable probe'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_isolation_probe_blocked') 'BLOCKED' 'Evidence client blocked probe'
        foreach ($name in @('client_isolation_java_rule_absent','client_isolation_javaw_rule_absent','client_isolation_cleanup')) { Assert-EvidencePassMarker -Text $text -Name $name }
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_isolation_java_program_sha256') $Jdk.JavaSha256 'Evidence client java.exe SHA-256'
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'client_isolation_javaw_program_sha256') $Jdk.JavawSha256 'Evidence client javaw.exe SHA-256'
        foreach ($name in @('client_distribution_sha256_before','client_distribution_sha256_after','client_runtime_copy_sha256_before','client_runtime_copy_sha256_after')) {
            Assert-Equal (Get-EvidenceMarker -Text $text -Name $name) $distributionHash "Evidence final client hash $name"
        }
        if ([string]::IsNullOrWhiteSpace($ReceiptFile)) { Throw-Failure 'RequireUatPass requires SessionReceiptPath' }
        [void](Assert-ReceiptContract -ReceiptFile $ReceiptFile -EvidenceText $text -Jdk $Jdk -DistributionHash $distributionHash)
    }
    else {
        Assert-Equal (Get-EvidenceMarker -Text $text -Name 'uat_status') 'PENDING' 'Pending UAT status'
        foreach ($name in @('client_isolation_status','client_session_id','client_session_evidence_id','client_supervisor_exit','client_receipt_payload_sha256','online_mod_list','online_world_entry','online_token','online_save_exit','isolated_mod_list','isolated_world_entry','isolated_token','isolated_save_exit')) {
            Assert-Equal (Get-EvidenceMarker -Text $text -Name $name) 'PENDING' "Pending evidence marker $name"
        }
    }
    [void](Invoke-FoundationAudit -Root $script:RepositoryRoot -JarPath $distribution.Path)
    Write-Pass "foundation evidence validated (UAT required: $([bool]$RequirePass))"
}

function Invoke-SelfCheckMode {
    param(
        [Parameter(Mandatory)] $Toolchain,
        [Parameter(Mandatory)] $Jdk
    )
    $requiredParameters = @('SelfCheck','PrimeAndCompare','RunServerSmoke','ClientPreflight','SuperviseInteractiveUat','ValidateEvidence','RequireUatPass','EvidencePath','SessionDirectory','SessionPointerPath','SessionReceiptPath','DistributionPath')
    $scriptText = Get-Content -LiteralPath $PSCommandPath -Raw
    foreach ($name in $requiredParameters) {
        if ($scriptText -notmatch '(?m)^\s*\[(?:switch|string)\]\s*\$' + [regex]::Escape($name) + '\b') { Throw-Failure "Harness parameter surface missing: $name" }
    }
    if ($script:TrackedManifest.Count -ne (@($script:TrackedManifest | Sort-Object -Unique).Count)) { Throw-Failure 'Tracked manifest contains duplicate paths' }
    foreach ($relative in $script:TrackedManifest) {
        if (-not (Test-Path -LiteralPath (Join-Path $script:RepositoryRoot ($relative -replace '/', '\')))) { Throw-Failure "Self-check required local path missing: $relative" }
    }
    $source = Get-Content -LiteralPath $PSCommandPath -Raw
    foreach ($token in @('GetFinalPathNameByHandle',"'worktree','list','--porcelain','-z'", "'worktree','remove','--force','--'",'New-NetFirewallRule','Remove-NetFirewallRule','member_count_active','member_count_after','ONLINE_READY','ISOLATED_READY','receipt_payload_sha256','finally','Copy-FileAtomically','Assert-ReceiptContract')) {
        if ($source -notmatch [regex]::Escape($token)) { Throw-Failure "Self-check harness contract token missing: $token" }
    }
    if ([regex]::Matches($source, '(?m)^\s*\[void\]\(New-NetFirewallRule\b').Count -ne 2) { Throw-Failure 'Firewall primitive must contain exactly two rule creations' }
    if ($source -match '(?i)Get-NetFirewallRule\s*\|\s*Remove-NetFirewallRule|Remove-NetFirewallRule\s+-Group') { Throw-Failure 'Broad firewall cleanup pattern found' }
    if ($source -match '(?m)^\s*(?:\[[^\]]+\]\s*)?Get-NetFirewallRule(?![^\r\n]*(?:-Name|-Group))') { Throw-Failure 'Unscoped firewall rule enumeration found' }
    $defaultDistribution = Resolve-CanonicalPath -LiteralPath $script:DefaultDistributionPath -AllowMissingLeaf
    if ((Split-Path -Leaf $defaultDistribution) -cne $script:ExpectedDistributionName) { Throw-Failure 'Default distribution binding is invalid' }
    $repository = Resolve-CanonicalPath -LiteralPath $script:RepositoryRoot
    $temp = Resolve-CanonicalPath -LiteralPath ([IO.Path]::GetTempPath())
    $userProfileRoot = Resolve-CanonicalPath -LiteralPath ([Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile))
    foreach ($root in @($repository,$temp,$userProfileRoot)) { if (-not (Test-Path -LiteralPath $root -PathType Container)) { Throw-Failure 'Canonical root self-check failed' } }
    $porcelain = Get-WorktreePorcelainBytes -RepositoryRoot $repository
    if (@(ConvertFrom-WorktreePorcelain -Bytes $porcelain).Count -lt 1) { Throw-Failure 'Git worktree porcelain parser found no main checkout' }
    $samplePayload = [ordered]@{ status='COMPLETE'; values=[ordered]@{ alpha=1; beta=$true } }
    $sampleJson = $samplePayload | ConvertTo-Json -Compress -Depth 20
    $roundTrip = ($sampleJson | ConvertFrom-Json) | ConvertTo-Json -Compress -Depth 20
    Assert-Equal (Get-StringSha256 $sampleJson) (Get-StringSha256 $roundTrip) 'Canonical receipt JSON round trip'
    Assert-RepositoryBuildContract -Root $script:RepositoryRoot -Toolchain $Toolchain -Jdk $Jdk
    $localJar = Join-Path $script:RepositoryRoot "build\libs\$($script:ExpectedDistributionName)"
    if (Test-Path -LiteralPath $localJar -PathType Leaf) { [void](Get-ProductionArchiveContract -JarPath $localJar) }
    $wrapperIgnore = Invoke-Git -Arguments @('-C',$repository,'check-ignore','--','gradle/wrapper/gradle-wrapper.jar') -AllowFailure
    if ($wrapperIgnore.ExitCode -eq 0) { Throw-Failure 'Tracked Gradle wrapper JAR is ignored' }
    foreach ($ignored in @('.work/self-check.log','dist/developers-hell-0.1.0.jar','run/production-client/logs/latest.log')) {
        $check = Invoke-Git -Arguments @('-C',$repository,'check-ignore','--',$ignored) -AllowFailure
        if ($check.ExitCode -ne 0) { Throw-Failure "Expected ignored path is visible to Git: $ignored" }
    }
    Write-Pass 'verification harness parameter, path, manifest, archive, firewall, supervisor, receipt, and ignore self-check'
}

try {
    $script:RepositoryRoot = Resolve-CanonicalPath -LiteralPath $script:RepositoryRoot
    $script:PhaseDirectory = Join-Path $script:RepositoryRoot '.planning\phases\01-java-25-and-fabric-26-2-foundation'
    $script:ToolchainEvidencePath = Join-Path $script:PhaseDirectory '01-TOOLCHAIN-EVIDENCE.md'
    $script:DefaultEvidencePath = Join-Path $script:PhaseDirectory '01-FOUNDATION-EVIDENCE.md'
    $script:DefaultDistributionPath = Join-Path $script:RepositoryRoot 'dist\developers-hell-0.1.0.jar'
    $script:DefaultSessionPointerPath = Join-Path $script:RepositoryRoot '.work\interactive-uat-active.json'
    $script:AuditScriptPath = Join-Path $script:RepositoryRoot 'scripts\audit-foundation.ps1'

    $primaryModes = @(@($SelfCheck,$PrimeAndCompare,$RunServerSmoke,$ClientPreflight,$SuperviseInteractiveUat,$ValidateEvidence) | Where-Object { [bool]$_ })
    if ($primaryModes.Count -eq 0) { Throw-Failure 'Select at least one harness mode' }
    if ($RequireUatPass -and -not $ValidateEvidence) { Throw-Failure 'RequireUatPass is valid only with ValidateEvidence' }
    if ($SuperviseInteractiveUat -and $primaryModes.Count -ne 1) { Throw-Failure 'SuperviseInteractiveUat must run as an exclusive background mode' }

    $evidenceFile = Resolve-RepositoryPath -Path $EvidencePath -Default $script:DefaultEvidencePath -AllowMissingLeaf
    $distributionFile = Resolve-RepositoryPath -Path $DistributionPath -Default $script:DefaultDistributionPath -AllowMissingLeaf
    if (($PrimeAndCompare -or $RunServerSmoke -or $ClientPreflight -or $SuperviseInteractiveUat) -and
        -not $evidenceFile.Equals((Resolve-CanonicalPath -LiteralPath $script:DefaultEvidencePath -AllowMissingLeaf), [StringComparison]::OrdinalIgnoreCase)) {
        Throw-Failure 'Mutating/runtime modes require the exact phase foundation evidence path'
    }
    if ($ValidateEvidence -and -not (Test-Path -LiteralPath $evidenceFile -PathType Leaf)) {
        Throw-Failure "Foundation evidence does not exist: $evidenceFile"
    }

    $toolchain = Get-ToolchainContract
    $jdk = Select-VerifiedJdk -Toolchain $toolchain

    if ($SelfCheck) { Invoke-SelfCheckMode -Toolchain $toolchain -Jdk $jdk }
    if ($PrimeAndCompare) { Invoke-PrimeAndCompareMode -Toolchain $toolchain -Jdk $jdk -EvidenceFile $evidenceFile -DistributionFile $distributionFile }
    if ($RunServerSmoke) { Invoke-RunServerSmokeMode -Toolchain $toolchain -Jdk $jdk -EvidenceFile $evidenceFile -DistributionFile $distributionFile }
    if ($ClientPreflight) { Invoke-ClientPreflightMode -Toolchain $toolchain -Jdk $jdk -EvidenceFile $evidenceFile -DistributionFile $distributionFile }
    if ($SuperviseInteractiveUat) {
        if ([string]::IsNullOrWhiteSpace($SessionDirectory)) { Throw-Failure 'SuperviseInteractiveUat requires SessionDirectory' }
        $sessionPath = Resolve-RepositoryPath -Path $SessionDirectory -Default ''
        $pointerPath = Resolve-RepositoryPath -Path $SessionPointerPath -Default $script:DefaultSessionPointerPath -AllowMissingLeaf
        $receiptDefault = Join-Path $sessionPath 'receipt.json'
        $receiptPath = Resolve-RepositoryPath -Path $SessionReceiptPath -Default $receiptDefault -AllowMissingLeaf
        Invoke-SuperviseInteractiveUatMode -Toolchain $toolchain -Jdk $jdk -EvidenceFile $evidenceFile -DistributionFile $distributionFile -SessionPath $sessionPath -PointerPath $pointerPath -ReceiptPath $receiptPath
    }
    if ($ValidateEvidence) {
        $receiptPath = if ([string]::IsNullOrWhiteSpace($SessionReceiptPath)) { $null } else { Resolve-RepositoryPath -Path $SessionReceiptPath -Default '' }
        Invoke-ValidateEvidenceMode -Toolchain $toolchain -Jdk $jdk -EvidenceFile $evidenceFile -DistributionFile $distributionFile -RequirePass:$RequireUatPass -ReceiptFile $receiptPath
    }
    Write-Host 'PASS: Developer''s Hell foundation verification harness completed'
    exit 0
}
catch {
    Write-Error ("FAIL: Developer's Hell foundation verification harness: " + $_.Exception.Message)
    exit 1
}
