param([switch]$UseWorkingAars)
$ErrorActionPreference = 'Stop'
$oemRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$target = Join-Path $oemRoot 'company/capture-regression'
$version = (Select-String -LiteralPath (Join-Path $oemRoot 'android/gradle.properties') -Pattern '^oemSdkVersion=(.+)$').Matches[0].Groups[1].Value
New-Item -ItemType Directory -Force -Path $target | Out-Null
Copy-Item -Path (Join-Path $PSScriptRoot 'fixture/*') -Destination $target -Recurse -Force
Add-Content -LiteralPath (Join-Path $target 'gradle.properties') -Value "oemSdkVersion=$version" -Encoding utf8
foreach ($name in @('gradlew','gradlew.bat','gradle')) {
    Copy-Item -LiteralPath (Join-Path $oemRoot "android/$name") -Destination $target -Recurse -Force
}
$localSdk = Join-Path $target 'local-sdk'
New-Item -ItemType Directory -Force -Path $localSdk | Out-Null
Copy-Item -LiteralPath (Join-Path $oemRoot 'SDK/repo') -Destination $localSdk -Recurse -Force
if ($UseWorkingAars) {
    foreach ($module in @('oem-ime-sdk-material','oem-ime-sdk-automation')) {
        $built = Join-Path $oemRoot "android/$module/build/outputs/aar/$module-release.aar"
        $copied = Join-Path $localSdk "repo/com/paopaoime/oem/$module/$version/$module-$version.aar"
        Copy-Item -LiteralPath $built -Destination $copied -Force
        if ((Get-FileHash -LiteralPath $built).Hash -ne (Get-FileHash -LiteralPath $copied).Hash) {
            throw "AAR hash mismatch: $module"
        }
    }
}
Write-Output "Prepared isolated consumer: $target"
Write-Output "Build with -PoemSdkVersion=$version"
