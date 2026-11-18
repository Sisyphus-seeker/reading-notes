param(
    [Parameter(Mandatory = $true)][string]$BuildIdeHome,
    [Parameter(Mandatory = $true)][string[]]$TargetIdeHome,
    [string]$VerifierJar,
    [int]$TimeoutSeconds = 240
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
if ($TimeoutSeconds -lt 30) { throw 'TimeoutSeconds must be at least 30.' }
& (Join-Path $root 'build.ps1') -IdeHome $BuildIdeHome
[xml]$descriptor = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $root 'src/main/resources/META-INF/plugin.xml')
$zip = Join-Path $root "dist/ReadingNotes-$($descriptor.'idea-plugin'.version).zip"
$packageHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash

function Wait-TestResult([string]$sandbox, [string]$name, [int]$expectedChecks) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $result = Join-Path $sandbox "results/$name"
    $failure = Join-Path $sandbox 'results/smoke-failure.txt'
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $failure) { throw (Get-Content -Raw -Encoding UTF8 -LiteralPath $failure) }
        if (Test-Path -LiteralPath $result) {
            $checks = @(Get-Content -Encoding UTF8 -LiteralPath $result)
            if ($checks.Count -ne $expectedChecks -or @($checks | Where-Object { $_ -notlike 'PASS *' }).Count -gt 0) {
                throw "Unexpected test result in $result"
            }
            # The activity writes its result before requesting IDE shutdown.
            $lock = Join-Path $sandbox 'config/.lock'
            $value = if (Test-Path -LiteralPath $lock) { (Get-Content -Raw -LiteralPath $lock).Trim() } else { '' }
            if ($value -notmatch '^\d+$' -or -not (Get-Process -Id ([int]$value) -ErrorAction SilentlyContinue)) {
                $checks | Write-Host
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $name and sandbox shutdown. Inspect $sandbox/log/idea.log."
}

foreach ($target in $TargetIdeHome) {
    $sdk = (Resolve-Path -LiteralPath $target).Path
    $product = Get-Content -Raw -LiteralPath (Join-Path $sdk 'product-info.json') | ConvertFrom-Json
    $sandbox = Join-Path $root ("build/sandboxes/PY-" + $product.buildNumber)
    Write-Host "Checking $($product.version), build $($product.buildNumber)..."
    $verification = @{ IdeHome = $sdk; PluginZip = $zip }
    if ($VerifierJar) { $verification.VerifierJar = $VerifierJar }
    & (Join-Path $root 'verify-plugin.ps1') @verification
    & (Join-Path $root 'run-sandbox.ps1') -IdeHome $sdk -BuildIdeHome $BuildIdeHome -PluginZip $zip -SmokeTest
    Wait-TestResult $sandbox 'smoke-result.txt' 19
    & (Join-Path $root 'run-sandbox.ps1') -IdeHome $sdk -BuildIdeHome $BuildIdeHome -PluginZip $zip -CheckRestart
    Wait-TestResult $sandbox 'restart-result.txt' 5
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $packageHash) { throw 'The release ZIP changed during testing.' }
}
Write-Host "PASS: all requested IDE targets checked against the same ZIP. SHA256: $($packageHash.ToLowerInvariant())"
