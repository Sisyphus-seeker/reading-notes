# Binary compatibility verification

Use JetBrains' standalone [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) before publishing a build. Compilation and runtime smoke tests do not replace this check.

## Tool

The first release uses **Verifier 1.411**, downloaded from the [official JetBrains release](https://github.com/JetBrains/intellij-plugin-verifier/releases/tag/1.411). Its `verifier-cli-1.411-all.jar` asset has SHA-256:

```text
d6cc1c1f17761bb5efff187f98b63fb792c7ef2a2fb9202bb5dbea05ebc02bfa
```

Download it into `build/tools/` (ignored by Git), or pass another location with `-VerifierJar`. The verifier is a development dependency and is not bundled into the plugin.

## Run on Windows

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -IdeHome 'C:\Path\To\PyCharm'
powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-plugin.ps1 -IdeHome 'C:\Path\To\PyCharm'
```

The wrapper uses the installed IDE and its JBR in offline mode, and writes a fresh timestamped report directory under `build/verification/`. It requires a verification verdict and fails on binary compatibility problems, internal API usage, deprecated API usage, invalid plugins and plugin structure warnings. Checking the exit code alone is insufficient: a verifier invocation can exit successfully even if the input descriptor was rejected before any checks ran.

Read the generated `verification-verdict.txt` and the full `verifier.log`. SDK layout warnings in the log should be examined separately from the plugin findings; record any limitations in the validation notes.

The declared range is Platform `253` through `263.*`. A successful check against selected builds does not cover every patch or IDE product in that range.

## Check the same package in old and new IDEs

Download a portable Windows IDE from JetBrains, check its official SHA-256 and extract it to a separate directory. There is no need to replace your everyday PyCharm installation. Keep the compilation SDK at 2025.3 / Java 21, even when the runtime under test uses Java 25.

```powershell
.\test-compatibility.ps1 -BuildIdeHome 'C:\SDKs\PyCharm2025.3' -TargetIdeHome @('C:\SDKs\PyCharm2025.3', 'C:\SDKs\PyCharm2026.3')
```

This builds once, then runs Plugin Verifier, 19 IDE integration checks and 5 restart checks for each target. Every IDE build gets its own sandbox under `build/sandboxes/PY-<build>`. The release ZIP's hash is checked throughout; only sandbox copies receive the test activity. Results include the actual IDE build and Java runtime in `results/environment.txt`.

The `IDE binary compatibility` GitHub workflow compiles against 2025.3, runs the model checks and executes Plugin Verifier on selected 2025.3, 2026.1, 2026.2 and 2026.3 Windows releases. It downloads official IDEs, checks their hashes and retains verifier reports and the candidate ZIP as temporary workflow artifacts. Runtime and restart checks use the local isolated IDE workflow above: a fresh hosted Windows desktop can stop at IDE licensing/onboarding before opening a project, so CI does not claim to validate desktop behavior. Inspect failures before publishing; workflow configuration alone is not evidence of compatibility.
