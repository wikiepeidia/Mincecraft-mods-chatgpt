[CmdletBinding()]
param(
	[switch] $SourceAndDependencies,
	[string] $JarPath,
	[string] $EvidencePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:AuditLines = [System.Collections.Generic.List[string]]::new()
$script:EvidenceDetails = [System.Collections.Generic.List[string]]::new()
$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:RuntimeClasspathOutput = @()
$script:GradleJvmArguments = @()

function Add-AuditLine {
	param([Parameter(Mandatory = $true)][AllowEmptyString()][string] $Line)

	[void] $script:AuditLines.Add($Line)
	Write-Output $Line
}

function Add-EvidenceDetail {
	param([Parameter(Mandatory = $true)][AllowEmptyString()][string] $Line)

	[void] $script:EvidenceDetails.Add($Line)
}

function Get-CanonicalPath {
	param(
		[Parameter(Mandatory = $true)][string] $Path,
		[switch] $AllowMissing
	)

	if ($AllowMissing) {
		return [System.IO.Path]::GetFullPath($Path).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
	}

	return (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
}

if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
	throw 'PSScriptRoot is unavailable; repository root cannot be resolved safely.'
}

$scriptPath = Get-CanonicalPath -Path $MyInvocation.MyCommand.Path
$repoRoot = Get-CanonicalPath -Path (Join-Path (Split-Path -Parent $scriptPath) '..')
$homeRoot = if ([string]::IsNullOrWhiteSpace([Environment]::GetFolderPath('UserProfile'))) {
	$null
} else {
	Get-CanonicalPath -Path ([Environment]::GetFolderPath('UserProfile')) -AllowMissing
}
$tempRoot = Get-CanonicalPath -Path ([System.IO.Path]::GetTempPath()) -AllowMissing

function Initialize-VerifiedGradleRuntime {
	if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
		throw 'JAVA_HOME must select the retained checksum-bound Java 25 runtime.'
	}

	$jdkRoot = Get-CanonicalPath -Path $env:JAVA_HOME
	$toolchainEvidencePath = Join-Path $repoRoot '.planning/phases/01-java-25-and-fabric-26-2-foundation/01-TOOLCHAIN-EVIDENCE.md'
	if (-not (Test-Path -LiteralPath $toolchainEvidencePath -PathType Leaf)) {
		throw 'Committed toolchain evidence is missing.'
	}
	$toolchainEvidence = [System.IO.File]::ReadAllText($toolchainEvidencePath)

	$markers = @{}
	foreach ($name in @('jdk_runtime_version', 'jdk_vendor', 'jdk_java_sha256', 'jdk_javac_sha256', 'jdk_path_sha256')) {
		$match = [regex]::Match($toolchainEvidence, '(?m)^' + [regex]::Escape($name) + ':\s*(?<value>[^\r\n]+?)\s*$')
		if (-not $match.Success) {
			throw "Toolchain evidence marker is missing: $name"
		}
		$markers[$name] = $match.Groups['value'].Value.Trim()
	}
	if ($markers['jdk_runtime_version'] -cne '25.0.4+7' -or $markers['jdk_vendor'] -cne 'Eclipse Adoptium') {
		throw 'Toolchain evidence does not bind Eclipse Temurin 25.0.4+7.'
	}

	foreach ($tool in @('java', 'javac')) {
		$executable = Join-Path $jdkRoot "bin/$tool.exe"
		if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
			throw "Verified JDK executable is missing: $tool.exe"
		}
		$actualHash = (Get-FileHash -LiteralPath $executable -Algorithm SHA256).Hash.ToLowerInvariant()
		if ($actualHash -cne $markers["jdk_${tool}_sha256"].ToLowerInvariant()) {
			throw "Verified JDK executable hash mismatch: $tool.exe"
		}
	}

	$sha = [System.Security.Cryptography.SHA256]::Create()
	try {
		$pathBytes = [System.Text.Encoding]::UTF8.GetBytes($jdkRoot.ToLowerInvariant())
		$actualPathHash = ([System.BitConverter]::ToString($sha.ComputeHash($pathBytes))).Replace('-', '').ToLowerInvariant()
	} finally {
		$sha.Dispose()
	}
	if ($actualPathHash -cne $markers['jdk_path_sha256'].ToLowerInvariant()) {
		throw 'JAVA_HOME canonical path does not match the retained toolchain evidence.'
	}

	$script:GradleJvmArguments = @(
		"-Dorg.gradle.java.installations.paths=$jdkRoot",
		'-Dorg.gradle.java.installations.auto-detect=false',
		'-Dorg.gradle.java.installations.auto-download=false'
	)
	Add-EvidenceDetail "verified_java_home_path_sha256=$actualPathHash"
}

