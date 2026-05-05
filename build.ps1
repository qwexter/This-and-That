# build.ps1 - build and run TaT
# Usage:
#   .\build.ps1           - build app image + run compose
#   .\build.ps1 -Builder  - force rebuild tat-builder (re-download Kotlin/Native)
#   .\build.ps1 -Run      - skip build, just run compose
#   .\build.ps1 -Down     - stop and remove containers

param(
    [switch]$Builder,
    [switch]$Run,
    [switch]$Down
)

$ErrorActionPreference = "Stop"

$composeFile = "docker-compose.auth-test.yml"
$builderImage = "tat-builder"
$appImage = "tat"
$checksumFile = ".builder-checksum"

function Get-BuilderChecksum {
    $content = ""
    $files = @(
        "Dockerfile.builder",
        "gradle.properties",
        "settings.gradle.kts",
        "build.gradle.kts"
    )
    foreach ($f in $files) {
        if (Test-Path $f) { $content += Get-Content $f -Raw }
    }
    Get-ChildItem "gradle/" -Recurse -File | ForEach-Object {
        $content += Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
    }
    if (Test-Path "libs/") {
        Get-ChildItem "libs/" -Recurse -File | ForEach-Object {
            $content += Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
        }
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($content)
    $stream = [System.IO.MemoryStream]::new($bytes)
    return (Get-FileHash -Algorithm MD5 -InputStream $stream).Hash
}

if ($Down) {
    Write-Host "Stopping containers..." -ForegroundColor Yellow
    podman compose -f $composeFile down
    exit 0
}

if ($Run) {
    Write-Host "Starting containers..." -ForegroundColor Green
    podman compose -f $composeFile up -d
    exit 0
}

$needsBuilderRebuild = $Builder.IsPresent

if (-not $needsBuilderRebuild) {
    podman image exists $builderImage 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "tat-builder image not found - will build." -ForegroundColor Yellow
        $needsBuilderRebuild = $true
    } else {
        $currentChecksum = Get-BuilderChecksum
        $savedChecksum = if (Test-Path $checksumFile) { Get-Content $checksumFile } else { "" }
        if ($currentChecksum -ne $savedChecksum) {
            Write-Host "Gradle files changed - rebuilding tat-builder." -ForegroundColor Yellow
            $needsBuilderRebuild = $true
        } else {
            Write-Host "tat-builder up to date, skipping." -ForegroundColor Green
        }
    }
}

if ($needsBuilderRebuild) {
    Write-Host "Building tat-builder (downloads Kotlin/Native - slow first time)..." -ForegroundColor Yellow
    podman build --layers -t $builderImage -f Dockerfile.builder .
    if ($LASTEXITCODE -ne 0) { Write-Error "tat-builder build failed"; exit 1 }
    Get-BuilderChecksum | Set-Content $checksumFile -Encoding UTF8
    Write-Host "tat-builder built and cached." -ForegroundColor Green
}

Write-Host "Building app image..." -ForegroundColor Yellow
podman build --layers -t $appImage .
if ($LASTEXITCODE -ne 0) { Write-Error "App build failed"; exit 1 }

Write-Host "Starting containers..." -ForegroundColor Green
podman compose -f $composeFile up -d

Write-Host "Done." -ForegroundColor Green
