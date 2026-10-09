#Requires -Version 7.0
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-AetherBaseUri([string]$Address) {
    $uri = $null
    if (![Uri]::TryCreate($Address, [UriKind]::Absolute, [ref]$uri) -or
        $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
        ($uri.Scheme -ne 'https' -and !($uri.Scheme -eq 'http' -and
            $uri.Host -in @('localhost', '127.0.0.1', '[::1]')))) {
        throw 'Configure an HTTPS URL without credentials, query or fragment. HTTP is allowed only on loopback.'
    }
    return $uri
}

function New-AetherUploadPlan([string]$ReleaseDirectory, [string]$RemoteDirectory) {
    if ($RemoteDirectory -notmatch '^[A-Za-z0-9_-][A-Za-z0-9._/-]*$' -or
        @($RemoteDirectory.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count) {
        throw 'Use a relative SFTP release directory with simple path components.'
    }
    $root = (Resolve-Path -LiteralPath $ReleaseDirectory).Path
    $manifestPath = Join-Path $root 'release.json'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -AsHashtable
    if ($manifest.releaseId -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$' -or !$manifest.files.Count) {
        throw 'Invalid release manifest.'
    }
    $remote = "$RemoteDirectory/$($manifest.releaseId)"
    $files = @($manifest.files)
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $files) {
        $relative = [string]$file.path
        if ($relative -notmatch '^[A-Za-z0-9_-][A-Za-z0-9._/-]*$' -or
            @($relative.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count -or
            !$seen.Add($relative) -or $relative -eq 'release.json') { throw 'Invalid release file path.' }
        $local = Join-Path $root $relative
        $item = Get-Item -LiteralPath $local
        if ($item.PSIsContainer -or $item.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint) -or
            $file.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or
            (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash -ne $file.sha256) {
            throw 'Release integrity check failed. Prepare the release again.'
        }
    }
    $commands = [Collections.Generic.List[string]]::new()
    $directories = [Collections.Generic.HashSet[string]]::new()
    foreach ($directory in @($RemoteDirectory)) {
        $parts = $directory.Split('/')
        for ($index = 1; $index -le $parts.Count; $index++) {
            $parent = ($parts[0..($index - 1)] -join '/')
            if ($directories.Add($parent)) { $commands.Add('-mkdir "' + $parent + '"') }
        }
    }
    # A previously staged or active release must never be overwritten by a repeated upload.
    $commands.Add('mkdir "' + $remote + '"')
    $directories.Add($remote) | Out-Null
    foreach ($file in $files) {
        $relative = [string]$file.path
        $slash = $relative.LastIndexOf('/')
        if ($slash -ge 0) {
            $parts = $relative.Substring(0, $slash).Split('/')
            for ($index = 1; $index -le $parts.Count; $index++) {
                $parent = "$remote/" + ($parts[0..($index - 1)] -join '/')
                if ($directories.Add($parent)) { $commands.Add('-mkdir "' + $parent + '"') }
            }
        }
        $local = (Join-Path $root $relative).Replace('\', '/')
        if ($local.Contains('"') -or $local.Contains("`n") -or $local.Contains("`r")) { throw 'Invalid local SFTP path.' }
        $commands.Add('put "' + $local + '" "' + "$remote/$relative" + '"')
    }
    # Publish the manifest last so an interrupted transfer is identifiable as incomplete.
    $commands.Add('put "' + $manifestPath.Replace('\', '/') + '" "' + "$remote/release.json" + '"')
    $commands.Add('bye')
    return $commands.ToArray()
}

function Invoke-AetherHttp {
    param([string]$BaseUrl, [string]$Path, [string]$TokenEnvironment = '',
        [string]$Method = 'GET', [hashtable]$Body = $null)
    $base = Get-AetherBaseUri $BaseUrl
    if ($Path -notmatch '^/[A-Za-z0-9/_-]+$') { throw 'Configure a documented API path without a query.' }
    $address = [Uri]::new($base.AbsoluteUri.TrimEnd('/') + $Path)
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $deadline = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(40))
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), $address)
    $response = $null
    try {
        if ($TokenEnvironment) {
            if ($TokenEnvironment -notmatch '^[A-Z][A-Z0-9_]{0,127}$') { throw 'Invalid token environment reference.' }
            $secret = [Environment]::GetEnvironmentVariable($TokenEnvironment)
            if (!$secret) { throw "Set $TokenEnvironment in your terminal before this check." }
            if ($secret.Length -gt 4096 -or $secret -match '[\r\n]') { throw 'Invalid credential format.' }
            $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $secret)
        }
        if ($null -ne $Body) {
            $request.Content = [Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 12 -Compress),
                [Text.Encoding]::UTF8, 'application/json')
        }
        $response = $client.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead,
            $deadline.Token).GetAwaiter().GetResult()
        $stream = $response.Content.ReadAsStreamAsync($deadline.Token).GetAwaiter().GetResult()
        $bytes = [IO.MemoryStream]::new()
        try {
            $buffer = [byte[]]::new(4096)
            while (($count = $stream.ReadAsync($buffer, 0, $buffer.Length, $deadline.Token).GetAwaiter().GetResult()) -gt 0) {
                if ($bytes.Length + $count -gt 65536) { throw 'API response exceeds the operator response limit.' }
                $bytes.Write($buffer, 0, $count)
            }
            $bodyText = [Text.Encoding]::UTF8.GetString($bytes.ToArray())
        } finally { $bytes.Dispose(); $stream.Dispose() }
        $data = $null
        try { $data = $bodyText | ConvertFrom-Json -AsHashtable } catch { }
        return @{ httpStatus = [int]$response.StatusCode; data = $data }
    } finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose(); $deadline.Dispose(); $client.Dispose(); $handler.Dispose()
    }
}

