# Contributing to Reading Notes

Use [GitHub Issues](https://github.com/Sisyphus-seeker/reading-notes/issues) for bug reports and feature proposals. Include your IDE version/build, OS, reproduction steps and expected behavior. Small synthetic examples are sufficient for code-location bugs.

## Development

- Source: `src/main/java/dev/readingnotes`.
- Persistent data and recovery model: `model/Notebook.java`, `model/AnchorResolver.java`.
- Project service and gutter tracking: `NotebookService.java`, `NoteAnchors.java`.
- UI: `ui/NotesPanel.java`, `ui/NoteDialog.java`, `ui/NotesUi.java`.
- Resources and descriptor: `src/main/resources`.
- Model tests: `src/test/java`.
- IDE integration tests: `src/ideTest/java` (not packaged in releases).

Run `test-model.ps1` with JDK 21. On Windows, compile/package using `build.ps1 -IdeHome <PyCharm 2025.3 installation>`. See the README for sandbox integration checks and `docs/VERIFYING.md` for Plugin Verifier.

For UI changes, inspect both a narrow sidebar and a wide view. For persistence or anchor changes, run the smoke and restart checks. Describe the problem, final behavior and checks performed in your pull request.

Preserve the public plugin ID `io.github.sisyphusseeker.readingnotes` and existing data-file format unless a migration is included. Keep downloaded SDKs, local IDE data and generated build outputs out of source commits.

Contributions are distributed under the project's [MIT License](LICENSE).
