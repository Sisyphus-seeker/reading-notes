# Changelog

## 0.2.0 — 2026-11-27

- Share selected topics, groups or individual notes as local JSON with portable paths and up to seven lines of source context.
- Preview path mappings, conservative source relocation, duplicate detection and explicit conflict choices before importing.
- Back up existing notes in restorable IDE XML before merging, and reject stale previews.
- Preserve supplementary Unicode characters in saved notebooks and backups using an optional UTF-8 envelope; legacy notebooks remain readable.
- Add cross-project sharing tests and narrow/wide import-dialog rendering checks.

## 0.1.1 — 2026-11-18

- Extend the declared compatibility range to IntelliJ Platform 263 (PyCharm 2026.3), retaining the Platform 253 / Java 21 compilation baseline.
- Isolate development projects, configuration, caches and plugins for each tested IDE build.
- Add a combined compatibility workflow that checks binary APIs, IDE behavior and restart persistence using the same release ZIP.
- Record the actual IDE build and Java runtime with test results, and require an explicit `Compatible` verifier verdict.

The public plugin ID and `.idea/reading-notes.xml` data format are unchanged. See [validation records](docs/VALIDATION.md) for exact tested versions and remaining limits.

## 0.1.0 — 2026-11-14

Initial open-source release of Reading Notes.

- Add, edit and delete notes on arbitrary code lines, including blank lines.
- Click a gutter icon to open a note or select between multiple notes on a line.
- Organize notes by learning topic and group.
- Search titles, bodies, file paths, topics and groups.
- Navigate to code from the topic tree and explicitly rebind detached notes.
- Resize the note dialog and switch between stacked and side-by-side tool-window layouts.
- Track line edits and IDE file renames, recover closed-file locations from source context, and persist notes locally.

The plugin interface is in Simplified Chinese. This release targets IntelliJ Platform 253 and has been runtime-tested on Windows with PyCharm 2025.3.6.1. It does not import notes from other plugins, provide a recycle bin, or synchronize notes to cloud services.
