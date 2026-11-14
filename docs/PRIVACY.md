# Privacy

Reading Notes is a local code-reading notebook maintained by **siseeker**.

## Data stored locally

When you create notes, the plugin stores topic/group names, note titles and bodies, source-file paths, line numbers, nearby source text, identifiers, timestamps, and split-layout settings in your project's **`.idea/reading-notes.xml`**. The IDE stores the note dialog's size and position using its standard window-dimension service.

Files inside the project use relative paths; external files use absolute file URLs. Source context is stored to recover a note's location after code changes.

## Transmission and services

The plugin does not collect analytics or telemetry, contact external services, upload source code or notes, or require a cloud account. It does not control any separate data processing performed by the IDE or other plugins.

If you commit, share, synchronize or back up your notebook XML using Git or other software, that transfer is performed through the tools you choose.

## Removal

Notes can be removed through the plugin. Deleting a topic/group also deletes its child notes after confirmation. To remove the entire stored notebook manually, close the project and remove `.idea/reading-notes.xml`. Uninstalling the plugin alone does not delete that project file.

Contact: **olivia0v0666@gmail.com**.
