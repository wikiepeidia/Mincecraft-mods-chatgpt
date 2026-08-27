[CmdletBinding()]
param(
    [switch] $SelfCheck,
    [switch] $Verify,
    [switch] $ValidateEvidence,
    [string] $EvidencePath,
    [string] $DistributionPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$script:ExpectedDistributionName = 'developers-hell-0.1.0.jar'
$script:ExpectedReadyMarker = 'DEVELOPERS_HELL_SERVER_FIRST_TICK_READY'
$script:ExpectedStopCleanupMarker = 'DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE'
$script:RepositoryRoot = (Resolve-Path -LiteralPath ([System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))) -ErrorAction Stop).Path.TrimEnd('\', '/')
$script:VerifierScriptPath = [System.IO.Path]::GetFullPath($MyInvocation.MyCommand.Path)
$script:PhaseRelativePath = '.planning/phases/02-persistent-lecture-vertical-slice'
$script:DefaultEvidenceRelativePath = "$($script:PhaseRelativePath)/02-LECTURE-EVIDENCE.md"
$script:DefaultDistributionRelativePath = 'dist/developers-hell-0.1.0.jar'
$script:BuildJarRelativePath = 'build/libs/developers-hell-0.1.0.jar'
$script:ToolchainEvidenceRelativePath = '.planning/phases/01-java-25-and-fabric-26-2-foundation/01-TOOLCHAIN-EVIDENCE.md'
$script:JdkRelativePath = '.work/toolchain/temurin-25.0.4+7-x64'
$script:GradleInitRelativePath = 'scripts/loom-resolution.init.gradle'
$script:ServerRunRelativePath = 'run/production-server'
$script:RequiredAutomatedRows = @('02-CFG-01','02-STATE-01','02-GEO-01','02-ITEM-01','02-BOSS-01','02-LIFE-01','02-REWARD-01','02-DISC-01','02-GATE-01')
$script:ManualRows = @(
    'MANUAL-UI-01',
    'MANUAL-I18N-02',
    'MANUAL-EFFECTS-03',
    'MANUAL-ACCESS-04',
    'MANUAL-MOTION-05',
    'MANUAL-MODELS-06',
    'MANUAL-REMOTE-07'
)

function Resolve-SafeRepositoryPath {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $ExpectedRelativePath,
        [switch] $AllowMissingLeaf
    )
    if ([string]::IsNullOrWhiteSpace($Path) -or [string]::IsNullOrWhiteSpace($ExpectedRelativePath)) {
        throw 'Repository path and expected relative path must be non-empty.'
    }

    $expectedRelative = $ExpectedRelativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    if ($expectedRelative.StartsWith('.' + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::Ordinal)) {
        $expectedRelative = $expectedRelative.Substring(2)
    }
    $expectedRelative = $expectedRelative.TrimStart([System.IO.Path]::DirectorySeparatorChar)
    if ([System.IO.Path]::IsPathRooted($expectedRelative) -or $expectedRelative -match '(^|[\\/])[.][.]([\\/]|$)') {
        throw 'Expected repository path must be a canonical relative child.'
    }

    $expected = [System.IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $expectedRelative))
    $candidate = if ([System.IO.Path]::IsPathRooted($Path)) {
        [System.IO.Path]::GetFullPath($Path)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $Path))
    }
    if (-not $candidate.Equals($expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is not the exact repository child required by this mode: $ExpectedRelativePath"
    }

    $rootWithSeparator = $script:RepositoryRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Path escapes the canonical repository root.'
    }

    $resolvedParent = Resolve-Path -LiteralPath (Split-Path -Parent $candidate) -ErrorAction Stop
    if (($resolvedParent.Path.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar).StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase) -eq $false) {
        throw 'Path parent resolves outside the canonical repository root.'
    }
    if (Test-Path -LiteralPath $candidate) {
        $item = Get-Item -LiteralPath $candidate -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Repository verification paths must not be reparse points.'
        }
        return (Resolve-Path -LiteralPath $candidate -ErrorAction Stop).Path
    }
    if (-not $AllowMissingLeaf) {
        throw "Required repository path does not exist: $ExpectedRelativePath"
    }
    return $candidate
}

function Assert-FreshArtifact {
    param(
        [Parameter(Mandatory)][string] $JarPath,
        [Parameter(Mandatory)][DateTime] $BuildStartedUtc
    )
    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw 'Fresh ordinary JAR is missing.'
    }
    $item = Get-Item -LiteralPath $JarPath -Force
    if ($item.Name -cne $script:ExpectedDistributionName) {
        throw "Fresh artifact must be the exact ordinary JAR $($script:ExpectedDistributionName)."
    }
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Fresh ordinary JAR must not be a reparse point.'
    }
    if ($item.Length -le 0) {
        throw 'Fresh ordinary JAR is empty.'
    }
    if ($item.LastWriteTimeUtc -lt $BuildStartedUtc.AddSeconds(-2)) {
        throw 'Fresh ordinary JAR predates the current clean build transaction.'
    }
    return [pscustomobject]@{
        Path = $item.FullName
        Size = [long] $item.Length
        LastWriteTimeUtc = $item.LastWriteTimeUtc
        Sha256 = Get-FileSha256 -LiteralPath $item.FullName
    }
}

