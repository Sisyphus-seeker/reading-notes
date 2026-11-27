# Privacy

Reading Notes is a local code-reading notebook maintained by **siseeker**.

## Data stored locally

When you create notes, the plugin stores topic/group names, note titles and bodies, source-file paths, line numbers, nearby source text, identifiers, timestamps, and split-layout settings in your project's **`.idea/reading-notes.xml`**. The IDE stores the note dialog's size and position using its standard window-dimension service.

Files inside the project use relative paths; external files use absolute file URLs. Source context is stored to recover a note's location after code changes.

From 0.3.0, the interface language choice is stored in the IDE configuration's `options/reading-notes-settings.xml`. It is shared across local projects and excluded from exported note bundles. Changing this preference does not translate existing note content or topic/group names.

## Transmission and services

The plugin does not collect analytics or telemetry, contact external services, upload source code or notes, or require a cloud account. It does not control any separate data processing performed by the IDE or other plugins.

If you commit, share, synchronize or back up your notebook XML using Git or other software, that transfer is performed through the tools you choose.

Version 0.2.0 can export a local JSON sharing file containing selected notes, topic/group information, relative file paths, timestamps, file fingerprints and the anchored source line with up to three lines on each side. It does not bundle complete source files or automatically include the sender's absolute source paths. Note bodies and source snippets can themselves contain information you entered; review them before sharing. The plugin does not send the export anywhere.

Imports create a full local XML backup in `.idea/reading-notes-backups/`. These backups may include absolute paths for external files and are intended for local restoration. Removing the active notebook or uninstalling the plugin does not remove these backups. The optional `utf8Payload` field is an encoding for Unicode preservation, not encryption.

## Removal

Notes can be removed through the plugin. Deleting a topic/group also deletes its child notes after confirmation. To remove the entire stored notebook manually, close the project and remove `.idea/reading-notes.xml`. Uninstalling the plugin alone does not delete that project file.

Contact: **olivia0v0666@gmail.com**.
