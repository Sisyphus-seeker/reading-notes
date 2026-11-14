# Marketplace submission materials

GitHub publication and Marketplace approval are separate. This file provides the materials for the first Marketplace submission; it does not mean a listing has been submitted or approved.

## Listing fields

| Field | Value |
| --- | --- |
| Name | Reading Notes |
| Plugin ID | `io.github.sisyphusseeker.readingnotes` |
| Version | `0.1.0` |
| Vendor | siseeker |
| Contact email | olivia0v0666@gmail.com |
| Vendor website / project home | https://github.com/Sisyphus-seeker/reading-notes |
| Source code | https://github.com/Sisyphus-seeker/reading-notes |
| Issue tracker | https://github.com/Sisyphus-seeker/reading-notes/issues |
| License | MIT; https://github.com/Sisyphus-seeker/reading-notes/blob/main/LICENSE |
| Privacy | https://github.com/Sisyphus-seeker/reading-notes/blob/main/docs/PRIVACY.md |
| Tested IDE | PyCharm 2025.3.6.1, build 253.33813.50 on Windows |
| Declared compatibility | IntelliJ Platform 253 through 253.* |
| UI language | Simplified Chinese |

Use tags relevant to code reading, notes and productivity if they are available in the upload form.

## English description

Add notes to code lines and organize them by learning topic and group.

Reading Notes keeps your understanding beside the code without adding comments to source files. Click a gutter bookmark to view or edit notes, search your reading notebook, and jump back to the associated source line.

The note dialog can be resized, and the tool window can switch between stacked and side-by-side layouts with an adjustable divider. Notes follow editor line changes and IDE file renames. When a source location becomes ambiguous or is deleted, explicitly rebind the note to the correct line.

Notes are stored locally in `.idea/reading-notes.xml`. The plugin does not upload notes or source code, collect telemetry, or require cloud accounts. The plugin interface is currently in Simplified Chinese.

Open source under the MIT License. Version 0.1.0 targets IntelliJ Platform 253 and has been runtime-tested with PyCharm 2025.3.6.1 on Windows. It does not import other plugins' notebooks, include a recycle bin, or provide cloud sync.

## 中文介绍

在代码行号旁创建和查看笔记，按学习主题、分组组织阅读记录。支持搜索、代码跳转、可调整大小的编辑窗口、上下或左右分区以及布局记忆。笔记随编辑和 IDE 内文件重命名移动；失效或存在歧义的位置可以手动重新绑定。

笔记本保存在项目本地，不上传笔记、源代码或统计信息。界面为简体中文，采用 MIT 许可证开源。

## Media

- Logo: `src/main/resources/META-INF/pluginIcon.svg` (40×40 SVG), with a dark variant.
- Note editor: `docs/previews/note-dialog.png`.
- Wide tool window: `docs/previews/sidebar-wide.png`.
- Narrow sidebar: `docs/previews/sidebar-narrow.png`.

The previews render actual plugin components with synthetic notes. They should be described as component previews, not complete desktop workflow captures.

## Steps requiring the account owner

1. Log in to JetBrains Marketplace with your own account.
2. Create or choose the vendor profile, confirm its contact details and any required identity/trader declarations.
3. Review and accept the Marketplace Developer Agreement yourself.
4. Choose **Upload plugin**, select the vendor, and upload the release ZIP together with these listing details.
5. Monitor review status; technical questions and compatibility findings can be addressed in the source project.

Do not upload the sandbox's instrumented test JAR. Use only `dist/ReadingNotes-0.1.0.zip` or its matching GitHub Release asset.

Official references: [uploading a plugin](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html), [approval guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html), [plugin logo](https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html).
