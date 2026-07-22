[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$ReleaseLabel
)

$ErrorActionPreference = "Stop"
$gradleWrapper = Join-Path $PSScriptRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}

& $gradleWrapper createGithubRelease "--release-label=$ReleaseLabel"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Waiting for GitHub release '$ReleaseLabel' to become available..."
Start-Sleep -Seconds 3

& $gradleWrapper publishToGithubRelease "--release-label=$ReleaseLabel"
exit $LASTEXITCODE
