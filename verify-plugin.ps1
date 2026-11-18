param(
    [Parameter(Mandatory = $true)][string]$IdeHome,
    [string]$VerifierJar,
    [string]$PluginZip
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
if (-not $VerifierJar) { $VerifierJar = Join-Path $root 'build/tools/verifier-cli-1.411-all.jar' }
$sdk = (Resolve-Path -LiteralPath $IdeHome).Path
$verifier = (Resolve-Path -LiteralPath $VerifierJar).Path
if (-not $PluginZip) {
    [xml]$descriptor = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $root 'src/main/resources/META-INF/plugin.xml')
    $PluginZip = Join-Path $root "dist/ReadingNotes-$($descriptor.'idea-plugin'.version).zip"
}
$zip = (Resolve-Path -LiteralPath $PluginZip).Path
$reportRoot = Join-Path $root ('build/verification/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
$log = Join-Path $reportRoot 'verifier.log'
$cache = (Join-Path $root 'build/verifier-cache').Replace('\', '/')
$java = Join-Path $sdk 'jbr/bin/java.exe'
$runtime = Join-Path $sdk 'jbr'
$priorPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & $java "-Dplugin.verifier.home.dir=$cache" -jar $verifier check-plugin $zip $sdk -runtime-dir $runtime -verification-reports-dir $reportRoot -offline -suppress-internal-api-usages no *> $log
    $verifierExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $priorPreference }
if ($verifierExit -ne 0) { throw "Verifier failed with exit code $verifierExit. See $log" }
$verdicts = @(Get-ChildItem -LiteralPath $reportRoot -Recurse -File -Filter 'verification-verdict.txt')
if ($verdicts.Count -eq 0) { throw "No verification verdict was generated. The descriptor or IDE may be invalid. See $log" }
$blockingNames = @('invalid-plugin.txt', 'compatibility-problems.txt', 'compatibility-warnings.txt', 'internal-api-usages.txt', 'deprecated-usages.txt', 'override-only-usages.txt', 'non-extendable-api-usages.txt', 'plugin-structure-warnings.txt')
$findings = @(Get-ChildItem -LiteralPath $reportRoot -Recurse -File | Where-Object { $_.Name -in $blockingNames -and $_.Length -gt 0 })
foreach ($verdict in $verdicts) {
    $text = (Get-Content -Raw -Encoding UTF8 -LiteralPath $verdict.FullName).Trim()
    Write-Host $text
    if (($text -split '\r?\n')[0].Trim() -ne 'Compatible') { throw "Verifier did not return Compatible. See $log" }
}
if ($findings.Count -gt 0) {
    foreach ($finding in $findings) { Write-Host "$($finding.Name):"; Write-Host (Get-Content -Raw -Encoding UTF8 -LiteralPath $finding.FullName) }
    throw "Release verification found blocking API or structure findings. Reports: $reportRoot"
}
Write-Host "PASS: plugin compatibility and release API checks. Reports: $reportRoot"
