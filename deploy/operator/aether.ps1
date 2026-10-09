#Requires -Version 7.0
<#
Owner-operated preparation and checks. No action downloads models or activates a production host.
Remote uploads are staged under a new release ID; behavior publication requires a stopped Aether process.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Init', 'Check', 'Build', 'Bundle', 'Prepare', 'PrepareBehavior',
        'UploadPlan', 'PromoteBehaviorPlan', 'Probe', 'Generate', 'Restart')][string]$Action,
    [string]$ConnectionConfig = (Join-Path $PSScriptRoot '../../src/main/resources/aether/aether-connection.local.json'),
    [string]$ServerConfig = (Join-Path $PSScriptRoot '../../src/main/resources/aether/aether-server.yml'),
    [string]$PromptsPath = '', [string]$BundleJar = '', [string]$ReleaseDirectory = '',
    [switch]$ServerStopped, [switch]$ExecuteSftp
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'AetherOperator.psm1') -Force
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$operatorDirectory = Join-Path $repository '.codex-build/aether-operator'
$settingsDirectory = Join-Path $repository 'src/main/resources/aether'
$gatewayJar = Join-Path $repository 'aether-server/.codex-build/libs/Aether-Gateway.jar'
$ConnectionConfig = [IO.Path]::GetFullPath($ConnectionConfig)
$ServerConfig = [IO.Path]::GetFullPath($ServerConfig)
if (!$PromptsPath) { $PromptsPath = Join-Path $settingsDirectory 'aether-prompts.yml' }

