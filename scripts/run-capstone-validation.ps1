param(
    [string]$GitHubRepository,
    [string]$MavenRepository,
    [switch]$SkipEvaluation
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Stop-WithMessage([string]$Message) {
    Write-Error $Message
    exit 1
}

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { Stop-WithMessage 'Java 21 is required. Install a Java 21 JDK and add java to PATH.' }
$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javac) { Stop-WithMessage 'A Java 21 JDK is required for Maven test compilation; javac was not found on PATH.' }
$versionStartInfo = New-Object System.Diagnostics.ProcessStartInfo
$versionStartInfo.FileName = $java.Source
$versionStartInfo.Arguments = '-version'
$versionStartInfo.UseShellExecute = $false
$versionStartInfo.RedirectStandardError = $true
$versionProcess = New-Object System.Diagnostics.Process
$versionProcess.StartInfo = $versionStartInfo
[void]$versionProcess.Start()
$javaVersionOutput = $versionProcess.StandardError.ReadToEnd()
$versionProcess.WaitForExit()
$javaVersion = ($javaVersionOutput -split "`r?`n" | Select-Object -First 1).Trim()
if ($javaVersion -notmatch '21\.') { Stop-WithMessage "Java 21 is required. Detected: $javaVersion" }

$maven = Get-Command mvn -ErrorAction SilentlyContinue
if ($maven) {
    $mavenCommand = $maven.Source
} else {
    $bundledMaven = Join-Path $projectRoot 'work\apache-maven-3.9.11\bin\mvn.cmd'
    if (-not (Test-Path $bundledMaven)) {
        Stop-WithMessage 'Maven was not found. Install Maven or restore work\apache-maven-3.9.11.'
    }
    $mavenCommand = $bundledMaven
}

Write-Host 'Step 1/5: running API Dependabot Maven verify with the portable Eclipse Java compiler profile...'
$mavenArguments = @()
if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
    $mavenArguments += "-Dmaven.repo.local=$MavenRepository"
}
$mavenArguments += '-Pecj-compiler', 'verify'
& $mavenCommand @mavenArguments
if ($LASTEXITCODE -ne 0) { Stop-WithMessage "Maven verify failed with exit code $LASTEXITCODE. Fix that failure before running the model evaluation." }

Write-Host 'Step 2/5: running the Stripe Basil consumer fixture tests...'
$stripePom = Join-Path $projectRoot 'test-projects\stripe-consumer\pom.xml'
$stripeArguments = @()
if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
    $stripeArguments += "-Dmaven.repo.local=$MavenRepository"
}
$stripeArguments += '-Dmaven.compiler.fork=true', '-f', $stripePom, 'test'
& $mavenCommand @stripeArguments
if ($LASTEXITCODE -ne 0) { Stop-WithMessage "Stripe consumer fixture tests failed with exit code $LASTEXITCODE." }

if (-not [string]::IsNullOrWhiteSpace($GitHubRepository)) {
    Write-Host 'Step 3/5: checking out and searching the supplied GitHub repository...'
    $jar = Join-Path $projectRoot 'target\api-dependabot-0.1.0-SNAPSHOT.jar'
    $oldSpec = Join-Path $projectRoot 'src\test\resources\specs\v1.yaml'
    $newSpec = Join-Path $projectRoot 'src\test\resources\specs\v2-removed-response-property.yaml'
    $jsonOutput = & java -jar $jar "--old=$oldSpec" "--new=$newSpec" "--repo=$GitHubRepository" '--find=name' --format=json
    if ($LASTEXITCODE -ne 0) { Stop-WithMessage "GitHub repository smoke check failed with exit code $LASTEXITCODE." }
    try { $githubResult = ($jsonOutput -join "`n") | ConvertFrom-Json }
    catch { Stop-WithMessage 'GitHub repository smoke check did not return valid JSON.' }
    if ($githubResult.status -ne 'success') {
        Stop-WithMessage "GitHub repository smoke check failed: $($githubResult.error)"
    }
    Write-Host "GitHub checkout/search passed for $GitHubRepository."
} else {
    Write-Host 'Step 3/5: remote GitHub checkout smoke test skipped; pass -GitHubRepository <URL> to run it.'
}

if ($SkipEvaluation) {
    Write-Host 'Local verification passed. Promptfoo evaluation skipped by request.'
    exit 0
}

$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { Stop-WithMessage 'Node.js 22.22.0 or later is required for the Promptfoo evaluation.' }
$nodeVersion = (& node -p 'process.versions.node').Trim()
$versionParts = $nodeVersion.Split('.')
$supported = $false
if ($versionParts.Count -ge 2) {
    if ([int]$versionParts[0] -gt 22) { $supported = $true }
    elseif ([int]$versionParts[0] -eq 22 -and [int]$versionParts[1] -ge 22) { $supported = $true }
}
if (-not $supported) { Stop-WithMessage "Node.js 22.22.0 or later is required. Detected: v$nodeVersion" }
if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    Stop-WithMessage 'Set OPENAI_API_KEY in this PowerShell session before running the full live evaluation.'
}

$npm = Get-Command npm -ErrorAction SilentlyContinue
if (-not $npm) { Stop-WithMessage 'npm was not found. Install Node.js 22.22.0 or later with npm.' }
if (-not (Test-Path (Join-Path $projectRoot 'node_modules\promptfoo'))) {
    Write-Host 'Installing locked Promptfoo dependencies with npm ci...'
    & npm ci
    if ($LASTEXITCODE -ne 0) { Stop-WithMessage "npm ci failed with exit code $LASTEXITCODE." }
}

if ([string]::IsNullOrWhiteSpace($env:SPRING_AI_MODEL_CHAT)) {
    $env:SPRING_AI_MODEL_CHAT = 'openai'
}

Write-Host 'Step 4/5: running the complete shared Vanilla RAG vs ReAct evaluation...'
& npm run eval
$evaluationExitCode = $LASTEXITCODE

Write-Host 'Step 5/5: summarizing pass rate, judge scores, latency, and ReAct tool use...'
& node 'evaluation/promptfoo/summarize-results.mjs'
if ($LASTEXITCODE -ne 0) { Stop-WithMessage "Evaluation summary failed with exit code $LASTEXITCODE." }

if ($evaluationExitCode -ne 0) {
    Write-Warning "Promptfoo reported one or more failed assertions (exit code $evaluationExitCode). The completed evaluation summary is available at evaluation/promptfoo/summary.json."
    exit $evaluationExitCode
}

Write-Host 'Validation complete. Share evaluation/promptfoo/summary.json and any failed case output to continue with results analysis and capstone documentation.'
