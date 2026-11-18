param(
    [string]$IdeHome = $env:PYCHARM_HOME,
    [string]$BuildIdeHome = $env:PYCHARM_BUILD_HOME,
    [string]$PluginZip,
    [switch]$SmokeTest,
    [switch]$CheckRestart
)
$ErrorActionPreference = 'Stop'
if ($SmokeTest -and $CheckRestart) { throw 'Choose either -SmokeTest or -CheckRestart.' }
if (-not $IdeHome) {
    $running = Get-Process -Name pycharm64 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($running -and $running.Path) { $IdeHome = Split-Path (Split-Path $running.Path -Parent) -Parent }
}
if (-not $IdeHome) { throw 'Pass -IdeHome with your PyCharm installation directory.' }
$sdk = (Resolve-Path -LiteralPath $IdeHome).Path
$root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$product = Get-Content -Raw -LiteralPath (Join-Path $sdk 'product-info.json') | ConvertFrom-Json
if ($product.buildNumber -notmatch '^\d+\.\d+(\.\d+)*$') { throw 'Unexpected IDE build number.' }
if (-not $BuildIdeHome) {
    if ($product.buildNumber -notlike '253.*') {
        throw 'Pass -BuildIdeHome with a PyCharm 2025.3 installation to preserve the Java 21 / Platform 253 compilation baseline.'
    }
    $BuildIdeHome = $sdk
}
$compilerSdk = (Resolve-Path -LiteralPath $BuildIdeHome).Path
# Each tested IDE gets its own project, config, indexes, plugins and results.
# A newer IDE must never upgrade the older IDE's sandbox state.
$sandbox = Join-Path $root ("build\sandboxes\PY-" + $product.buildNumber)
$plugin = Join-Path $sandbox 'plugins\ReadingNotes\lib'
$results = Join-Path $sandbox 'results'
$lockFile = Join-Path $sandbox 'config\.lock'
if (Test-Path -LiteralPath $lockFile) {
    $lockValue = (Get-Content -Raw -LiteralPath $lockFile).Trim()
    if ($lockValue -match '^\d+$') {
        if (Get-Process -Id ([int]$lockValue) -ErrorAction SilentlyContinue) { throw 'Close the previous sandbox IDE before rebuilding its plugin.' }
        # Only stale instance files inside this dedicated development sandbox are removed.
        foreach ($relative in @('config\.lock', 'system\.port', 'system\.pid')) {
            $staleFile = Join-Path $sandbox $relative
            if (Test-Path -LiteralPath $staleFile) { Remove-Item -LiteralPath $staleFile -Force }
        }
    } else { throw 'The sandbox has an unrecognized instance lock. Close its IDE before continuing.' }
}
foreach ($directory in @($plugin, $results, (Join-Path $sandbox 'config\options'), (Join-Path $sandbox 'reading-notes-sandbox'))) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
if ($SmokeTest -or $CheckRestart) {
    $resultName = if ($CheckRestart) { 'restart-result.txt' } else { 'smoke-result.txt' }
    foreach ($fileName in @($resultName, 'smoke-failure.txt')) {
        $resultFile = Join-Path $results $fileName
        if (Test-Path -LiteralPath $resultFile) { Remove-Item -LiteralPath $resultFile -Force }
    }
}
$jar = Join-Path $plugin 'reading-notes.jar'
if (-not $PluginZip) {
    & (Join-Path $root 'build.ps1') -IdeHome $compilerSdk
    [xml]$buildDescriptor = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $root 'src\main\resources\META-INF\plugin.xml')
    $PluginZip = Join-Path $root "dist\ReadingNotes-$($buildDescriptor.'idea-plugin'.version).zip"
}
# Test an already built package unchanged across runtimes; instrument only its sandbox copy.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packageArchive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $PluginZip).Path)
try {
    $pluginEntry = $packageArchive.GetEntry('ReadingNotes/lib/reading-notes.jar')
    if (-not $pluginEntry) { throw 'The ZIP does not contain the Reading Notes plugin JAR.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($pluginEntry, $jar, $true)
} finally { $packageArchive.Dispose() }
if ($SmokeTest -or $CheckRestart) {
    $smokeClasses = Join-Path $sandbox 'smoke-classes'
    New-Item -ItemType Directory -Path $smokeClasses -Force | Out-Null
    & (Join-Path $compilerSdk 'jbr\bin\javac.exe') '-J-Duser.language=en' -encoding UTF-8 --release 21 -proc:none -classpath "$jar;$compilerSdk\lib\*" -d $smokeClasses (Join-Path $root 'src\ideTest\java\dev\readingnotes\IdeSmokeActivity.java')
    if ($LASTEXITCODE -ne 0) { throw 'IDE smoke test compilation failed.' }
    $sourceArchive = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $reader = New-Object IO.StreamReader($sourceArchive.GetEntry('META-INF/plugin.xml').Open())
        try { [xml]$descriptor = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $sourceArchive.Dispose() }
    $activity = $descriptor.CreateElement('postStartupActivity')
    $activity.SetAttribute('implementation', 'dev.readingnotes.IdeSmokeActivity')
    $descriptor.'idea-plugin'.extensions.AppendChild($activity) | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $archive = [IO.Compression.ZipFile]::Open($jar, [IO.Compression.ZipArchiveMode]::Update)
    try {
        $archive.GetEntry('META-INF/plugin.xml').Delete()
        $entry = $archive.CreateEntry('META-INF/plugin.xml')
        $stream = $entry.Open()
        try { $descriptor.Save($stream) } finally { $stream.Dispose() }
        Get-ChildItem -LiteralPath $smokeClasses -Recurse -File -Filter '*.class' | ForEach-Object {
            $name = $_.FullName.Substring($smokeClasses.Length + 1).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $_.FullName, $name) | Out-Null
        }
    } finally { $archive.Dispose() }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
$sample = @'
def authenticate(request):
    # Validate identity before entering business logic.
    token = request.headers.get("Authorization")
    if not token:
        raise ValueError("Missing token")
    return verify_token(token)


def verify_token(token):
    return {"user_id": 42, "token": token}
'@
if (-not $CheckRestart) {
    if ($SmokeTest) {
        $renamedFixture = Join-Path $sandbox 'reading-notes-sandbox\renamed.py'
        if (Test-Path -LiteralPath $renamedFixture) { Remove-Item -LiteralPath $renamedFixture -Force }
    }
    [IO.File]::WriteAllText((Join-Path $sandbox 'reading-notes-sandbox\sample.py'), $sample.Replace("`r`n", "`n"), $utf8)
    [IO.File]::WriteAllText((Join-Path $sandbox 'reading-notes-sandbox\closed.py'), "# external edit`n# another insertion`nreturn value`n", $utf8)
}
$properties = @(
    "idea.config.path=$($sandbox.Replace('\','/'))/config"
    "idea.system.path=$($sandbox.Replace('\','/'))/system"
    "idea.plugins.path=$($sandbox.Replace('\','/'))/plugins"
    "idea.log.path=$($sandbox.Replace('\','/'))/log"
    'idea.initially.ask.config=never'
    "reading.notes.smoke=$(($SmokeTest.IsPresent -or $CheckRestart.IsPresent).ToString().ToLowerInvariant())"
    "reading.notes.restart=$($CheckRestart.IsPresent.ToString().ToLowerInvariant())"
    "reading.notes.results=$($results.Replace('\','/'))"
)
if ($SmokeTest -or $CheckRestart) {
    # Official integration-test settings, scoped to this generated fixture and
    # this isolated IDE process. Normal sandbox launches keep the default prompts.
    $properties += @('idea.trust.all.projects=true', 'ide.show.tips.on.startup.default.value=false')
}
$propertiesFile = Join-Path $sandbox 'idea.properties'
[IO.File]::WriteAllLines($propertiesFile, $properties, $utf8)
$oldProperties = $env:PYCHARM_PROPERTIES
try {
    $env:PYCHARM_PROPERTIES = $propertiesFile
    $instance = Start-Process -FilePath (Join-Path $sdk 'bin\pycharm64.exe') -ArgumentList ('"' + (Join-Path $sandbox 'reading-notes-sandbox') + '"') -WindowStyle Hidden -PassThru
} finally { $env:PYCHARM_PROPERTIES = $oldProperties }
Write-Host "IDE: $($product.version), build $($product.buildNumber); PID: $($instance.Id)"
Write-Host "Sandbox: $sandbox"
Write-Host "Results: $results"