function Invoke-CheckedNative([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Command failed; do not publish this candidate.' }
}

function Get-OperatorJava {
    $homeDirectory = if ($connection.javaHome) { $connection.javaHome } else { $env:JAVA_HOME }
    if ($homeDirectory) {
        $candidate = Join-Path $homeDirectory 'bin/java.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
        throw 'The configured Java home does not contain Java. Use JDK 21.'
    }
    return (Get-Command java -ErrorAction Stop).Source
}

function Test-OperatorPrompts([string]$Path) {
    if (!(Test-Path -LiteralPath $gatewayJar)) { throw 'Run Build first to obtain the current gateway validator.' }
    Invoke-CheckedNative (Get-OperatorJava) @('-jar', $gatewayJar, '--validate-prompts', $Path)
}

function Test-OperatorConfig([string]$Path) {
    if (!(Test-Path -LiteralPath $gatewayJar)) { throw 'Run Build first to obtain the current gateway validator.' }
    Invoke-CheckedNative (Get-OperatorJava) @('-jar', $gatewayJar, '--validate-config', $Path)
}

function Copy-InitialSetting([string]$Source, [string]$Destination) {
    if (!(Test-Path -LiteralPath $Destination)) {
        New-Item -ItemType Directory -Path (Split-Path $Destination -Parent) -Force | Out-Null
        [IO.File]::Copy($Source, $Destination, $false)
    }
}

function Write-ReleaseManifest([string]$Directory, [string]$Id, [string]$Kind) {
    $files = @(Get-ChildItem -LiteralPath $Directory -File -Recurse | ForEach-Object {
        @{ path = [IO.Path]::GetRelativePath($Directory, $_.FullName).Replace('\', '/');
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash; bytes = $_.Length }
    })
    @{ releaseId = $Id; kind = $Kind; createdUtc = [DateTime]::UtcNow.ToString('o');
        hostVerified = $false; files = $files } | ConvertTo-Json -Depth 10 |
        Set-Content -LiteralPath (Join-Path $Directory 'release.json') -Encoding utf8
}

function Write-SftpPlan([string[]]$Commands, [string]$PlanName) {
    $hostName = $connection.sftp.host
    $userName = $connection.sftp.user
    $port = $connection.sftp.port
    if ($hostName -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$' -or
        $userName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$' -or $port -lt 1 -or $port -gt 65535) {
        throw 'Fill in the SFTP host, username and port from Kinetic before creating a transfer plan.'
    }
    $plan = Join-Path (Resolve-Path -LiteralPath $ReleaseDirectory).Path ".operator-$PlanName.sftp"
    $Commands | Set-Content -LiteralPath $plan -Encoding utf8NoBOM
    if ($ExecuteSftp) {
        if (!$connection.sftp.identityFile) {
            throw 'Automatic batch transfer needs a provider-supported SSH key. For password login, use the interactive instructions below without ExecuteSftp.'
        }
        Invoke-CheckedNative (Get-Command sftp -ErrorAction Stop).Source @('-P', "$port", '-i',
            $connection.sftp.identityFile, '-b', $plan, "$userName@$hostName")
    } else {
        "Plan: $plan"
        "Connect from your terminal: sftp -P $port $userName@$hostName"
        'After login, paste these commands. Authenticate in SFTP; never put your password in this config.'
        $Commands
    }
}

if ($Action -eq 'Init') {
    $connectionSource = Join-Path $settingsDirectory 'aether-connection.example.json'
    $legacyConnection = Join-Path $PSScriptRoot 'aether-connection.local.json'
    if ($ConnectionConfig -eq (Join-Path $settingsDirectory 'aether-connection.local.json') -and
        (Test-Path -LiteralPath $legacyConnection)) {
        $connectionSource = $legacyConnection
    }
    Copy-InitialSetting $connectionSource $ConnectionConfig
    Copy-InitialSetting (Join-Path $settingsDirectory 'aether-server.example.yml') $ServerConfig
    $promptsSource = Join-Path $repository 'aether-server/src/main/resources/aether-prompts.yml'
    $legacyPrompts = Join-Path $operatorDirectory 'behavior/aether-prompts.yml'
    if ($PromptsPath -eq (Join-Path $settingsDirectory 'aether-prompts.yml') -and
        (Test-Path -LiteralPath $legacyPrompts)) {
        $promptsSource = $legacyPrompts
    }
    Copy-InitialSetting $promptsSource $PromptsPath
    "Connection config: $ConnectionConfig"
    "Standalone server config: $ServerConfig"
    "Editable behavior: $PromptsPath"
    'Existing settings were preserved. Fill in the local files; keep credentials in environment variables.'
    return
}
if (!(Test-Path -LiteralPath $ConnectionConfig)) {
    if ($Action -ne 'Check') { throw 'Run Init, then fill in the local connection config.' }
    $legacyConnection = Join-Path $PSScriptRoot 'aether-connection.local.json'
    $ConnectionConfig = if (Test-Path -LiteralPath $legacyConnection) { $legacyConnection }
        else { Join-Path $settingsDirectory 'aether-connection.example.json' }
}
$connection = Get-Content -LiteralPath $ConnectionConfig -Raw | ConvertFrom-Json -AsHashtable

switch ($Action) {
    'Check' {
        [ordered]@{
            connectionConfig = $ConnectionConfig
            serverConfig = $ServerConfig
            serverConfigExists = (Test-Path -LiteralPath $ServerConfig)
            gatewayConfigured = [bool]$connection.gatewayUrl
            sftpConfigured = [bool]($connection.sftp.host -and $connection.sftp.user)
            terminalRestartConfigured = [bool]$connection.panel.restartPath
            gatewayJarExists = (Test-Path -LiteralPath $gatewayJar)
            editablePromptsExist = (Test-Path -LiteralPath $PromptsPath)
            hostCapacity = 'UNVERIFIED'
            liveMinecraft = 'UNVERIFIED'
        } | ConvertTo-Json
        if ($connection.gatewayUrl) { Get-AetherBaseUri $connection.gatewayUrl | Out-Null }
        if (Test-Path -LiteralPath $PromptsPath) { Test-OperatorPrompts $PromptsPath }
        if (Test-Path -LiteralPath $ServerConfig) { Test-OperatorConfig $ServerConfig }
    }
    { $_ -in @('Build', 'Bundle') } {
        $java = Get-OperatorJava
        $javaVersion = & $java -version 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?:java|openjdk) version "21\.') { throw 'Build with JDK 21.' }
        $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
        New-Item -ItemType Directory -Path $operatorDirectory -Force | Out-Null
        $buildLock = [IO.File]::Open((Join-Path $operatorDirectory 'build.lock'),
            [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        try {
            if ($Action -eq 'Build') {
                $arguments = @(':aether-server:test', ':aether-server:verifyStandalone', ':aiTest', ':jar')
            } else {
                if ($connection.platform -notin @('linux-amd64', 'linux-arm64', 'windows-amd64') -or
                    !$connection.runtimeDirectory -or !$connection.modelDirectory) { throw 'Configure explicit native runtime and installed model paths for Bundle.' }
                $arguments = @(':aether-server:bundleJar', "-PaetherBundlePlatform=$($connection.platform)",
                    "-PaetherRuntimeDirectory=$($connection.runtimeDirectory)",
                    "-PaetherModelDirectory=$($connection.modelDirectory)", "-PaetherSourceModel=$($connection.sourceModel)")
            }
            Push-Location $repository
            try { Invoke-CheckedNative (Join-Path $repository 'gradlew.bat') ($arguments + @('-PcodexBuildDir=.codex-build', '--no-parallel', '--console=plain')) }
            finally { Pop-Location }
        } finally { $buildLock.Dispose() }
    }
    { $_ -in @('Prepare', 'PrepareBehavior') } {
        if ($Action -eq 'Prepare') {
            if (!(Test-Path -LiteralPath $ServerConfig)) { throw 'Run Init, then fill in the local standalone server config.' }
            Test-OperatorConfig $ServerConfig
        }
        Test-OperatorPrompts $PromptsPath
        $id = 'aether-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
        $stage = Join-Path $operatorDirectory "releases/$id"
        New-Item -ItemType Directory -Path (Join-Path $stage 'aether') | Out-Null
        Copy-Item -LiteralPath $PromptsPath -Destination (Join-Path $stage 'aether/aether-prompts.yml')
        if ($Action -eq 'Prepare') {
            Copy-Item -LiteralPath $gatewayJar -Destination $stage
            Copy-Item -LiteralPath $ServerConfig -Destination (Join-Path $stage 'aether/aether-server.yml')
            Test-OperatorConfig (Join-Path $stage 'aether/aether-server.yml')
            $versionLine = Get-Content -LiteralPath (Join-Path $repository 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }
            $version = ($versionLine -split '=', 2)[1].Trim()
            $mod = Join-Path $repository ".codex-build/libs/wildernessodysseyapi-$version.jar"
            if (!(Test-Path -LiteralPath $mod)) { throw 'Build the regular Wilderness API mod before preparing a release.' }
            New-Item -ItemType Directory -Path (Join-Path $stage 'minecraft') | Out-Null
            Copy-Item -LiteralPath $mod -Destination (Join-Path $stage 'minecraft')
            if ($BundleJar) {
                Test-AetherBundle $gatewayJar $BundleJar $connection.platform | ConvertTo-Json
                Copy-Item -LiteralPath $BundleJar -Destination $stage
            }
        }
        Write-ReleaseManifest $stage $id $Action
        "Prepared candidate: $stage"
        'Preparation does not activate or deploy the host. Read deploy/operator/README.md before initial installation or publication.'
    }
    'UploadPlan' {
        if (!$ReleaseDirectory) { throw 'Supply ReleaseDirectory from Prepare or PrepareBehavior.' }
        $commands = @(New-AetherUploadPlan $ReleaseDirectory $connection.sftp.releaseDirectory)
        Write-SftpPlan $commands 'upload'
    }
    'PromoteBehaviorPlan' {
        if (!$ServerStopped -or !$ReleaseDirectory) { throw 'Stop only the Aether instance first, then supply ServerStopped and ReleaseDirectory.' }
        # Also verifies the local candidate before composing any publication command.
        New-AetherUploadPlan $ReleaseDirectory $connection.sftp.releaseDirectory | Out-Null
        $manifest = Get-Content -LiteralPath (Join-Path $ReleaseDirectory 'release.json') -Raw | ConvertFrom-Json -AsHashtable
        if ($manifest.kind -ne 'PrepareBehavior') { throw 'Use a behavior-only candidate for this action.' }
        $dataDirectory = $connection.sftp.dataDirectory
        if ($dataDirectory -notmatch '^[A-Za-z0-9_-]+$') { throw 'Use one simple application data-directory name.' }
        $id = $manifest.releaseId
        $source = "$($connection.sftp.releaseDirectory)/$id/aether/aether-prompts.yml"
        $commands = @(
            ('rename "{0}" "{1}"' -f "$dataDirectory/aether-prompts.yml", "$dataDirectory/aether-prompts.previous-$id.yml")
            ('rename "{0}" "{1}"' -f $source, "$dataDirectory/aether-prompts.yml")
            'bye'
        )
        Write-SftpPlan $commands 'promote-behavior'
        'If publication fails, restore the previous file before restarting. The state journal and model files are untouched.'
    }
    { $_ -in @('Probe', 'Generate') } {
        if (!$connection.gatewayUrl) { throw 'NOT_CONFIGURED: fill in gatewayUrl after HTTPS/private connectivity is set up.' }
        if ($Action -eq 'Probe') {
            foreach ($probe in @(@('/health', ''), @('/health', $connection.monitoringTokenEnv),
                @('/ready', $connection.monitoringTokenEnv), @('/v1/aether/status', $connection.inferenceTokenEnv))) {
                $result = Invoke-AetherHttp $connection.gatewayUrl $probe[0] $probe[1]
                @{ path = $probe[0]; authenticated = [bool]$probe[1]; result = $result } | ConvertTo-Json -Depth 8
            }
        } else {
            $requestId = [guid]::NewGuid().ToString()
            $body = @{ requestId = $requestId; serverId = $connection.minecraftServerId; world = 'minecraft:overworld';
                playerId = [guid]::NewGuid().ToString(); playerName = 'OperatorCheck'; speaker = 'Aether';
                message = 'Aether, introduce yourself briefly.' }
            $timer = [Diagnostics.Stopwatch]::StartNew()
            $result = Invoke-AetherHttp $connection.gatewayUrl '/v1/aether/generate' $connection.inferenceTokenEnv 'POST' $body
            $timer.Stop()
            if ($result.httpStatus -ne 200 -or !$result.data -or !$result.data.success -or
                $result.data.requestId -ne $requestId -or !$result.data.response) { throw 'Generation did not pass. Check Probe and the host console.' }
            @{ seconds = $timer.Elapsed.TotalSeconds; result = $result } | ConvertTo-Json -Depth 8
        }
    }
    'Restart' {
        if (!$connection.panel.restartPath) { throw 'Configure the exact restart path/body from the in-panel Kinetic API docs. No endpoint is assumed.' }
        $result = Invoke-AetherHttp $connection.panel.baseUrl $connection.panel.restartPath $connection.panel.apiTokenEnv 'POST' $connection.panel.restartBody
        if ($result.httpStatus -lt 200 -or $result.httpStatus -ge 300) { throw 'Panel restart request was rejected.' }
        'Restart requested. Run Probe after startup; acceptance of this request does not prove readiness.'
    }
}
