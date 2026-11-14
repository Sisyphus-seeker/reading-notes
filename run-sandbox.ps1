param([string]$IdeHome = $env:PYCHARM_HOME, [switch]$SmokeTest, [switch]$CheckRestart)
$ErrorActionPreference = 'Stop'
if (-not $IdeHome) {
    $running = Get-Process -Name pycharm64 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($running -and $running.Path) { $IdeHome = Split-Path (Split-Path $running.Path -Parent) -Parent }
}
if (-not $IdeHome) { throw 'Pass -IdeHome with your PyCharm installation directory.' }
$sdk = (Resolve-Path -LiteralPath $IdeHome).Path
$root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$sandbox = Join-Path $root 'build\sandbox'
$classes = Join-Path $root 'build\classes'
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
& (Join-Path $root 'build.ps1') -IdeHome $sdk
$jar = Join-Path $plugin 'reading-notes.jar'
Copy-Item -LiteralPath (Join-Path $root 'build\package\ReadingNotes\lib\reading-notes.jar') -Destination $jar -Force
if ($SmokeTest -or $CheckRestart) {
    $smokeClasses = Join-Path $sandbox 'smoke-classes'
    New-Item -ItemType Directory -Path $smokeClasses -Force | Out-Null
    & (Join-Path $sdk 'jbr\bin\javac.exe') '-J-Duser.language=en' -encoding UTF-8 --release 21 -proc:none -classpath "$jar;$sdk\lib\*" -d $smokeClasses (Join-Path $root 'src\ideTest\java\dev\readingnotes\IdeSmokeActivity.java')
    if ($LASTEXITCODE -ne 0) { throw 'IDE smoke test compilation failed.' }
    [xml]$descriptor = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $classes 'META-INF\plugin.xml')
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
$propertiesFile = Join-Path $sandbox 'idea.properties'
[IO.File]::WriteAllLines($propertiesFile, $properties, $utf8)
$oldProperties = $env:PYCHARM_PROPERTIES
try {
    $env:PYCHARM_PROPERTIES = $propertiesFile
    Start-Process -FilePath (Join-Path $sdk 'bin\pycharm64.exe') -ArgumentList ('"' + (Join-Path $sandbox 'reading-notes-sandbox') + '"') -WindowStyle Hidden
} finally { $env:PYCHARM_PROPERTIES = $oldProperties }
Write-Host "Sandbox: $sandbox"
Write-Host "Results: $results"
