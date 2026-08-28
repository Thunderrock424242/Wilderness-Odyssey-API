param(
    [string]$VoiceName = "af_nicole",
    [double]$Speed = 0.95,
    [switch]$AllowModelDownloads
)

$ErrorActionPreference = "Stop"

$pythonPath = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
$generatorPath = Join-Path $PSScriptRoot "generate_cryo_voice.py"
if (-not (Test-Path -LiteralPath $pythonPath)) {
    throw "Create tools\aether_voice_service\.venv and install Kokoro before generating authored voice assets."
}

$arguments = @(
    $generatorPath,
    "--voice", $VoiceName,
    "--speed", $Speed.ToString([Globalization.CultureInfo]::InvariantCulture)
)
if ($AllowModelDownloads) {
    $arguments += "--allow-downloads"
}

& $pythonPath @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Cryo voice generation failed with exit code $LASTEXITCODE"
}
