param(
    [switch]$Clean = $true,
    [string]$VersionTag = "current",
    [string]$Modules = "all"
)

$ErrorActionPreference = "Stop"

$sdkRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$oemRoot = Split-Path -Parent $sdkRoot
$sourceRepo = Join-Path $oemRoot "android\build\oem-sdk-repo"
$targetRepo = Join-Path $sdkRoot "repo"
$targetDocs = Join-Path $sdkRoot "docs"
$docRoot = Join-Path $oemRoot "Doc"
$siteRoot = Join-Path $oemRoot "website\oem"
$catalogPath = Join-Path $sdkRoot "catalog\oem-sdk-catalog.json"

$modulesArg = $Modules
if ([string]::IsNullOrWhiteSpace($modulesArg)) { $modulesArg = "all" }
$isAllMode = $modulesArg.Trim().ToLower() -eq "all"

$zipOutput = Join-Path $sdkRoot ("oem-sdk-delivery-" + $VersionTag + ".zip")

if (!(Test-Path $catalogPath)) {
    throw "Missing SDK catalog: $catalogPath"
}
$catalog = Get-Content -Raw -Encoding UTF8 $catalogPath | ConvertFrom-Json
$closureModules = @($catalog.modules)
$requestedIds = @("all")
$comboId = "all"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function New-DeliveryZip {
    param(
        [string[]]$SourcePaths,
        [string]$DestinationPath
    )

    Remove-Item -Force $DestinationPath -ErrorAction SilentlyContinue
    $stagingRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("oem-sdk-export-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $stagingRoot | Out-Null
    try {
        foreach ($path in $SourcePaths) {
            if (Test-Path $path -PathType Container) {
                Copy-Item -Recurse -Force $path (Join-Path $stagingRoot (Split-Path $path -Leaf))
            } elseif (Test-Path $path -PathType Leaf) {
                Copy-Item -Force $path (Join-Path $stagingRoot (Split-Path $path -Leaf))
            } else {
                throw "Missing zip source path: $path"
            }
        }

        for ($attempt = 1; $attempt -le 3; $attempt++) {
            try {
                [System.IO.Compression.ZipFile]::CreateFromDirectory(
                    $stagingRoot,
                    $DestinationPath,
                    [System.IO.Compression.CompressionLevel]::Optimal,
                    $false
                )
                return
            } catch {
                if ($attempt -ge 3) { throw }
                Start-Sleep -Milliseconds (400 * $attempt)
            }
        }
    } finally {
        $resolvedStaging = [System.IO.Path]::GetFullPath($stagingRoot)
        $expectedPrefix = Join-Path ([System.IO.Path]::GetTempPath()) 'oem-sdk-export-'
        if (-not $resolvedStaging.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Unexpected export staging path: $resolvedStaging"
        }
        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if (-not $isAllMode) {
    $catalogModuleIds = @($catalog.modules | ForEach-Object { $_.id.ToLower() })
    $requestedIds = @($modulesArg.Split(',') | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ -ne "" })
    if ($requestedIds.Count -eq 0) {
        throw "No module specified. Use -Modules all or a comma-separated list of: $($catalogModuleIds -join ', ')"
    }
    $unknownIds = @($requestedIds | Where-Object { $catalogModuleIds -notcontains $_ })
    if ($unknownIds.Count -gt 0) {
        throw "Unknown module id: $($unknownIds -join ', '). Valid ids: $($catalogModuleIds -join ', ')"
    }

    $closureSet = New-Object System.Collections.Generic.HashSet[string]
    $closureQueue = New-Object System.Collections.Queue
    foreach ($id in $requestedIds) {
        [void]$closureSet.Add($id)
        $closureQueue.Enqueue($id)
    }
    foreach ($m in $catalog.modules) {
        if ($m.required -and -not $closureSet.Contains($m.id.ToLower())) {
            [void]$closureSet.Add($m.id.ToLower())
            $closureQueue.Enqueue($m.id.ToLower())
        }
    }
    while ($closureQueue.Count -gt 0) {
        $currentId = $closureQueue.Dequeue()
        $currentModule = $catalog.modules | Where-Object { $_.id.ToLower() -eq $currentId } | Select-Object -First 1
        foreach ($dep in @($currentModule.dependsOn)) {
            $depId = $dep.ToLower()
            if (-not $closureSet.Contains($depId)) {
                [void]$closureSet.Add($depId)
                $closureQueue.Enqueue($depId)
            }
        }
    }
    $closureModules = @($catalog.modules | Where-Object { $closureSet.Contains($_.id.ToLower()) })
    $comboId = ($closureModules | ForEach-Object { $_.id.ToLower() }) -join '+'
    $zipOutput = Join-Path $sdkRoot ("oem-sdk-delivery-" + $VersionTag + "-" + $comboId + ".zip")
}

$integrationDoc = Get-ChildItem -Path $docRoot -Filter *.md |
    Where-Object {
        $_.Name -like "*SDK*" -and
        $_.Name -notlike "*API*" -and
        $_.Name -notlike "*FAQ*" -and
        $_.Name -notlike "0*-*" -and
        $_.Name -notlike "README.md"
    } |
    Sort-Object Name |
    Select-Object -First 1

$apiDoc = Get-ChildItem -Path $docRoot -Filter *.md |
    Where-Object {
        $_.Name -like "*API*"
    } |
    Sort-Object Name |
    Select-Object -First 1

$automationDoc = Get-Item -Path (Join-Path $docRoot "外部版-自动化授权配置.md") -ErrorAction SilentlyContinue

if ($null -eq $automationDoc) {
    $automationDoc = Get-ChildItem -Path $docRoot -Filter *.md |
        Where-Object {
            Select-String -Path $_.FullName -Pattern "automationEngineApplicationId=|stellarManagerApplicationId=|roro\.stellar\.permissions|enableAutomation" -Quiet
        } |
        Sort-Object Name |
        Select-Object -First 1
}

if (!(Test-Path $sourceRepo)) {
    throw "Missing source repo: $sourceRepo. Run publishReleasePublicationToOemLocalRepository in OEM/android first."
}

if ($isAllMode) {
    if ($null -eq $integrationDoc) {
        throw "Missing integration doc in: $docRoot"
    }

    if ($null -eq $automationDoc) {
        throw "Missing automation doc in: $docRoot"
    }

    if ($null -eq $apiDoc) {
        throw "Missing API doc in: $docRoot"
    }
}

if ($Clean) {
    foreach ($target in @($targetRepo, $targetDocs)) {
        $resolvedTarget = [System.IO.Path]::GetFullPath($target)
        if ((Split-Path -Parent $resolvedTarget) -ne [System.IO.Path]::GetFullPath($sdkRoot)) {
            throw "Export cleanup escaped SDK directory: $resolvedTarget"
        }
        Remove-Item -LiteralPath $resolvedTarget -Recurse -Force -ErrorAction SilentlyContinue
    }
    if ($isAllMode) {
        Get-ChildItem -Path $sdkRoot -Filter "oem-sdk-delivery-*.zip" -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
    } else {
        Remove-Item -Force $zipOutput -ErrorAction SilentlyContinue
    }
}

New-Item -ItemType Directory -Force -Path $targetRepo | Out-Null
New-Item -ItemType Directory -Force -Path $targetDocs | Out-Null

if ($isAllMode) {
    Copy-Item -Recurse -Force (Join-Path $sourceRepo "*") $targetRepo
    Copy-Item -Force $integrationDoc.FullName $targetDocs
    Copy-Item -Force $automationDoc.FullName $targetDocs
    Copy-Item -Force $apiDoc.FullName $targetDocs
    Get-ChildItem -Path $docRoot -Filter *.md |
        Where-Object {
            Select-String -Path $_.FullName -Pattern "automationEngineApplicationId=|automationEngineDefaultAllowedPackages|Binder ready|Binder 分发目标" -Quiet
        } |
        ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination $targetDocs -Force
        }
    Copy-Item -Force (Join-Path $siteRoot "index.html") $targetDocs
    Copy-Item -Force (Join-Path $siteRoot "automation.html") $targetDocs
    Copy-Item -Force (Join-Path $siteRoot "styles.css") $targetDocs
    Copy-Item -Force (Join-Path $siteRoot "app.js") $targetDocs
    foreach ($name in @('pages.js', 'debug-tools.js', 'md-copy.js', 'architecture.html', 'oem-ai-one-click-install-latest.md')) {
        Copy-Item -LiteralPath (Join-Path $siteRoot $name) -Destination $targetDocs -Force
    }
    Copy-Item -LiteralPath (Join-Path $siteRoot 'assets') -Destination $targetDocs -Recurse -Force
    $docsDownloads = Join-Path $targetDocs 'downloads'
    New-Item -ItemType Directory -Force -Path $docsDownloads | Out-Null
    foreach ($name in @('EnterpriseRemoteLicenseProvider.kt', 'oem-host-optional-r8.pro', 'oem-ai-one-click-install-latest.md', 'paopao-oem-sdk-doc-latest.pdf')) {
        Copy-Item -LiteralPath (Join-Path $siteRoot "downloads/$name") -Destination $docsDownloads -Force
    }
    Copy-Item -LiteralPath (Join-Path $docRoot '外部版-版本说明.md') -Destination (Join-Path $docsDownloads 'oem-sdk-release-notes.md') -Force
    Copy-Item -LiteralPath (Join-Path $docRoot '外部版-版本说明.md') -Destination $targetDocs -Force
    Copy-Item -Recurse -Force (Join-Path $siteRoot "API") (Join-Path $targetDocs "API")

    $manifestArtifacts = @()
    $missingArtifacts = @()
    $seenCoords = @{}
    foreach ($module in $closureModules) {
        foreach ($coord in @($module.artifacts)) {
            if ($seenCoords.ContainsKey($coord)) { continue }
            $seenCoords[$coord] = $true
            $parts = $coord.Split(':')
            if ($parts.Count -ne 3) {
                throw "Invalid artifact coordinate in catalog: $coord"
            }
            $group = $parts[0]
            $artifact = $parts[1]
            $version = $parts[2]
            $groupPath = $group.Replace('.', '/')
            $artDir = Join-Path (Join-Path (Join-Path $targetRepo $groupPath) $artifact) $version
            if (!(Test-Path $artDir)) {
                $missingArtifacts += $coord
                Write-Warning "Artifact not found in exported repo, skipped: $coord"
                continue
            }
            $primaryFile = Join-Path $artDir ($artifact + "-" + $version + ".aar")
            if (!(Test-Path $primaryFile)) {
                $primaryFile = Join-Path $artDir ($artifact + "-" + $version + ".jar")
            }
            if (!(Test-Path $primaryFile)) {
                $primaryFile = Get-ChildItem -Path $artDir -File |
                    Sort-Object Name |
                    Select-Object -First 1 |
                    ForEach-Object { $_.FullName }
            }
            if ($null -eq $primaryFile -or !(Test-Path $primaryFile)) {
                $missingArtifacts += $coord
                Write-Warning "Artifact directory has no deliverable file, skipped: $coord"
                continue
            }
            $md5Hash = (Get-FileHash -Algorithm MD5 $primaryFile).Hash.ToLower()
            $sha256Hash = (Get-FileHash -Algorithm SHA256 $primaryFile).Hash.ToLower()
            $manifestArtifacts += [PSCustomObject]@{
                module = $module.id
                group = $group
                artifact = $artifact
                version = $version
                file = Split-Path -Leaf $primaryFile
                md5 = $md5Hash
                sha256 = $sha256Hash
            }
        }
    }

    if ($missingArtifacts.Count -gt 0) { throw "Incomplete SDK export: $($missingArtifacts -join ', ')" }
    $manifestObject = [PSCustomObject]@{
        schemaVersion = 1
        sdkName = $catalog.sdkName
        sdkVersion = $catalog.sdkVersion
        generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
        requestedModules = @($requestedIds)
        resolvedModules = @($closureModules | ForEach-Object { $_.id })
        modules = @($closureModules | ForEach-Object {
            [PSCustomObject]@{
                id = $_.id
                name = $_.name
                capability = $_.capability
                required = [bool]$_.required
            }
        })
        artifacts = $manifestArtifacts
        missingArtifacts = $missingArtifacts
    }
    $manifestJson = $manifestObject | ConvertTo-Json -Depth 10
    $manifestPath = Join-Path $sdkRoot "oem-sdk-manifest.json"
    [System.IO.File]::WriteAllText($manifestPath, $manifestJson, (New-Object System.Text.UTF8Encoding($false)))

    $readme = @"
OEM SDK delivery package

SDK version: $($catalog.sdkVersion)

Contents:
- repo/: local Maven repository for customer integration
- docs/: customer-facing integration documents
- oem-sdk-manifest.json: full module list, artifact MD5/SHA256

Rules:
1. Integrate the whole repo directory instead of picking single AAR files by hand
2. docs/index.html and the markdown integration manual describe the same flow
"@

    Set-Content -Path (Join-Path $sdkRoot "DELIVERY_README.txt") -Value $readme -Encoding UTF8

    $zipSource = @(
        (Join-Path $sdkRoot "repo"),
        (Join-Path $sdkRoot "docs"),
        (Join-Path $sdkRoot "DELIVERY_README.txt"),
        $manifestPath
    )

    New-DeliveryZip -SourcePaths $zipSource -DestinationPath $zipOutput
} else {
    $manifestArtifacts = @()
    $missingArtifacts = @()
    $copiedCoords = @{}

    foreach ($module in $closureModules) {
        foreach ($coord in @($module.artifacts)) {
            if ($copiedCoords.ContainsKey($coord)) { continue }
            $copiedCoords[$coord] = $true
            $parts = $coord.Split(':')
            if ($parts.Count -ne 3) {
                throw "Invalid artifact coordinate in catalog: $coord"
            }
            $group = $parts[0]
            $artifact = $parts[1]
            $version = $parts[2]
            $groupPath = $group.Replace('.', '/')
            $srcDir = Join-Path (Join-Path (Join-Path $sourceRepo $groupPath) $artifact) $version
            if (!(Test-Path $srcDir)) {
                $missingArtifacts += $coord
                Write-Warning "Artifact not found in source repo, skipped: $coord"
                continue
            }
            $dstParent = Join-Path (Join-Path $targetRepo $groupPath) $artifact
            New-Item -ItemType Directory -Force -Path $dstParent | Out-Null
            Copy-Item -Recurse -Force $srcDir (Join-Path $dstParent $version)

            $primaryFile = Join-Path $srcDir ($artifact + "-" + $version + ".aar")
            if (!(Test-Path $primaryFile)) {
                $primaryFile = Join-Path $srcDir ($artifact + "-" + $version + ".jar")
            }
            if (!(Test-Path $primaryFile)) {
                $primaryFile = Get-ChildItem -Path $srcDir -File |
                    Sort-Object Name |
                    Select-Object -First 1 |
                    ForEach-Object { $_.FullName }
            }
            if ($null -eq $primaryFile -or !(Test-Path $primaryFile)) {
                $missingArtifacts += $coord
                Write-Warning "Artifact directory has no deliverable file, skipped: $coord"
                continue
            }
            $md5Hash = (Get-FileHash -Algorithm MD5 $primaryFile).Hash.ToLower()
            $sha256Hash = (Get-FileHash -Algorithm SHA256 $primaryFile).Hash.ToLower()
            $manifestArtifacts += [PSCustomObject]@{
                module = $module.id
                group = $group
                artifact = $artifact
                version = $version
                file = Split-Path -Leaf $primaryFile
                md5 = $md5Hash
                sha256 = $sha256Hash
            }
        }
    }

    $closureConfigKeys = @()
    $configKeyIndex = @{}
    foreach ($module in $closureModules) {
        foreach ($ck in @($module.configKeys)) {
            if ($configKeyIndex.ContainsKey($ck.key)) {
                $existingIndex = $configKeyIndex[$ck.key]
                $closureConfigKeys[$existingIndex].modules += $module.id
            } else {
                $configKeyIndex[$ck.key] = $closureConfigKeys.Count
                $closureConfigKeys += [PSCustomObject]@{
                    key = $ck.key
                    type = $ck.type
                    default = $ck.default
                    required = [bool]$ck.required
                    modules = @($module.id)
                    description = $ck.description
                }
            }
        }
    }

    $metaTemplateLines = @(
        '<application>',
        '    <!-- Paopao OEM SDK Manifest meta template, generated for selected modules -->'
    )
    foreach ($ck in $closureConfigKeys) {
        $metaTemplateLines += ('    <meta-data android:name="{0}" android:value="{1}" />' -f $ck.key, $ck.default)
    }
    $metaTemplateLines += '</application>'
    $configKeysTemplate = $metaTemplateLines -join "`n"

    if ($missingArtifacts.Count -gt 0) { throw "Incomplete modular SDK export: $($missingArtifacts -join ', ')" }
    $manifestObject = [PSCustomObject]@{
        schemaVersion = 1
        sdkName = $catalog.sdkName
        sdkVersion = $catalog.sdkVersion
        generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
        requestedModules = @($requestedIds)
        resolvedModules = @($closureModules | ForEach-Object { $_.id })
        modules = @($closureModules | ForEach-Object {
            [PSCustomObject]@{
                id = $_.id
                name = $_.name
                capability = $_.capability
                required = [bool]$_.required
            }
        })
        artifacts = $manifestArtifacts
        missingArtifacts = $missingArtifacts
        configKeysTemplate = $configKeysTemplate
        configKeys = $closureConfigKeys
    }
    $manifestJson = $manifestObject | ConvertTo-Json -Depth 10
    $manifestPath = Join-Path $sdkRoot "oem-sdk-manifest.json"
    [System.IO.File]::WriteAllText($manifestPath, $manifestJson, (New-Object System.Text.UTF8Encoding($false)))

    $copiedDocs = @{}
    foreach ($module in $closureModules) {
        foreach ($relDoc in @($module.docs)) {
            $docFile = Join-Path $oemRoot ($relDoc.Replace('/', '\'))
            if (!(Test-Path $docFile)) {
                Write-Warning "Catalog doc missing, skipped: $relDoc"
                continue
            }
            $docName = Split-Path -Leaf $docFile
            if ($copiedDocs.ContainsKey($docName)) { continue }
            $copiedDocs[$docName] = $true
            Copy-Item -Force $docFile $targetDocs
        }
    }
    if ($null -ne $integrationDoc -and -not $copiedDocs.ContainsKey($integrationDoc.Name)) {
        Copy-Item -Force $integrationDoc.FullName $targetDocs
    }

    $comboSummary = ($closureModules | ForEach-Object { "$($_.id) ($($_.name))" }) -join ", "
    $readme = @"
OEM SDK modular delivery package

Modules: $comboSummary
Requested: $($requestedIds -join ', ')
SDK version: $($catalog.sdkVersion)

Contents:
- repo/: local Maven repository containing only the artifacts of selected modules
- docs/: module integration documents
- oem-sdk-manifest.json: selected modules, artifact MD5/SHA256 and Manifest meta template

Rules:
1. Integrate the whole repo directory instead of picking single AAR files by hand
2. Configure the host AndroidManifest.xml with configKeysTemplate from oem-sdk-manifest.json
"@

    Set-Content -Path (Join-Path $sdkRoot "DELIVERY_README.txt") -Value $readme -Encoding UTF8

    $zipSource = @(
        (Join-Path $sdkRoot "repo"),
        (Join-Path $sdkRoot "docs"),
        (Join-Path $sdkRoot "DELIVERY_README.txt"),
        $manifestPath
    )

    New-DeliveryZip -SourcePaths $zipSource -DestinationPath $zipOutput
}

Write-Output "SDK export completed: $sdkRoot"
Write-Output "Mode: $(if ($isAllMode) { 'all' } else { "modules [$comboId]" })"
Write-Output "Delivery zip: $zipOutput"
