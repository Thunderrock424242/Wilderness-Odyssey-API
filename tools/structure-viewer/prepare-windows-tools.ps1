[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../../.codex-build/tools/structure-viewer/toolchains')
)

$ErrorActionPreference = 'Stop'
$toolDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $toolDirectory -Force | Out-Null

# Portable build inputs only. Nothing is installed and PATH is not changed.
$downloads = @(
    @{
        File = 'wix314-binaries.zip'
        Url = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
        Sha256 = '6AC824E1642D6F7277D0ED7EA09411A508F6116BA6FAE0AA5F2C7DAA2FF43D31'
        Folder = 'wix-3.14.1'
    },
    @{
        File = 'temurin-21-jre.zip'
        Url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip'
        Sha256 = 'D35F31E712F0FCF6AC5A093EDC90204FBFF22F720BA3950BD09D331D5E621636'
        Folder = 'temurin-21-jre'
    }
)

foreach ($download in $downloads) {
    $archive = Join-Path $toolDirectory $download.File
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
        Write-Host "Downloading $($download.File) from its official release..."
        Invoke-WebRequest -UseBasicParsing -Uri $download.Url -OutFile $archive
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $download.Sha256) {
        throw "Checksum mismatch: $archive. No extraction performed. Remove that file and retry the download."
    }
    $destination = Join-Path $toolDirectory $download.Folder
    Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
}

$runtimeDirectory = Join-Path $toolDirectory 'temurin-21-jre/jdk-21.0.12.1+1-jre'
$wixDirectory = Join-Path $toolDirectory 'wix-3.14.1'
foreach ($required in @((Join-Path $runtimeDirectory 'bin/java.exe'), (Join-Path $wixDirectory 'candle.exe'), (Join-Path $wixDirectory 'light.exe'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing extracted tool: $required" }
}
Write-Host "Verified Windows x64 build tools:"
Write-Host "-PviewerRuntimeDir=$runtimeDirectory"
Write-Host "-PviewerWixDir=$wixDirectory"
