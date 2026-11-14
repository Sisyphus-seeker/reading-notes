param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -and $env:PYCHARM_HOME) { $JavaHome = Join-Path $env:PYCHARM_HOME 'jbr' }
if (-not $JavaHome) {
    $compilerCommand = Get-Command javac -ErrorAction SilentlyContinue
    if ($compilerCommand) { $JavaHome = Split-Path (Split-Path $compilerCommand.Source -Parent) -Parent }
}
if (-not $JavaHome) { throw 'Set JAVA_HOME to JDK 21, or pass -JavaHome.' }
$suffix = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
$javac = Join-Path $JavaHome "bin/javac$suffix"
$java = Join-Path $JavaHome "bin/java$suffix"
if (-not (Test-Path -LiteralPath $javac)) { throw 'JavaHome must contain a Java compiler.' }
$root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$output = Join-Path $root 'build/model-test-classes'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/main/java/dev/readingnotes/model') -File -Filter '*.java' | ForEach-Object { $_.FullName })
$tests = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/test/java') -Recurse -File -Filter '*Test.java' | ForEach-Object { $_.FullName })
& $javac '-J-Duser.language=en' -encoding UTF-8 --release 21 -proc:none -d $output @sources @tests
if ($LASTEXITCODE -ne 0) { throw 'Model test compilation failed.' }
& $java -ea -cp $output dev.readingnotes.model.NotebookTest
if ($LASTEXITCODE -ne 0) { throw 'Model checks failed.' }
