param(
    [switch]$Clean = $true
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$companyRoot = Split-Path -Parent $projectRoot
$oemRoot = Split-Path -Parent $companyRoot
$sourceSdkRoot = Join-Path $oemRoot "SDK"
$sourceRepo = Join-Path $sourceSdkRoot "repo"
$sourceDocs = Join-Path $sourceSdkRoot "docs"
$sourceReadme = Join-Path $sourceSdkRoot "DELIVERY_README.txt"
$targetSdkRoot = Join-Path $projectRoot "local-sdk"
$targetRepo = Join-Path $targetSdkRoot "repo"
$targetDocs = Join-Path $targetSdkRoot "docs"

if (!(Test-Path $sourceRepo)) {
    throw "Missing SDK repo: $sourceRepo. Export the delivery package in OEM/SDK first."
}

if ($Clean) {
    Remove-Item -Recurse -Force $targetSdkRoot -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Force -Path $targetRepo | Out-Null

Copy-Item -Recurse -Force (Join-Path $sourceRepo "*") $targetRepo

if (Test-Path $sourceDocs) {
    New-Item -ItemType Directory -Force -Path $targetDocs | Out-Null
    Copy-Item -Recurse -Force (Join-Path $sourceDocs "*") $targetDocs
}

if (Test-Path $sourceReadme) {
    Copy-Item -Force $sourceReadme (Join-Path $targetSdkRoot "DELIVERY_README.txt")
}

$latestZip = Get-ChildItem -Path $sourceSdkRoot -Filter "oem-sdk-delivery-*.zip" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

Write-Output "Local SDK prepared: $targetSdkRoot"
Write-Output "Repository path: $targetRepo"
if ($latestZip) {
    Write-Output "Latest delivery zip: $($latestZip.FullName)"
}