function Test-AetherBundle([string]$GatewayJar, [string]$BundleJar, [string]$Platform) {
    $gateway = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $GatewayJar).Path)
    $bundle = $null
    try {
        $bundle = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $BundleJar).Path)
        $manifestEntry = $bundle.GetEntry('bundle/manifest.json')
        if (!$manifestEntry -or $manifestEntry.Length -gt 1048576) { throw 'The candidate is not a complete native bundle.' }
        $reader = [IO.StreamReader]::new($manifestEntry.Open())
        try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json -AsHashtable } finally { $reader.Dispose() }
        if ($manifest.platform -ne $Platform -or $manifest.format -ne 1) { throw 'Bundle platform/format does not match the selected candidate.' }
        foreach ($entry in $gateway.Entries) {
            if ($entry.FullName.EndsWith('/') -or $entry.FullName -eq 'META-INF/MANIFEST.MF') { continue }
            $other = $bundle.GetEntry($entry.FullName)
            if (!$other -or $entry.Length -ne $other.Length) { throw 'Bundle contains an outdated gateway. Rebuild Bundle from the current source.' }
            $left = $entry.Open(); $right = $other.Open()
            try {
                $leftHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($left))
                $rightHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($right))
                if ($leftHash -ne $rightHash) { throw 'Bundle contains an outdated gateway. Rebuild Bundle from the current source.' }
            } finally { $left.Dispose(); $right.Dispose() }
        }
        foreach ($asset in $manifest.assets) {
            $entry = $bundle.GetEntry('bundle/files/' + $asset.path)
            if (!$entry -or $entry.Length -ne $asset.size) { throw 'Bundle asset inventory is incomplete.' }
        }
        if (@($bundle.Entries | Where-Object { $_.FullName -match '^net/(minecraft|neoforged)/' }).Count) {
            throw 'Standalone bundle contains Minecraft classes.'
        }
        return @{ platform = $manifest.platform; sourceModel = $manifest.sourceModel; targetModel = $manifest.targetModel;
            assets = $manifest.assets.Count; gatewayMatches = $true }
    } finally {
        if ($null -ne $bundle) { $bundle.Dispose() }
        $gateway.Dispose()
    }
}

Export-ModuleMember -Function Get-AetherBaseUri, New-AetherUploadPlan, Invoke-AetherHttp, Test-AetherBundle