function Get-FileSha256 {
    param([Parameter(Mandatory)][string] $LiteralPath)
    if (-not (Test-Path -LiteralPath $LiteralPath -PathType Leaf)) {
        throw 'Required SHA-256 input is missing.'
    }
    return (Get-FileHash -LiteralPath $LiteralPath -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-SharedTextFile {
    param([Parameter(Mandatory)][string] $LiteralPath)
    $stream = [System.IO.File]::Open($LiteralPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $reader = $null
    try {
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
        return $reader.ReadToEnd()
    } finally {
        if ($null -ne $reader) { $reader.Dispose() } else { $stream.Dispose() }
    }
}

function Get-StringSha256 {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Value)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Get-EvidenceMarker {
    param(
        [Parameter(Mandatory)][string] $Text,
        [Parameter(Mandatory)][string] $Name
    )
    $matches = [regex]::Matches($Text, '(?m)^' + [regex]::Escape($Name) + ':\s*(?<value>[^\r\n]+?)\s*$')
    if ($matches.Count -ne 1) {
        throw "Evidence marker must appear exactly once: $Name"
    }
    return $matches[0].Groups['value'].Value.Trim()
}

function Get-RequiredLectureArchiveEntries {
    return @(
        'fabric.mod.json',
        'LICENSE_developers-hell',
        'dev/developershell/DevelopersHell.class',
        'dev/developershell/client/DevelopersHellClient.class',
        'dev/developershell/config/DevHellConfig.class',
        'dev/developershell/config/DevHellConfigLoader.class',
        'dev/developershell/campaign/CampaignReducer.class',
        'dev/developershell/campaign/CampaignSavedData.class',
        'dev/developershell/campaign/CampaignService.class',
        'dev/developershell/campaign/PlayerCampaignState.class',
        'dev/developershell/entity/ProfessorInfiniteSlidesEntity.class',
        'dev/developershell/entity/HomeworkAddEntity.class',
        'dev/developershell/item/CursedInternshipContractItem.class',
        'dev/developershell/item/RetakeFormItem.class',
        'dev/developershell/item/AttendanceSheetItem.class',
        'dev/developershell/item/InfiniteSlidesRemoteItem.class',
        'dev/developershell/lecture/ArenaValidator.class',
        'dev/developershell/lecture/LectureEncounterManager.class',
        'dev/developershell/lecture/LectureGeometry.class',
        'dev/developershell/lecture/LecturePresentation.class',
        'dev/developershell/lecture/LectureRules.class',
        'dev/developershell/lecture/LectureStateMachine.class',
        'dev/developershell/lecture/RetakeService.class',
        'dev/developershell/lecture/RewardService.class',
        'dev/developershell/registry/ModEntities.class',
        'dev/developershell/registry/ModItemIds.class',
        'dev/developershell/registry/ModItems.class',
        'dev/developershell/server/CampaignLifecycle.class',
        'dev/developershell/server/DeskInteraction.class',
        'dev/developershell/server/DevelopersHellRuntime.class',
        'assets/developers_hell/lang/en_us.json',
        'assets/developers_hell/items/foundation_token.json',
        'assets/developers_hell/items/cursed_unpaid_internship_contract.json',
        'assets/developers_hell/items/retake_form.json',
        'assets/developers_hell/items/attendance_sheet.json',
        'assets/developers_hell/items/infinite_slides_remote.json',
        'assets/developers_hell/models/item/foundation_token.json',
        'assets/developers_hell/models/item/cursed_unpaid_internship_contract.json',
        'assets/developers_hell/models/item/retake_form.json',
        'assets/developers_hell/models/item/attendance_sheet.json',
        'assets/developers_hell/models/item/infinite_slides_remote.json',
        'data/developers_hell/advancement/a_suspicious_opportunity.json',
        'data/developers_hell/recipe/cursed_unpaid_internship_contract.json'
    )
}

function Get-LectureArchiveContract {
    param([Parameter(Mandatory)][string] $JarPath)
    if ((Split-Path -Leaf $JarPath) -cne $script:ExpectedDistributionName) {
        throw 'Archive audit accepts only the exact ordinary production JAR name.'
    }
    Add-Type -AssemblyName System.IO.Compression -ErrorAction Stop
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
    $archive = $null
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
        $entries = @($archive.Entries)
        if ($entries.Count -eq 0) { throw 'Production archive is empty.' }
        $names = @($entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        if (@($names | Where-Object { $_ -ceq 'fabric.mod.json' }).Count -ne 1) {
            throw 'Production archive must contain exactly one root fabric.mod.json.'
        }
        $licenses = @($names | Where-Object { $_ -match '(?i)(?:^|/)LICENSE(?:$|[_\-.].*)' })
        if ($licenses.Count -ne 1 -or $licenses[0] -cne 'LICENSE_developers-hell') {
            throw 'Production archive must contain exactly one renamed root LICENSE_developers-hell.'
        }
        foreach ($required in (Get-RequiredLectureArchiveEntries)) {
            if ($names -cnotcontains $required) { throw "Production archive entry missing: $required" }
        }
        $forbiddenNames = @($names | Where-Object {
            $_ -match '(?i)(?:^|/)(?:src/(?:test|gametest)|test-results|reports/tests|run|world|logs|eula[.]txt|[.]work)(?:/|$)' -or
            $_ -match '(?i)^dev/developershell/gametest/' -or
            $_ -match '(?i)(?:^|/)[^/]*(?:Test|Tests|TestCase)(?:\$[^/]*)?[.]class$' -or
            $_ -match '(?i)^(?:com/openai|okhttp3|retrofit2|io/sentry|com/mixpanel|com/amplitude|com/segment|io/segment)(?:/|$)' -or
            $_ -match '(?i)(?:^|/)(?:[.]env(?:[.][^/]*)?|credentials?|secrets?|id_rsa|id_ed25519)(?:$|[.])'
        })
        if ($forbiddenNames.Count -gt 0) { throw "Forbidden production archive entry: $($forbiddenNames[0])" }

        $latin1 = [System.Text.Encoding]::GetEncoding(28591)
        $manifestText = $null
        $totalBytes = [long] 0
        foreach ($entry in $entries) {
            $name = $entry.FullName.Replace('\', '/')
            if ($name.StartsWith('/') -or $name -match '(?:^|/)[.][.](?:/|$)') { throw "Unsafe archive entry path: $name" }
            if ($name.EndsWith('/')) { continue }
            if ($entry.Length -lt 0 -or $entry.Length -gt 33554432) { throw "Archive entry is too large: $name" }
            $totalBytes += $entry.Length
            if ($totalBytes -gt 134217728) { throw 'Production archive expands beyond the audit limit.' }
            if ($name -notmatch '(?i)[.](?:class|json|properties|txt|xml|mf)$') { continue }
            $stream = $entry.Open()
            $memory = [System.IO.MemoryStream]::new()
            try { $stream.CopyTo($memory); $bytes = $memory.ToArray() }
            finally { $memory.Dispose(); $stream.Dispose() }
            $content = $latin1.GetString($bytes)
            if ($name -ceq 'fabric.mod.json') { $manifestText = [System.Text.Encoding]::UTF8.GetString($bytes) }
            if ($name.StartsWith('dev/developershell/', [System.StringComparison]::Ordinal) -and
                -not $name.StartsWith('dev/developershell/client/', [System.StringComparison]::Ordinal) -and
                $name.EndsWith('.class', [System.StringComparison]::OrdinalIgnoreCase) -and
                $content -match '(?i)(?:net/minecraft/client|com/mojang/blaze3d)') {
                throw "Common production class links a client-only namespace: $name"
            }
            $scannableContent = $content
            if ($name.EndsWith('.json', [System.StringComparison]::OrdinalIgnoreCase)) {
                $telemetryKeys = [regex]::Matches($scannableContent, '(?i)"sends_telemetry_event"\s*:')
                if ($telemetryKeys.Count -gt 0) {
                    if ($telemetryKeys.Count -ne 1) { throw "Telemetry opt-out key is duplicated in archive entry: $name" }
                    try { $json = $scannableContent | ConvertFrom-Json -ErrorAction Stop } catch { throw "Telemetry opt-out JSON is invalid in archive entry: $name" }
                    $property = $json.PSObject.Properties['sends_telemetry_event']
                    if ($null -eq $property -or $property.Value -isnot [bool] -or $property.Value) { throw "Telemetry opt-out must be the exact boolean false in archive entry: $name" }
                    $telemetryOptOutPattern = [regex]::new('"sends_telemetry_event"\s*:\s*false\b', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                    $scannableContent = $telemetryOptOutPattern.Replace($scannableContent, '', 1)
                }
            }
            if ($scannableContent -match '(?i)(?:developers_hell_test|fabric-gametest|java/net/http|https?://|wss?://|com[/\.]openai|okhttp3|retrofit2|io[/\.]sentry|telemetry|analytics|remote[-_./ ]?config|api[-_. ]?key|access[-_. ]?token|Authorization\s*[:=]|Bearer\s+)') {
                throw "Forbidden test/network/API/telemetry/credential marker in archive entry: $name"
            }
        }
        if ([string]::IsNullOrWhiteSpace($manifestText)) { throw 'Production metadata could not be read.' }
        try { $manifest = $manifestText | ConvertFrom-Json -ErrorAction Stop }
        catch { throw 'Root fabric.mod.json is not valid JSON.' }
        if ($manifest.id -cne 'developers_hell' -or $manifest.license -cne 'Unlicense') {
            throw 'Production metadata identity/license is not the exact public contract.'
        }
        if (-not $manifest.entrypoints.main -or -not $manifest.entrypoints.client) {
            throw 'Production metadata lacks the required common/client entrypoints.'
        }
        return [pscustomobject]@{
            Entries = @($names | Sort-Object)
            EntryCount = $names.Count
            EntriesSha256 = Get-StringSha256 -Value ((@($names | Sort-Object)) -join "`n")
            Sha256 = Get-FileSha256 -LiteralPath $JarPath
            Size = (Get-Item -LiteralPath $JarPath).Length
        }
    } catch {
        if ($_.Exception.Message -match '(?i)central directory|end of central directory|not a valid zip') {
            throw "Archive inspection failed closed: $($_.Exception.Message)"
        }
        throw
    } finally {
        if ($null -ne $archive) { $archive.Dispose() }
    }
}

function Assert-ServerTranscript {
    param([Parameter(Mandatory)][string] $Text)
    $orderedMarkers = @(
        $script:ExpectedReadyMarker,
        $script:ExpectedStopCleanupMarker,
        'Stopping server',
        'All dimensions are saved'
    )
    $cursor = -1
    foreach ($marker in $orderedMarkers) {
        $next = $Text.IndexOf($marker, $cursor + 1, [System.StringComparison]::Ordinal)
        if ($next -lt 0) { throw "Production server transcript lacks ordered marker: $marker" }
        $cursor = $next
    }
    if ($Text -match '(?i)(NoClassDefFoundError|ClassNotFoundException|crash report|Exception in server tick loop|Encountered an unexpected exception|Failed to execute command)') {
        throw 'Production server transcript contains a crash/linkage/command failure marker.'
    }
    return $true
}

function Assert-EvidenceContract {
    param(
        [Parameter(Mandatory)][string] $EvidenceText,
        [Parameter(Mandatory)][string] $DistributionHash
    )
    if ($DistributionHash -notmatch '^[0-9a-fA-F]{64}$') { throw 'Distribution hash is not SHA-256.' }
    $normalizedHash = $DistributionHash.ToLowerInvariant()
    foreach ($marker in @('source_jar_sha256','build_jar_sha256','distribution_sha256')) {
        if ((Get-EvidenceMarker -Text $EvidenceText -Name $marker).ToLowerInvariant() -cne $normalizedHash) {
            throw "Evidence hash mismatch: $marker"
        }
    }
    foreach ($marker in @('gradle_transaction_exit','foundation_audit_adjudication_exit','production_server_exit')) {
        if ((Get-EvidenceMarker -Text $EvidenceText -Name $marker) -cne '0') { throw "Evidence exit is not zero: $marker" }
    }
    if ((Get-EvidenceMarker -Text $EvidenceText -Name 'foundation_audit_exit') -cne '1') { throw 'Evidence must preserve the raw foundation audit sanitizer false-positive exit 1.' }
    foreach ($marker in @('server_ready','server_stop_cleanup_callback','server_ordered_shutdown','clean_exit','owned_child_cleanup','source_archive_audit','phase2_archive_audit','hash_equality')) {
        $value = Get-EvidenceMarker -Text $EvidenceText -Name $marker
        if ($value -notmatch '(?i)(?:PASS|equal|clean|zero)') { throw "Evidence PASS marker is not green: $marker" }
    }
    foreach ($row in $script:RequiredAutomatedRows) {
        $matches = [regex]::Matches($EvidenceText, '(?m)^\|\s*' + [regex]::Escape($row) + '\s*\|[^\r\n]*\|\s*PASS\s*\|\s*$')
        if ($matches.Count -ne 1) { throw "Automated evidence row must appear once as PASS: $row" }
    }
    foreach ($row in $script:ManualRows) {
        $matches = [regex]::Matches($EvidenceText, '(?m)^\|\s*' + [regex]::Escape($row) + '\s*\|[^\r\n]*\|\s*PENDING\s*\|\s*$')
        if ($matches.Count -ne 1) { throw "Manual backstop row must appear once as PENDING: $row" }
    }
    $allManual = [regex]::Matches($EvidenceText, '(?im)^\|\s*MANUAL-[^|]+\|[^\r\n]*\|\s*(?:PENDING|PASS|FAIL)\s*\|\s*$')
    if ($allManual.Count -ne 7 -or @($allManual | Where-Object { $_.Value -match '(?i)\|\s*(?:PASS|FAIL)\s*\|\s*$' }).Count -ne 0) {
        throw 'Evidence must retain exactly seven distinct manual/client rows as PENDING.'
    }
    $homePath = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    foreach ($privatePattern in @(
        [regex]::Escape($script:RepositoryRoot),
        $(if ([string]::IsNullOrWhiteSpace($homePath)) { '(?!)' } else { [regex]::Escape($homePath) }),
        '(?i)(?:Authorization\s*[:=]|Bearer\s+[A-Za-z0-9._~+/=-]+|[?&](?:api[-_]?key|access[-_]?token|secret|password)=)',
        '(?i)(?:^|[\s`"''])(?:[A-Z]:\\Users\\|/home/[^/\s]+/)'
    )) {
        if ($EvidenceText -match $privatePattern) { throw 'Evidence contains a private path or credential-like value.' }
    }
    return $true
}

function ConvertTo-NativeArgument {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Argument)
    if ($Argument -notmatch '[\s"]') { return $Argument }
    return '"' + ([regex]::Replace($Argument, '(\\*)"', '$1$1\"') -replace '(\\+)$', '$1$1') + '"'
}

function Get-CmdBatchArguments {
    param(
        [Parameter(Mandatory)][string] $BatchPath,
        [Parameter(Mandatory)][string[]] $ArgumentList
    )
    foreach ($value in @($BatchPath) + @($ArgumentList)) {
        if ([string]$value -match '[\r\n"&|<>^%!]') { throw 'Batch argument contains a forbidden cmd.exe metacharacter.' }
    }
    $quoted = ($ArgumentList | ForEach-Object { '"' + [string]$_ + '"' }) -join ' '
    return '/d /s /c ""' + $BatchPath + '" ' + $quoted + '"'
}

function Get-LiveProcessStartTicks {
    param([Parameter(Mandatory)][int] $ProcessId)
    $process = $null
    try {
        $process = [System.Diagnostics.Process]::GetProcessById($ProcessId)
        return [long] $process.StartTime.ToUniversalTime().Ticks
    } catch [System.ArgumentException] { return $null }
    catch [System.InvalidOperationException] { return $null }
    finally { if ($null -ne $process) { $process.Dispose() } }
}

function Assert-ProcessInspectionAvailable {
    if (-not (Get-Command Get-CimInstance -ErrorAction SilentlyContinue)) {
        throw 'Scoped CIM process inspection is unavailable.'
    }
    $rows = @(Get-CimInstance Win32_Process -Filter "ProcessId = $PID" -ErrorAction Stop)
    if ($rows.Count -ne 1 -or [string]::IsNullOrWhiteSpace([string]$rows[0].ExecutablePath)) {
        throw 'Scoped CIM process inspection cannot bind the verifier process.'
    }
}

function Test-OwnedChildStartOrder {
    param([Parameter(Mandatory)][long] $ParentStartTicks, [Parameter(Mandatory)][long] $ChildStartTicks)
    return $ChildStartTicks -ge $ParentStartTicks
}

function Get-OwnedProcessTree {
    param(
        [Parameter(Mandatory)] $Runtime,
        [switch] $RequireServerClasses
    )
    if ($Runtime.Process.HasExited) { return @() }
    $rootTicks = Get-LiveProcessStartTicks -ProcessId $Runtime.RootPid
    if ($null -eq $rootTicks -or [long]$rootTicks -ne [long]$Runtime.RootStartTicks) {
        throw 'Owned root PID/start-time identity changed.'
    }
    $rootRows = @(Get-CimInstance Win32_Process -Filter "ProcessId = $($Runtime.RootPid)" -ErrorAction Stop)
    if ($rootRows.Count -ne 1) { throw 'Owned root PID is absent or ambiguous.' }
    $rows = [System.Collections.Generic.List[object]]::new()
    $rows.Add($rootRows[0])
    $depth = @{}
    $depth[[string]$Runtime.RootPid] = 0
    $startByPid = @{}
    $startByPid[[string]$Runtime.RootPid] = [long]$Runtime.RootStartTicks
    $queue = [System.Collections.Generic.Queue[int]]::new()
    $queue.Enqueue([int]$Runtime.RootPid)
    while ($queue.Count -gt 0) {
        $parent = $queue.Dequeue()
        foreach ($row in @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $parent" -ErrorAction Stop)) {
            $key = [string][int]$row.ProcessId
            if ($depth.ContainsKey($key)) { continue }
            $childTicks = Get-LiveProcessStartTicks -ProcessId ([int]$row.ProcessId)
            if ($null -eq $childTicks -or -not (Test-OwnedChildStartOrder -ParentStartTicks ([long]$startByPid[[string]$parent]) -ChildStartTicks ([long]$childTicks))) {
                # Win32_Process retains a numeric parent PID after the parent exits.
                # Ignore stale edges whose child predates this exact parent identity.
                continue
            }
            $depth[$key] = [int]$depth[[string][int]$row.ParentProcessId] + 1
            $startByPid[$key] = [long]$childTicks
            $rows.Add($row)
            $queue.Enqueue([int]$row.ProcessId)
        }
    }
    $rootExecutable = (Resolve-Path -LiteralPath ([string]$rootRows[0].ExecutablePath) -ErrorAction Stop).Path
    if (-not $rootExecutable.Equals($Runtime.RootExecutable, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Owned root executable identity changed.'
    }
    foreach ($anchor in $Runtime.RootAnchors) {
        if ([string]$rootRows[0].CommandLine -notlike ('*' + [string]$anchor + '*')) { throw "Owned root command line lost anchor: $anchor" }
    }
    $snapshot = [System.Collections.Generic.List[object]]::new()
    $classes = [System.Collections.Generic.List[string]]::new()
    foreach ($row in $rows) {
        $pidValue = [int]$row.ProcessId
        $ticks = Get-LiveProcessStartTicks -ProcessId $pidValue
        if ($null -eq $ticks) { continue }
        if ([string]::IsNullOrWhiteSpace([string]$row.ExecutablePath)) {
            $retryRows = @(Get-CimInstance Win32_Process -Filter "ProcessId = $pidValue" -ErrorAction Stop)
            $retryTicks = Get-LiveProcessStartTicks -ProcessId $pidValue
            if ($retryRows.Count -eq 0 -or $null -eq $retryTicks -or [long]$retryTicks -ne [long]$ticks) { continue }
            if ($retryRows.Count -ne 1 -or [string]::IsNullOrWhiteSpace([string]$retryRows[0].ExecutablePath)) {
                throw "Owned live PID $pidValue parent $([int]$row.ParentProcessId) startTicks $ticks lacks a complete executable identity after exact requery."
            }
            $row = $retryRows[0]
        }
        $executable = (Resolve-Path -LiteralPath ([string]$row.ExecutablePath) -ErrorAction Stop).Path
        $commandLine = [string]$row.CommandLine
        if ([string]::IsNullOrWhiteSpace($commandLine)) { throw "Owned PID $pidValue has no command line." }
        $allowed = $executable.Equals($Runtime.RootExecutable, [System.StringComparison]::OrdinalIgnoreCase) -or
            $executable.Equals($Runtime.Jdk.Java, [System.StringComparison]::OrdinalIgnoreCase) -or
            $executable.Equals($Runtime.ComSpec, [System.StringComparison]::OrdinalIgnoreCase) -or
            $executable.Equals($Runtime.PowerShell, [System.StringComparison]::OrdinalIgnoreCase) -or
            $executable.Equals($Runtime.Conhost, [System.StringComparison]::OrdinalIgnoreCase)
        if (-not $allowed) { throw "Owned PID $pidValue uses an unexpected executable: $([System.IO.Path]::GetFileName($executable))" }
        $class = if ($pidValue -eq $Runtime.RootPid) { 'Root' } elseif ($commandLine -match '(?i)(GradleWrapperMain|gradle-wrapper[.]jar)') { 'GradleWrapperMain' } elseif ($commandLine -match '(?i)GradleDaemon') { 'GradleDaemon' } elseif ($commandLine -match '(?i)(ServerLauncher|FabricServerLauncher|KnotServer|MinecraftGameProvider)') { 'ServerLauncher' } else { 'OwnedHelper' }
        [void]$classes.Add($class)
        $snapshot.Add([pscustomobject]@{ Pid=$pidValue; ParentPid=[int]$row.ParentProcessId; Depth=[int]$depth[[string]$pidValue]; StartTicks=[long]$ticks; Executable=$executable; CommandLine=$commandLine; Class=$class })
    }
    if ($RequireServerClasses) {
        foreach ($required in @('GradleWrapperMain','GradleDaemon','ServerLauncher')) {
            if ($classes -cnotcontains $required) { throw "Owned ready server tree lacks class: $required" }
        }
    }
    return @($snapshot)
}

function Merge-OwnedProcessSnapshot {
    param([Parameter(Mandatory)] $Runtime)
    if ($Runtime.Process.HasExited) { return }
    $current = @(Get-OwnedProcessTree -Runtime $Runtime)
    foreach ($entry in $current) { $Runtime.Snapshot[([string]$entry.Pid + '|' + [string]$entry.StartTicks)] = $entry }
}

function Get-LiveOwnedResidue {
    param([Parameter(Mandatory)] $Runtime)
    return @($Runtime.Snapshot.Values | Where-Object {
        $ticks = Get-LiveProcessStartTicks -ProcessId ([int]$_.Pid)
        $null -ne $ticks -and [long]$ticks -eq [long]$_.StartTicks
    })
}

function Stop-ExactOwnedRuntime {
    param([Parameter(Mandatory)] $Runtime)
    if (-not $Runtime.Process.HasExited) {
        Merge-OwnedProcessSnapshot -Runtime $Runtime
        $ticks = Get-LiveProcessStartTicks -ProcessId $Runtime.RootPid
        if ($null -eq $ticks -or [long]$ticks -ne [long]$Runtime.RootStartTicks) { throw 'Cannot revalidate exact runtime root for cleanup.' }
        $taskkill = Join-Path $env:SystemRoot 'System32\taskkill.exe'
        & $taskkill /PID ([string]$Runtime.RootPid) /T /F *> $null
    }
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $live = @(Get-LiveOwnedResidue -Runtime $Runtime)
        if ($live.Count -eq 0) { return }
        foreach ($entry in @($live | Sort-Object Depth -Descending)) {
            $ticks = Get-LiveProcessStartTicks -ProcessId ([int]$entry.Pid)
            if ($null -ne $ticks -and [long]$ticks -eq [long]$entry.StartTicks) {
                Stop-Process -Id ([int]$entry.Pid) -Force -ErrorAction Stop
            }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    $remaining = @(Get-LiveOwnedResidue -Runtime $Runtime)
    if ($remaining.Count -ne 0) { throw 'Exact owned runtime cleanup left PID/start-time residue.' }
}

function Start-OwnedProcess {
    param(
        [Parameter(Mandatory)][string] $Executable,
        [Parameter(Mandatory)][string] $Arguments,
        [Parameter(Mandatory)][string[]] $RootAnchors,
        [Parameter(Mandatory)] $Jdk
    )
    Assert-ProcessInspectionAvailable
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Executable
    $start.Arguments = $Arguments
    $start.WorkingDirectory = $script:RepositoryRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Owned process could not start.' }
    return [pscustomobject]@{
        Process=$process; StdOutTask=$process.StandardOutput.ReadToEndAsync(); StdErrTask=$process.StandardError.ReadToEndAsync(); RootPid=[int]$process.Id; RootStartTicks=[long]$process.StartTime.ToUniversalTime().Ticks; RootExecutable=(Resolve-Path -LiteralPath $Executable).Path; RootAnchors=$RootAnchors; Jdk=$Jdk; ComSpec=(Resolve-Path -LiteralPath $env:ComSpec).Path; PowerShell=(Resolve-Path -LiteralPath (Join-Path $PSHOME 'powershell.exe') -ErrorAction SilentlyContinue).Path; Conhost=(Resolve-Path -LiteralPath (Join-Path $env:SystemRoot 'System32\conhost.exe')).Path; Snapshot=[ordered]@{}
    }
}

function Complete-OwnedProcess {
    param(
        [Parameter(Mandatory)] $Runtime,
        [Parameter(Mandatory)][int] $TimeoutSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    try {
        while (-not $Runtime.Process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
            Merge-OwnedProcessSnapshot -Runtime $Runtime
            [void]$Runtime.Process.WaitForExit(250)
        }
        if (-not $Runtime.Process.HasExited) { throw "Owned process exceeded timeout ${TimeoutSeconds}s." }
        try { $Runtime.Process.StandardInput.Close() } catch { }
        $residueDeadline = [DateTime]::UtcNow.AddSeconds(20)
        do {
            $live = @(Get-LiveOwnedResidue -Runtime $Runtime)
            if ($live.Count -eq 0) { break }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $residueDeadline)
        if ($live.Count -ne 0) { throw 'Owned root exited while captured descendants remained alive.' }
        $stdout = $Runtime.StdOutTask.GetAwaiter().GetResult()
        $stderr = $Runtime.StdErrTask.GetAwaiter().GetResult()
        return [pscustomobject]@{ ExitCode=[int]$Runtime.Process.ExitCode; StdOut=$stdout; StdErr=$stderr; Combined=(($stdout.TrimEnd(),$stderr.TrimEnd()) | Where-Object { $_ }) -join "`n"; CapturedCount=$Runtime.Snapshot.Count; RootPid=$Runtime.RootPid }
    } catch {
        $primary = $_
        try { Stop-ExactOwnedRuntime -Runtime $Runtime }
        catch { throw "$($primary.Exception.Message) Exact-child cleanup also failed: $($_.Exception.Message)" }
        throw $primary
    } finally {
        if ($Runtime.Process.HasExited) { $Runtime.Process.Dispose() }
    }
}

function Get-ToolchainMarker {
    param([Parameter(Mandatory)][string] $Name)
    $path = Join-Path $script:RepositoryRoot $script:ToolchainEvidenceRelativePath
    $text = [System.IO.File]::ReadAllText($path)
    return Get-EvidenceMarker -Text $text -Name $Name
}

function Get-VerifiedJdk {
    $root = (Resolve-Path -LiteralPath (Join-Path $script:RepositoryRoot $script:JdkRelativePath) -ErrorAction Stop).Path
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME) -or -not (Resolve-Path -LiteralPath $env:JAVA_HOME).Path.Equals($root, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'JAVA_HOME must select the retained checksum-bound Java 25 runtime.' }
    $java = Join-Path $root 'bin\java.exe'
    $javac = Join-Path $root 'bin\javac.exe'
    foreach ($path in @($root,$java,$javac)) {
        $item = Get-Item -LiteralPath $path -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Verified JDK paths must not be reparse points.' }
    }
    if ((Get-FileSha256 $java) -cne (Get-ToolchainMarker 'jdk_java_sha256').ToLowerInvariant()) { throw 'Checksum-bound java.exe hash mismatch.' }
    if ((Get-FileSha256 $javac) -cne (Get-ToolchainMarker 'jdk_javac_sha256').ToLowerInvariant()) { throw 'Checksum-bound javac.exe hash mismatch.' }
    if ((Get-StringSha256 $root.ToLowerInvariant()) -cne (Get-ToolchainMarker 'jdk_path_sha256').ToLowerInvariant()) { throw 'Checksum-bound JDK canonical path mismatch.' }
    $env:Path = "$(Join-Path $root 'bin');$env:Path"
    return [pscustomobject]@{ Root=$root; Java=(Resolve-Path $java).Path; Javac=(Resolve-Path $javac).Path }
}

function Get-GradleArguments {
    param([Parameter(Mandatory)] $Jdk, [Parameter(Mandatory)][string[]] $Tasks)
    return @("-Dorg.gradle.java.installations.paths=$($Jdk.Root)",'-Dorg.gradle.java.installations.auto-detect=false','-Dorg.gradle.java.installations.auto-download=false','--offline') + $Tasks + @('--no-daemon','--console=plain','--stacktrace','--init-script',$script:GradleInitRelativePath)
}

function Invoke-BoundedBatch {
    param([Parameter(Mandatory)][string[]] $Arguments, [Parameter(Mandatory)] $Jdk, [int] $TimeoutSeconds=1200)
    $wrapper = (Resolve-Path -LiteralPath (Join-Path $script:RepositoryRoot 'gradlew.bat')).Path
    $runtime = Start-OwnedProcess -Executable $env:ComSpec -Arguments (Get-CmdBatchArguments -BatchPath $wrapper -ArgumentList $Arguments) -RootAnchors @($wrapper,$Arguments[4]) -Jdk $Jdk
    return Complete-OwnedProcess -Runtime $runtime -TimeoutSeconds $TimeoutSeconds
}

function Initialize-ProductionServerProfile {
    $runRoot = Join-Path $script:RepositoryRoot $script:ServerRunRelativePath
    if (-not (Test-Path -LiteralPath $runRoot -PathType Container)) { [void](New-Item -ItemType Directory -Path $runRoot) }
    $runItem = Get-Item -LiteralPath $runRoot -Force
    if (($runItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Production server profile must not be a reparse point.' }
    $eulaPath = Join-Path $runRoot 'eula.txt'
    $propertiesPath = Join-Path $runRoot 'server.properties'
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($eulaPath, "# Local automated Developer's Hell smoke profile only`neula=true`n", $utf8)
    $properties = @(
        'server-ip=127.0.0.1',
        'server-port=0',
        'online-mode=false',
        'enable-query=false',
        'enable-rcon=false',
        'enable-status=false',
        'resource-pack=',
        'resource-pack-sha1=',
        'motd=Developer''s Hell local automated smoke',
        'level-name=lecture-verification-world'
    ) -join "`n"
    [System.IO.File]::WriteAllText($propertiesPath, $properties + "`n", $utf8)
}

function Invoke-BoundedProductionServer {
    param(
        [Parameter(Mandatory)] $Jdk,
        [Parameter(Mandatory)][string] $ArtifactPath
    )
    $artifactHash = Get-FileSha256 -LiteralPath $ArtifactPath
    Initialize-ProductionServerProfile
    $logPath = Join-Path $script:RepositoryRoot "$($script:ServerRunRelativePath)\logs\latest.log"
    if (Test-Path -LiteralPath $logPath -PathType Leaf) { [System.IO.File]::Delete($logPath) }
    $arguments = Get-GradleArguments -Jdk $Jdk -Tasks @('runProductionServer')
    $wrapper = (Resolve-Path -LiteralPath (Join-Path $script:RepositoryRoot 'gradlew.bat')).Path
    $runtime = Start-OwnedProcess -Executable $env:ComSpec -Arguments (Get-CmdBatchArguments -BatchPath $wrapper -ArgumentList $arguments) -RootAnchors @($wrapper,'runProductionServer') -Jdk $Jdk
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds(300)
        $readyText = ''
        while ([DateTime]::UtcNow -lt $deadline) {
            if ($runtime.Process.HasExited) { throw 'Production server exited before first-tick readiness.' }
            Merge-OwnedProcessSnapshot -Runtime $runtime
            if (Test-Path -LiteralPath $logPath -PathType Leaf) { $readyText = Read-SharedTextFile -LiteralPath $logPath }
            if ($readyText.Contains($script:ExpectedReadyMarker)) { break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $readyText.Contains($script:ExpectedReadyMarker)) { throw 'Production server did not reach first-tick readiness within 300 seconds.' }
        foreach ($entry in @(Get-OwnedProcessTree -Runtime $runtime -RequireServerClasses)) { $runtime.Snapshot[([string]$entry.Pid + '|' + [string]$entry.StartTicks)] = $entry }
        $runtime.Process.StandardInput.WriteLine('stop')
        $runtime.Process.StandardInput.Flush()
        $complete = Complete-OwnedProcess -Runtime $runtime -TimeoutSeconds 120
        if ($complete.ExitCode -ne 0) { throw "Production server exited nonzero: $($complete.ExitCode)" }
        $finalLog = Read-SharedTextFile -LiteralPath $logPath
        [void](Assert-ServerTranscript -Text $finalLog)
        if ((Get-FileSha256 -LiteralPath $ArtifactPath) -cne $artifactHash) { throw 'Inspected build JAR changed during production server proof.' }
        return [pscustomobject]@{ Ready=$true; StopCleanup=$true; CleanExit=$true; ExitCode=0; RootPid=$complete.RootPid; CapturedCount=$complete.CapturedCount; LogSha256=Get-FileSha256 $logPath; Transcript=$finalLog }
    } catch {
        $primary = $_
        if (-not $runtime.Process.HasExited) {
            try { Stop-ExactOwnedRuntime -Runtime $runtime }
            catch { throw "$($primary.Exception.Message) Exact-child server cleanup also failed: $($_.Exception.Message)" }
        }
        throw $primary
    }
}

function Assert-SelfCheckRejects {
    param(
        [Parameter(Mandatory)][scriptblock] $Action,
        [Parameter(Mandatory)][string] $Label
    )
    $rejected = $false
    try { & $Action | Out-Null }
    catch { $rejected = $true }
    if (-not $rejected) { throw "Self-check mutation was accepted: $Label" }
}

function New-SyntheticLectureArchive {
    param(
        [Parameter(Mandatory)][string] $Path,
        [string] $OmitEntry,
        [string] $AdditionalEntry,
        [string] $TelemetryValue
    )
    Add-Type -AssemblyName System.IO.Compression -ErrorAction Stop
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    $file = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    $archive = $null
    try {
        $archive = [System.IO.Compression.ZipArchive]::new($file, [System.IO.Compression.ZipArchiveMode]::Create, $false)
        $entries = @((Get-RequiredLectureArchiveEntries) + $(if ([string]::IsNullOrWhiteSpace($AdditionalEntry)) { @() } else { @($AdditionalEntry) }))
        foreach ($name in $entries) {
            if (-not [string]::IsNullOrWhiteSpace($OmitEntry) -and $name -ceq $OmitEntry) { continue }
            $entry = $archive.CreateEntry($name)
            $stream = $entry.Open()
            try {
                $content = if ($name -ceq 'fabric.mod.json') {
                    '{"schemaVersion":1,"id":"developers_hell","version":"0.1.0","environment":"*","entrypoints":{"main":["dev.developershell.DevelopersHell"],"client":["dev.developershell.client.DevelopersHellClient"]},"license":"Unlicense"}'
                } elseif ($name -ceq 'LICENSE_developers-hell') { 'The Unlicense' } elseif ($name -ceq 'data/developers_hell/advancement/a_suspicious_opportunity.json' -and -not [string]::IsNullOrWhiteSpace($TelemetryValue)) { '{"sends_telemetry_event":' + $TelemetryValue + '}' } else { 'fixture' }
                $bytes = [System.Text.Encoding]::UTF8.GetBytes($content)
                $stream.Write($bytes, 0, $bytes.Length)
            } finally { $stream.Dispose() }
        }
    } finally {
        if ($null -ne $archive) { $archive.Dispose() } else { $file.Dispose() }
    }
}

function New-SyntheticEvidenceText {
    param([Parameter(Mandatory)][string] $Hash)
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in @(
        '# Phase 2 Lecture Evidence',
        "source_jar_sha256: $Hash",
        "build_jar_sha256: $Hash",
        "distribution_sha256: $Hash",
        'gradle_transaction_exit: 0',
        'foundation_audit_exit: 1',
        'foundation_audit_adjudication_exit: 0',
        'production_server_exit: 0',
        'server_ready: PASS',
        'server_stop_cleanup_callback: PASS',
        'server_ordered_shutdown: PASS',
        'clean_exit: PASS',
        'owned_child_cleanup: PASS - zero owned child residue',
        'source_archive_audit: PASS',
        'phase2_archive_audit: PASS',
        'hash_equality: source/build/dist hashes equal',
        '',
        '| Automated ID | Evidence | Status |',
        '|---|---|---|'
    )) { [void] $lines.Add($line) }
    foreach ($row in $script:RequiredAutomatedRows) { [void] $lines.Add("| $row | synthetic green anchor | PASS |") }
    [void] $lines.Add('')
    [void] $lines.Add('| Manual backstop ID | Direct-client observation | Status |')
    [void] $lines.Add('|---|---|---|')
    foreach ($row in $script:ManualRows) { [void] $lines.Add("| $row | visible isolated-client observation not run | PENDING |") }
    return ($lines -join "`n") + "`n"
}

function Invoke-SelfCheckMode {
    $defaultEvidence = Resolve-SafeRepositoryPath `
        -Path '.planning/phases/02-persistent-lecture-vertical-slice/02-LECTURE-EVIDENCE.md' `
        -ExpectedRelativePath '.planning/phases/02-persistent-lecture-vertical-slice/02-LECTURE-EVIDENCE.md' `
        -AllowMissingLeaf
    if ([string]::IsNullOrWhiteSpace($defaultEvidence)) {
        throw 'Safe evidence path contract returned no path.'
    }
    [void](Resolve-SafeRepositoryPath -Path $script:DefaultDistributionRelativePath -ExpectedRelativePath $script:DefaultDistributionRelativePath -AllowMissingLeaf)
        Assert-SelfCheckRejects -Label 'path traversal/alternate target' -Action {
        Resolve-SafeRepositoryPath -Path '..\outside.md' -ExpectedRelativePath $script:DefaultEvidenceRelativePath -AllowMissingLeaf
    }
    if (-not (Test-OwnedChildStartOrder -ParentStartTicks 1000 -ChildStartTicks 1000) -or
        -not (Test-OwnedChildStartOrder -ParentStartTicks 1000 -ChildStartTicks 1001) -or
        (Test-OwnedChildStartOrder -ParentStartTicks 1000 -ChildStartTicks 999)) {
        throw 'Owned-child start-order self-check accepted a stale ParentProcessId PID-reuse edge.'
    }

    $fixtureRoot = Join-Path $script:RepositoryRoot ('.work\lecture-verifier-self-check-' + [guid]::NewGuid().ToString('N'))
    [void](New-Item -ItemType Directory -Path $fixtureRoot)
    try {
        $jar = Join-Path $fixtureRoot $script:ExpectedDistributionName
        New-SyntheticLectureArchive -Path $jar
        $fresh = Assert-FreshArtifact -JarPath $jar -BuildStartedUtc ([DateTime]::UtcNow.AddSeconds(-5))
        if ($fresh.Sha256 -notmatch '^[0-9a-f]{64}$') { throw 'Fresh-artifact self-check returned an invalid hash.' }
        [void](Get-LectureArchiveContract -JarPath $jar)

        [System.IO.File]::SetLastWriteTimeUtc($jar, [DateTime]::UtcNow.AddMinutes(-10))
        Assert-SelfCheckRejects -Label 'stale ordinary JAR' -Action {
            Assert-FreshArtifact -JarPath $jar -BuildStartedUtc ([DateTime]::UtcNow)
        }
        New-SyntheticLectureArchive -Path $jar -OmitEntry 'dev/developershell/server/CampaignLifecycle.class'
        Assert-SelfCheckRejects -Label 'missing archive entry' -Action { Get-LectureArchiveContract -JarPath $jar }
        New-SyntheticLectureArchive -Path $jar -AdditionalEntry 'dev/developershell/gametest/TamperedTest.class'
        Assert-SelfCheckRejects -Label 'test output in archive' -Action { Get-LectureArchiveContract -JarPath $jar }
        New-SyntheticLectureArchive -Path $jar -TelemetryValue 'true'
        Assert-SelfCheckRejects -Label 'telemetry opt-out true' -Action { Get-LectureArchiveContract -JarPath $jar }
        New-SyntheticLectureArchive -Path $jar -TelemetryValue '"false"'
        Assert-SelfCheckRejects -Label 'telemetry opt-out string false' -Action { Get-LectureArchiveContract -JarPath $jar }

        $hash = ('a' * 64)
        $evidence = New-SyntheticEvidenceText -Hash $hash
        [void](Assert-EvidenceContract -EvidenceText $evidence -DistributionHash $hash)
        Assert-SelfCheckRejects -Label 'evidence hash mismatch' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace "distribution_sha256: $hash", ('distribution_sha256: ' + ('b' * 64))) -DistributionHash $hash
        }
        Assert-SelfCheckRejects -Label 'missing real stop callback marker' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace '(?m)^server_stop_cleanup_callback:.*\r?\n', '') -DistributionHash $hash
        }
        Assert-SelfCheckRejects -Label 'manual observation inferred as PASS' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace '(?m)^\| MANUAL-UI-01 \|([^\r\n]+)\| PENDING \|$', '| MANUAL-UI-01 |$1| PASS |') -DistributionHash $hash
        }

        $ordered = "$($script:ExpectedReadyMarker)`n$($script:ExpectedStopCleanupMarker)`nStopping server`nAll dimensions are saved"
        [void](Assert-ServerTranscript -Text $ordered)
        Assert-SelfCheckRejects -Label 'missing production stop callback transcript' -Action {
            Assert-ServerTranscript -Text ($ordered -replace [regex]::Escape($script:ExpectedStopCleanupMarker), '')
        }
        Assert-SelfCheckRejects -Label 'out-of-order production shutdown transcript' -Action {
            Assert-ServerTranscript -Text "$($script:ExpectedReadyMarker)`nStopping server`n$($script:ExpectedStopCleanupMarker)`nAll dimensions are saved"
        }
        $copySource = Join-Path $fixtureRoot 'copy-source.bin'
        $copyDestination = Join-Path $fixtureRoot 'copy-destination.bin'
        [System.IO.File]::WriteAllText($copySource, 'new exact bytes', [System.Text.UTF8Encoding]::new($false))
        [System.IO.File]::WriteAllText($copyDestination, 'old bytes', [System.Text.UTF8Encoding]::new($false))
        $sourceHashBefore = Get-FileSha256 $copySource
        Copy-ArtifactAtomically -Source $copySource -Destination $copyDestination
        if (-not (Test-Path -LiteralPath $copySource -PathType Leaf) -or (Get-FileSha256 $copySource) -cne $sourceHashBefore -or (Get-FileSha256 $copyDestination) -cne $sourceHashBefore) { throw 'Atomic replacement self-check did not preserve exact source/destination bytes.' }
        [System.IO.File]::WriteAllText($copyDestination, 'preserve on failure', [System.Text.UTF8Encoding]::new($false))
        $preservedHash = Get-FileSha256 $copyDestination
        Assert-SelfCheckRejects -Label 'atomic promotion injected failure' -Action {
            Copy-ArtifactAtomically -Source $copySource -Destination $copyDestination -BeforeReplaceAction { throw 'synthetic pre-replace failure' }
        }
        if ((Get-FileSha256 $copyDestination) -cne $preservedHash -or @(Get-ChildItem -LiteralPath $fixtureRoot -File -Filter '.*.tmp').Count -ne 0) { throw 'Atomic replacement failure did not preserve the old destination or clean exact temp residue.' }

        $source = [System.IO.File]::ReadAllText($script:VerifierScriptPath)
        foreach ($requiredToken in @('Get-LectureArchiveContract','Assert-EvidenceContract','Invoke-BoundedProductionServer','finally','DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE')) {
            if ($source -notmatch [regex]::Escape($requiredToken)) { throw "Verifier source contract token is missing: $requiredToken" }
        }
        $operationalSource = (([regex]::Split($source, '\r?\n') | Where-Object { $_ -notmatch '^\s*(?:if\s*\(\$source\s+-match|\$broadOperationPattern\s*=)' }) -join "`n")
        $broadOperationPattern = '(?i)Stop-Process\s+(?:-Name|\*)|Get-Process\s*\|\s*Stop-Process|Remove-Item[^\r\n]+(?:-Recurse[^\r\n]+(?:\$HOME|~)|(?:\$HOME|~)[^\r\n]+-Recurse)'
        if ($operationalSource -match $broadOperationPattern) {
            throw 'Verifier source contains a broad process/delete operation.'
        }
    } finally {
        if (Test-Path -LiteralPath $fixtureRoot -PathType Container) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    }
    Write-Host 'PASS: lecture verifier canonical path, freshness, archive, evidence, shutdown-order, and mutation self-checks'
}

function Invoke-BoundedFoundationAudit {
    param([Parameter(Mandatory)] $Jdk, [Parameter(Mandatory)][string] $JarPath)
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $auditScript = (Resolve-Path -LiteralPath (Join-Path $script:RepositoryRoot 'scripts\audit-foundation.ps1')).Path
    $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$auditScript,'-SourceAndDependencies','-JarPath',$JarPath)
    $argumentText = ($arguments | ForEach-Object { ConvertTo-NativeArgument -Argument ([string]$_) }) -join ' '
    $runtime = Start-OwnedProcess -Executable $powershell -Arguments $argumentText -RootAnchors @($auditScript,'SourceAndDependencies') -Jdk $Jdk
    return Complete-OwnedProcess -Runtime $runtime -TimeoutSeconds 1200
}

function Assert-IndependentProductionSourceContract {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $script:RepositoryRoot 'src\main') -File -Recurse) + @(Get-ChildItem -LiteralPath (Join-Path $script:RepositoryRoot 'src\client') -File -Recurse)
    foreach ($file in $files) {
        if ($file.Extension -notin @('.java','.json','.properties','.mcmeta')) { continue }
        $text = [System.IO.File]::ReadAllText($file.FullName)
        if ($text -match '(?i)\bimport\s+(?:static\s+)?java[.]net(?:[.]|\b)|\bjava[.]net[.]|\b(?:HttpClient|HttpRequest|HttpResponse|URLConnection|HttpURLConnection|ServerSocket|DatagramSocket|WebSocket)\b|\b(?:openConnection|openStream)\s*\(|\b(?:https?|wss?)://|\bcom[.]openai\b|\b(?:OpenAI|ChatGPT)(?:Api|API|Client|Sdk|SDK|Service|Connector|Transport)\b|\b(?:AnalyticsClient|TelemetryClient|MixpanelAPI|AmplitudeClient|SegmentAnalytics)\b|\b(?:LaunchDarkly|UnleashClient|FirebaseRemoteConfig|OpenFeatureClient)\b') {
            throw "Independent source scan found an operational network/API/telemetry surface: $($file.Name)"
        }
    }
    return $true
}

function Assert-FoundationAuditAdjudication {
    param([Parameter(Mandatory)] $AuditResult)
    if ($AuditResult.ExitCode -eq 0) { throw 'Foundation audit unexpectedly stopped reporting the pinned sanitizer literal; review the adjudication instead of silently widening it.' }
    if ($AuditResult.ExitCode -ne 1) { throw "Foundation audit returned unexpected raw exit $($AuditResult.ExitCode)." }
    $text = $AuditResult.Combined.Replace('\', '/')
    $expectedPath = 'src/main/java/dev/developershell/config/ConfigIssue.java'
    $failureSections = [regex]::Matches($text, '(?m)^FAIL:').Count
    if ($failureSections -ne 2 -or
        $text -notmatch '(?m)^## SOURCE_RUNTIME_SURFACES\r?\nFAIL: Forbidden account credential or authorization surface found in: src/main/java/dev/developershell/config/ConfigIssue[.]java\s*$' -or
        $text -notmatch '(?m)^## FINAL_RESULT\r?\nFAIL: FOUNDATION_AUDIT \(1 section failure\(s\)\)\s*$' -or
        $text -notmatch '(?m)^- SOURCE_RUNTIME_SURFACES - Forbidden account credential or authorization surface found in: src/main/java/dev/developershell/config/ConfigIssue[.]java\s*$') {
        throw 'Foundation audit output is not the one exact adjudicable sanitizer-literal false positive.'
    }
    foreach ($pass in @('PREREQUISITES','COMMON_CLIENT_LINKAGE','OFFICIAL_REPOSITORIES','DIRECT_DEPENDENCIES','RUNTIME_CLASSPATH_REPORT','PRODUCTION_ARCHIVE','WRAPPER_AND_GIT_HYGIENE')) {
        if ($text -notmatch ('(?s)## ' + [regex]::Escape($pass) + '\r?\nPASS:')) { throw "Foundation audit has no exact PASS for required section: $pass" }
    }
    $configPath = Join-Path $script:RepositoryRoot $expectedPath
    if ((Get-FileSha256 $configPath) -cne '63adf50dffaf1143d0339510dc7203b56d857d7af2352fd9e5c308ed56ba67ab') { throw 'ConfigIssue.java changed from the reviewed sanitizer-only source hash.' }
    $configText = [System.IO.File]::ReadAllText($configPath)
    if ([regex]::Matches($configText, '(?i)credential(?:s)?').Count -ne 1 -or $configText -notmatch [regex]::Escape('lower.contains("credential")') -or $configText -notmatch '(?s)sanitizeRejectedValue.*?return REDACTED;') {
        throw 'The adjudicated credential literal is not the one exact sanitizeRejectedValue denylist context.'
    }
    [void](Assert-IndependentProductionSourceContract)
    return $true
}

function Copy-ArtifactAtomically {
    param([Parameter(Mandatory)][string] $Source, [Parameter(Mandatory)][string] $Destination, [scriptblock] $BeforeReplaceAction)
    $parent = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { [void](New-Item -ItemType Directory -Path $parent) }
    $temporary = Join-Path $parent ('.' + (Split-Path -Leaf $Destination) + '.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [System.IO.File]::Copy($Source, $temporary, $false)
        if ((Get-FileSha256 $temporary) -cne (Get-FileSha256 $Source)) { throw 'Atomic distribution staging hash mismatch.' }
        if ($BeforeReplaceAction) { & $BeforeReplaceAction }
        if (Test-Path -LiteralPath $Destination -PathType Leaf) {
            [System.IO.File]::Replace($temporary, $Destination, [System.Management.Automation.Language.NullString]::Value, $true)
        } else {
            [System.IO.File]::Move($temporary, $Destination)
        }
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) { [System.IO.File]::Delete($temporary) }
    }
}

function Get-UnitTestSummary {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $script:RepositoryRoot 'build\test-results\test') -File -Filter 'TEST-*.xml' -ErrorAction Stop)
    if ($files.Count -eq 0) { throw 'Fresh unit-test XML reports are missing.' }
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($file in $files) {
        [xml]$xml = [System.IO.File]::ReadAllText($file.FullName)
        $suite = $xml.testsuite
        $tests += [int]$suite.tests; $failures += [int]$suite.failures; $errors += [int]$suite.errors; $skipped += [int]$suite.skipped
    }
    if ($tests -le 0 -or $failures -ne 0 -or $errors -ne 0) { throw 'Fresh unit-test reports are empty or not green.' }
    return [pscustomobject]@{ Files=$files.Count; Tests=$tests; Failures=$failures; Errors=$errors; Skipped=$skipped }
}

function Get-GameTestSourceCount {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $script:RepositoryRoot 'src\gametest\java') -File -Recurse -Filter '*.java')
    $count = 0
    foreach ($file in $files) { $count += [regex]::Matches([System.IO.File]::ReadAllText($file.FullName), '@GameTest\b').Count }
    if ($count -le 0) { throw 'No Phase 2 GameTest anchors were found.' }
    return $count
}

