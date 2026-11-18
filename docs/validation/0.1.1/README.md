# Reading Notes 0.1.1 兼容性验证

日期：2026-11-18。声明范围：IntelliJ Platform `253` 至 `263.*`。

## 安装包

- 文件：`ReadingNotes-0.1.1.zip`，59,166 字节。
- SHA-256：`8fa596a0097e79016c796710aad2f94b44658dc8e61b1b70aca2636d8a6fc712`。
- 使用 PyCharm 2025.3.6.1 / Platform 253 的 SDK 编译，Java class 版本保持为 65（Java 21）。
- 核对单个 `ReadingNotes/lib/reading-notes.jar` 的 ZIP 结构、正式插件 ID、`263.*` 上限、MIT 许可及图标资源。
- 安装包不包含 IDE 测试 Activity 或测试扩展。所有本机检查使用上述同一 ZIP；只向沙箱副本加入测试代码。
- 保留插件 ID `io.github.sisyphusseeker.readingnotes` 与 `.idea/reading-notes.xml` 数据格式。

## 本机检查

环境为 Windows 11，使用分别隔离的配置、缓存、插件与示例项目。

| IDE | 构建 | Java | Plugin Verifier 1.411 | IDE 集成 | 重启恢复 |
| --- | --- | --- | --- | --- | --- |
| PyCharm 2025.3.6.1 | PY-253.33813.50 | 21.0.11 | Compatible | 19 项通过 | 5 项通过 |
| PyCharm 2026.3 | PY-263.7307.206 | 25.0.4.1 | Compatible | 19 项通过 | 5 项通过 |

24 项无外部依赖的模型及代码定位检查通过。正式安装包在新旧 IDE 上均没有 Verifier 的二进制兼容、内部/过期 API、覆盖限制或结构问题报告。

原始结果副本分别位于 [253 检查](PY-253.33813.50/) 和 [263 检查](PY-263.7307.206/)。`environment.txt` 记录测试实例实际报告的 IDE 与 Java 版本；并未把用户口述的 2025.3.6.3 当作已测试版本。

检查覆盖行锚点、同一行多条笔记图标、插入/删除代码、重命名、中文多行 XML、主题和分组、代码位置失效及重新绑定、关闭文件后的定位、工具窗口与编辑弹窗、Ctrl+Enter 保存、布局持久化和重启恢复。查看了新版的窄侧栏与编辑窗口真实组件渲染图。

## 云端二进制检查

[GitHub Actions 44335963370](https://github.com/Sisyphus-seeker/reading-notes/actions/runs/44335963370) 的四个任务全部成功。检查源码提交为 `015032fafc9e64d83458654045ab1806fc5e3043`；后续提交仅整理文档和检查结果。

| IDE | 实际构建 | Plugin Verifier 1.411 |
| --- | --- | --- |
| PyCharm 2025.3.6.1 | PY-253.33813.50 | Compatible |
| PyCharm 2026.1 | PY-261.22158.340 | Compatible |
| PyCharm 2026.2 | PY-262.8665.309 | Compatible |
| PyCharm 2026.3 | PY-263.7307.206 | Compatible |

每个任务均以 2025.3 SDK 编译、执行 24 项模型检查，并对目标 IDE 运行 Verifier。四个 CI 产物与上述本机 ZIP 中的全部 JAR 条目内容逐字节一致；ZIP/JAR 容器的时间戳和压缩结果可不同。各版本原始结论与产物核对摘要位于 [ci/](ci/)。

云端记录仅代表模型及二进制检查。全新 Windows 执行器的桌面启动曾停在 IDE 授权/引导阶段，未生成运行时结果，因此最终 CI 不执行桌面功能检查；真实 IDE 功能和重启恢复结论采用上面的本机验证。

## 测试环境来源

2026.3 使用 JetBrains 官方 [Windows 便携包](https://download.jetbrains.com/python/pycharm-2026.3.win.zip)，下载完成后与[官方校验文件](https://download.jetbrains.com/python/pycharm-2026.3.win.zip.sha256)核对。

安装包 SHA-256：`f2611657f0703fc2748081afab7f98871003b6d60dad23fcc81e3ca1eef99e15`。解压后的产品元数据为 `PY-263.7307.206`。

自动测试仅打开脚本生成的专用示例项目，按 [JetBrains 集成测试文档](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html)在测试进程中设置项目确认及启动提示选项。普通开发沙箱启动和日常 IDE 配置不应用这些测试选项。

## 验证边界和日志说明

- 此记录仅覆盖列出的构建与 Windows 环境；未测试每个补丁版、其他 IDE 产品或 macOS/Linux 运行行为。
- 组件渲染与可调整大小配置检查不等同于完整的桌面拖拽和全部菜单自动化。
- Verifier 对官方 IDE 布局中部分不存在的模块路径给出了布局警告，最终结论为 Compatible。
- 2026.3 的 IDE 日志出现自带 `PythonCore` 的 CatBoost/ML 模型组件加载异常；该异常栈归属自带插件，没有指向 Reading Notes。上述笔记功能与重启检查均完成，不能据此声称整个 IDE 日志无异常。
- 该记录不表示 0.1.1 已上传或通过 Marketplace 审核。
