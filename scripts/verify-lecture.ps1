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
$script:TestManifestRelativePath = 'scripts/lecture-test-manifest.json'
$script:UnitTestReportRelativePath = 'build/test-results/test'
$script:GameTestReportRelativePath = 'build/test-results/gametest/TEST-gametest.xml'
$script:EvidenceBlockStart = '<!-- DEVELOPERS_HELL_PHASE2_EVIDENCE_V1_BEGIN -->'
$script:EvidenceBlockEnd = '<!-- DEVELOPERS_HELL_PHASE2_EVIDENCE_V1_END -->'
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

function Invoke-GitRepositoryQuery {
    param([Parameter(Mandatory)][AllowEmptyCollection()][string[]] $Arguments)
    $git = (Get-Command git.exe -ErrorAction Stop).Source
    Push-Location -LiteralPath $script:RepositoryRoot
    try {
        $output = @(& $git @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($exitCode -ne 0) { throw 'Git repository identity query failed.' }
    return @($output)
}

function Get-RepositorySourceIdentity {
    $formatLines = @(Invoke-GitRepositoryQuery -Arguments @('rev-parse','--show-object-format'))
    $commitLines = @(Invoke-GitRepositoryQuery -Arguments @('rev-parse','--verify','HEAD'))
    $treeLines = @(Invoke-GitRepositoryQuery -Arguments @('rev-parse','--verify','HEAD^{tree}'))
    if ($formatLines.Count -ne 1 -or $commitLines.Count -ne 1 -or $treeLines.Count -ne 1) { throw 'Git repository identity output is ambiguous.' }
    $format = [string]$formatLines[0]
    $commit = [string]$commitLines[0]
    $tree = [string]$treeLines[0]
    $hashPattern = if ($format -ceq 'sha1') { '^[0-9a-f]{40}$' } elseif ($format -ceq 'sha256') { '^[0-9a-f]{64}$' } else { throw 'Git repository object format is unsupported.' }
    if ($commit -notmatch $hashPattern -or $tree -notmatch $hashPattern) { throw 'Git repository commit or tree identity is not canonical.' }
    return [pscustomobject]@{ ObjectFormat=$format; Commit=$commit; Tree=$tree }
}

function Assert-EmptySourceStatus {
    param([Parameter(Mandatory)][AllowEmptyCollection()][string[]] $Lines)
    if (@($Lines | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count -ne 0) {
        throw 'Verification requires a clean tracked and untracked source worktree.'
    }
    return $true
}

function Get-CleanSourceSnapshot {
    # Keep the repository's reviewed .gitignore for generated outputs, but disable
    # user-global excludes so an untracked source file cannot disappear from status.
    $status = @(Invoke-GitRepositoryQuery -Arguments @('-c','core.excludesFile=','status','--porcelain=v1','--untracked-files=all'))
    [void](Assert-EmptySourceStatus -Lines $status)
    $identity = Get-RepositorySourceIdentity
    return [pscustomobject]@{ ObjectFormat=$identity.ObjectFormat; Commit=$identity.Commit; Tree=$identity.Tree; Status='CLEAN' }
}

function Assert-SourceSnapshotStillClean {
    param([Parameter(Mandatory)] $Expected)
    $actual = Get-CleanSourceSnapshot
    if ([string]$actual.ObjectFormat -cne [string]$Expected.ObjectFormat -or [string]$actual.Commit -cne [string]$Expected.Commit -or [string]$actual.Tree -cne [string]$Expected.Tree) {
        throw 'Source commit or tree changed during the verification transaction.'
    }
    return $true
}

function Assert-RecordedSourceIdentity {
    param(
        [Parameter(Mandatory)][string] $ObjectFormat,
        [Parameter(Mandatory)][string] $Commit,
        [Parameter(Mandatory)][string] $Tree
    )
    $current = Get-RepositorySourceIdentity
    if ($ObjectFormat -cne $current.ObjectFormat) { throw 'Evidence source object format does not match this repository.' }
    $hashPattern = if ($ObjectFormat -ceq 'sha1') { '^[0-9a-f]{40}$' } elseif ($ObjectFormat -ceq 'sha256') { '^[0-9a-f]{64}$' } else { throw 'Evidence source object format is unsupported.' }
    if ($Commit -notmatch $hashPattern -or $Tree -notmatch $hashPattern) { throw 'Evidence source commit or tree is not canonical.' }
    $commitType = @(Invoke-GitRepositoryQuery -Arguments @('cat-file','-t',$Commit))
    $recordedTree = @(Invoke-GitRepositoryQuery -Arguments @('rev-parse','--verify',($Commit + '^{tree}')))
    $treeType = @(Invoke-GitRepositoryQuery -Arguments @('cat-file','-t',$Tree))
    if ($commitType.Count -ne 1 -or $commitType[0] -cne 'commit' -or $recordedTree.Count -ne 1 -or $recordedTree[0] -cne $Tree -or $treeType.Count -ne 1 -or $treeType[0] -cne 'tree') {
        throw 'Evidence source commit does not resolve to the recorded tree.'
    }
    return $true
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

function Assert-ExactProperties {
    param(
        [Parameter(Mandatory)] $Value,
        [Parameter(Mandatory)][string[]] $Expected,
        [Parameter(Mandatory)][string] $Label
    )
    $actual = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    $missing = @($Expected | Where-Object { $_ -cnotin $actual })
    $unknown = @($actual | Where-Object { $_ -cnotin $Expected })
    if ($missing.Count -ne 0 -or $unknown.Count -ne 0 -or $actual.Count -ne $Expected.Count) {
        throw "$Label has missing, duplicate, or unknown properties."
    }
}

function Assert-UniqueStrings {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]] $Values,
        [Parameter(Mandatory)][string] $Label
    )
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($value in $Values) {
        if ([string]::IsNullOrWhiteSpace($value) -or -not $seen.Add($value)) {
            throw "$Label contains an empty or duplicate value."
        }
    }
}

function Get-CanonicalReceiptHash {
    param([Parameter(Mandatory)][AllowEmptyCollection()][string[]] $Lines)
    $ordered = @($Lines)
    [System.Array]::Sort($ordered, [System.StringComparer]::Ordinal)
    return Get-StringSha256 -Value (($ordered -join "`n") + "`n")
}

function Get-LectureTestManifest {
    $path = Resolve-SafeRepositoryPath -Path $script:TestManifestRelativePath -ExpectedRelativePath $script:TestManifestRelativePath
    $raw = [System.IO.File]::ReadAllText($path) | ConvertFrom-Json
    Assert-ExactProperties -Value $raw -Expected @('schema_version','unit_suites','gametest_suites','validation_rows') -Label 'Test manifest'
    if ([int]$raw.schema_version -ne 1) { throw 'Test manifest schema version is unsupported.' }

    $unitGroups = @{}
    $expectedUnit = [System.Collections.Generic.List[string]]::new()
    foreach ($suiteProperty in @($raw.unit_suites.PSObject.Properties)) {
        $suite = [string]$suiteProperty.Name
        if ($suite -notmatch '^dev[.]developershell(?:[.][A-Za-z][A-Za-z0-9_]*)+[.]?[A-Za-z][A-Za-z0-9_]*Test$') {
            throw 'Test manifest has an invalid unit suite name.'
        }
        $methods = @($suiteProperty.Value | ForEach-Object { [string]$_ })
        if ($methods.Count -eq 0) { throw "Test manifest unit suite is empty: $suite" }
        Assert-UniqueStrings -Values $methods -Label "Test manifest unit suite $suite"
        $ids = [System.Collections.Generic.List[string]]::new()
        foreach ($method in $methods) {
            if ($method -notmatch '^[A-Za-z][A-Za-z0-9_]*[(][)]$') { throw "Test manifest has an invalid unit method in $suite." }
            $id = $suite + '#' + $method
            $ids.Add($id)
            $expectedUnit.Add($id)
        }
        $unitGroups[$suite] = @($ids)
    }
    if ($unitGroups.Count -eq 0) { throw 'Test manifest has no unit suites.' }
    Assert-UniqueStrings -Values @($expectedUnit) -Label 'Test manifest unit IDs'

    $gameTestGroups = @{}
    $expectedGameTests = [System.Collections.Generic.List[string]]::new()
    foreach ($groupProperty in @($raw.gametest_suites.PSObject.Properties)) {
        $group = [string]$groupProperty.Name
        if ($group -notmatch '^[a-z][a-z0-9_]*$') { throw 'Test manifest has an invalid GameTest group name.' }
        $ids = @($groupProperty.Value | ForEach-Object { [string]$_ })
        if ($ids.Count -eq 0) { throw "Test manifest GameTest group is empty: $group" }
        Assert-UniqueStrings -Values $ids -Label "Test manifest GameTest group $group"
        foreach ($id in $ids) {
            if ($id -notmatch '^[a-z0-9_.-]+:[a-z0-9_./-]+$') { throw "Test manifest has an invalid GameTest ID in $group." }
            $expectedGameTests.Add($id)
        }
        $gameTestGroups[$group] = $ids
    }
    if ($gameTestGroups.Count -eq 0) { throw 'Test manifest has no GameTest groups.' }
    Assert-UniqueStrings -Values @($expectedGameTests) -Label 'Test manifest GameTest IDs'
    if (@($gameTestGroups['vanilla_baseline']).Count -ne 1 -or [string]$gameTestGroups['vanilla_baseline'][0] -cne 'minecraft:always_pass') {
        throw 'Test manifest must pin the Fabric runner vanilla always-pass baseline explicitly.'
    }

    $allowedGates = @('gradle_transaction','foundation_audit','source_archive','phase2_archive','production_server')
    $rows = [System.Collections.Generic.List[object]]::new()
    $rowIds = [System.Collections.Generic.List[string]]::new()
    $referencedUnitGroups = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $referencedGameTestGroups = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($rawRow in @($raw.validation_rows)) {
        Assert-ExactProperties -Value $rawRow -Expected @('id','description','unit_suites','gametest_suites','gates') -Label 'Test manifest validation row'
        $id = [string]$rawRow.id
        $description = [string]$rawRow.description
        $unitSuites = @($rawRow.unit_suites | ForEach-Object { [string]$_ })
        $gameTestSuites = @($rawRow.gametest_suites | ForEach-Object { [string]$_ })
        $gates = @($rawRow.gates | ForEach-Object { [string]$_ })
        if ($id -cnotin $script:RequiredAutomatedRows -or $description -notmatch '^[a-z0-9 ,_-]+$') {
            throw 'Test manifest validation row identity or description is invalid.'
        }
        Assert-UniqueStrings -Values $unitSuites -Label "Validation row $id unit suites"
        Assert-UniqueStrings -Values $gameTestSuites -Label "Validation row $id GameTest suites"
        Assert-UniqueStrings -Values $gates -Label "Validation row $id gates"
        foreach ($suite in $unitSuites) {
            if (-not $unitGroups.ContainsKey($suite)) { throw "Validation row $id references an unknown unit suite." }
            [void]$referencedUnitGroups.Add($suite)
        }
        foreach ($group in $gameTestSuites) {
            if (-not $gameTestGroups.ContainsKey($group)) { throw "Validation row $id references an unknown GameTest group." }
            [void]$referencedGameTestGroups.Add($group)
        }
        foreach ($gate in $gates) {
            if ($gate -cnotin $allowedGates) { throw "Validation row $id references an unknown gate." }
        }
        if ($unitSuites.Count + $gameTestSuites.Count + $gates.Count -eq 0) { throw "Validation row $id has no receipts." }
        $rowIds.Add($id)
        $rows.Add([pscustomobject]@{ Id=$id; Description=$description; UnitSuites=$unitSuites; GameTestSuites=$gameTestSuites; Gates=$gates })
    }
    Assert-UniqueStrings -Values @($rowIds) -Label 'Test manifest validation row IDs'
    if ($rowIds.Count -ne $script:RequiredAutomatedRows.Count) { throw 'Test manifest validation row count is not exact.' }
    for ($index = 0; $index -lt $script:RequiredAutomatedRows.Count; $index++) {
        if ($rowIds[$index] -cne $script:RequiredAutomatedRows[$index]) { throw 'Test manifest validation row order is not canonical.' }
    }
    foreach ($suite in @($unitGroups.Keys)) {
        if (-not $referencedUnitGroups.Contains([string]$suite)) { throw "Unit suite has no explicit validation-row receipt group: $suite" }
    }
    foreach ($group in @($gameTestGroups.Keys)) {
        if (-not $referencedGameTestGroups.Contains([string]$group)) { throw "GameTest group has no explicit validation-row receipt group: $group" }
    }

    return [pscustomobject]@{
        Path = $path
        Sha256 = Get-FileSha256 -LiteralPath $path
        UnitGroups = $unitGroups
        GameTestGroups = $gameTestGroups
        ExpectedUnitIds = @($expectedUnit)
        ExpectedGameTestIds = @($expectedGameTests)
        Rows = @($rows)
    }
}

function Read-SafeXmlDocument {
    param([Parameter(Mandatory)][string] $LiteralPath)
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = [System.Xml.XmlReader]::Create($LiteralPath, $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
        return $document
    } finally {
        $reader.Dispose()
    }
}

function Assert-ExactExecutedIds {
    param(
        [Parameter(Mandatory)][string[]] $Actual,
        [Parameter(Mandatory)][string[]] $Expected,
        [Parameter(Mandatory)][string] $Label
    )
    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($id in $Actual) {
        if ([string]::IsNullOrWhiteSpace($id) -or -not $actualSet.Add($id)) { throw "$Label receipt contains an empty or duplicate executed ID." }
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($id in $Expected) {
        if (-not $expectedSet.Add($id)) { throw "$Label manifest contains a duplicate expected ID." }
    }
    $missing = @($Expected | Where-Object { -not $actualSet.Contains($_) })
    $unexpected = @($Actual | Where-Object { -not $expectedSet.Contains($_) })
    if ($missing.Count -ne 0 -or $unexpected.Count -ne 0 -or $Actual.Count -ne $Expected.Count) {
        throw "$Label receipt does not exactly equal the reviewed manifest (missing=$($missing.Count), unexpected=$($unexpected.Count))."
    }
}

function Get-TestExecutionReceipts {
    param(
        [Parameter(Mandatory)] $Manifest,
        [string] $UnitReportDirectory,
        [string] $GameTestReportPath
    )
    if ([string]::IsNullOrWhiteSpace($UnitReportDirectory)) { $UnitReportDirectory = Join-Path $script:RepositoryRoot $script:UnitTestReportRelativePath }
    if ([string]::IsNullOrWhiteSpace($GameTestReportPath)) { $GameTestReportPath = Join-Path $script:RepositoryRoot $script:GameTestReportRelativePath }
    $unitFiles = @(Get-ChildItem -LiteralPath $UnitReportDirectory -File -Filter 'TEST-*.xml' -ErrorAction Stop | Sort-Object Name)
    if ($unitFiles.Count -eq 0) { throw 'Fresh unit-test XML receipts are missing.' }
    $unitIds = [System.Collections.Generic.List[string]]::new()
    $unitFailures = 0; $unitErrors = 0; $unitSkipped = 0
    foreach ($file in $unitFiles) {
        $document = Read-SafeXmlDocument -LiteralPath $file.FullName
        $suite = $document.DocumentElement
        if ($null -eq $suite -or
            [string]$suite.PSBase.LocalName -cne 'testsuite' -or
            -not [string]::IsNullOrEmpty([string]$suite.PSBase.NamespaceURI)) {
            throw 'Unit receipt root must be one unnamespaced testsuite.'
        }
        $cases = @($suite.SelectNodes('./testcase'))
        foreach ($attribute in @('tests','failures','errors','skipped')) {
            if (-not $suite.HasAttribute($attribute) -or $suite.GetAttribute($attribute) -notmatch '^[0-9]+$') { throw "Unit receipt lacks a numeric $attribute count." }
        }
        $caseFailures = @($suite.SelectNodes('./testcase/failure')).Count
        $caseErrors = @($suite.SelectNodes('./testcase/error')).Count
        $caseSkipped = @($suite.SelectNodes('./testcase/skipped')).Count
        if ([int]$suite.GetAttribute('tests') -ne $cases.Count -or [int]$suite.GetAttribute('failures') -ne $caseFailures -or [int]$suite.GetAttribute('errors') -ne $caseErrors -or [int]$suite.GetAttribute('skipped') -ne $caseSkipped) {
            throw 'Unit receipt declared counts disagree with testcase nodes.'
        }
        $unitFailures += $caseFailures; $unitErrors += $caseErrors; $unitSkipped += $caseSkipped
        foreach ($case in $cases) {
            $className = [string]$case.GetAttribute('classname')
            $methodName = [string]$case.GetAttribute('name')
            if ([string]::IsNullOrWhiteSpace($className) -or [string]::IsNullOrWhiteSpace($methodName)) { throw 'Unit receipt testcase identity is incomplete.' }
            $unitIds.Add($className + '#' + $methodName)
        }
    }
    if ($unitFailures -ne 0 -or $unitErrors -ne 0 -or $unitSkipped -ne 0) { throw 'Unit receipt contains a failed, errored, or skipped testcase.' }
    Assert-ExactExecutedIds -Actual @($unitIds) -Expected @($Manifest.ExpectedUnitIds) -Label 'Unit'

    if (-not (Test-Path -LiteralPath $GameTestReportPath -PathType Leaf)) { throw 'Fresh GameTest XML receipt is missing.' }
    $gameDocument = Read-SafeXmlDocument -LiteralPath $GameTestReportPath
    $gameCases = @($gameDocument.SelectNodes('//testcase'))
    if ($gameCases.Count -eq 0) { throw 'Fresh GameTest XML receipt has no testcase nodes.' }
    $gameFailures = @($gameDocument.SelectNodes('//testcase/failure')).Count
    $gameErrors = @($gameDocument.SelectNodes('//testcase/error')).Count
    $gameSkipped = @($gameDocument.SelectNodes('//testcase/skipped')).Count
    if ($gameFailures -ne 0 -or $gameErrors -ne 0 -or $gameSkipped -ne 0) { throw 'GameTest receipt contains a failed, errored, or skipped testcase.' }
    $gameTestIds = [System.Collections.Generic.List[string]]::new()
    foreach ($case in $gameCases) {
        $id = [string]$case.GetAttribute('name')
        if ([string]::IsNullOrWhiteSpace($id)) { throw 'GameTest receipt testcase identity is incomplete.' }
        $gameTestIds.Add($id)
    }
    Assert-ExactExecutedIds -Actual @($gameTestIds) -Expected @($Manifest.ExpectedGameTestIds) -Label 'GameTest'

    return [pscustomobject]@{
        UnitFiles = $unitFiles.Count
        UnitIds = @($unitIds)
        UnitCount = $unitIds.Count
        UnitFailures = $unitFailures
        UnitErrors = $unitErrors
        UnitSkipped = $unitSkipped
        UnitSha256 = Get-CanonicalReceiptHash -Lines @($unitIds | ForEach-Object { 'unit:' + $_ })
        GameTestFiles = 1
        GameTestIds = @($gameTestIds)
        GameTestCount = $gameTestIds.Count
        GameTestFailures = $gameFailures
        GameTestErrors = $gameErrors
        GameTestSkipped = $gameSkipped
        GameTestSha256 = Get-CanonicalReceiptHash -Lines @($gameTestIds | ForEach-Object { 'gametest:' + $_ })
    }
}

function Get-ValidationRowReceipts {
    param(
        [Parameter(Mandatory)] $Manifest,
        [Parameter(Mandatory)] $TestReceipts,
        [Parameter(Mandatory)][System.Collections.IDictionary] $GateResults
    )
    $actualUnit = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($id in @($TestReceipts.UnitIds)) { [void]$actualUnit.Add([string]$id) }
    $actualGameTests = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($id in @($TestReceipts.GameTestIds)) { [void]$actualGameTests.Add([string]$id) }
    $result = [System.Collections.Generic.List[object]]::new()
    foreach ($row in @($Manifest.Rows)) {
        $lines = [System.Collections.Generic.List[string]]::new()
        $unitCount = 0; $gameTestCount = 0
        foreach ($suite in @($row.UnitSuites)) {
            foreach ($id in @($Manifest.UnitGroups[$suite])) {
                if (-not $actualUnit.Contains([string]$id)) { throw "Validation row $($row.Id) lacks a measured unit receipt." }
                $lines.Add('unit:' + [string]$id); $unitCount++
            }
        }
        foreach ($group in @($row.GameTestSuites)) {
            foreach ($id in @($Manifest.GameTestGroups[$group])) {
                if (-not $actualGameTests.Contains([string]$id)) { throw "Validation row $($row.Id) lacks a measured GameTest receipt." }
                $lines.Add('gametest:' + [string]$id); $gameTestCount++
            }
        }
        foreach ($gate in @($row.Gates)) {
            if (-not $GateResults.Contains($gate) -or [bool]$GateResults[$gate] -ne $true) { throw "Validation row $($row.Id) lacks a green measured gate: $gate" }
            $lines.Add('gate:' + [string]$gate + '=PASS')
        }
        if ($lines.Count -eq 0) { throw "Validation row $($row.Id) has no measured receipts." }
        $result.Add([pscustomobject]@{
            Id = $row.Id
            Description = $row.Description
            UnitCount = $unitCount
            GameTestCount = $gameTestCount
            Gates = if (@($row.Gates).Count -eq 0) { 'none' } else { @($row.Gates) -join ',' }
            Sha256 = Get-CanonicalReceiptHash -Lines @($lines)
            Status = 'PASS'
        })
    }
    return @($result)
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
    param([Parameter(Mandatory)][string] $JarPath, [switch] $AllowTransactionStageName)
    $leafName = Split-Path -Leaf $JarPath
    $stageNamePattern = '^[.]' + [regex]::Escape($script:ExpectedDistributionName) + '[.][0-9a-f]{32}[.]stage$'
    if ($leafName -cne $script:ExpectedDistributionName -and
        (-not $AllowTransactionStageName -or $leafName -notmatch $stageNamePattern)) {
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
        [Parameter(Mandatory)][string] $DistributionHash,
        [Parameter(Mandatory)] $Manifest,
        [Parameter(Mandatory)] $TestReceipts,
        [Parameter(Mandatory)] $ValidationRows,
        $ExpectedSourceSnapshot
    )
    if ($DistributionHash -notmatch '^[0-9a-fA-F]{64}$') { throw 'Distribution hash is not SHA-256.' }
    $normalizedHash = $DistributionHash.ToLowerInvariant()
    $fields = Get-StructuredEvidenceFields -Text $EvidenceText
    if ([string]$fields['evidence_schema'] -cne 'developers_hell_phase2_v1') { throw 'Evidence schema identity is not exact.' }
    $timestamp = [string]$fields['evidence_timestamp_utc']
    if ($timestamp -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[.]\d{7}Z$') { throw 'Evidence timestamp is not canonical UTC round-trip format.' }
    try { [void][DateTime]::ParseExact($timestamp, 'o', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind) }
    catch { throw 'Evidence timestamp is not a valid UTC timestamp.' }
    $sourceObjectFormat = [string]$fields['source_object_format']
    $sourceCommit = [string]$fields['source_commit']
    $sourceTree = [string]$fields['source_tree']
    if ([string]$fields['source_worktree_status'] -cne 'CLEAN') { throw 'Evidence source worktree status is not exactly CLEAN.' }
    [void](Assert-RecordedSourceIdentity -ObjectFormat $sourceObjectFormat -Commit $sourceCommit -Tree $sourceTree)
    if ($null -ne $ExpectedSourceSnapshot -and
        ($sourceObjectFormat -cne [string]$ExpectedSourceSnapshot.ObjectFormat -or $sourceCommit -cne [string]$ExpectedSourceSnapshot.Commit -or $sourceTree -cne [string]$ExpectedSourceSnapshot.Tree -or [string]$ExpectedSourceSnapshot.Status -cne 'CLEAN')) {
        throw 'Evidence source identity differs from the clean verification snapshot.'
    }
    $exactFields = [ordered]@{
        java_runtime = 'Eclipse Temurin 25.0.4+7 checksum-bound'
        gradle_command = 'gradlew.bat pinned-jvm --offline clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle'
        gradle_transaction_exit = '0'
        foundation_audit_command = 'powershell.exe scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath build/libs/developers-hell-0.1.0.jar'
        foundation_audit_exit = '0'
        foundation_audit_status = 'PASS'
        production_server_profile = 'local_automated_loopback_offline_no_query_no_rcon_no_resource_pack'
        hash_equality_status = 'PASS'
        hash_equality_detail = 'source_build_distribution_sha256_equal'
        source_archive_audit_status = 'PASS'
        source_archive_audit_detail = 'dependency_source_archive_policy'
        phase2_archive_audit_status = 'PASS'
        phase2_archive_audit_detail = 'production_contract_present_forbidden_residue_absent'
        server_ready_status = 'PASS'
        server_ready_detail = $script:ExpectedReadyMarker
        server_stop_cleanup_status = 'PASS'
        server_stop_cleanup_detail = $script:ExpectedStopCleanupMarker
        server_ordered_shutdown_status = 'PASS'
        server_ordered_shutdown_detail = 'first_tick_then_cleanup_then_stop_then_all_dimensions_saved'
        production_server_exit = '0'
        clean_exit_status = 'PASS'
        clean_exit_detail = 'production_server_exit_zero'
        owned_child_cleanup_status = 'PASS'
        owned_child_cleanup_detail = 'zero_owned_child_residue'
    }
    foreach ($entry in $exactFields.GetEnumerator()) {
        if ([string]$fields[[string]$entry.Key] -cne [string]$entry.Value) { throw "Evidence field is not the exact allowed value: $($entry.Key)" }
    }
    foreach ($field in @('source_jar_sha256','build_jar_sha256','distribution_sha256')) {
        if ([string]$fields[$field] -cne $normalizedHash) { throw "Evidence hash mismatch: $field" }
    }
    foreach ($field in @('gradle_log_sha256','foundation_audit_log_sha256','ordinary_jar_entries_sha256','production_server_log_sha256')) {
        if ([string]$fields[$field] -notmatch '^[0-9a-f]{64}$') { throw "Evidence hash field is not canonical SHA-256: $field" }
    }
    if ([string]$fields['previous_distribution_sha256'] -cne 'absent' -and [string]$fields['previous_distribution_sha256'] -notmatch '^[0-9a-f]{64}$') {
        throw 'Previous distribution hash is neither absent nor canonical SHA-256.'
    }
    foreach ($field in @('ordinary_jar_size','ordinary_jar_entries','owned_server_root_pid')) {
        if ([string]$fields[$field] -notmatch '^[1-9][0-9]*$') { throw "Evidence positive integer field is invalid: $field" }
    }
    if ([string]$fields['owned_child_count'] -notmatch '^[0-9]+$') { throw 'Evidence owned-child count is invalid.' }
    $receiptMarkers = [ordered]@{
        test_manifest_sha256 = [string]$Manifest.Sha256
        unit_test_report_files = [string]$TestReceipts.UnitFiles
        unit_receipt_count = [string]$TestReceipts.UnitCount
        unit_receipt_failures = [string]$TestReceipts.UnitFailures
        unit_receipt_errors = [string]$TestReceipts.UnitErrors
        unit_receipt_skipped = [string]$TestReceipts.UnitSkipped
        unit_receipt_sha256 = [string]$TestReceipts.UnitSha256
        gametest_report_files = [string]$TestReceipts.GameTestFiles
        gametest_receipt_count = [string]$TestReceipts.GameTestCount
        gametest_receipt_failures = [string]$TestReceipts.GameTestFailures
        gametest_receipt_errors = [string]$TestReceipts.GameTestErrors
        gametest_receipt_skipped = [string]$TestReceipts.GameTestSkipped
        gametest_receipt_sha256 = [string]$TestReceipts.GameTestSha256
    }
    foreach ($entry in $receiptMarkers.GetEnumerator()) {
        if ([string]$fields[[string]$entry.Key] -cne [string]$entry.Value) {
            throw "Evidence execution receipt mismatch: $($entry.Key)"
        }
    }
    $rows = @($ValidationRows)
    if ($rows.Count -ne $script:RequiredAutomatedRows.Count) { throw 'Evidence validation-row receipt count is not exact.' }
    foreach ($row in $rows) {
        if ([string]$row.Status -cne 'PASS' -or [string]$row.Sha256 -notmatch '^[0-9a-f]{64}$') { throw "Validation row receipt is not green and canonical: $($row.Id)" }
        $receipt = 'unit=' + $row.UnitCount + '; gametest=' + $row.GameTestCount + '; gates=' + $row.Gates + '; receipt_sha256=' + $row.Sha256
        $expectedLine = '| ' + $row.Id + ' | ' + $row.Description + ' | ' + $receipt + ' | PASS |'
        $matches = [regex]::Matches($EvidenceText, '(?m)^' + [regex]::Escape($expectedLine) + '\r?$')
        if ($matches.Count -ne 1) { throw "Automated evidence row must match its measured receipt exactly: $($row.Id)" }
    }
    $allAutomated = [regex]::Matches($EvidenceText, '(?m)^\|[ \t]+02-[A-Z]+-[0-9]+[ \t]+\|')
    if ($allAutomated.Count -ne $script:RequiredAutomatedRows.Count) { throw 'Evidence must contain exactly the nine reviewed automated receipt rows.' }
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

function Get-ExpectedStructuredEvidenceFields {
    return @(
        'evidence_schema',
        'evidence_timestamp_utc',
        'source_object_format',
        'source_commit',
        'source_tree',
        'source_worktree_status',
        'java_runtime',
        'gradle_command',
        'gradle_transaction_exit',
        'gradle_log_sha256',
        'foundation_audit_command',
        'foundation_audit_exit',
        'foundation_audit_status',
        'foundation_audit_log_sha256',
        'test_manifest_sha256',
        'unit_test_report_files',
        'unit_receipt_count',
        'unit_receipt_failures',
        'unit_receipt_errors',
        'unit_receipt_skipped',
        'unit_receipt_sha256',
        'gametest_report_files',
        'gametest_receipt_count',
        'gametest_receipt_failures',
        'gametest_receipt_errors',
        'gametest_receipt_skipped',
        'gametest_receipt_sha256',
        'ordinary_jar_size',
        'ordinary_jar_entries',
        'ordinary_jar_entries_sha256',
        'production_server_profile',
        'previous_distribution_sha256',
        'source_jar_sha256',
        'build_jar_sha256',
        'distribution_sha256',
        'hash_equality_status',
        'hash_equality_detail',
        'source_archive_audit_status',
        'source_archive_audit_detail',
        'phase2_archive_audit_status',
        'phase2_archive_audit_detail',
        'server_ready_status',
        'server_ready_detail',
        'server_stop_cleanup_status',
        'server_stop_cleanup_detail',
        'server_ordered_shutdown_status',
        'server_ordered_shutdown_detail',
        'production_server_exit',
        'clean_exit_status',
        'clean_exit_detail',
        'owned_server_root_pid',
        'owned_child_count',
        'owned_child_cleanup_status',
        'owned_child_cleanup_detail',
        'production_server_log_sha256'
    )
}

function Get-StructuredEvidenceFields {
    param([Parameter(Mandatory)][string] $Text)
    if ([regex]::Matches($Text, '(?m)^' + [regex]::Escape($script:EvidenceBlockStart) + '\s*$').Count -ne 1 -or
        [regex]::Matches($Text, '(?m)^' + [regex]::Escape($script:EvidenceBlockEnd) + '\s*$').Count -ne 1) {
        throw 'Evidence machine-block delimiters must each appear exactly once.'
    }
    $pattern = '(?ms)^' + [regex]::Escape($script:EvidenceBlockStart) + '\r?\n(?<body>.*?)^' + [regex]::Escape($script:EvidenceBlockEnd) + '\s*$'
    $blocks = [regex]::Matches($Text, $pattern)
    if ($blocks.Count -ne 1) { throw 'Evidence must contain exactly one structured machine block.' }
    $block = $blocks[0]
    $body = $block.Groups['body'].Value.TrimEnd("`r", "`n")
    $lines = @([regex]::Split($body, '\r?\n'))
    $expected = Get-ExpectedStructuredEvidenceFields
    if ($lines.Count -ne $expected.Count) { throw 'Structured evidence field count is not exact.' }
    $fields = [ordered]@{}
    for ($index = 0; $index -lt $lines.Count; $index++) {
        $match = [regex]::Match($lines[$index], '^(?<name>[a-z][a-z0-9_]*): (?<value>[^\r\n]+)$')
        if (-not $match.Success) { throw 'Structured evidence line does not match the exact field grammar.' }
        $name = $match.Groups['name'].Value
        $value = $match.Groups['value'].Value
        if ($name -cne $expected[$index]) { throw 'Structured evidence fields are missing, unknown, duplicated, or out of canonical order.' }
        if ($fields.Contains($name)) { throw "Structured evidence field is duplicated: $name" }
        $fields.Add($name, $value)
    }
    $outside = $Text.Remove($block.Index, $block.Length)
    if ($outside -match '(?m)^[ \t]*[A-Za-z][A-Za-z0-9_]*:\s') { throw 'Evidence contains a machine-style field outside the structured block.' }
    return $fields
}

function Write-SyntheticTestcaseXml {
    param(
        [Parameter(Mandatory)][System.Xml.XmlWriter] $Writer,
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $ClassName,
        [ValidateSet('pass','fail','error','skip')][string] $Outcome = 'pass'
    )
    $Writer.WriteStartElement('testcase')
    $Writer.WriteAttributeString('name', $Name)
    $Writer.WriteAttributeString('classname', $ClassName)
    if ($Outcome -cne 'pass') {
        $Writer.WriteStartElement($(if ($Outcome -ceq 'fail') { 'failure' } elseif ($Outcome -ceq 'error') { 'error' } else { 'skipped' }))
        $Writer.WriteAttributeString('message', 'synthetic mutation')
        $Writer.WriteEndElement()
    }
    $Writer.WriteEndElement()
}

function New-SyntheticTestReceiptSet {
    param(
        [Parameter(Mandatory)] $Manifest,
        [Parameter(Mandatory)][string] $Root,
        [ValidateSet('none','missing','duplicate','unexpected','fail','error','skip')][string] $GameTestMutation = 'none',
        [ValidateSet('none','missing','duplicate','unexpected','fail','error','skip')][string] $UnitMutation = 'none',
        [ValidateSet('testsuite','testsuites')][string] $UnitRootElement = 'testsuite',
        [ValidateSet('none','synthetic')][string] $UnitRootNamespace = 'none'
    )
    $unitDirectory = Join-Path $Root 'unit'
    $gameTestPath = Join-Path $Root 'gametest.xml'
    if (Test-Path -LiteralPath $unitDirectory -PathType Container) { Remove-Item -LiteralPath $unitDirectory -Recurse -Force }
    [void](New-Item -ItemType Directory -Path $unitDirectory -Force)
    if (Test-Path -LiteralPath $gameTestPath -PathType Leaf) { Remove-Item -LiteralPath $gameTestPath -Force }
    $settings = [System.Xml.XmlWriterSettings]::new()
    $settings.Encoding = [System.Text.UTF8Encoding]::new($false)
    $settings.Indent = $true

    $firstUnit = [string]$Manifest.ExpectedUnitIds[0]
    foreach ($suite in @($Manifest.UnitGroups.Keys | Sort-Object)) {
        $ids = [System.Collections.Generic.List[string]]::new()
        foreach ($id in @($Manifest.UnitGroups[$suite])) {
            if ($UnitMutation -ceq 'missing' -and [string]$id -ceq $firstUnit) { continue }
            $ids.Add([string]$id)
            if ($UnitMutation -ceq 'duplicate' -and [string]$id -ceq $firstUnit) { $ids.Add([string]$id) }
        }
        if ($UnitMutation -ceq 'unexpected' -and [string]$suite -ceq [string](@($Manifest.UnitGroups.Keys | Sort-Object)[0])) {
            $ids.Add([string]$suite + '#unexpectedReceiptMethod()')
        }
        $path = Join-Path $unitDirectory ('TEST-' + [string]$suite + '.xml')
        $writer = [System.Xml.XmlWriter]::Create($path, $settings)
        try {
            $writer.WriteStartDocument()
            if ($UnitRootNamespace -ceq 'synthetic') {
                $writer.WriteStartElement($UnitRootElement, 'urn:developers-hell:self-check')
            } else {
                $writer.WriteStartElement($UnitRootElement)
            }
            $writer.WriteAttributeString('name', 'arbitrary.class')
            $writer.WriteAttributeString('tests', [string]$ids.Count)
            $writer.WriteAttributeString('failures', $(if ($UnitMutation -ceq 'fail' -and @($ids) -ccontains $firstUnit) { '1' } else { '0' }))
            $writer.WriteAttributeString('errors', $(if ($UnitMutation -ceq 'error' -and @($ids) -ccontains $firstUnit) { '1' } else { '0' }))
            $writer.WriteAttributeString('skipped', $(if ($UnitMutation -ceq 'skip' -and @($ids) -ccontains $firstUnit) { '1' } else { '0' }))
            foreach ($id in @($ids)) {
                $parts = [string]$id -split '#', 2
                $outcome = if ([string]$id -cne $firstUnit) { 'pass' } elseif ($UnitMutation -cin @('fail','error','skip')) { $UnitMutation } else { 'pass' }
                Write-SyntheticTestcaseXml -Writer $writer -Name $parts[1] -ClassName $parts[0] -Outcome $outcome
            }
            $writer.WriteEndElement()
            $writer.WriteEndDocument()
        } finally { $writer.Dispose() }
    }

    $firstGameTest = [string]$Manifest.ExpectedGameTestIds[0]
    $gameTestIds = [System.Collections.Generic.List[string]]::new()
    foreach ($id in @($Manifest.ExpectedGameTestIds)) {
        if ($GameTestMutation -ceq 'missing' -and [string]$id -ceq $firstGameTest) { continue }
        $gameTestIds.Add([string]$id)
        if ($GameTestMutation -ceq 'duplicate' -and [string]$id -ceq $firstGameTest) { $gameTestIds.Add([string]$id) }
    }
    if ($GameTestMutation -ceq 'unexpected') { $gameTestIds.Add('developers_hell_test:unexpected_game_test') }
    $gameWriter = [System.Xml.XmlWriter]::Create($gameTestPath, $settings)
    try {
        $gameWriter.WriteStartDocument()
        $gameWriter.WriteStartElement('testsuite')
        foreach ($id in @($gameTestIds)) {
            $outcome = if ([string]$id -cne $firstGameTest) { 'pass' } elseif ($GameTestMutation -cin @('fail','error','skip')) { $GameTestMutation } else { 'pass' }
            Write-SyntheticTestcaseXml -Writer $gameWriter -Name ([string]$id) -ClassName 'fabric-gametest-api-v1:empty' -Outcome $outcome
        }
        $gameWriter.WriteEndElement()
        $gameWriter.WriteEndDocument()
    } finally { $gameWriter.Dispose() }
    return [pscustomobject]@{ UnitDirectory=$unitDirectory; GameTestPath=$gameTestPath }
}

function New-SyntheticEvidenceText {
    param(
        [Parameter(Mandatory)][string] $Hash,
        [Parameter(Mandatory)] $Manifest,
        [Parameter(Mandatory)] $TestReceipts,
        [Parameter(Mandatory)] $ValidationRows,
        [Parameter(Mandatory)] $SourceSnapshot
    )
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in @(
        '# Phase 2 Lecture Evidence',
        $script:EvidenceBlockStart,
        'evidence_schema: developers_hell_phase2_v1',
        'evidence_timestamp_utc: 2026-01-01T00:00:00.0000000Z',
        ('source_object_format: ' + $SourceSnapshot.ObjectFormat),
        ('source_commit: ' + $SourceSnapshot.Commit),
        ('source_tree: ' + $SourceSnapshot.Tree),
        'source_worktree_status: CLEAN',
        'java_runtime: Eclipse Temurin 25.0.4+7 checksum-bound',
        'gradle_command: gradlew.bat pinned-jvm --offline clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle',
        'gradle_transaction_exit: 0',
        ('gradle_log_sha256: ' + ('d' * 64)),
        'foundation_audit_command: powershell.exe scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath build/libs/developers-hell-0.1.0.jar',
        'foundation_audit_exit: 0',
        'foundation_audit_status: PASS',
        ('foundation_audit_log_sha256: ' + ('e' * 64)),
        ('test_manifest_sha256: ' + $Manifest.Sha256),
        ('unit_test_report_files: ' + $TestReceipts.UnitFiles),
        ('unit_receipt_count: ' + $TestReceipts.UnitCount),
        ('unit_receipt_failures: ' + $TestReceipts.UnitFailures),
        ('unit_receipt_errors: ' + $TestReceipts.UnitErrors),
        ('unit_receipt_skipped: ' + $TestReceipts.UnitSkipped),
        ('unit_receipt_sha256: ' + $TestReceipts.UnitSha256),
        ('gametest_report_files: ' + $TestReceipts.GameTestFiles),
        ('gametest_receipt_count: ' + $TestReceipts.GameTestCount),
        ('gametest_receipt_failures: ' + $TestReceipts.GameTestFailures),
        ('gametest_receipt_errors: ' + $TestReceipts.GameTestErrors),
        ('gametest_receipt_skipped: ' + $TestReceipts.GameTestSkipped),
        ('gametest_receipt_sha256: ' + $TestReceipts.GameTestSha256),
        'ordinary_jar_size: 1',
        'ordinary_jar_entries: 1',
        ('ordinary_jar_entries_sha256: ' + ('f' * 64)),
        'production_server_profile: local_automated_loopback_offline_no_query_no_rcon_no_resource_pack',
        'previous_distribution_sha256: absent',
        "source_jar_sha256: $Hash",
        "build_jar_sha256: $Hash",
        "distribution_sha256: $Hash",
        'hash_equality_status: PASS',
        'hash_equality_detail: source_build_distribution_sha256_equal',
        'source_archive_audit_status: PASS',
        'source_archive_audit_detail: dependency_source_archive_policy',
        'phase2_archive_audit_status: PASS',
        'phase2_archive_audit_detail: production_contract_present_forbidden_residue_absent',
        'server_ready_status: PASS',
        ('server_ready_detail: ' + $script:ExpectedReadyMarker),
        'server_stop_cleanup_status: PASS',
        ('server_stop_cleanup_detail: ' + $script:ExpectedStopCleanupMarker),
        'server_ordered_shutdown_status: PASS',
        'server_ordered_shutdown_detail: first_tick_then_cleanup_then_stop_then_all_dimensions_saved',
        'production_server_exit: 0',
        'clean_exit_status: PASS',
        'clean_exit_detail: production_server_exit_zero',
        'owned_server_root_pid: 1',
        'owned_child_count: 0',
        'owned_child_cleanup_status: PASS',
        'owned_child_cleanup_detail: zero_owned_child_residue',
        ('production_server_log_sha256: ' + ('a' * 64)),
        $script:EvidenceBlockEnd,
        '',
        '| Automated ID | Measured receipt group | Receipt | Status |',
        '|---|---|---|---|'
    )) { [void] $lines.Add($line) }
    foreach ($row in @($ValidationRows)) {
        $receipt = 'unit=' + $row.UnitCount + '; gametest=' + $row.GameTestCount + '; gates=' + $row.Gates + '; receipt_sha256=' + $row.Sha256
        [void]$lines.Add('| ' + $row.Id + ' | ' + $row.Description + ' | ' + $receipt + ' | ' + $row.Status + ' |')
    }
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
    $manifest = Get-LectureTestManifest
    Assert-SelfCheckRejects -Label 'path traversal/alternate target' -Action {
        Resolve-SafeRepositoryPath -Path '..\outside.md' -ExpectedRelativePath $script:DefaultEvidenceRelativePath -AllowMissingLeaf
    }
    [void](Assert-EmptySourceStatus -Lines @())
    Assert-SelfCheckRejects -Label 'modified tracked source status' -Action { Assert-EmptySourceStatus -Lines @(' M src/main/java/example.java') }
    Assert-SelfCheckRejects -Label 'untracked source status' -Action { Assert-EmptySourceStatus -Lines @('?? src/main/java/untracked.java') }
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
        $validStageJar = Join-Path $fixtureRoot ('.' + $script:ExpectedDistributionName + '.' + [guid]::NewGuid().ToString('N') + '.stage')
        [System.IO.File]::Copy($jar, $validStageJar)
        [void](Get-LectureArchiveContract -JarPath $validStageJar -AllowTransactionStageName)
        $arbitraryStageJar = Join-Path $fixtureRoot 'arbitrary-stage.jar'
        [System.IO.File]::Copy($jar, $arbitraryStageJar)
        Assert-SelfCheckRejects -Label 'arbitrary archive name through transaction seam' -Action {
            Get-LectureArchiveContract -JarPath $arbitraryStageJar -AllowTransactionStageName
        }
        [System.IO.File]::Delete($validStageJar)
        [System.IO.File]::Delete($arbitraryStageJar)

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

        $receiptRoot = Join-Path $fixtureRoot 'receipts'
        [void](New-Item -ItemType Directory -Path $receiptRoot)
        $receiptPaths = New-SyntheticTestReceiptSet -Manifest $manifest -Root $receiptRoot
        $commentedSource = Join-Path $receiptRoot 'CommentOnlyGameTest.java'
        [System.IO.File]::WriteAllText($commentedSource, '// @GameTest is documentation, not an execution receipt.', [System.Text.UTF8Encoding]::new($false))
        $testReceipts = Get-TestExecutionReceipts -Manifest $manifest -UnitReportDirectory $receiptPaths.UnitDirectory -GameTestReportPath $receiptPaths.GameTestPath
        if ([System.IO.File]::ReadAllText($commentedSource) -notmatch '@GameTest' -or $testReceipts.UnitCount -ne 88 -or $testReceipts.GameTestCount -ne 51) {
            throw 'Comment-only source affected receipt-derived execution counts or the reviewed 88/51 manifest drifted.'
        }
        $wrongRoot = New-SyntheticTestReceiptSet -Manifest $manifest -Root $receiptRoot -UnitRootElement 'testsuites'
        Assert-SelfCheckRejects -Label 'unit receipt wrong XML root element' -Action {
            Get-TestExecutionReceipts -Manifest $manifest -UnitReportDirectory $wrongRoot.UnitDirectory -GameTestReportPath $wrongRoot.GameTestPath
        }
        $namespacedRoot = New-SyntheticTestReceiptSet -Manifest $manifest -Root $receiptRoot -UnitRootNamespace 'synthetic'
        Assert-SelfCheckRejects -Label 'unit receipt namespaced XML root element' -Action {
            Get-TestExecutionReceipts -Manifest $manifest -UnitReportDirectory $namespacedRoot.UnitDirectory -GameTestReportPath $namespacedRoot.GameTestPath
        }
        $gateResults = [ordered]@{ gradle_transaction=$true; foundation_audit=$true; source_archive=$true; phase2_archive=$true; production_server=$true }
        $validationRows = Get-ValidationRowReceipts -Manifest $manifest -TestReceipts $testReceipts -GateResults $gateResults

        foreach ($mutation in @('missing','duplicate','unexpected','fail','error','skip')) {
            $mutated = New-SyntheticTestReceiptSet -Manifest $manifest -Root $receiptRoot -GameTestMutation $mutation
            Assert-SelfCheckRejects -Label "GameTest $mutation execution receipt" -Action {
                Get-TestExecutionReceipts -Manifest $manifest -UnitReportDirectory $mutated.UnitDirectory -GameTestReportPath $mutated.GameTestPath
            }
        }
        foreach ($mutation in @('missing','duplicate','unexpected','fail','error','skip')) {
            $mutated = New-SyntheticTestReceiptSet -Manifest $manifest -Root $receiptRoot -UnitMutation $mutation
            Assert-SelfCheckRejects -Label "unit $mutation execution receipt" -Action {
                Get-TestExecutionReceipts -Manifest $manifest -UnitReportDirectory $mutated.UnitDirectory -GameTestReportPath $mutated.GameTestPath
            }
        }
        $receiptPaths = New-SyntheticTestReceiptSet -Manifest $manifest -Root $receiptRoot
        $testReceipts = Get-TestExecutionReceipts -Manifest $manifest -UnitReportDirectory $receiptPaths.UnitDirectory -GameTestReportPath $receiptPaths.GameTestPath
        $validationRows = Get-ValidationRowReceipts -Manifest $manifest -TestReceipts $testReceipts -GateResults $gateResults

        $hash = ('a' * 64)
        $sourceIdentity = Get-RepositorySourceIdentity
        $sourceSnapshot = [pscustomobject]@{ ObjectFormat=$sourceIdentity.ObjectFormat; Commit=$sourceIdentity.Commit; Tree=$sourceIdentity.Tree; Status='CLEAN' }
        $evidence = New-SyntheticEvidenceText -Hash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows -SourceSnapshot $sourceSnapshot
        [void](Assert-EvidenceContract -EvidenceText $evidence -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows -ExpectedSourceSnapshot $sourceSnapshot)
        $productionShape = New-LectureEvidenceText `
            -Hash $hash `
            -PreviousHash 'absent' `
            -Artifact ([pscustomobject]@{ Size=1 }) `
            -Archive ([pscustomobject]@{ EntryCount=1; EntriesSha256=('f' * 64) }) `
            -BuildResult ([pscustomobject]@{ Combined='synthetic build transcript' }) `
            -AuditResult ([pscustomobject]@{ ExitCode=0; Combined='synthetic audit transcript' }) `
            -ServerResult ([pscustomobject]@{ RootPid=1; CapturedCount=0; LogSha256=('a' * 64) }) `
            -Manifest $manifest `
            -TestReceipts $testReceipts `
            -ValidationRows $validationRows `
            -SourceSnapshot $sourceSnapshot
        [void](Assert-EvidenceContract -EvidenceText $productionShape -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows -ExpectedSourceSnapshot $sourceSnapshot)
        [void](Assert-EvidenceContract -EvidenceText ($productionShape -replace "`n", "`r`n") -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows -ExpectedSourceSnapshot $sourceSnapshot)
        Assert-SelfCheckRejects -Label 'evidence hash mismatch' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace "distribution_sha256: $hash", ('distribution_sha256: ' + ('b' * 64))) -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'dirty source worktree claim' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace '(?m)^source_worktree_status: CLEAN$', 'source_worktree_status: DIRTY') -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'source commit/tree mismatch' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace ('source_tree: ' + [regex]::Escape([string]$sourceSnapshot.Tree)), ('source_tree: ' + $sourceSnapshot.Commit)) -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'missing real stop callback marker' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace '(?m)^server_stop_cleanup_status:.*\r?\n', '') -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        foreach ($mutation in @(
            [pscustomobject]@{ Label='BYPASS status'; Pattern='(?m)^server_ready_status: PASS$'; Replacement='server_ready_status: BYPASS' },
            [pscustomobject]@{ Label='unequal hash status'; Pattern='(?m)^hash_equality_status: PASS$'; Replacement='hash_equality_status: unequal' },
            [pscustomobject]@{ Label='unclean exit status'; Pattern='(?m)^clean_exit_status: PASS$'; Replacement='clean_exit_status: unclean' },
            [pscustomobject]@{ Label='negated PASS status'; Pattern='(?m)^foundation_audit_status: PASS$'; Replacement='foundation_audit_status: NOT_PASS' },
            [pscustomobject]@{ Label='free-form PASS prefix'; Pattern='(?m)^server_ready_status: PASS$'; Replacement='server_ready_status: PASS - fabricated' }
        )) {
            Assert-SelfCheckRejects -Label ([string]$mutation.Label) -Action {
                Assert-EvidenceContract -EvidenceText ($evidence -replace $mutation.Pattern, $mutation.Replacement) -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
            }
        }
        Assert-SelfCheckRejects -Label 'duplicate structured marker' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace '(?m)^server_ready_status: PASS$', "server_ready_status: PASS`nserver_ready_status: PASS") -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'unknown structured marker' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace ([regex]::Escape($script:EvidenceBlockEnd)), ("unknown_status: PASS`n" + $script:EvidenceBlockEnd)) -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'machine marker outside structured block' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence + "rogue_status: PASS`n") -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'PASS row missing measured receipt' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace '(?m)^(\| 02-CFG-01 \|[^|]+\|)[^|]+(\| PASS \|)$', '$1 omitted $2') -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'PASS row tampered receipt hash' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace ([regex]::Escape([string]$validationRows[0].Sha256)), ('c' * 64)) -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'unknown automated PASS row' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence + "| 02-FAKE-99 | forged | unit=0; gametest=0; gates=none; receipt_sha256=$hash | PASS |`n") -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }
        Assert-SelfCheckRejects -Label 'manual observation inferred as PASS' -Action {
            Assert-EvidenceContract -EvidenceText ($evidence -replace '(?m)^\| MANUAL-UI-01 \|([^\r\n]+)\| PENDING \|$', '| MANUAL-UI-01 |$1| PASS |') -DistributionHash $hash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows
        }

        $ordered = "$($script:ExpectedReadyMarker)`n$($script:ExpectedStopCleanupMarker)`nStopping server`nAll dimensions are saved"
        [void](Assert-ServerTranscript -Text $ordered)
        Assert-SelfCheckRejects -Label 'missing production stop callback transcript' -Action {
            Assert-ServerTranscript -Text ($ordered -replace [regex]::Escape($script:ExpectedStopCleanupMarker), '')
        }
        Assert-SelfCheckRejects -Label 'out-of-order production shutdown transcript' -Action {
            Assert-ServerTranscript -Text "$($script:ExpectedReadyMarker)`nStopping server`n$($script:ExpectedStopCleanupMarker)`nAll dimensions are saved"
        }
        $pairSource = Join-Path $fixtureRoot 'pair-source.bin'
        $pairDistribution = Join-Path $fixtureRoot 'pair-distribution.bin'
        $pairEvidence = Join-Path $fixtureRoot 'pair-evidence.md'
        $candidateArtifactText = 'new artifact exact bytes'
        $candidateEvidenceText = 'new evidence exact bytes'
        $originalDistributionText = 'old distribution exact bytes'
        $originalEvidenceText = 'old evidence exact bytes'
        [System.IO.File]::WriteAllText($pairSource, $candidateArtifactText, [System.Text.UTF8Encoding]::new($false))
        $sourceHashBefore = Get-FileSha256 $pairSource
        $resetOriginalPair = {
            [System.IO.File]::WriteAllText($pairDistribution, $originalDistributionText, [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText($pairEvidence, $originalEvidenceText, [System.Text.UTF8Encoding]::new($false))
        }
        $validateSyntheticPair = {
            param([string] $ArtifactPath, [string] $EvidencePath)
            if ((Read-SharedTextFile -LiteralPath $ArtifactPath) -cne $candidateArtifactText -or
                (Read-SharedTextFile -LiteralPath $EvidencePath) -cne $candidateEvidenceText) {
                throw 'Synthetic publication pair is not internally consistent.'
            }
            return $true
        }
        $assertNoPublicationResidue = {
            $residue = @(Get-ChildItem -LiteralPath $fixtureRoot -Recurse -File | Where-Object { $_.Name -match '[.](?:stage|backup)$' })
            if ($residue.Count -ne 0) { throw 'Publication transaction left stage or backup residue after a complete outcome.' }
        }
        $assertOriginalPair = {
            if ((Read-SharedTextFile -LiteralPath $pairDistribution) -cne $originalDistributionText -or
                (Read-SharedTextFile -LiteralPath $pairEvidence) -cne $originalEvidenceText) {
                throw 'Publication rollback did not restore both original files byte-for-byte.'
            }
            & $assertNoPublicationResidue
        }

        & $resetOriginalPair
        $published = Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $validateSyntheticPair
        if ((Get-FileSha256 $pairSource) -cne $sourceHashBefore -or
            (Read-SharedTextFile -LiteralPath $pairDistribution) -cne $candidateArtifactText -or
            (Read-SharedTextFile -LiteralPath $pairEvidence) -cne $candidateEvidenceText -or
            [string]$published.DistributionSha256 -cne $sourceHashBefore) {
            throw 'Successful paired publication did not preserve the source and publish both exact candidates.'
        }
        & $assertNoPublicationResidue

        & $resetOriginalPair
        $cleanupLock = [pscustomobject]@{ Stream=$null; Path=$null }
        $warningPreferenceBeforeCleanupTest = $WarningPreference
        $WarningPreference = 'SilentlyContinue'
        try {
            $cleanupLockedPublication = Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $validateSyntheticPair -AfterEvidenceReplaceAction {
                $backups = @(Get-ChildItem -LiteralPath (Split-Path -Parent $pairDistribution) -File | Where-Object { $_.Name -like '.pair-distribution.bin.*.backup' })
                if ($backups.Count -ne 1) { throw 'Synthetic cleanup-lock hook could not identify the exact distribution backup.' }
                $cleanupLock.Path = $backups[0].FullName
                $cleanupLock.Stream = [System.IO.File]::Open($cleanupLock.Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
            }
        } finally {
            $WarningPreference = $warningPreferenceBeforeCleanupTest
            if ($null -ne $cleanupLock.Stream) { $cleanupLock.Stream.Dispose() }
        }
        if ([string]$cleanupLockedPublication.DistributionSha256 -cne $sourceHashBefore -or
            (Read-SharedTextFile -LiteralPath $pairDistribution) -cne $candidateArtifactText -or
            (Read-SharedTextFile -LiteralPath $pairEvidence) -cne $candidateEvidenceText -or
            [string]::IsNullOrWhiteSpace([string]$cleanupLock.Path) -or
            -not (Test-Path -LiteralPath $cleanupLock.Path -PathType Leaf)) {
            throw 'Non-fatal post-commit cleanup failure incorrectly invalidated the published pair or discarded recovery residue.'
        }
        [System.IO.File]::Delete($cleanupLock.Path)
        & $assertNoPublicationResidue

        & $resetOriginalPair
        Assert-SelfCheckRejects -Label 'recorded previous distribution hash drift' -Action {
            Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $validateSyntheticPair -ExpectedOriginalDistributionHash ('0' * 64)
        }
        & $assertOriginalPair

        & $resetOriginalPair
        Assert-SelfCheckRejects -Label 'paired publication stage validation failure' -Action {
            Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair { throw 'synthetic stage validation failure' }
        }
        & $assertOriginalPair

        & $resetOriginalPair
        Assert-SelfCheckRejects -Label 'paired publication failure after distribution replace' -Action {
            Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $validateSyntheticPair -AfterDistributionReplaceAction { throw 'synthetic first-boundary failure' }
        }
        & $assertOriginalPair

        & $resetOriginalPair
        Assert-SelfCheckRejects -Label 'paired publication failure after evidence replace' -Action {
            Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $validateSyntheticPair -AfterEvidenceReplaceAction { throw 'synthetic second-boundary failure' }
        }
        & $assertOriginalPair

        & $resetOriginalPair
        $finalValidationState = [pscustomobject]@{ Calls=0 }
        $failFinalPairValidation = {
            param([string] $ArtifactPath, [string] $EvidencePath)
            $finalValidationState.Calls = [int]$finalValidationState.Calls + 1
            & $validateSyntheticPair $ArtifactPath $EvidencePath | Out-Null
            if ($finalValidationState.Calls -eq 2) { throw 'synthetic final pair validation failure' }
            return $true
        }
        Assert-SelfCheckRejects -Label 'paired publication final validation failure' -Action {
            Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $failFinalPairValidation
        }
        if ($finalValidationState.Calls -ne 2) { throw 'Final pair validation failure injection did not cross both validation boundaries.' }
        & $assertOriginalPair

        & $resetOriginalPair
        $lockedEvidence = [System.IO.File]::Open($pairEvidence, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
        try {
            Assert-SelfCheckRejects -Label 'locked evidence destination during paired publication' -Action {
                Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $validateSyntheticPair
            }
        } finally {
            $lockedEvidence.Dispose()
        }
        & $assertOriginalPair

        $absentDistribution = Join-Path $fixtureRoot 'absent-distribution.bin'
        $absentEvidence = Join-Path $fixtureRoot 'absent-evidence.md'
        Assert-SelfCheckRejects -Label 'originally absent pair rollback' -Action {
            Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $absentDistribution -EvidenceDestination $absentEvidence -ValidatePair $validateSyntheticPair -AfterEvidenceReplaceAction { throw 'synthetic absent-pair failure' }
        }
        if ((Test-Path -LiteralPath $absentDistribution) -or (Test-Path -LiteralPath $absentEvidence)) { throw 'Originally absent publication pair was not restored to absence.' }
        & $assertNoPublicationResidue

        & $resetOriginalPair
        $originalDistributionState = Get-OptionalFileState -Path $pairDistribution
        $lockedBackup = [pscustomobject]@{ Stream=$null; Path=$null }
        $restoreFailureMessage = $null
        try {
            Publish-ArtifactEvidenceTransaction -ArtifactSource $pairSource -EvidenceText $candidateEvidenceText -DistributionDestination $pairDistribution -EvidenceDestination $pairEvidence -ValidatePair $validateSyntheticPair -AfterDistributionReplaceAction {
                $backups = @(Get-ChildItem -LiteralPath (Split-Path -Parent $pairDistribution) -File | Where-Object { $_.Name -like '.pair-distribution.bin.*.backup' })
                if ($backups.Count -ne 1) { throw 'Synthetic restore-failure hook could not identify the exact distribution backup.' }
                $lockedBackup.Path = $backups[0].FullName
                $lockedBackup.Stream = [System.IO.File]::Open($lockedBackup.Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
                throw 'synthetic rollback trigger with locked backup'
            }
            throw 'Restore-failure self-check unexpectedly accepted a failed rollback.'
        } catch {
            $restoreFailureMessage = $_.Exception.Message
        } finally {
            if ($null -ne $lockedBackup.Stream) { $lockedBackup.Stream.Dispose() }
        }
        if ($restoreFailureMessage -notmatch 'Publication rollback was incomplete' -or
            [string]::IsNullOrWhiteSpace([string]$lockedBackup.Path) -or
            -not (Test-Path -LiteralPath $lockedBackup.Path -PathType Leaf) -or
            (Get-FileSha256 -LiteralPath $lockedBackup.Path) -cne [string]$originalDistributionState.Sha256) {
            throw 'Incomplete publication rollback was not reported with its exact retained recovery backup.'
        }
        Restore-PublishedFile -Destination $pairDistribution -Backup $lockedBackup.Path -Original $originalDistributionState
        & $assertOriginalPair

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

function Assert-FoundationAuditGreen {
    param([Parameter(Mandatory)] $AuditResult)
    if ($AuditResult.ExitCode -ne 0) { throw "Foundation audit returned non-zero exit $($AuditResult.ExitCode)." }
    $text = $AuditResult.Combined.Replace('\', '/')
    if ($text -match '(?m)^FAIL:' -or $text -notmatch '(?m)^## FINAL_RESULT\r?\nPASS: FOUNDATION_AUDIT\s*$') {
        throw 'Foundation audit output is not exactly green.'
    }
    foreach ($pass in @('PREREQUISITES','COMMON_CLIENT_LINKAGE','OFFICIAL_REPOSITORIES','DIRECT_DEPENDENCIES','RUNTIME_CLASSPATH_REPORT','PRODUCTION_ARCHIVE','SOURCE_RUNTIME_SURFACES','WRAPPER_AND_GIT_HYGIENE')) {
        if ($text -notmatch ('(?s)## ' + [regex]::Escape($pass) + '\r?\nPASS:')) { throw "Foundation audit has no exact PASS for required section: $pass" }
    }
    [void](Assert-IndependentProductionSourceContract)
    return $true
}

function Get-OptionalFileState {
    param([Parameter(Mandatory)][string] $Path)
    if (-not (Test-Path -LiteralPath $Path)) { return [pscustomobject]@{ Exists=$false; Length=0L; Sha256=$null } }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'Publication destination must be absent or an ordinary file.' }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Publication destination must not be a reparse point.' }
    return [pscustomobject]@{ Exists=$true; Length=[long]$item.Length; Sha256=Get-FileSha256 -LiteralPath $item.FullName }
}

function Assert-OptionalFileState {
    param([Parameter(Mandatory)][string] $Path, [Parameter(Mandatory)] $Expected)
    $actual = Get-OptionalFileState -Path $Path
    if ([bool]$actual.Exists -ne [bool]$Expected.Exists -or
        ($actual.Exists -and ([long]$actual.Length -ne [long]$Expected.Length -or [string]$actual.Sha256 -cne [string]$Expected.Sha256))) {
        throw 'Publication rollback did not restore the exact original file state.'
    }
    return $true
}

function Publish-StagedFile {
    param(
        [Parameter(Mandatory)][string] $Stage,
        [Parameter(Mandatory)][string] $Destination,
        [Parameter(Mandatory)][string] $Backup,
        [Parameter(Mandatory)] $Original
    )
    if ($Original.Exists) {
        [System.IO.File]::Replace($Stage, $Destination, $Backup, $true)
    } else {
        if (Test-Path -LiteralPath $Destination) { throw 'Absent publication destination appeared during staging.' }
        [System.IO.File]::Move($Stage, $Destination)
    }
}

function Restore-PublishedFile {
    param(
        [Parameter(Mandatory)][string] $Destination,
        [Parameter(Mandatory)][string] $Backup,
        [Parameter(Mandatory)] $Original
    )
    if ($Original.Exists) {
        if (-not (Test-Path -LiteralPath $Backup -PathType Leaf)) { throw 'Publication recovery backup is missing.' }
        if (Test-Path -LiteralPath $Destination -PathType Leaf) {
            [System.IO.File]::Replace($Backup, $Destination, [System.Management.Automation.Language.NullString]::Value, $true)
        } elseif (-not (Test-Path -LiteralPath $Destination)) {
            [System.IO.File]::Move($Backup, $Destination)
        } else {
            throw 'Publication destination changed type before rollback.'
        }
    } elseif (Test-Path -LiteralPath $Destination -PathType Leaf) {
        [System.IO.File]::Delete($Destination)
    } elseif (Test-Path -LiteralPath $Destination) {
        throw 'Originally absent publication destination changed type before rollback.'
    }
}

function Publish-ArtifactEvidenceTransaction {
    param(
        [Parameter(Mandatory)][string] $ArtifactSource,
        [Parameter(Mandatory)][string] $EvidenceText,
        [Parameter(Mandatory)][string] $DistributionDestination,
        [Parameter(Mandatory)][string] $EvidenceDestination,
        [Parameter(Mandatory)][scriptblock] $ValidatePair,
        [string] $ExpectedOriginalDistributionHash,
        [scriptblock] $AfterDistributionReplaceAction,
        [scriptblock] $AfterEvidenceReplaceAction
    )
    if ([string]::IsNullOrWhiteSpace($EvidenceText)) { throw 'Candidate evidence text must not be empty.' }
    if ([System.IO.Path]::GetFullPath($DistributionDestination).Equals([System.IO.Path]::GetFullPath($EvidenceDestination), [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Distribution and evidence destinations must be distinct.'
    }
    foreach ($parent in @((Split-Path -Parent $DistributionDestination),(Split-Path -Parent $EvidenceDestination))) {
        if (-not (Test-Path -LiteralPath $parent -PathType Container)) { [void](New-Item -ItemType Directory -Path $parent) }
        $item = Get-Item -LiteralPath $parent -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Publication parent must not be a reparse point.' }
    }

    $token = [guid]::NewGuid().ToString('N')
    $distributionParent = Split-Path -Parent $DistributionDestination
    $evidenceParent = Split-Path -Parent $EvidenceDestination
    $distributionStage = Join-Path $distributionParent ('.' + (Split-Path -Leaf $DistributionDestination) + '.' + $token + '.stage')
    $evidenceStage = Join-Path $evidenceParent ('.' + (Split-Path -Leaf $EvidenceDestination) + '.' + $token + '.stage')
    $distributionBackup = Join-Path $distributionParent ('.' + (Split-Path -Leaf $DistributionDestination) + '.' + $token + '.backup')
    $evidenceBackup = Join-Path $evidenceParent ('.' + (Split-Path -Leaf $EvidenceDestination) + '.' + $token + '.backup')
    $originalDistribution = Get-OptionalFileState -Path $DistributionDestination
    $originalEvidence = Get-OptionalFileState -Path $EvidenceDestination
    if (-not [string]::IsNullOrWhiteSpace($ExpectedOriginalDistributionHash)) {
        $actualOriginalHash = if ($originalDistribution.Exists) { [string]$originalDistribution.Sha256 } else { 'absent' }
        if ($actualOriginalHash -cne $ExpectedOriginalDistributionHash) {
            throw 'Recorded previous distribution hash no longer matches the transaction input pair.'
        }
    }
    $distributionReplaced = $false
    $evidenceReplaced = $false
    $committed = $false
    $rollbackComplete = $false
    $publicationResult = $null
    try {
        [System.IO.File]::Copy($ArtifactSource, $distributionStage, $false)
        [System.IO.File]::WriteAllText($evidenceStage, $EvidenceText, [System.Text.UTF8Encoding]::new($false))
        if ((Get-FileSha256 -LiteralPath $distributionStage) -cne (Get-FileSha256 -LiteralPath $ArtifactSource) -or
            (Read-SharedTextFile -LiteralPath $evidenceStage) -cne $EvidenceText) {
            throw 'Publication staging did not preserve exact candidate bytes.'
        }
        & $ValidatePair $distributionStage $evidenceStage | Out-Null
        [void](Assert-OptionalFileState -Path $DistributionDestination -Expected $originalDistribution)
        [void](Assert-OptionalFileState -Path $EvidenceDestination -Expected $originalEvidence)

        Publish-StagedFile -Stage $distributionStage -Destination $DistributionDestination -Backup $distributionBackup -Original $originalDistribution
        $distributionReplaced = $true
        if ($AfterDistributionReplaceAction) { & $AfterDistributionReplaceAction }

        Publish-StagedFile -Stage $evidenceStage -Destination $EvidenceDestination -Backup $evidenceBackup -Original $originalEvidence
        $evidenceReplaced = $true
        if ($AfterEvidenceReplaceAction) { & $AfterEvidenceReplaceAction }

        & $ValidatePair $DistributionDestination $EvidenceDestination | Out-Null
        $finalDistributionHash = Get-FileSha256 -LiteralPath $DistributionDestination
        $finalEvidenceHash = Get-FileSha256 -LiteralPath $EvidenceDestination
        if ($finalDistributionHash -cne (Get-FileSha256 -LiteralPath $ArtifactSource) -or
            (Read-SharedTextFile -LiteralPath $EvidenceDestination) -cne $EvidenceText) {
            throw 'Final publication pair is not byte-identical to both staged candidates.'
        }
        $publicationResult = [pscustomobject]@{
            DistributionSha256 = $finalDistributionHash
            EvidenceSha256 = $finalEvidenceHash
        }
        $committed = $true
        $rollbackComplete = $true
    } catch {
        $primary = $_
        $rollbackErrors = [System.Collections.Generic.List[string]]::new()
        if ($evidenceReplaced) {
            try { Restore-PublishedFile -Destination $EvidenceDestination -Backup $evidenceBackup -Original $originalEvidence }
            catch { $rollbackErrors.Add('evidence restore failed') }
        }
        if ($distributionReplaced) {
            try { Restore-PublishedFile -Destination $DistributionDestination -Backup $distributionBackup -Original $originalDistribution }
            catch { $rollbackErrors.Add('distribution restore failed') }
        }
        try { [void](Assert-OptionalFileState -Path $EvidenceDestination -Expected $originalEvidence) }
        catch { $rollbackErrors.Add('evidence state verification failed') }
        try { [void](Assert-OptionalFileState -Path $DistributionDestination -Expected $originalDistribution) }
        catch { $rollbackErrors.Add('distribution state verification failed') }
        if ($rollbackErrors.Count -ne 0) {
            throw "$($primary.Exception.Message) Publication rollback was incomplete; recovery backups were retained: $($rollbackErrors -join ', ')."
        }
        $rollbackComplete = $true
        throw $primary
    } finally {
        $cleanupErrors = [System.Collections.Generic.List[string]]::new()
        foreach ($stage in @($distributionStage,$evidenceStage)) {
            if (Test-Path -LiteralPath $stage -PathType Leaf) {
                try { [System.IO.File]::Delete($stage) }
                catch { $cleanupErrors.Add('stage cleanup failed') }
            }
        }
        if ($committed -or $rollbackComplete) {
            foreach ($backup in @($distributionBackup,$evidenceBackup)) {
                if (Test-Path -LiteralPath $backup -PathType Leaf) {
                    try { [System.IO.File]::Delete($backup) }
                    catch { $cleanupErrors.Add('backup cleanup failed') }
                }
            }
        }
        if ($cleanupErrors.Count -ne 0) {
            try { Write-Warning 'Publication pair outcome is final, but temporary recovery-file cleanup was incomplete.' }
            catch { <# A post-commit diagnostic must never invalidate an already-validated pair. #> }
        }
    }
    return $publicationResult
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
        [Parameter(Mandatory)] $Manifest,
        [Parameter(Mandatory)] $TestReceipts,
        [Parameter(Mandatory)] $ValidationRows,
        [Parameter(Mandatory)] $SourceSnapshot
    )
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in @(
        '# Phase 2 Lecture Evidence',
        '',
        'Machine-produced public-safe facts for the exact fresh ordinary JAR. Automated PASS never stands in for client rendering, readability, audio, motion, model, or playability observation.',
        '',
        $script:EvidenceBlockStart,
        'evidence_schema: developers_hell_phase2_v1',
        ('evidence_timestamp_utc: ' + [DateTime]::UtcNow.ToString('o')),
        ('source_object_format: ' + $SourceSnapshot.ObjectFormat),
        ('source_commit: ' + $SourceSnapshot.Commit),
        ('source_tree: ' + $SourceSnapshot.Tree),
        'source_worktree_status: CLEAN',
        'java_runtime: Eclipse Temurin 25.0.4+7 checksum-bound',
        'gradle_command: gradlew.bat pinned-jvm --offline clean test runGameTest auditDirectDependencies build --no-daemon --console=plain --stacktrace --init-script scripts/loom-resolution.init.gradle',
        'gradle_transaction_exit: 0',
        ('gradle_log_sha256: ' + (Get-StringSha256 $BuildResult.Combined)),
        'foundation_audit_command: powershell.exe scripts/audit-foundation.ps1 -SourceAndDependencies -JarPath build/libs/developers-hell-0.1.0.jar',
        ('foundation_audit_exit: ' + $AuditResult.ExitCode),
        'foundation_audit_status: PASS',
        ('foundation_audit_log_sha256: ' + (Get-StringSha256 $AuditResult.Combined)),
        ('test_manifest_sha256: ' + $Manifest.Sha256),
        ('unit_test_report_files: ' + $TestReceipts.UnitFiles),
        ('unit_receipt_count: ' + $TestReceipts.UnitCount),
        ('unit_receipt_failures: ' + $TestReceipts.UnitFailures),
        ('unit_receipt_errors: ' + $TestReceipts.UnitErrors),
        ('unit_receipt_skipped: ' + $TestReceipts.UnitSkipped),
        ('unit_receipt_sha256: ' + $TestReceipts.UnitSha256),
        ('gametest_report_files: ' + $TestReceipts.GameTestFiles),
        ('gametest_receipt_count: ' + $TestReceipts.GameTestCount),
        ('gametest_receipt_failures: ' + $TestReceipts.GameTestFailures),
        ('gametest_receipt_errors: ' + $TestReceipts.GameTestErrors),
        ('gametest_receipt_skipped: ' + $TestReceipts.GameTestSkipped),
        ('gametest_receipt_sha256: ' + $TestReceipts.GameTestSha256),
        ('ordinary_jar_size: ' + $Artifact.Size),
        ('ordinary_jar_entries: ' + $Archive.EntryCount),
        ('ordinary_jar_entries_sha256: ' + $Archive.EntriesSha256),
        'production_server_profile: local_automated_loopback_offline_no_query_no_rcon_no_resource_pack',
        ('previous_distribution_sha256: ' + $PreviousHash),
        ('source_jar_sha256: ' + $Hash),
        ('build_jar_sha256: ' + $Hash),
        ('distribution_sha256: ' + $Hash),
        'hash_equality_status: PASS',
        'hash_equality_detail: source_build_distribution_sha256_equal',
        'source_archive_audit_status: PASS',
        'source_archive_audit_detail: dependency_source_archive_policy',
        'phase2_archive_audit_status: PASS',
        'phase2_archive_audit_detail: production_contract_present_forbidden_residue_absent',
        'server_ready_status: PASS',
        ('server_ready_detail: ' + $script:ExpectedReadyMarker),
        'server_stop_cleanup_status: PASS',
        ('server_stop_cleanup_detail: ' + $script:ExpectedStopCleanupMarker),
        'server_ordered_shutdown_status: PASS',
        'server_ordered_shutdown_detail: first_tick_then_cleanup_then_stop_then_all_dimensions_saved',
        'production_server_exit: 0',
        'clean_exit_status: PASS',
        'clean_exit_detail: production_server_exit_zero',
        ('owned_server_root_pid: ' + $ServerResult.RootPid),
        ('owned_child_count: ' + $ServerResult.CapturedCount),
        'owned_child_cleanup_status: PASS',
        'owned_child_cleanup_detail: zero_owned_child_residue',
        ('production_server_log_sha256: ' + $ServerResult.LogSha256),
        $script:EvidenceBlockEnd,
        '',
        '## Automated validation rows',
        '',
        '| Automated ID | Measured receipt group | Receipt | Status |',
        '|---|---|---|---|'
    )) { [void]$lines.Add([string]$line) }
    foreach ($row in @($ValidationRows)) {
        $receipt = 'unit=' + $row.UnitCount + '; gametest=' + $row.GameTestCount + '; gates=' + $row.Gates + '; receipt_sha256=' + $row.Sha256
        [void]$lines.Add('| ' + $row.Id + ' | ' + $row.Description + ' | ' + $receipt + ' | ' + $row.Status + ' |')
    }
    foreach ($line in @(
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
    $sourceSnapshot = Get-CleanSourceSnapshot
    $jdk = Get-VerifiedJdk
    $manifest = Get-LectureTestManifest
    $buildStartedUtc = [DateTime]::UtcNow
    $gradleArgs = Get-GradleArguments -Jdk $jdk -Tasks @('clean','test','runGameTest','auditDirectDependencies','build')
    $build = Invoke-BoundedBatch -Arguments $gradleArgs -Jdk $jdk -TimeoutSeconds 1200
    if ($build.ExitCode -ne 0 -or $build.Combined -notmatch 'BUILD SUCCESSFUL' -or $build.Combined -notmatch '(?m)> Task :runGameTest' -or $build.Combined -notmatch 'DEVELOPERS_HELL_DIRECT_DEPENDENCIES=') { throw 'Fresh offline Gradle transaction did not prove build, GameTest, and dependency anchors.' }
    $jar = Resolve-SafeRepositoryPath -Path $script:BuildJarRelativePath -ExpectedRelativePath $script:BuildJarRelativePath
    $artifact = Assert-FreshArtifact -JarPath $jar -BuildStartedUtc $buildStartedUtc
    $testReceipts = Get-TestExecutionReceipts -Manifest $manifest
    $audit = Invoke-BoundedFoundationAudit -Jdk $jdk -JarPath $jar
    [void](Assert-FoundationAuditGreen -AuditResult $audit)
    $archive = Get-LectureArchiveContract -JarPath $jar
    if ($archive.Sha256 -cne $artifact.Sha256) { throw 'Fresh-artifact and Phase 2 archive hashes disagree.' }
    $server = Invoke-BoundedProductionServer -Jdk $jdk -ArtifactPath $jar
    if ((Get-FileSha256 $jar) -cne $artifact.Sha256) { throw 'Fresh build JAR changed after all candidate gates.' }
    [void](Assert-SourceSnapshotStillClean -Expected $sourceSnapshot)
    $gateResults = [ordered]@{ gradle_transaction=$true; foundation_audit=$true; source_archive=$true; phase2_archive=$true; production_server=$true }
    $validationRows = Get-ValidationRowReceipts -Manifest $manifest -TestReceipts $testReceipts -GateResults $gateResults

    $distributionHash = $artifact.Sha256
    $previousHash = if (Test-Path -LiteralPath $DistributionFile -PathType Leaf) { Get-FileSha256 $DistributionFile } else { 'absent' }
    $evidenceText = New-LectureEvidenceText -Hash $distributionHash -PreviousHash $previousHash -Artifact $artifact -Archive $archive -BuildResult $build -AuditResult $audit -ServerResult $server -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows -SourceSnapshot $sourceSnapshot
    [void](Assert-EvidenceContract -EvidenceText $evidenceText -DistributionHash $distributionHash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows -ExpectedSourceSnapshot $sourceSnapshot)
    $validatePublicationPair = {
        param([string] $CandidateDistribution, [string] $CandidateEvidence)
        $candidateHash = Get-FileSha256 -LiteralPath $CandidateDistribution
        if ($candidateHash -cne $artifact.Sha256) { throw 'Candidate publication JAR does not equal the inspected build artifact.' }
        $candidateArchive = Get-LectureArchiveContract -JarPath $CandidateDistribution -AllowTransactionStageName
        if ($candidateArchive.Sha256 -cne $archive.Sha256 -or
            $candidateArchive.EntryCount -ne $archive.EntryCount -or
            $candidateArchive.EntriesSha256 -cne $archive.EntriesSha256) {
            throw 'Candidate publication archive contract changed during staging.'
        }
        $candidateEvidenceText = Read-SharedTextFile -LiteralPath $CandidateEvidence
        [void](Assert-EvidenceContract -EvidenceText $candidateEvidenceText -DistributionHash $candidateHash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows -ExpectedSourceSnapshot $sourceSnapshot)
        return $true
    }
    [void](Assert-SourceSnapshotStillClean -Expected $sourceSnapshot)
    $publication = Publish-ArtifactEvidenceTransaction -ArtifactSource $jar -EvidenceText $evidenceText -DistributionDestination $DistributionFile -EvidenceDestination $EvidenceFile -ValidatePair $validatePublicationPair -ExpectedOriginalDistributionHash $previousHash
    $distributionHash = [string]$publication.DistributionSha256
    Write-Host "PASS: fresh Phase 2 artifact promoted after all gates; server root PID $($server.RootPid), captured owned children $($server.CapturedCount), SHA-256 $distributionHash"
}

function Invoke-ValidateEvidenceMode {
    param([Parameter(Mandatory)][string] $EvidenceFile, [Parameter(Mandatory)][string] $DistributionFile)
    [void](Get-VerifiedJdk)
    if (-not (Test-Path -LiteralPath $EvidenceFile -PathType Leaf)) { throw 'Phase 2 lecture evidence is missing.' }
    if (-not (Test-Path -LiteralPath $DistributionFile -PathType Leaf)) { throw 'Phase 2 distribution JAR is missing.' }
    $distributionHash = Get-FileSha256 $DistributionFile
    $manifest = Get-LectureTestManifest
    $testReceipts = Get-TestExecutionReceipts -Manifest $manifest
    $gateResults = [ordered]@{ gradle_transaction=$true; foundation_audit=$true; source_archive=$true; phase2_archive=$true; production_server=$true }
    $validationRows = Get-ValidationRowReceipts -Manifest $manifest -TestReceipts $testReceipts -GateResults $gateResults
    $buildJar = Resolve-SafeRepositoryPath -Path $script:BuildJarRelativePath -ExpectedRelativePath $script:BuildJarRelativePath
    if ((Get-FileSha256 $buildJar) -cne $distributionHash) { throw 'Current build and distribution hashes are not equal.' }
    $text = [System.IO.File]::ReadAllText($EvidenceFile)
    [void](Assert-EvidenceContract -EvidenceText $text -DistributionHash $distributionHash -Manifest $manifest -TestReceipts $testReceipts -ValidationRows $validationRows)
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