function New-LectureEvidenceText {
    param(
        [Parameter(Mandatory)][string] $Hash,
        [Parameter(Mandatory)][string] $PreviousHash,
        [Parameter(Mandatory)] $Artifact,
        [Parameter(Mandatory)] $Archive,
        [Parameter(Mandatory)] $BuildResult,
        [Parameter(Mandatory)] $AuditResult,
        [Parameter(Mandatory)] $ServerResult,
        [Parameter(Mandatory)] $UnitSummary,
        [Parameter(Mandatory)][int] $GameTestCount
    )
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in @(
        '# Phase 2 Lecture Evidence',
        '',
        'Machine-produced public-safe facts for the exact fresh ordinary JAR. Automated PASS never stands in for client rendering, readability, audio, motion, model, or playability observation.',
        '',
        ('evidence_timestamp_utc: ' + [DateTime]::UtcNow.ToString('o')),
        'java_runtime: Eclipse Temurin 25.0.4+7 checksum-bound',
        'gradle_command: gradlew.bat pinned-jvm --offline clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle',
        'gradle_transaction_exit: 0',
        ('gradle_log_sha256: ' + (Get-StringSha256 $BuildResult.Combined)),
        'foundation_audit_command: powershell.exe scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath build/libs/developers-hell-0.1.0.jar',
        ('foundation_audit_exit: ' + $AuditResult.ExitCode),
        'foundation_audit_adjudication_exit: 0',
        'foundation_audit_adjudication: PASS - one exact ConfigIssue.sanitizeRejectedValue denylist literal at pinned source hash; no other raw finding',
        ('foundation_audit_log_sha256: ' + (Get-StringSha256 $AuditResult.Combined)),
        ('unit_test_report_files: ' + $UnitSummary.Files),
        ('unit_tests: ' + $UnitSummary.Tests),
        ('unit_failures: ' + $UnitSummary.Failures),
        ('unit_errors: ' + $UnitSummary.Errors),
        ('unit_skipped: ' + $UnitSummary.Skipped),
        ('gametest_anchors_executed_by_runGameTest: ' + $GameTestCount),
        ('ordinary_jar_size: ' + $Artifact.Size),
        ('ordinary_jar_entries: ' + $Archive.EntryCount),
        ('ordinary_jar_entries_sha256: ' + $Archive.EntriesSha256),
        'production_server_profile: local automated smoke; loopback; online-mode=false; query=false; rcon=false; no resource-pack URL',
        ('previous_distribution_sha256: ' + $PreviousHash),
        ('source_jar_sha256: ' + $Hash),
        ('build_jar_sha256: ' + $Hash),
        ('distribution_sha256: ' + $Hash),
        'hash_equality: source/build/dist hashes equal',
        'source_archive_audit: PASS - dependency/source/archive policy',
        'phase2_archive_audit: PASS - stable items/entities/recipe/advancement/lang/models/classes present; test/client-link/network/API/telemetry/credential residue absent; one license',
        'server_ready: PASS - DEVELOPERS_HELL_SERVER_FIRST_TICK_READY',
        'server_stop_cleanup_callback: PASS - DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE',
        'server_ordered_shutdown: PASS - FIRST_TICK_READY -> STOPPING_CLEANUP_COMPLETE -> Stopping server -> All dimensions are saved',
        'production_server_exit: 0',
        'clean_exit: PASS',
        ('owned_server_root_pid: ' + $ServerResult.RootPid),
        ('owned_child_count: ' + $ServerResult.CapturedCount),
        'owned_child_cleanup: PASS - clean; zero owned child residue',
        ('production_server_log_sha256: ' + $ServerResult.LogSha256),
        '',
        '## Automated validation rows',
        '',
        '| Automated ID | Existing green evidence | Status |',
        '|---|---|---|',
        '| 02-CFG-01 | DevHellConfigTest strict whole-file defaults/rejection matrix | PASS |',
        '| 02-STATE-01 | CampaignCodecTest and CampaignReducerTest monotonic/replay-safe persistence | PASS |',
        '| 02-GEO-01 | LectureGeometryTest and LectureStateMachineTest bounded deterministic geometry | PASS |',
        '| 02-ITEM-01 | ContractArenaGameTests, RetakeGameTests, RemoteGameTests transaction/cooldown cases | PASS |',
        '| 02-BOSS-01 | LectureStateMachineTest and LectureBossGameTests identity/acts/vulnerability | PASS |',
        '| 02-LIFE-01 | LectureLifecycleGameTests terminal/reload/orphan/server-stop cleanup matrix | PASS |',
        '| 02-REWARD-01 | CampaignReducerTest and RewardGameTests exactly-once/fallback/recovery cases | PASS |',
        '| 02-DISC-01 | FoundationGameTests recipe/advancement/localization/valid-desk discovery | PASS |',
        '| 02-GATE-01 | Fresh offline build, dependency/source/archive audit, and ordered dedicated-server clean stop | PASS |',
        '',
        '## Direct-client backstops',
        '',
        'These rows require a visible isolated Fabric 26.2 client run of this exact hash. No client was launched by this verifier.',
        '',
        '| Manual backstop ID | Direct observation still required | Status |',
        '|---|---|---|',
        '| MANUAL-UI-01 | Small/normal/large GUI scale and narrow-window boss/action clipping or overlap | PENDING |',
        '| MANUAL-I18N-02 | Held-out long localization wrapping for quiz plus fixed-budget boss/action strings | PENDING |',
        '| MANUAL-EFFECTS-03 | Normal/reduced-effects lane, pad, and ring geometry equivalence | PENDING |',
        '| MANUAL-ACCESS-04 | Muted audio/minimal particles preserve text and stable-shape completion cues | PENDING |',
        '| MANUAL-MOTION-05 | No camera shake, nausea, full-screen flash, strobe, or stale cleanup marker | PENDING |',
        '| MANUAL-MODELS-06 | Accepted vanilla-backed items and Professor/Homework silhouettes have no missing model | PENDING |',
        '| MANUAL-REMOTE-07 | Remote overlay, 20-second tooltip, recharge line, and ready cue stay recognizable without covering boss instructions | PENDING |'
    )) { [void]$lines.Add([string]$line) }
    return ($lines -join "`n") + "`n"
}

