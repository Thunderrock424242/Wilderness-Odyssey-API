#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'AetherOperator.psm1') -Force

function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}
function Assert-Rejected([scriptblock]$Work, [string]$Message) {
    $rejected = $false
    try { & $Work | Out-Null } catch { $rejected = $true }
    Assert-True $rejected $Message
}

function Invoke-HttpFixture([string]$Response, [scriptblock]$Check) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $address = "http://127.0.0.1:$($listener.LocalEndpoint.Port)"
    $job = Start-ThreadJob -ArgumentList $listener, $Response -ScriptBlock {
        param($Listener, $Response)
        $socket = $Listener.AcceptTcpClient()
        try {
            $stream = $socket.GetStream()
            $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::ASCII, $false, 1024, $true)
            while ($reader.ReadLine()) { }
            $bytes = [Text.Encoding]::UTF8.GetBytes($Response)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush()
            $reader.Dispose()
        } finally { $socket.Dispose() }
    }
    try { & $Check $address } finally {
        $listener.Stop()
        $job | Stop-Job
        $job | Remove-Job -Force
    }
}

$fixture = Join-Path ([IO.Path]::GetTempPath()) ('aether-operator-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture | Out-Null
try {
    Assert-Rejected { Get-AetherBaseUri 'http://public.example.com' } 'Remote HTTP must be rejected.'
    Assert-Rejected { Get-AetherBaseUri 'https://user:secret@example.com' } 'Credentials in URLs must be rejected.'
    Assert-Rejected { Get-AetherBaseUri 'https://example.com/path?token=secret' } 'Queries must be rejected.'
    Assert-True ((Get-AetherBaseUri 'http://127.0.0.1:8085').Host -eq '127.0.0.1') 'Loopback development must work.'
    Assert-True ((Get-AetherBaseUri 'https://aether.example.com').Scheme -eq 'https') 'Remote HTTPS must work.'
    $httpBody = '{"available":true}'
    Invoke-HttpFixture "HTTP/1.1 200 OK`r`nContent-Type: application/json`r`nContent-Length: $($httpBody.Length)`r`nConnection: close`r`n`r`n$httpBody" {
        param($Address)
        $result = Invoke-AetherHttp $Address '/health'
        Assert-True ($result.httpStatus -eq 200 -and $result.data.available) 'HTTP checks must decode actual JSON responses.'
    }
    Invoke-HttpFixture "HTTP/1.1 302 Found`r`nLocation: http://127.0.0.1:9/other`r`nContent-Length: 0`r`nConnection: close`r`n`r`n" {
        param($Address)
        $result = Invoke-AetherHttp $Address '/health'
        Assert-True ($result.httpStatus -eq 302) 'Operator requests must never follow credential redirects.'
    }
    $oversized = 'x' * 70000
    Invoke-HttpFixture "HTTP/1.1 200 OK`r`nContent-Length: 70000`r`nConnection: close`r`n`r`n$oversized" {
        param($Address)
        Assert-Rejected { Invoke-AetherHttp $Address '/health' } 'Oversized operator responses must be rejected.'
    }

    $artifact = Join-Path $fixture 'Aether-Gateway.jar'
    [IO.File]::WriteAllText($artifact, 'original')
    $manifest = @{
        releaseId = 'release-1'
        files = @(@{ path = 'Aether-Gateway.jar'; sha256 = (Get-FileHash $artifact -Algorithm SHA256).Hash })
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $fixture 'release.json')
    $plan = @(New-AetherUploadPlan $fixture 'aether-releases')
    Assert-True ($plan[-1] -eq 'bye') 'SFTP plan must finish cleanly.'
    Assert-True (($plan -join "`n") -match 'aether-releases/release-1/Aether-Gateway.jar') 'Uploads must use a versioned release directory.'
    Assert-True (!(($plan -join "`n") -match '\brm\b|\brename\b')) 'Staging must never delete or replace a live release.'
    [IO.File]::WriteAllText($artifact, 'modified')
    Assert-Rejected { New-AetherUploadPlan $fixture 'aether-releases' } 'Modified artifacts must be rejected before upload.'
    [IO.File]::WriteAllText($artifact, 'original')
    $manifest.files[0].path = '../outside.jar'
    $manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $fixture 'release.json')
    Assert-Rejected { New-AetherUploadPlan $fixture 'aether-releases' } 'Manifest traversal must be rejected.'
    Assert-Rejected { New-AetherUploadPlan $fixture 'aether-releases";rm *' } 'SFTP command injection must be rejected.'
    $behaviorDirectory = Join-Path $fixture 'aether'
    New-Item -ItemType Directory -Path $behaviorDirectory | Out-Null
    $behaviorPath = Join-Path $behaviorDirectory 'aether-prompts.yml'
    Set-Content -LiteralPath $behaviorPath 'personality: {name: Aether}'
    $manifest.kind = 'PrepareBehavior'
    $manifest.files = @(@{ path = 'aether/aether-prompts.yml'; sha256 = (Get-FileHash $behaviorPath -Algorithm SHA256).Hash })
    $manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $fixture 'release.json')
    $connection = Get-Content (Join-Path $PSScriptRoot '../../src/main/resources/aether/aether-connection.example.json') -Raw | ConvertFrom-Json -AsHashtable
    $connection.sftp.host = 'example.invalid'
    $connection.sftp.user = 'operator'
    $connectionPath = Join-Path $fixture 'connection.json'
    $connection | ConvertTo-Json -Depth 8 | Set-Content $connectionPath
    $publication = & (Join-Path $PSScriptRoot 'aether.ps1') -Action PromoteBehaviorPlan -ServerStopped -ConnectionConfig $connectionPath -ReleaseDirectory $fixture
    Assert-True ($publication -contains 'rename "aether/aether-prompts.yml" "aether/aether-prompts.previous-release-1.yml"') 'Publication must preserve the previous behavior file.'
    Assert-True ($publication -contains 'rename "aether-releases/release-1/aether/aether-prompts.yml" "aether/aether-prompts.yml"') 'Publication must move the staged behavior into the configured data directory.'
    Assert-Rejected { & (Join-Path $PSScriptRoot 'aether.ps1') -Action PromoteBehaviorPlan -ConnectionConfig $connectionPath -ReleaseDirectory $fixture } 'Publication must require an explicit stopped-process assertion.'

    $gatewaySource = Join-Path $fixture 'gateway-fixture'
    $bundleSource = Join-Path $fixture 'bundle-fixture'
    New-Item -ItemType Directory -Path $gatewaySource | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $bundleSource 'bundle/files/runtime') | Out-Null
    Set-Content (Join-Path $gatewaySource 'Gateway.class') 'current-gateway'
    Copy-Item (Join-Path $gatewaySource 'Gateway.class') $bundleSource
    [IO.File]::WriteAllText((Join-Path $bundleSource 'bundle/files/runtime/ollama'), 'bin')
    @{ format = 1; platform = 'linux-amd64'; sourceModel = 'llama3.1:8b'; targetModel = 'aether-custom:8b';
        assets = @(@{ path = 'runtime/ollama'; size = 3 }) } | ConvertTo-Json -Depth 6 |
        Set-Content (Join-Path $bundleSource 'bundle/manifest.json')
    $gatewayFixture = Join-Path $fixture 'gateway-fixture.jar'
    $bundleFixture = Join-Path $fixture 'bundle-fixture.jar'
    [IO.Compression.ZipFile]::CreateFromDirectory($gatewaySource, $gatewayFixture)
    [IO.Compression.ZipFile]::CreateFromDirectory($bundleSource, $bundleFixture)
    Assert-True (Test-AetherBundle $gatewayFixture $bundleFixture 'linux-amd64').gatewayMatches 'A complete matching gateway candidate must pass.'
    Assert-Rejected { Test-AetherBundle $gatewayFixture $bundleFixture 'windows-amd64' } 'Wrong-platform bundles must be rejected.'
    Set-Content (Join-Path $bundleSource 'Gateway.class') 'previous-gateway'
    $staleFixture = Join-Path $fixture 'stale-fixture.jar'
    [IO.Compression.ZipFile]::CreateFromDirectory($bundleSource, $staleFixture)
    Assert-Rejected { Test-AetherBundle $gatewayFixture $staleFixture 'linux-amd64' } 'A stale embedded gateway must be rejected.'
    'Aether operator checks passed (HTTP bounds, redirects, integrity, staging, publication and bundle consistency).'
} finally {
    # Only the uniquely named test directory created above is removed.
    $resolvedFixture = [IO.Path]::GetFullPath($fixture)
    $resolvedTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (!$resolvedFixture.StartsWith($resolvedTemp, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid test cleanup path.' }
    Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
}
