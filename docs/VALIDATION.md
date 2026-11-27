# 验证记录

0.3.0 中英文切换、语言保存和新旧 IDE 检查见 [0.3.0 验证记录](validation/0.3.0/README.md)。

0.2.0 分享与导入功能的检查见 [0.2.0 验证记录](validation/0.2.0/README.md)。

0.1.1 的新旧版本兼容性检查、实际环境和安装包摘要见 [0.1.1 验证记录](validation/0.1.1/README.md)。以下保留首版历史记录。

## 0.1.0 首版记录

验证日期：2026-11-14。

环境：Windows，本机 PyCharm 2025.3.6.1 / 253.33813.50，随 IDE 提供的 JBR 21。

## 已完成

- 编译插件，生成 `dist/ReadingNotes-0.1.0.zip`。
- 24 项无外部依赖的模型和定位上下文检查通过。
- 19 项独立 IDE 实例内的集成检查通过（含 Ctrl+Enter 的正式保存 Action）。
- 再次启动 IDE 后，5 项磁盘持久化恢复检查通过。
- 核对 ZIP 的 `ReadingNotes/lib/reading-notes.jar` 结构、JAR 的正斜线路径、必要资源和扩展类。
- 核对正式安装包不含测试 Activity、测试类或测试扩展声明。
- 通过离屏绘制检查窄侧栏、宽窗口、编辑弹窗的真实 Swing 组件布局。
- 使用正式 ID `io.github.sisyphusseeker.readingnotes` 验证发布包、亮暗色商店图标及内置 MIT 许可。
- JetBrains Plugin Verifier 1.411 对 PY-253.33813.50 返回 `Compatible`，无插件内部 API、过期 API、结构或二进制兼容性问题报告。

IDE 集成检查使用专用的配置、缓存、插件目录和示例项目，未安装到原有 PyCharm 用户配置中。

## 集成检查范围

检查真实编辑器中的行锚点、同一行多笔记共用图标及其点击 Action、插入/删除行后位置、中文多行 XML 数据、主题/分组序列化、文件重命名、工具窗口注册、窗口可调整大小及尺寸记忆键、代码删除后的失效状态、显式重新绑定、关闭文件后的上下文定位。

重启检查确认：笔记条数、中文多行正文、主题/分组、布局方向及比例、有效代码锚点均能恢复。

本次原始结果副本：[IDE 检查](validation/ide-smoke.txt)、[重启恢复](validation/ide-restart.txt)。构建后的原始文件位于 `build/sandbox/results`。测试源码见 `src/ideTest/java/dev/readingnotes/IdeSmokeActivity.java`。

兼容性检查步骤见 [VERIFYING.md](VERIFYING.md)。Verifier 读取本机 IDE 时，对其布局中不存在的部分模块路径输出了警告；检查完成并给出上述兼容结论。此结果针对本机安装，不代表已检查其他 IDE 产品或整个 253 系列的每个版本。

[本次 Verifier 摘要及安装包 SHA-256](validation/plugin-verifier.txt)。

## 界面预览

预览来自 IDE 内真实 Swing 组件的渲染，使用合成示例数据；不是桌面鼠标操作录像。

- [窄侧栏：主题树与笔记详情上下分区](previews/sidebar-narrow.png)
- [宽视图：主题树与笔记详情左右分区](previews/sidebar-wide.png)
- [可调整大小的笔记编辑窗口](previews/note-dialog.png)

## 验证边界

未执行鼠标拖拽缩放或全部菜单操作的桌面端自动化；缩放验证覆盖 DialogWrapper 的可调整大小配置、尺寸记忆、不同宽度下的组件渲染。

未提交 Marketplace 审核，未执行其他 IDE 产品或版本的 Plugin Verifier。兼容性范围暂限定为 253 平台系列；未实现旧插件数据迁移、回收站或云同步。