function Invoke-VerifyMode {
    param([Parameter(Mandatory)][string] $EvidenceFile, [Parameter(Mandatory)][string] $DistributionFile)
    $jdk = Get-VerifiedJdk
    $previousHash = if (Test-Path -LiteralPath $DistributionFile -PathType Leaf) { Get-FileSha256 $DistributionFile } else { 'absent' }
    $buildStartedUtc = [DateTime]::UtcNow
    $gradleArgs = Get-GradleArguments -Jdk $jdk -Tasks @('clean','test','runGameTest','auditDirectDependencies','build')
    $build = Invoke-BoundedBatch -Arguments $gradleArgs -Jdk $jdk -TimeoutSeconds 1200
    if ($build.ExitCode -ne 0 -or $build.Combined -notmatch 'BUILD SUCCESSFUL' -or $build.Combined -notmatch '(?m)> Task :runGameTest' -or $build.Combined -notmatch 'DEVELOPERS_HELL_DIRECT_DEPENDENCIES=') { throw 'Fresh offline Gradle transaction did not prove build, GameTest, and dependency anchors.' }
    $jar = Resolve-SafeRepositoryPath -Path $script:BuildJarRelativePath -ExpectedRelativePath $script:BuildJarRelativePath
    $artifact = Assert-FreshArtifact -JarPath $jar -BuildStartedUtc $buildStartedUtc
    $unit = Get-UnitTestSummary
    $gameTests = Get-GameTestSourceCount
    $audit = Invoke-BoundedFoundationAudit -Jdk $jdk -JarPath $jar
    [void](Assert-FoundationAuditAdjudication -AuditResult $audit)
    $archive = Get-LectureArchiveContract -JarPath $jar
    if ($archive.Sha256 -cne $artifact.Sha256) { throw 'Fresh-artifact and Phase 2 archive hashes disagree.' }
    $server = Invoke-BoundedProductionServer -Jdk $jdk -ArtifactPath $jar
    if ((Get-FileSha256 $jar) -cne $artifact.Sha256) { throw 'Fresh build JAR changed after all candidate gates.' }

    Copy-ArtifactAtomically -Source $jar -Destination $DistributionFile
    $distributionHash = Get-FileSha256 $DistributionFile
    if ($distributionHash -cne $artifact.Sha256) { throw 'Promoted distribution does not equal the inspected source/build JAR.' }
    $evidenceText = New-LectureEvidenceText -Hash $distributionHash -PreviousHash $previousHash -Artifact $artifact -Archive $archive -BuildResult $build -AuditResult $audit -ServerResult $server -UnitSummary $unit -GameTestCount $gameTests
    [void](Assert-EvidenceContract -EvidenceText $evidenceText -DistributionHash $distributionHash)
    [System.IO.File]::WriteAllText($EvidenceFile, $evidenceText, [System.Text.UTF8Encoding]::new($false))
    Write-Host "PASS: fresh Phase 2 artifact promoted after all gates; server root PID $($server.RootPid), captured owned children $($server.CapturedCount), SHA-256 $distributionHash"
}

