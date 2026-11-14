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

The current declared range is Platform `253` through `253.*`. A successful check against one local build establishes binary compatibility with that build only. Before claiming support for additional IDE products or versions, verify and runtime-test those targets as well.
