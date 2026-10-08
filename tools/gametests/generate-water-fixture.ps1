# Rebuild the authored empty 16x12x16 fixture. Its footprint isolates flowing
# water GameTests whose small legacy template allowed neighboring tests to overlap.
$fixtureOutput = Join-Path $PSScriptRoot '../../src/main/resources/data/wildernessodysseyapi/structure/water_flow.nbt'
$fixtureBuffer = [System.IO.MemoryStream]::new()
$fixtureWriter = [System.IO.BinaryWriter]::new($fixtureBuffer)
function Write-FixtureInt([int] $value) {
    $bytes = [BitConverter]::GetBytes($value)
    [Array]::Reverse($bytes)
    $fixtureWriter.Write($bytes)
}
function Write-FixtureString([string] $value) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
    $fixtureWriter.Write([byte]($bytes.Length -shr 8))
    $fixtureWriter.Write([byte]($bytes.Length -band 255))
    $fixtureWriter.Write($bytes)
}
function Write-FixtureTag([byte] $type, [string] $name) {
    $fixtureWriter.Write($type)
    Write-FixtureString $name
}
try {
    Write-FixtureTag 10 ''
    Write-FixtureTag 3 'DataVersion'
    Write-FixtureInt 3955
    Write-FixtureTag 9 'size'
    $fixtureWriter.Write([byte]3)
    Write-FixtureInt 3
    Write-FixtureInt 16
    Write-FixtureInt 12
    Write-FixtureInt 16
    Write-FixtureTag 9 'palette'
    $fixtureWriter.Write([byte]10)
    Write-FixtureInt 1
    Write-FixtureTag 8 'Name'
    Write-FixtureString 'minecraft:air'
    $fixtureWriter.Write([byte]0)
    foreach ($name in @('blocks', 'entities')) {
        Write-FixtureTag 9 $name
        $fixtureWriter.Write([byte]10)
        Write-FixtureInt 0
    }
    $fixtureWriter.Write([byte]0)
    $fixtureWriter.Flush()
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($fixtureOutput)) | Out-Null
    $fixtureFile = [System.IO.File]::Create($fixtureOutput)
    $fixtureGzip = [System.IO.Compression.GZipStream]::new($fixtureFile, [System.IO.Compression.CompressionMode]::Compress)
    try { $fixtureGzip.Write($fixtureBuffer.ToArray()) } finally { $fixtureGzip.Dispose(); $fixtureFile.Dispose() }
} finally { $fixtureWriter.Dispose(); $fixtureBuffer.Dispose() }