function Invoke-ValidateEvidenceMode {
    param([Parameter(Mandatory)][string] $EvidenceFile, [Parameter(Mandatory)][string] $DistributionFile)
    [void](Get-VerifiedJdk)
    if (-not (Test-Path -LiteralPath $EvidenceFile -PathType Leaf)) { throw 'Phase 2 lecture evidence is missing.' }
    if (-not (Test-Path -LiteralPath $DistributionFile -PathType Leaf)) { throw 'Phase 2 distribution JAR is missing.' }
    $distributionHash = Get-FileSha256 $DistributionFile
    $buildJar = Resolve-SafeRepositoryPath -Path $script:BuildJarRelativePath -ExpectedRelativePath $script:BuildJarRelativePath
    if ((Get-FileSha256 $buildJar) -cne $distributionHash) { throw 'Current build and distribution hashes are not equal.' }
    $text = [System.IO.File]::ReadAllText($EvidenceFile)
    [void](Assert-EvidenceContract -EvidenceText $text -DistributionHash $distributionHash)
    [void](Get-LectureArchiveContract -JarPath $DistributionFile)
    Write-Host "PASS: lecture evidence validates exact build/dist SHA-256 $distributionHash with seven manual rows PENDING"
}

try {
    $selectedModes = @(@($SelfCheck, $Verify, $ValidateEvidence) | Where-Object { [bool] $_ })
    if ($selectedModes.Count -ne 1) {
        throw 'Select exactly one mode: -SelfCheck, -Verify, or -ValidateEvidence'
    }
    $evidenceInput = if ([string]::IsNullOrWhiteSpace($EvidencePath)) { $script:DefaultEvidenceRelativePath } else { $EvidencePath }
    $distributionInput = if ([string]::IsNullOrWhiteSpace($DistributionPath)) { $script:DefaultDistributionRelativePath } else { $DistributionPath }
    $evidenceFile = Resolve-SafeRepositoryPath -Path $evidenceInput -ExpectedRelativePath $script:DefaultEvidenceRelativePath -AllowMissingLeaf
    $distributionFile = Resolve-SafeRepositoryPath -Path $distributionInput -ExpectedRelativePath $script:DefaultDistributionRelativePath -AllowMissingLeaf
    if ($SelfCheck) { Invoke-SelfCheckMode }
    if ($Verify) { Invoke-VerifyMode -EvidenceFile $evidenceFile -DistributionFile $distributionFile }
    if ($ValidateEvidence) { Invoke-ValidateEvidenceMode -EvidenceFile $evidenceFile -DistributionFile $distributionFile }
    Write-Host "PASS: Developer's Hell lecture verification harness completed"
    exit 0
}
catch {
    Write-Error ("FAIL: Developer's Hell lecture verification harness: " + $_.Exception.Message)
    exit 1
}
