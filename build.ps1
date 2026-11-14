param(
    [string]$IdeHome = $env:PYCHARM_HOME,
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
if (-not $IdeHome) {
    $running = Get-Process -Name pycharm64 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($running -and $running.Path) { $IdeHome = Split-Path (Split-Path $running.Path -Parent) -Parent }
}
if (-not $IdeHome -or -not (Test-Path -LiteralPath (Join-Path $IdeHome 'product-info.json'))) {
    throw 'Pass -IdeHome with your PyCharm 2025.3 installation directory.'
}
$sdk = (Resolve-Path -LiteralPath $IdeHome).Path
$projectRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$product = Get-Content -Raw -LiteralPath (Join-Path $sdk 'product-info.json') | ConvertFrom-Json
if ($product.buildNumber -notlike '253.*') { throw 'This version targets platform 253 (PyCharm 2025.3).' }
$java = Join-Path $sdk 'jbr\bin\java.exe'
$javac = Join-Path $sdk 'jbr\bin\javac.exe'
$classes = Join-Path $projectRoot 'build\classes'
$tests = Join-Path $projectRoot 'build\test-classes'
$package = Join-Path $projectRoot 'build\package'
$dist = Join-Path $projectRoot 'dist'
function Reset-BuildDirectory([string]$target) {
    $absolute = [IO.Path]::GetFullPath($target)
    $allowed = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build')) + [IO.Path]::DirectorySeparatorChar
    if (-not $absolute.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Build target is outside the project build directory.' }
    if (Test-Path -LiteralPath $absolute) { Remove-Item -LiteralPath $absolute -Recurse -Force }
    New-Item -ItemType Directory -Path $absolute -Force | Out-Null
}
Reset-BuildDirectory $classes
Reset-BuildDirectory $tests
Reset-BuildDirectory $package
New-Item -ItemType Directory -Path $dist -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Recurse -File -Filter '*.java' | ForEach-Object { $_.FullName })
Write-Host "Compiling against $($product.name) $($product.version), build $($product.buildNumber)..."
& $javac '-J-Duser.language=en' -encoding UTF-8 --release 21 -proc:none -classpath "$sdk\lib\*" -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Plugin compilation failed.' }
Copy-Item -Path (Join-Path $projectRoot 'src\main\resources\*') -Destination $classes -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'LICENSE') -Destination (Join-Path $classes 'META-INF\LICENSE') -Force
if (-not $SkipTests) {
    $testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\test\java') -Recurse -File -Filter '*Test.java' | ForEach-Object { $_.FullName })
    $modelSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java\dev\readingnotes\model') -File -Filter '*.java' | ForEach-Object { $_.FullName })
    & $javac '-J-Duser.language=en' -encoding UTF-8 --release 21 -proc:none -d $tests @modelSources @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & $java -ea -cp $tests dev.readingnotes.model.NotebookTest
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
function Write-PortableZip([string]$directory, [string]$destination) {
    if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination -Force }
    $archive = [IO.Compression.ZipFile]::Open($destination, [IO.Compression.ZipArchiveMode]::Create)
    try {
        Get-ChildItem -LiteralPath $directory -Recurse -File | ForEach-Object {
            $entryName = $_.FullName.Substring($directory.Length + 1).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $_.FullName, $entryName, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $archive.Dispose() }
}
$lib = Join-Path $package 'ReadingNotes\lib'
New-Item -ItemType Directory -Path $lib -Force | Out-Null
Write-PortableZip $classes (Join-Path $lib 'reading-notes.jar')
$descriptor = [xml](Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $classes 'META-INF\plugin.xml'))
$zip = Join-Path $dist "ReadingNotes-$($descriptor.'idea-plugin'.version).zip"
Write-PortableZip $package $zip
Write-Host "Plugin ZIP: $zip"
