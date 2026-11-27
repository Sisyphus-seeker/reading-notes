# Reading Notes · PyCharm 行笔记

[English](README.md) · 简体中文

独立开发的 PyCharm 插件，在代码行号旁显示笔记图标，按 **学习主题 → 分组 → 笔记** 组织阅读记录。

当前版本面向 **PyCharm 2025.3–2026.3 / IntelliJ Platform 253–263**。仍以 2025.3 / Java 21 编译，保留旧版兼容性；具体测试构建及结果见验证记录。

[查看界面预览和验证记录](docs/VALIDATION.md)

## 安装

1. 打开 PyCharm 的 **设置 → 插件**。
2. 点击齿轮菜单，选择 **从磁盘安装插件 / Install Plugin from Disk**。
3. 从 [GitHub Releases](https://github.com/Sisyphus-seeker/reading-notes/releases) 下载已发布的 `ReadingNotes-<版本>.zip` 并选择它，不要解压；也可从 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/36128-reading-notes) 安装已审核版本。
4. 按 IDE 提示完成安装或重启。
5. 从右侧工具栏，或 **视图 → 工具窗口 → Reading Notes** 打开笔记面板。

正式插件 ID 为 `io.github.sisyphusseeker.readingnotes`，与 Code Reading Mark Note Pro 独立。首版未实现原插件笔记导入；原插件的数据不会被读取或改写。

早期本地测试包使用过 `dev.readingnotes.pycharm`。如果已安装该测试包，请先卸载旧包再安装正式版，避免工具窗口重复。卸载不会删除项目中的 `.idea/reading-notes.xml`。

## 使用

### 中英文切换

点击笔记面板顶部的 **中文 / EN**，选择 **简体中文** 或 **English**。默认中文；切换后已打开的笔记面板立即更新，新打开的编辑和导入导出窗口使用所选语言。选择在所有项目间共用，重启 IDE 后保留。

已有笔记正文、标题、学习主题与分组名称、代码位置保持原样；新建的默认主题和分组使用当前语言。该设置只控制 Reading Notes，IDE 与系统提供的通用控件仍使用各自的语言设置。

### 创建和查看行笔记

- 将光标放在任意代码行，包括空行，按 **Ctrl+Alt+Shift+N**，或在编辑器右键菜单选择 **新建行笔记**。
- 选择学习主题、分组，填写标题和正文；可在弹窗中直接新建主题、分组。
- 点击 **保存笔记**，或在正文中按 **Ctrl+Enter**。普通 Enter 用于换行。
- 点击行号旁的青色书签图标，即可查看和编辑笔记。同一行的多条笔记共用一个图标，点击后选择要查看的笔记。
- 双击主题树中的笔记跳转到代码；选中笔记后按 Enter 打开编辑窗口。
- 修改编辑窗口中的主题和分组，即可移动笔记。

快捷键可在 **设置 → 键盘映射 → 新建行笔记** 中修改。

### 主题、分组和搜索

- 主题树上方的 **＋** 在当前代码行创建笔记，并优先使用所选分组。
- 文件夹菜单管理学习主题、分组；也可在树节点上右键操作。
- 主题和分组支持重命名、折叠和展开，空分组也会显示。
- 搜索匹配标题、正文、文件路径、主题名和分组名，英文不区分大小写。
- 删除主题或分组会同时删除下面的笔记，操作前有确认。首版没有回收站。

### 调整窗口和布局

- 编辑弹窗可以拖动边缘调整宽高，并记住上一次的大小和位置。
- 工具窗口可以拖动边缘调整大小；打开工具窗口标题栏菜单，可以切换为 **浮动 / Float** 或 **独立窗口 / Window**。
- 点击面板工具栏的 **切换上下 / 左右布局**：窄侧栏适合上下排列，宽窗口适合左右排列。
- 拖动中间分隔线调整主题树与详情区的比例，项目会记住布局方向和比例。
- 笔记正文自动换行，长内容可以滚动；插件颜色跟随 IDE 的亮色、暗色主题。

## 代码位置的跟随规则

- 打开文件后，笔记用 IDE 的 RangeMarker 跟随代码插入、删除及行号变化。
- 在 IDE 中重命名或移动文件时，笔记通过 VirtualFilePointer 跟随文件位置。
- 重开文件或从列表跳转时，会核对保存的代码行文本和相邻上下文，尝试恢复位置。
- 原代码被删除，或出现多个无法区分的匹配位置时，笔记显示 **位置待确认**。不会直接跳到一个猜测的位置。
- 重新绑定：把编辑器光标放到目标行，选中笔记，点击 **绑定到当前行** 并确认。
- 文件被删除或不在当前分支时，笔记仍会保留，详情区显示文件不可用。

定位依赖代码文本及上下文。文件关闭期间发生的大规模重写、IDE 之外的文件重命名、分支切换造成的结构变化，可能需要手动重新绑定。

## 分享笔记与导入

0.2.0 支持全部或按主题、分组、单条笔记导出 JSON；携带绑定行与前后各 3 行代码。接收方导入时可预览定位结果、映射改名文件或目录、确认候选行，并处理重复与冲突。无法确定的位置保留为待绑定，导入前自动生成本地 XML 备份。详见[分享与恢复说明](docs/SHARING.md)。

## 数据保存

- 每个项目的笔记保存在 **`.idea/reading-notes.xml`**，代码文件本身不写入任何注释。
- 项目内文件使用相对路径；项目外文件使用绝对文件 URL。
- 备份时先保存项目，再备份此 XML；恢复前关闭项目，再放回对应路径。
- `.idea` 常被 Git 忽略。需要团队共享时，应明确把这个文件纳入版本管理；分支冲突需要自行处理。
- 编辑弹窗尺寸由 IDE 的窗口尺寸服务保存；分区比例和方向保存在项目笔记文件中。
- 界面语言保存在 IDE 配置目录的 `options/reading-notes-settings.xml`，不会进入笔记分享文件。

本插件没有云同步、网络请求或第三方服务依赖。

## 从源码构建（Windows）

使用本地 PyCharm 安装目录中的 JBR 21 编译，不需要额外下载 Gradle 或依赖：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -IdeHome 'C:\Path\To\PyCharm'
```

也可设置 `PYCHARM_HOME` 后省略 `-IdeHome`。脚本会运行模型测试并生成 `dist/ReadingNotes-0.3.0.zip`。编译 SDK 保持为 2025.3；测试新版 IDE 不需要升级日常开发环境。

构建脚本只清理本工程 `build` 目录内的指定输出目录。JAR 和 ZIP 中的文件路径统一使用 `/`，兼容 IDE 的插件加载器。

## 隔离运行和验证

```powershell
# 启动独立的开发 IDE：使用专用配置、缓存、插件目录和示例项目
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-sandbox.ps1 -IdeHome 'C:\Path\To\PyCharm'

# 自动运行 IDE 内集成检查并退出；会重置专用示例项目的测试笔记
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-sandbox.ps1 -IdeHome 'C:\Path\To\PyCharm' -SmokeTest

# 接着重启该测试实例，核验实际磁盘持久化
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-sandbox.ps1 -IdeHome 'C:\Path\To\PyCharm' -CheckRestart
```

运行前需退出该构建上一次开发 IDE。每个 IDE 构建独立使用 `build/sandboxes/PY-<构建号>` 下的配置、缓存、插件和示例项目；结果及渲染图位于其 `results` 子目录。

测试新版时，`-IdeHome` 指向新版独立安装目录，另传 `-BuildIdeHome '旧版 2025.3 安装目录'`。可通过 `-PluginZip` 测试已经构建的安装包，避免各次检查重建不同的 ZIP。完整的二进制、IDE 功能和重启验证命令见 [VERIFYING.md](docs/VERIFYING.md)。

模型测试覆盖层级关联、编辑和移动笔记、删除级联、快照隔离、数据往返、无效引用、搜索、定位上下文和歧义处理。

IDE 集成检查覆盖真实编辑器锚点、同一行多笔记、插入和删除行、中文 XML 序列化、文件重命名、删除代码后的状态、重新绑定、关闭文件后的定位，以及窄宽布局和编辑弹窗渲染。

测试辅助 Activity 只加入沙箱的插件 JAR，**不包含在最终安装包中**。集成检查会修改专用示例代码，正常插件功能不会修改用户代码。

## 工程结构

```text
src/main/java/dev/readingnotes/
  model/Notebook.java       主题、分组和笔记数据；线程安全快照
  model/AnchorResolver.java 关闭文件后按代码上下文恢复位置
  NotebookService.java     项目状态持久化和变更通知
  NoteAnchors.java         行锚点、图标和文件位置跟随
  NoteLocations.java       代码位置捕获和相对路径处理
  ui/NotesPanel.java       主题树、搜索、详情、分区布局
  ui/NoteDialog.java       可调整大小的笔记编辑窗口
  ui/NotesUi.java          创建、编辑、跳转和重新绑定
src/main/resources/        插件描述和亮暗色图标
src/test/java/             无外部依赖的模型检查
src/ideTest/java/          仅供开发沙箱使用的 IDE 集成检查
```

接口核对依据：[JetBrains Tool Windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)、[状态持久化](https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html)和官方 SDK。兼容上限为 `263.*`；具体检查的构建与验证边界见 [VALIDATION.md](docs/VALIDATION.md)。

## 开源与联系

作者：**siseeker** · 联系邮箱：**olivia0v0666@gmail.com**。

采用 [MIT 许可证](LICENSE)。问题反馈请使用 [GitHub Issues](https://github.com/Sisyphus-seeker/reading-notes/issues)。数据处理方式见[隐私说明](docs/PRIVACY.md)。
