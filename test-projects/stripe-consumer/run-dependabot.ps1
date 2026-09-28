param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('2025-08-27.basil', '2025-09-30.clover')]
    [string] $TargetVersion,
    [string] $Question = 'What consumer code is affected by the Stripe API upgrade, and what migration does the release documentation recommend?',
    [switch] $LiveAnswer
)

$ErrorActionPreference = 'Stop'
$fixtureRoot = $PSScriptRoot
$appRoot = (Resolve-Path (Join-Path $fixtureRoot '../..')).Path
$oldSpec = Join-Path $fixtureRoot 'openapi/basil.yaml'
$snapshotByVersion = @{
    '2025-08-27.basil' = 'basil'
    '2025-09-30.clover' = 'clover'
}
$newSpec = Join-Path $fixtureRoot "openapi/$($snapshotByVersion[$TargetVersion]).yaml"
$consumerRoot = $fixtureRoot
$jar = Join-Path $appRoot 'target/api-dependabot-0.1.0-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $jar)) {
    throw "API Dependabot JAR not found at $jar. Build the main project first."
}
if (-not (Test-Path -LiteralPath $newSpec)) {
    throw "No curated contract snapshot is available for target version '$TargetVersion'."
}

$argsForApp = @(
    "--old=$oldSpec",
    "--new=$newSpec",
    "--repo=$consumerRoot",
    "--question=$Question",
    '--top-k=8'
)
if ($LiveAnswer) {
    $argsForApp += '--answer'
}
Push-Location $appRoot
try {
    & java -jar $jar @argsForApp
    if ($LASTEXITCODE -ne 0) {
        throw "API Dependabot exited with status $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
