[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$WorkDirectory,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][guid]$UpgradeCode,
    [Parameter(Mandatory = $true)][string]$ReportFile
)

$ErrorActionPreference = 'Stop'
$packages = @(Get-ChildItem -LiteralPath $WorkDirectory -Filter '*.msi' -File -Recurse)
if ($packages.Count -ne 1) { throw 'Expected exactly one generated MSI in the installer work directory.' }

# Read the Windows Installer database in mode 0. This never installs, removes, or repairs a product.
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase($packages[0].FullName, 0)

function Read-MsiRows([string]$Query, [string[]]$Columns) {
    $view = $database.OpenView($Query)
    try {
        [void]$view.Execute()
        while ($record = $view.Fetch()) {
            try {
                $row = [ordered]@{}
                for ($column = 0; $column -lt $Columns.Count; $column++) {
                    $row[$Columns[$column]] = $record.StringData($column + 1)
                }
                [pscustomobject]$row
            } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) }
        }
    } finally {
        [void]$view.Close()
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)
    }
}

function Matches-UpgradeRange($Row, [version]$Candidate) {
    $flags = [int]$Row.Attributes
    if ($Row.VersionMin) {
        $minimum = [version]$Row.VersionMin
        if ($Candidate -lt $minimum -or ($Candidate -eq $minimum -and ($flags -band 256) -eq 0)) { return $false }
    }
    if ($Row.VersionMax) {
        $maximum = [version]$Row.VersionMax
        if ($Candidate -gt $maximum -or ($Candidate -eq $maximum -and ($flags -band 512) -eq 0)) { return $false }
    }
    return $true
}

try {
    $properties = @{}
    Read-MsiRows 'SELECT `Property`, `Value` FROM `Property`' @('Property', 'Value') | ForEach-Object { $properties[$_.Property] = $_.Value }
    if ($properties.ProductVersion -ne $Version) { throw 'The MSI version differs from the installer version.' }
    if ([guid]$properties.UpgradeCode -ne $UpgradeCode) { throw 'The MSI upgrade identity changed; older installations would not be recognized.' }

    $ranges = @(Read-MsiRows 'SELECT `UpgradeCode`, `VersionMin`, `VersionMax`, `Attributes`, `ActionProperty` FROM `Upgrade`' @('UpgradeCode', 'VersionMin', 'VersionMax', 'Attributes', 'ActionProperty'))
    $sameProduct = @($ranges | Where-Object { [guid]$_.UpgradeCode -eq $UpgradeCode })
    $current = [version]$Version
    $previous = [version]'1.0.0'
    if ($current -le $previous) { throw 'The update installer must be newer than the initial 1.0.0 release.' }
    $older = @($sameProduct | Where-Object { ([int]$_.Attributes -band 2) -eq 0 -and (Matches-UpgradeRange $_ $previous) })
    if ($older.Count -eq 0) { throw 'The MSI does not detect and replace the initial 1.0.0 installation.' }

    $future = [version]::new($current.Major + 1, 0, 0)
    $newer = @($sameProduct | Where-Object { ([int]$_.Attributes -band 2) -ne 0 -and (Matches-UpgradeRange $_ $future) })
    if ($newer.Count -eq 0) { throw 'The MSI does not detect a newer installed version.' }
    $sequence = @(Read-MsiRows 'SELECT `Action`, `Condition`, `Sequence` FROM `InstallExecuteSequence`' @('Action', 'Condition', 'Sequence'))
    $actions = @(Read-MsiRows 'SELECT `Action`, `Type` FROM `CustomAction`' @('Action', 'Type'))
    # jpackage uses a type-19 error action scheduled when its newer-product property is set.
    $errorActions = @($actions | Where-Object { ([int]$_.Type -band 63) -eq 19 })
    $blocksDowngrade = @($sequence | Where-Object {
        $scheduled = $_
        @($errorActions | Where-Object { $_.Action -eq $scheduled.Action }).Count -gt 0 -and
        @($newer | Where-Object { $scheduled.Condition.Trim() -eq $_.ActionProperty }).Count -gt 0
    })
    if ($blocksDowngrade.Count -eq 0) { throw 'The MSI does not block installation over a newer version.' }
    $detect = @($sequence | Where-Object { $_.Action -eq 'FindRelatedProducts' })
    $remove = @($sequence | Where-Object { $_.Action -eq 'RemoveExistingProducts' })
    if ($detect.Count -ne 1 -or $remove.Count -ne 1 -or [int]$remove[0].Sequence -le [int]$detect[0].Sequence) {
        throw 'The MSI does not schedule replacement of the detected older product.'
    }

    $report = [ordered]@{
        version = $properties.ProductVersion
        upgradeCode = $properties.UpgradeCode
        productCode = $properties.ProductCode
        detectsInitialRelease = $true
        replacesOlderInstallation = $true
        blocksDowngrade = $true
        upgradeRanges = $sameProduct
        validation = 'Read-only inspection of the built MSI; installation and upgrade execution are not tested.'
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ReportFile -Encoding UTF8
    Write-Output "Installer upgrade metadata verified: version $Version, detects/replaces 1.0.0, blocks downgrades."
} finally {
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database)
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer)
}
