# Deploys the built addon to a Prism Launcher instance's mods folder.
#
# The version is read out of gradle/libs.versions.toml rather than typed in. A hand-typed
# version is how a stale jar ends up being deployed: build/libs accumulates every jar that has
# ever been built, so build/libs/venomous-7.6.0.jar was sitting there next to the current one
# and a copy of the wrong file shipped silently. Nothing about the mistake was visible until
# the game reported the version it was running.
#
# Usage:  pwsh -File scripts/deploy.ps1 [-Instance "Meteor client 26.2 on Venom"] [-SkipBuild]

param(
    [string]$Instance = "Meteor client 26.2 on Venom",
    [string]$ModsPath = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root

try {
    # 1. The version, from the one place it is declared.
    $toml = Get-Content "gradle\libs.versions.toml" -Raw
    if ($toml -notmatch 'mod-version\s*=\s*"([^"]+)"') {
        throw "could not read mod-version out of gradle/libs.versions.toml"
    }
    $version = $Matches[1]

    # 2. The constant in the code has to agree with it, or the banner will lie.
    $java = Get-Content "src\main\java\net\venomous\meteoraddon\VenomousAddon.java" -Raw
    if ($java -notmatch 'VERSION\s*=\s*"([^"]+)"') {
        throw "could not read VERSION out of VenomousAddon.java"
    }
    if ($Matches[1] -ne $version) {
        throw "VERSION is '$($Matches[1])' but libs.versions.toml says '$version' - bump both together"
    }

    # 3. Where the mods live.
    if (-not $ModsPath) {
        $ModsPath = Join-Path $env:APPDATA "PrismLauncher\instances\$Instance\minecraft\mods"
    }
    if (-not (Test-Path $ModsPath)) {
        throw "mods folder not found: $ModsPath"
    }

    # 4. Build. The old jars are cleared first so there is exactly one candidate afterwards and
    #    a stale one can never be picked up by accident.
    if (-not $SkipBuild) {
        Write-Host "Building $version..."
        Remove-Item -Recurse -Force "build\libs" -ErrorAction SilentlyContinue
        & ".\gradlew.bat" build --console=plain
        if ($LASTEXITCODE -ne 0) { throw "the build failed" }
    }

    $jar = Join-Path $root "build\libs\venomous-$version.jar"
    if (-not (Test-Path $jar)) {
        $found = Get-ChildItem "build\libs" -Filter "venomous-*.jar" -ErrorAction SilentlyContinue
        throw "expected $jar but found: $($found.Name -join ', ')"
    }

    # 5. Swap it in. Every old venomous jar goes, so two versions can never be loaded at once.
    Get-ChildItem $ModsPath -Filter "venomous-*.jar" -ErrorAction SilentlyContinue |
        Remove-Item -Force
    Copy-Item $jar $ModsPath -Force

    $source = (Get-FileHash $jar -Algorithm SHA256).Hash
    $deployed = (Get-FileHash (Join-Path $ModsPath "venomous-$version.jar") -Algorithm SHA256).Hash
    if ($source -ne $deployed) { throw "the deployed jar does not match the built one" }

    Write-Host "Deployed venomous-$version.jar to $ModsPath"
    Write-Host "  sha256 $deployed"
}
finally {
    Pop-Location
}