function Protect-EvidenceText {
	param([AllowEmptyString()][string] $Text)

	if ($null -eq $Text) {
		return ''
	}

	$safe = [string] $Text
	$replacements = [System.Collections.Generic.List[object]]::new()
	foreach ($pair in @(@($repoRoot, '[REPOSITORY]'), @($homeRoot, '[USER_HOME]'), @($tempRoot, '[TEMP]'))) {
		if (-not [string]::IsNullOrWhiteSpace($pair[0])) {
			[void] $replacements.Add(@([string] $pair[0], [string] $pair[1]))
			$slashPath = ([string] $pair[0]).Replace('\', '/')
			if ($slashPath -cne [string] $pair[0]) {
				[void] $replacements.Add(@($slashPath, [string] $pair[1]))
			}
		}
	}
	$replacements = @($replacements | Sort-Object { $_[0].Length } -Descending)

	foreach ($replacement in $replacements) {
		$safe = [regex]::Replace($safe, [regex]::Escape([string] $replacement[0]), [string] $replacement[1], [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
	}

	$safe = [regex]::Replace($safe, '(?i)(https?://)([^/\s:@]+):([^@\s/]+)@', '$1[REDACTED]@')
	$safe = [regex]::Replace($safe, '(?i)([?&](?:api[-_]?key|access[-_]?token|auth[-_]?token|secret|password)=)[^&\s]+', '$1[REDACTED]')
	$safe = [regex]::Replace($safe, '(?i)(Authorization\s*[:=]\s*(?:Bearer\s+)?)[^\s,;]+', '$1[REDACTED]')
	return $safe
}

function Convert-ToSafeRelativePath {
	param([Parameter(Mandatory = $true)][string] $Path)

	$normalized = $Path.Replace('\', '/')
	$rootNormalized = $repoRoot.Replace('\', '/').TrimEnd('/')
	if ($normalized.StartsWith($rootNormalized + '/', [System.StringComparison]::OrdinalIgnoreCase)) {
		return $normalized.Substring($rootNormalized.Length + 1)
	}

	if ([System.IO.Path]::IsPathRooted($Path)) {
		return '[OUTSIDE_REPOSITORY]/' + [System.IO.Path]::GetFileName($Path)
	}

	return $normalized.TrimStart('./')
}

function Invoke-NativeCapture {
	param(
		[Parameter(Mandatory = $true)][string] $Executable,
		[Parameter(Mandatory = $true)][string[]] $Arguments
	)

	$previousErrorActionPreference = $ErrorActionPreference
	try {
		# Windows PowerShell promotes native stderr records when the caller uses Stop.
		# Capture the native numeric exit and combined stream without treating ordinary
		# tool diagnostics as a PowerShell exception.
		$ErrorActionPreference = 'Continue'
		$output = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
		$exitCode = $LASTEXITCODE
	} catch {
		throw "Native command could not start: $([System.IO.Path]::GetFileName($Executable))"
	} finally {
		$ErrorActionPreference = $previousErrorActionPreference
	}

	if ($null -eq $exitCode) {
		throw "Native command returned no exit code: $([System.IO.Path]::GetFileName($Executable))"
	}

	return [pscustomobject]@{
		ExitCode = [int] $exitCode
		Output = [string[]] $output
	}
}

function Invoke-AuditSection {
	param(
		[Parameter(Mandatory = $true)][string] $Name,
		[Parameter(Mandatory = $true)][string] $Success,
		[Parameter(Mandatory = $true)][scriptblock] $Action
	)

	Add-AuditLine "## $Name"
	try {
		& $Action | Out-Null
		Add-AuditLine "PASS: $Success"
	} catch {
		$message = Protect-EvidenceText -Text $_.Exception.Message
		[void] $script:Failures.Add("$Name - $message")
		Add-AuditLine "FAIL: $message"
	}
	Add-AuditLine ''
}

function Write-AuditEvidence {
	if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
		return
	}

	$target = if ([System.IO.Path]::IsPathRooted($EvidencePath)) {
		Get-CanonicalPath -Path $EvidencePath -AllowMissing
	} else {
		Get-CanonicalPath -Path (Join-Path $repoRoot $EvidencePath) -AllowMissing
	}

	$parent = Split-Path -Parent $target
	if ([string]::IsNullOrWhiteSpace($parent) -or -not (Test-Path -LiteralPath $parent -PathType Container)) {
		throw 'Evidence parent directory does not exist.'
	}

	if ([string]::Equals($target, $scriptPath, [System.StringComparison]::OrdinalIgnoreCase)) {
		throw 'Evidence path must not overwrite the audit script.'
	}

	$allLines = [System.Collections.Generic.List[string]]::new()
	foreach ($line in $script:AuditLines) {
		[void] $allLines.Add($line)
	}
	if ($script:EvidenceDetails.Count -gt 0) {
		[void] $allLines.Add('## CAPTURED_EVIDENCE')
		foreach ($line in $script:EvidenceDetails) {
			[void] $allLines.Add((Protect-EvidenceText -Text $line))
		}
	}

	[System.IO.File]::WriteAllLines($target, [string[]] $allLines, [System.Text.UTF8Encoding]::new($false))
}

function Get-RgTextArguments {
	return @(
		'--hidden',
		'--no-ignore'
	)
}

function Assert-RgNoMatches {
	param(
		[Parameter(Mandatory = $true)][string] $Rule,
		[Parameter(Mandatory = $true)][string] $Pattern,
		[Parameter(Mandatory = $true)][string[]] $Scopes
	)

	$args = [System.Collections.Generic.List[string]]::new()
	foreach ($arg in (Get-RgTextArguments)) {
		[void] $args.Add($arg)
	}
	foreach ($arg in @('--files-with-matches', '--color', 'never', '--regexp', $Pattern, '--')) {
		[void] $args.Add($arg)
	}
	foreach ($scope in $Scopes) {
		[void] $args.Add($scope)
	}

	$result = Invoke-NativeCapture -Executable $script:RgExecutable -Arguments ([string[]] $args)
	if ($result.ExitCode -eq 1) {
		return
	}
	if ($result.ExitCode -ne 0) {
		throw "rg failed for rule '$Rule' with exit $($result.ExitCode)."
	}

	$matches = @($result.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { Convert-ToSafeRelativePath -Path $_ } | Sort-Object -Unique)
	$shown = @($matches | Select-Object -First 20)
	$suffix = if ($matches.Count -gt $shown.Count) { " (+$($matches.Count - $shown.Count) more)" } else { '' }
	throw "Forbidden $Rule surface found in: $($shown -join ', ')$suffix"
}

function Assert-OfficialRepositories {
	param([Parameter(Mandatory = $true)][string] $RelativePath)

	$path = Join-Path $repoRoot $RelativePath
	if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
		throw "Missing repository declaration file: $RelativePath"
	}

	$text = [System.IO.File]::ReadAllText($path)
	$forbiddenRepositorySyntax = '(?i)\b(?:mavenLocal|jcenter|google|flatDir|artifactUrls)\s*(?:\(|\{)|\bivy\s*\{|\b(?:MavenArtifactRepository|ArtifactRepository|RepositoryHandler)\b|\brepositories\s*(?:\+=|\.add\s*\()'
	if ([regex]::IsMatch($text, $forbiddenRepositorySyntax)) {
		throw "Non-approved repository mechanism found in $RelativePath."
	}

	$urlLinePattern = '(?im)^\s*(?:url|setUrl)\b(?<expression>[^\r\n]*)'
	$urlValuePattern = '(?i)\b(?:url|setUrl)\s*(?:=|\()\s*(?:uri\s*\(\s*)?["''](?<url>https?://[^"'']+)'
	$allowedUrls = @(
		'https://maven.fabricmc.net',
		'https://repo.maven.apache.org/maven2',
		'https://repo1.maven.org/maven2',
		'https://plugins.gradle.org/m2'
	)

	foreach ($lineMatch in [regex]::Matches($text, $urlLinePattern)) {
		$valueMatch = [regex]::Match($lineMatch.Value, $urlValuePattern)
		if (-not $valueMatch.Success) {
			throw "Dynamic or non-literal repository URL found in $RelativePath."
		}
		$normalized = $valueMatch.Groups['url'].Value.Trim().TrimEnd('/')
		if ($allowedUrls -cnotcontains $normalized) {
			throw "Non-approved repository URL found in ${RelativePath}: $normalized"
		}
	}

	$mavenBlockCount = [regex]::Matches($text, '(?i)\bmaven\s*\{').Count
	$urlAssignmentCount = [regex]::Matches($text, $urlLinePattern).Count
	if ($mavenBlockCount -gt $urlAssignmentCount) {
		throw "Maven repository block without a literal approved URL found in $RelativePath."
	}
}

function Get-GradleProperty {
	param([Parameter(Mandatory = $true)][string] $Name)

	$text = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'gradle.properties'))
	$match = [regex]::Match($text, '(?m)^\s*' + [regex]::Escape($Name) + '=(?<value>[^\r\n]+)\s*$')
	if (-not $match.Success) {
		throw "Missing Gradle property: $Name"
	}
	return $match.Groups['value'].Value.Trim()
}

function Assert-ProductionArchive {
	param([Parameter(Mandatory = $true)][string] $RequestedJarPath)

	$resolved = if ([System.IO.Path]::IsPathRooted($RequestedJarPath)) {
		Get-CanonicalPath -Path $RequestedJarPath
	} else {
		Get-CanonicalPath -Path (Join-Path $repoRoot $RequestedJarPath)
	}
	if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
		throw 'JAR path does not resolve to a file.'
	}

	$expectedName = "$(Get-GradleProperty -Name 'archives_base_name')-$(Get-GradleProperty -Name 'mod_version').jar"
	if ([System.IO.Path]::GetFileName($resolved) -cne $expectedName) {
		throw "Expected exact ordinary production archive '$expectedName'."
	}

	Add-Type -AssemblyName System.IO.Compression -ErrorAction Stop
	Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
	$archive = $null
	try {
		$archive = [System.IO.Compression.ZipFile]::OpenRead($resolved)
		$entries = @($archive.Entries)
		if ($entries.Count -eq 0) {
			throw 'Production archive is empty.'
		}

		$entryNames = @($entries | ForEach-Object { $_.FullName.Replace('\', '/') })
		if (@($entryNames | Where-Object { $_ -eq 'fabric.mod.json' }).Count -ne 1) {
			throw 'Production archive must contain exactly one root fabric.mod.json.'
		}
		if (@($entryNames | Where-Object { $_ -match '(?i)(?:^|/)fabric[.]mod[.]json$' }).Count -ne 1) {
			throw 'Production archive contains nested or duplicate Fabric metadata.'
		}

		$licenseEntries = @($entryNames | Where-Object { $_ -match '(?i)(?:^|/)LICENSE(?:$|[_\-.].*)' })
		if ($licenseEntries.Count -ne 1 -or $licenseEntries[0] -cne 'LICENSE_developers-hell') {
			throw 'Production archive must contain exactly one renamed root LICENSE_developers-hell entry.'
		}

		$requiredEntries = @(
			'fabric.mod.json',
			'LICENSE_developers-hell',
			'dev/developershell/DevelopersHell.class',
			'dev/developershell/client/DevelopersHellClient.class',
			'dev/developershell/module/ModuleGate.class',
			'dev/developershell/module/ModuleId.class',
			'dev/developershell/registry/ModItemIds.class',
			'dev/developershell/registry/ModItems.class',
			'assets/developers_hell/lang/en_us.json',
			'assets/developers_hell/items/foundation_token.json',
			'assets/developers_hell/models/item/foundation_token.json'
		)
		foreach ($requiredEntry in $requiredEntries) {
			if ($entryNames -cnotcontains $requiredEntry) {
				throw "Production archive is missing required entry: $requiredEntry"
			}
		}

		$unsafeEntryPattern = '(?i)(?:^|/)(?:dev/developershell/gametest|gametest|gametests|test|tests)(?:/|$)|(?:^|/)[^/]*(?:Test|Tests|TestCase)(?:\$[^/]*)?[.]class$|FoundationGameTests(?:\$[^/]*)?[.]class$|ModuleGateTest(?:\$[^/]*)?[.]class$'
		$shadedPattern = '(?i)^(?:com/openai|okhttp3|retrofit2|io/sentry|com/mixpanel|com/amplitude|com/segment|io/segment|com/google/firebase/remoteconfig|com/launchdarkly|io/getunleash|dev/openfeature|org/apache/http|com/squareup/okhttp|org/eclipse/jetty/client|io/netty/handler/codec/http)(?:/|$)'
		$residuePattern = '(?i)(?:^|/)(?:com/example|example[-_]?mod|examplemod|modid)(?:/|$)|(?:^|/)(?:mixin|mixins)(?:/|$)|(?:^|/)[^/]*(?:ExampleMod|ExampleMixin)(?:\$[^/]*)?[.]class$|(?:^|/)[^/]*Mixin(?:\$[^/]*)?[.]class$|[.]mixins?[.]json$'
		$credentialFilePattern = '(?i)(?:^|/)(?:[.]env(?:[.][^/]*)?|credentials?(?:[.][^/]*)?|secrets?(?:[.][^/]*)?|id_rsa|id_ed25519|application[-_]?secrets?[.]properties|[^/]+[.](?:pem|p12|pfx|jks|keystore|key))$'

		$badTests = @($entryNames | Where-Object { $_ -match $unsafeEntryPattern })
		if ($badTests.Count -gt 0) {
			throw "Test output shipped in production archive: $($badTests[0])"
		}
		$badNamespaces = @($entryNames | Where-Object { $_ -match $shadedPattern })
		if ($badNamespaces.Count -gt 0) {
			throw "Forbidden shaded network/remote-service namespace: $($badNamespaces[0])"
		}
		$badResidue = @($entryNames | Where-Object { $_ -match $residuePattern })
		if ($badResidue.Count -gt 0) {
			throw "Example or mixin residue found in production archive: $($badResidue[0])"
		}
		$badCredentials = @($entryNames | Where-Object { $_ -match $credentialFilePattern })
		if ($badCredentials.Count -gt 0) {
			throw "Credential-like file found in production archive: $($badCredentials[0])"
		}

		$latin1 = [System.Text.Encoding]::GetEncoding(28591)
		$archiveStringPattern = '(?i)(?:developers_hell_test|fabric-gametest|java[/\\.]net(?:[/\\.]|\b)|java[/\\.]net[/\\.]http|https?://|wss?://|com[/\\.]openai|okhttp3|retrofit2|io[/\\.]sentry|com[/\\.](?:mixpanel|amplitude|segment)|io[/\\.]segment|remote[-_./ ]?config(?:uration)?|LaunchDarkly|UnleashClient|FirebaseRemoteConfig|OpenAI(?:Api|API|Client|Sdk|SDK|Service)|ChatGPT(?:Api|API|Client|Sdk|SDK|Service)|api[-_. ]?key|access[-_. ]?token|Authorization\s*[:=]|Bearer\s+[A-Za-z0-9._~+/=-]+)'
		$metadataText = $null
		$totalBytes = [int64] 0
		foreach ($entry in $entries) {
			$name = $entry.FullName.Replace('\', '/')
			if ($name.StartsWith('/') -or $name -match '(?:^|/)[.][.](?:/|$)') {
				throw "Unsafe archive entry path: $name"
			}
			if ($name.EndsWith('/')) {
				continue
			}
			if ($entry.Length -lt 0 -or $entry.Length -gt 33554432) {
				throw "Archive entry is too large to audit safely: $name"
			}
			$totalBytes += $entry.Length
			if ($totalBytes -gt 134217728) {
				throw 'Production archive expands beyond the audit safety limit.'
			}

			$mustRead = $name -eq 'fabric.mod.json' -or $name -match '(?i)[.](?:class|json|json5|mcmeta|properties|toml|ya?ml|xml|txt|csv|lang|cfg|conf|mf)$'
			if (-not $mustRead) {
				continue
			}

			$stream = $null
			$memory = $null
			try {
				$stream = $entry.Open()
				$memory = [System.IO.MemoryStream]::new()
				$stream.CopyTo($memory)
				$bytes = $memory.ToArray()
			} finally {
				if ($null -ne $memory) { $memory.Dispose() }
				if ($null -ne $stream) { $stream.Dispose() }
			}

			$content = $latin1.GetString($bytes)
			if ($name -eq 'fabric.mod.json') {
				$metadataText = [System.Text.Encoding]::UTF8.GetString($bytes)
			}
			if ([regex]::IsMatch($content, $archiveStringPattern)) {
				throw "Forbidden test/network/remote-service marker found in archive entry: $name"
			}
		}

		if ([string]::IsNullOrWhiteSpace($metadataText)) {
			throw 'Root fabric.mod.json could not be read.'
		}
		try {
			$metadata = $metadataText | ConvertFrom-Json -ErrorAction Stop
		} catch {
			throw 'Root fabric.mod.json is not valid JSON.'
		}
		if ($metadata.id -cne 'developers_hell') {
			throw 'Root fabric.mod.json must declare production id developers_hell.'
		}
		$metadataCompact = $metadataText -replace '\s+', ''
		if ($metadataCompact -match '(?i)developers_hell_test|fabric-gametest') {
			throw 'Production metadata contains test mod identity or fabric-gametest entrypoint.'
		}

		$jarHash = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
		Add-EvidenceDetail "production_archive=$expectedName"
		Add-EvidenceDetail "production_archive_sha256=$jarHash"
		Add-EvidenceDetail "production_archive_entries=$($entryNames.Count)"
	} catch {
		if ($_.Exception.Message -match 'central directory|archive|ZIP|End of Central Directory') {
			throw "Archive inspection failed closed: $($_.Exception.Message)"
		}
		throw
	} finally {
		if ($null -ne $archive) {
			$archive.Dispose()
		}
	}
}

function Test-ForbiddenGitPath {
	param([Parameter(Mandatory = $true)][string] $Path)

	$normalized = $Path.Trim('"').Replace('\', '/').TrimStart('./')
	$pattern = '(?i)^(?:[.]gradle|build|out|classes|dist|run|logs|[.]work|[.]idea|[.]vscode|[.]settings|bin|[.]jdk|jdk)(?:/|$)|(?:^|/)(?:world|world_nether|world_the_end|saves|private|credentials|secrets?|certificates?|keys?)(?:/|$)|(?:^|/)eula[.]txt$|(?:^|/)(?:local[.]properties|gradle-local[.]properties|gradle[.]properties[.]local|[.]env(?:[.][^/]*)?|credentials?(?:[.][^/]*)?|secrets?(?:[.][^/]*)?|id_rsa|id_ed25519|server[.]key)$|(?:^|/)[^/]+[.](?:pem|key|p12|pfx|jks|keystore)$|[.](?:log|hprof|jfr|jmc)$|(?:^|/)(?:hs_err|replay)_pid[^/]*'
	return [regex]::IsMatch($normalized, $pattern)
}

Add-AuditLine '# Developer''s Hell Foundation Audit'
Add-AuditLine 'Evidence policy: repository-relative paths only; machine roots and credential-like values are redacted.'
Add-AuditLine ''

if (-not $SourceAndDependencies) {
	Add-AuditLine '## INVOCATION'
	Add-AuditLine 'FAIL: -SourceAndDependencies is required for the fail-closed foundation audit.'
	[void] $script:Failures.Add('INVOCATION - missing -SourceAndDependencies')
	Add-AuditLine ''
} else {
	Invoke-AuditSection -Name 'PREREQUISITES' -Success 'rg, wrapper, Git, source roots, and build inputs are available.' -Action {
		Initialize-VerifiedGradleRuntime
		$rgCommand = Get-Command -Name 'rg' -CommandType Application -ErrorAction Stop | Select-Object -First 1
		if ($null -eq $rgCommand -or [string]::IsNullOrWhiteSpace($rgCommand.Source)) {
			throw 'rg executable was not found.'
		}
		$script:RgExecutable = $rgCommand.Source
		$rgVersion = Invoke-NativeCapture -Executable $script:RgExecutable -Arguments @('--version')
		if ($rgVersion.ExitCode -ne 0 -or $rgVersion.Output.Count -eq 0) {
			throw "rg version probe failed with exit $($rgVersion.ExitCode)."
		}

		foreach ($requiredPath in @('src/main', 'src/client', 'build.gradle', 'settings.gradle', 'gradle.properties', '.gitignore', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties')) {
			if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $requiredPath))) {
				throw "Required foundation input is missing: $requiredPath"
			}
		}
		$script:WrapperExecutable = Join-Path $repoRoot 'gradlew.bat'

		foreach ($scope in @('src/main', 'src/client')) {
			$args = [System.Collections.Generic.List[string]]::new()
			foreach ($arg in (Get-RgTextArguments)) { [void] $args.Add($arg) }
			foreach ($arg in @('--files', '--', $scope)) { [void] $args.Add($arg) }
			$result = Invoke-NativeCapture -Executable $script:RgExecutable -Arguments ([string[]] $args)
			if ($result.ExitCode -ne 0) {
				throw "rg recursive file enumeration failed for $scope with exit $($result.ExitCode)."
			}
			$fileCount = @($result.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
			if ($fileCount -eq 0) {
				throw "rg found no Java/resource text to audit under $scope."
			}
			Add-EvidenceDetail "rg_scope_$($scope.Replace('/', '_'))_source_files=$fileCount"
		}
	}

	Invoke-AuditSection -Name 'SOURCE_RUNTIME_SURFACES' -Success 'Recursive main/client Java and resource scans found no runtime network, remote-service, telemetry, credential, or download surface.' -Action {
		$rules = [ordered]@{
			'java.net import or qualified use' = '(?i)\bimport\s+(?:static\s+)?java[.]net(?:[.]|\b)|\bjava[.]net[.]'
			'network type' = '(?i)\b(?:URL|URI\.create|URLConnection|HttpURLConnection|Socket|ServerSocket|DatagramSocket|SocketChannel|DatagramChannel|HttpClient|HttpRequest|HttpResponse|WebSocket)\b'
			'network opening method' = '(?i)\b(?:openConnection|openStream)\s*\('
			'HTTP or WebSocket endpoint' = '(?i)\b(?:https?|wss?)://'
			'OpenAI or ChatGPT SDK/client' = '(?i)(?:\bcom[.]openai\b|\bopenai[-_.](?:java|client|sdk)\b|\b(?:OpenAI|ChatGPT)(?:Api|API|Client|Sdk|SDK|Service|Connector|Transport)\b)'
			'analytics or telemetry SDK' = '(?i)(?:\banalytics\b|\btelemetry\b|\bio[.]sentry\b|\bsentry(?:[-_. ]?(?:dsn|client|sdk))?\b|\bcom[.](?:mixpanel|amplitude|segment)\b|\bio[.]segment\b|\b(?:AnalyticsClient|AnalyticsService|AnalyticsTracker|MixpanelAPI|AmplitudeClient|SegmentAnalytics)\b)'
			'remote configuration client' = '(?i)(?:\bremote[-_. ]?config(?:uration)?\b|\b(?:LaunchDarkly|UnleashClient|FirebaseRemoteConfig|OpenFeatureClient)\b)'
			'account credential or authorization' = '(?i)\b(?:Authorization|Bearer|api[-_. ]?key|access[-_. ]?token|auth[-_. ]?token|secret[-_. ]?key|account[-_. ]?(?:id|token|key)|credential(?:s)?)\b'
			'runtime download code' = '(?i)\b(?:download\w*|fetchRemote\w*|remoteAsset\w*)\s*\('
			'external network command' = '(?i)\b(?:curl|wget|Invoke-WebRequest|Invoke-RestMethod|Start-BitsTransfer)\b'
		}
		foreach ($rule in $rules.GetEnumerator()) {
			Assert-RgNoMatches -Rule $rule.Key -Pattern $rule.Value -Scopes @('src/main', 'src/client')
		}
	}

	Invoke-AuditSection -Name 'COMMON_CLIENT_LINKAGE' -Success 'Common/main source contains no net.minecraft.client or Blaze3D linkage; client APIs remain allowed under src/client.' -Action {
		Assert-RgNoMatches -Rule 'common-side client linkage' -Pattern '(?i)\b(?:net[.]minecraft[.]client|com[.]mojang[.]blaze3d)(?:[.]|\b)' -Scopes @('src/main')
	}

	Invoke-AuditSection -Name 'OFFICIAL_REPOSITORIES' -Success 'Repository declarations are limited to literal official Fabric, Maven Central, and Gradle Plugin Portal sources.' -Action {
		Assert-OfficialRepositories -RelativePath 'settings.gradle'
		Assert-OfficialRepositories -RelativePath 'build.gradle'

		$settings = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'settings.gradle'))
		if (-not [regex]::IsMatch($settings, '(?i)https://maven[.]fabricmc[.]net/?')) {
			throw 'settings.gradle is missing the official Fabric Maven repository.'
		}
		if (-not [regex]::IsMatch($settings, '(?i)\bmavenCentral\s*\(')) {
			throw 'settings.gradle is missing mavenCentral().'
		}
		if (-not [regex]::IsMatch($settings, '(?i)\bgradlePluginPortal\s*\(')) {
			throw 'settings.gradle is missing gradlePluginPortal().'
		}
	}

	Invoke-AuditSection -Name 'DIRECT_DEPENDENCIES' -Success 'Gradle auditDirectDependencies accepted only the exact five project-owned direct declarations.' -Action {
		$buildText = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'build.gradle'))
		if (-not [regex]::IsMatch($buildText, '(?m)\bauditDirectDependencies\b')) {
			throw 'build.gradle does not define auditDirectDependencies.'
		}

		$result = Invoke-NativeCapture -Executable $script:WrapperExecutable -Arguments ($script:GradleJvmArguments + @('auditDirectDependencies', '--no-daemon', '--console=plain'))
		Add-EvidenceDetail '### COMMAND auditDirectDependencies'
		Add-EvidenceDetail '.\gradlew.bat auditDirectDependencies --no-daemon --console=plain'
		Add-EvidenceDetail "exit_code=$($result.ExitCode)"
		foreach ($line in $result.Output) { Add-EvidenceDetail (Protect-EvidenceText -Text $line) }
		if ($result.ExitCode -ne 0) {
			throw "auditDirectDependencies failed with exit $($result.ExitCode)."
		}

		$expectedMarkerValues = @(
			"implementation|net.fabricmc:fabric-loader:$(Get-GradleProperty -Name 'loader_version')",
			"implementation|net.fabricmc.fabric-api:fabric-api:$(Get-GradleProperty -Name 'fabric_api_version')",
			"minecraft|com.mojang:minecraft:$(Get-GradleProperty -Name 'minecraft_version')",
			"productionRuntimeMods|net.fabricmc.fabric-api:fabric-api:$(Get-GradleProperty -Name 'fabric_api_version')",
			"testImplementation|net.fabricmc:fabric-loader-junit:$(Get-GradleProperty -Name 'loader_version')"
		) | Sort-Object
		$markerMatches = @([regex]::Matches(($result.Output -join "`n"), '(?m)^DEVELOPERS_HELL_DIRECT_DEPENDENCIES=(?<value>[^\r\n]+)\s*$'))
		if ($markerMatches.Count -ne 1) {
			throw 'auditDirectDependencies did not emit exactly one direct-declaration evidence marker.'
		}
		$actualMarkerValues = @($markerMatches[0].Groups['value'].Value.Split(',') | ForEach-Object { $_.Trim() } | Sort-Object)
		if (($actualMarkerValues -join "`n") -cne ($expectedMarkerValues -join "`n")) {
			throw 'auditDirectDependencies evidence marker does not equal the exact five approved configuration/coordinate pairs.'
		}
	}

	Invoke-AuditSection -Name 'RUNTIME_CLASSPATH_REPORT' -Success 'runtimeClasspath was captured as report-only transitive evidence; the direct allowlist was not applied to its transitive nodes.' -Action {
		$result = Invoke-NativeCapture -Executable $script:WrapperExecutable -Arguments ($script:GradleJvmArguments + @('dependencies', '--configuration', 'runtimeClasspath', '--no-daemon', '--console=plain'))
		$script:RuntimeClasspathOutput = $result.Output
		Add-EvidenceDetail '### COMMAND runtimeClasspath report (report-only, no transitive allowlist)'
		Add-EvidenceDetail '.\gradlew.bat dependencies --configuration runtimeClasspath --no-daemon --console=plain'
		Add-EvidenceDetail "exit_code=$($result.ExitCode)"
		foreach ($line in $result.Output) { Add-EvidenceDetail (Protect-EvidenceText -Text $line) }
		if ($result.ExitCode -ne 0) {
			throw "runtimeClasspath report failed with exit $($result.ExitCode)."
		}
		if ($result.Output.Count -eq 0 -or -not (($result.Output -join "`n") -match '(?i)runtimeClasspath')) {
			throw 'runtimeClasspath report was empty or did not identify the requested configuration.'
		}
	}

	if ([string]::IsNullOrWhiteSpace($JarPath)) {
		Add-AuditLine '## PRODUCTION_ARCHIVE'
		Add-AuditLine 'PASS: Optional -JarPath was not supplied; source/dependency audit remains complete.'
		Add-AuditLine ''
	} else {
		Invoke-AuditSection -Name 'PRODUCTION_ARCHIVE' -Success 'Exact ordinary JAR has production metadata/license/content only and no test, example, mixin, shaded SDK, remote-service, or credential residue.' -Action {
			Assert-ProductionArchive -RequestedJarPath $JarPath
		}
	}

	Invoke-AuditSection -Name 'WRAPPER_AND_GIT_HYGIENE' -Success 'Wrapper inputs are tracked and unignored; generated, run, world, EULA, log, local-JDK, and private paths are ignored and absent from tracked/visible files.' -Action {
		$git = Get-Command -Name 'git' -CommandType Application -ErrorAction Stop | Select-Object -First 1
		if ($null -eq $git -or [string]::IsNullOrWhiteSpace($git.Source)) {
			throw 'git executable was not found.'
		}
		$script:GitExecutable = $git.Source
		# Disable the caller's global excludes file while retaining repository rules.
		# An empty value is portable in Git for Windows; the DOS NUL device is not
		# accepted by Git's exclude-file parser.
		$gitPrefix = @('-c', 'core.excludesFile=')

		$top = Invoke-NativeCapture -Executable $script:GitExecutable -Arguments ($gitPrefix + @('rev-parse', '--show-toplevel'))
		if ($top.ExitCode -ne 0 -or $top.Output.Count -ne 1) {
			throw "git repository-root probe failed with exit $($top.ExitCode)."
		}
		$gitRoot = Get-CanonicalPath -Path $top.Output[0]
		if (-not [string]::Equals($gitRoot, $repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
			throw 'Audit script root does not equal Git repository root.'
		}

		$wrapperInputs = @('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties')
		foreach ($path in $wrapperInputs) {
			$result = Invoke-NativeCapture -Executable $script:GitExecutable -Arguments ($gitPrefix + @('ls-files', '--error-unmatch', '--', $path))
			if ($result.ExitCode -ne 0) {
				throw "Required wrapper input is not tracked: $path"
			}
		}

		$wrapperIgnore = Invoke-NativeCapture -Executable $script:GitExecutable -Arguments ($gitPrefix + @('check-ignore', '--no-index', '-q', '--', 'gradle/wrapper/gradle-wrapper.jar'))
		if ($wrapperIgnore.ExitCode -eq 0) {
			throw 'gradle/wrapper/gradle-wrapper.jar is ignored.'
		}
		if ($wrapperIgnore.ExitCode -ne 1) {
			throw "git check-ignore failed for wrapper JAR with exit $($wrapperIgnore.ExitCode)."
		}

		$ignoredRepresentatives = @(
			'.gradle/foundation-audit/state.bin',
			'build/foundation-audit.tmp',
			'out/foundation-audit.class',
			'dist/developers-hell-0.1.0.jar',
			'.work/toolchain/private.txt',
			'run/production-client/world/level.dat',
			'run/production-server/world/level.dat',
			'run/production-server/eula.txt',
			'run/production-server/logs/latest.log',
			'logs/latest.log',
			'.idea/workspace.xml',
			'local.properties'
		)
		foreach ($path in $ignoredRepresentatives) {
			$result = Invoke-NativeCapture -Executable $script:GitExecutable -Arguments ($gitPrefix + @('check-ignore', '--no-index', '-q', '--', $path))
			if ($result.ExitCode -ne 0) {
				if ($result.ExitCode -eq 1) {
					throw "Representative generated/private path is visible to Git: $path"
				}
				throw "git check-ignore failed for $path with exit $($result.ExitCode)."
			}
		}

		$tracked = Invoke-NativeCapture -Executable $script:GitExecutable -Arguments ($gitPrefix + @('ls-files'))
		if ($tracked.ExitCode -ne 0) {
			throw "git ls-files failed with exit $($tracked.ExitCode)."
		}
		$visible = Invoke-NativeCapture -Executable $script:GitExecutable -Arguments ($gitPrefix + @('ls-files', '--others', '--exclude-standard'))
		if ($visible.ExitCode -ne 0) {
			throw "git untracked-file audit failed with exit $($visible.ExitCode)."
		}
		$status = Invoke-NativeCapture -Executable $script:GitExecutable -Arguments ($gitPrefix + @('status', '--porcelain=v1', '--untracked-files=all'))
		if ($status.ExitCode -ne 0) {
			throw "git status failed with exit $($status.ExitCode)."
		}

		$forbiddenTracked = @($tracked.Output | Where-Object { Test-ForbiddenGitPath -Path $_ })
		if ($forbiddenTracked.Count -gt 0) {
			throw "Generated/run/private file is tracked: $(Convert-ToSafeRelativePath -Path $forbiddenTracked[0])"
		}
		$forbiddenVisible = @($visible.Output | Where-Object { Test-ForbiddenGitPath -Path $_ })
		if ($forbiddenVisible.Count -gt 0) {
			throw "Generated/run/private file is visible to Git: $(Convert-ToSafeRelativePath -Path $forbiddenVisible[0])"
		}

		$wrapperProperties = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'gradle/wrapper/gradle-wrapper.properties'))
		if ($wrapperProperties -notmatch '(?m)^distributionUrl=https\\://services[.]gradle[.]org/distributions/gradle-9[.]5[.]1-bin[.]zip\s*$') {
			throw 'Wrapper distributionUrl is not exact Gradle 9.5.1 binary distribution.'
		}
		if ($wrapperProperties -notmatch '(?m)^validateDistributionUrl=true\s*$') {
			throw 'Wrapper URL validation is not enabled.'
		}

		$committedConfiguration = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'gradle.properties')) + "`n" +
			[System.IO.File]::ReadAllText((Join-Path $repoRoot 'settings.gradle')) + "`n" +
			[System.IO.File]::ReadAllText((Join-Path $repoRoot 'build.gradle'))
		if ($committedConfiguration -match '(?im)^\s*org[.]gradle[.]java[.](?:home|installations[.]paths)\s*=') {
			throw 'Committed Gradle configuration contains a machine-local Java path.'
		}
		if ($committedConfiguration -match '(?i)\b[A-Z]:\\Users\\[^\s"'']+') {
			throw 'Committed Gradle configuration contains an absolute user-profile path.'
		}

		Add-EvidenceDetail "tracked_file_count=$($tracked.Output.Count)"
		Add-EvidenceDetail "visible_untracked_file_count=$($visible.Output.Count)"
		Add-EvidenceDetail "working_tree_status_entry_count=$($status.Output.Count)"
	}
}

if ($script:Failures.Count -eq 0) {
	Add-AuditLine '## FINAL_RESULT'
	Add-AuditLine 'PASS: FOUNDATION_AUDIT'
	Add-AuditLine ''
	try {
		Write-AuditEvidence
	} catch {
		Add-AuditLine "FAIL: Evidence write failed closed: $(Protect-EvidenceText -Text $_.Exception.Message)"
		exit 1
	}
	exit 0
}

Add-AuditLine '## FINAL_RESULT'
Add-AuditLine "FAIL: FOUNDATION_AUDIT ($($script:Failures.Count) section failure(s))"
foreach ($failure in $script:Failures) {
	Add-AuditLine "- $failure"
}
Add-AuditLine ''

try {
	Write-AuditEvidence
} catch {
	Add-AuditLine "FAIL: Evidence write failed closed: $(Protect-EvidenceText -Text $_.Exception.Message)"
}
exit 1
