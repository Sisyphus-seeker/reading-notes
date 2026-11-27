package dev.readingnotes.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.*;
import com.intellij.ui.components.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.readingnotes.*;
import dev.readingnotes.model.Notebook;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public final class NotesPanel extends JPanel implements Disposable {
    private final Project project;
    private final NotebookService store;
    private final Tree tree = new Tree(new DefaultMutableTreeNode("root")) {
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
    };
    private final JBTextField search = new JBTextField();
    private final JLabel count = new JBLabel();
    private final JBSplitter splitter;
    private final JPanel details = new JPanel(new BorderLayout());
    private final Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
    private boolean rebuilding;

    public NotesPanel(Project project) {
        super(new BorderLayout()); this.project = project; store = NotebookService.get(project);
        setMinimumSize(JBUI.size(230, 200));
        Notebook.Data initial = store.book().snapshot();
        splitter = new JBSplitter(!initial.horizontal, initial.split, .15f, .85f);
        splitter.setFirstComponent(navigation()); splitter.setSecondComponent(details);
        splitter.addPropertyChangeListener("proportion", e -> store.book().layout(!splitter.getOrientation(), splitter.getProportion()));
        add(splitter, BorderLayout.CENTER);
        project.getMessageBus().connect(this).subscribe(NotebookService.CHANGED, () -> {
            if (SwingUtilities.isEventDispatchThread()) rebuild(false);
            else SwingUtilities.invokeLater(() -> { if (!project.isDisposed()) rebuild(false); });
        });
        rebuild(true);
    }

    private JComponent navigation() {
        JPanel area = new JPanel(new BorderLayout()); area.setMinimumSize(JBUI.size(100, 90));
        JPanel top = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        top.setBorder(JBUI.Borders.empty(12, 12, 6, 12));
        JPanel heading = new JPanel(new BorderLayout());
        JBLabel label = new JBLabel("学习笔记"); label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 2f));
        heading.add(label, BorderLayout.WEST); count.setForeground(UIUtil.getContextHelpForeground()); heading.add(count, BorderLayout.EAST);
        top.add(heading, BorderLayout.NORTH);
        search.getEmptyText().setText("搜索标题、正文、文件、主题…");
        search.getAccessibleContext().setAccessibleName("搜索笔记");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild(true); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild(true); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild(true); }
        });
        top.add(search, BorderLayout.CENTER);

        JToolBar toolbar = toolbar();
        toolbar.add(button("新建行笔记（Ctrl+Alt+Shift+N）", AllIcons.General.Add, this::addNote));
        JButton manage = button("管理学习主题与分组", AllIcons.Nodes.Folder, () -> {});
        manage.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            item(menu, "新建学习主题", this::addTopic); item(menu, "新建分组", this::addGroup);
            menu.addSeparator(); item(menu, "重命名所选主题 / 分组", this::rename); item(menu, "删除所选项…", this::deleteSelection);
            menu.show(manage, 0, manage.getHeight());
        });
        toolbar.add(manage);
        toolbar.add(button("展开全部", AllIcons.Actions.Expandall, () -> expandAll(true)));
        toolbar.add(button("折叠全部", AllIcons.Actions.Collapseall, () -> expandAll(false)));
        toolbar.addSeparator();
        toolbar.add(button("切换上下 / 左右布局", AllIcons.Actions.SplitHorizontally, () -> {
            splitter.setOrientation(!splitter.getOrientation());
            store.book().layout(!splitter.getOrientation(), splitter.getProportion());
        }));
        JButton sharing = button("分享笔记：导入 / 导出", AllIcons.Actions.MenuSaveall, () -> {});
        sharing.addActionListener(e -> ShareActions.menu(project, sharing, selected()));
        toolbar.add(sharing);
        top.add(toolbar, BorderLayout.SOUTH);
        area.add(top, BorderLayout.NORTH);

        tree.setRootVisible(false); tree.setShowsRootHandles(true); tree.setRowHeight(JBUI.scale(28));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new ColoredTreeCellRenderer() {
            @Override public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean focus) {
                Object item = ((DefaultMutableTreeNode) value).getUserObject();
                if (item instanceof Notebook.Topic topic) {
                    setIcon(AllIcons.Nodes.Folder); append(topic.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    append("  " + descendantNotes((DefaultMutableTreeNode) value), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else if (item instanceof Notebook.Group group) {
                    setIcon(AllIcons.Nodes.Folder); append(group.name);
                    append("  " + ((DefaultMutableTreeNode) value).getChildCount(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else if (item instanceof Notebook.Note note) {
                    setIcon(note.detached ? AllIcons.General.Warning : NotesIcons.NOTE); append(note.title);
                    String file = note.filePath.substring(note.filePath.lastIndexOf('/') + 1);
                    append("  " + file + ":" + note.line, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    if (note.detached) append("  位置待确认", SimpleTextAttributes.ERROR_ATTRIBUTES);
                }
            }
        });
        tree.addTreeSelectionListener(e -> { if (!rebuilding) showDetails(); });
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && selected() instanceof Notebook.Note note) NotesUi.navigate(project, note.id);
            }
            @Override public void mousePressed(MouseEvent e) { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "readingNotes.open");
        tree.getActionMap().put("readingNotes.open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (selected() instanceof Notebook.Note n) NotesUi.edit(project, n.id); }
        });
        tree.getEmptyText().setText("没有匹配的笔记");
        JBScrollPane scrolling = new JBScrollPane(tree); scrolling.setBorder(JBUI.Borders.empty());
        scrolling.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        area.add(scrolling, BorderLayout.CENTER);
        return area;
    }

    private void rebuild(boolean expand) {
        String selectedId = key(selected());
        Set<String> expanded = new HashSet<>();
        Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(tree.getModel().getRoot()));
        if (paths != null) while (paths.hasMoreElements()) expanded.add(key(((DefaultMutableTreeNode) paths.nextElement().getLastPathComponent()).getUserObject()));
        rebuilding = true;
        try {
            Notebook.Data data = store.book().snapshot();
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root"); nodes.clear(); int matches = 0;
            for (Notebook.Topic topic : data.topics) {
                DefaultMutableTreeNode topicNode = new DefaultMutableTreeNode(topic);
                boolean topicMatch = topic.name.toLowerCase(Locale.ROOT).contains(query);
                for (Notebook.Group group : data.groups) {
                    if (!group.topicId.equals(topic.id)) continue;
                    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
                    boolean groupMatch = topicMatch || group.name.toLowerCase(Locale.ROOT).contains(query);
                    for (Notebook.Note note : data.notes) {
                        if (!note.groupId.equals(group.id) || (!groupMatch && !Notebook.matches(note, query))) continue;
                        DefaultMutableTreeNode node = new DefaultMutableTreeNode(note, false);
                        groupNode.add(node); nodes.put(note.id, node); matches++;
                    }
                    if (query.isEmpty() || groupMatch || groupNode.getChildCount() > 0) { topicNode.add(groupNode); nodes.put(group.id, groupNode); }
                }
                if (query.isEmpty() || topicMatch || topicNode.getChildCount() > 0) { root.add(topicNode); nodes.put(topic.id, topicNode); }
            }
            tree.setModel(new DefaultTreeModel(root));
            count.setText(query.isEmpty() ? data.notes.size() + " 条笔记" : matches + " / " + data.notes.size());
            for (Map.Entry<String, DefaultMutableTreeNode> entry : nodes.entrySet())
                if (expand || expanded.contains(entry.getKey())) tree.expandPath(new TreePath(entry.getValue().getPath()));
            DefaultMutableTreeNode restore = nodes.get(selectedId);
            if (restore != null) tree.setSelectionPath(new TreePath(restore.getPath()));
        } finally { rebuilding = false; }
        showDetails();
    }

    private void showDetails() {
        details.removeAll();
        Object selected = selected();
        if (selected instanceof Notebook.Note note) renderNote(note);
        else {
            JPanel empty = new JPanel(new BorderLayout(JBUI.scale(8), JBUI.scale(12)));
            empty.setBorder(JBUI.Borders.empty(18));
            String title = selected instanceof Notebook.Topic t ? t.name : selected instanceof Notebook.Group g ? g.name : "把理解留在代码旁";
            JBLabel heading = new JBLabel(title); heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize() + 2f));
            empty.add(heading, BorderLayout.NORTH);
            JBTextArea help = text("在代码中按 Ctrl+Alt+Shift+N，\n或右键选择「新建行笔记」。\n\n点击行号旁的笔记图标查看或编辑；\n双击列表中的笔记跳转到代码。\n\n可拖动中间分隔线调整区域大小；\n工具窗口菜单可切换浮动窗口。", false);
            help.setForeground(UIUtil.getContextHelpForeground()); empty.add(help, BorderLayout.CENTER);
            JButton add = new JButton("在当前行创建笔记"); add.addActionListener(e -> addNote());
            empty.add(add, BorderLayout.SOUTH); details.add(empty, BorderLayout.CENTER);
        }
        details.revalidate(); details.repaint();
    }

    private void renderNote(Notebook.Note note) {
        JPanel view = new JPanel(new BorderLayout(0, JBUI.scale(10))); view.setBorder(JBUI.Borders.empty(12));
        JPanel heading = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        JBTextArea title = text(note.title, false); title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 2f));
        heading.add(title, BorderLayout.NORTH);
        Notebook.Data data = store.book().snapshot();
        Notebook.Group group = data.groups.stream().filter(g -> g.id.equals(note.groupId)).findFirst().orElse(null);
        String topic = group == null ? "" : data.topics.stream().filter(t -> t.id.equals(group.topicId)).map(t -> t.name).findFirst().orElse("");
        JBTextArea breadcrumb = text(topic + "  /  " + (group == null ? "" : group.name), false);
        breadcrumb.setForeground(UIUtil.getContextHelpForeground()); heading.add(breadcrumb, BorderLayout.CENTER);
        JToolBar commands = toolbar();
        commands.add(button("编辑笔记 / 移动分组", AllIcons.Actions.Edit, () -> NotesUi.edit(project, note.id)));
        commands.add(button("跳转代码", AllIcons.Actions.Forward, () -> NotesUi.navigate(project, note.id)));
        commands.add(button("绑定到当前行", AllIcons.General.Locate, () -> NotesUi.rebind(project, note.id)));
        commands.add(button("删除笔记…", AllIcons.General.Remove, this::deleteSelection));
        heading.add(commands, BorderLayout.SOUTH);
        view.add(heading, BorderLayout.NORTH);

        JBTextArea content = text(note.text.isBlank() ? "暂无正文，点击编辑补充你的理解。" : note.text, true);
        content.setMargin(JBUI.insets(10));
        JBScrollPane scroll = new JBScrollPane(content); view.add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, JBUI.scale(5)));
        String status = note.detached ? "位置待确认 · 请绑定到当前行" : NoteLocations.resolve(project, note.filePath) == null ? "文件不可用 · 可能已删除或切换分支" : "已绑定代码";
        JBTextArea location = text(note.filePath + " : " + note.line + "\n" + status, false);
        location.setForeground(note.detached ? UIUtil.getErrorForeground() : UIUtil.getContextHelpForeground()); footer.add(location, BorderLayout.NORTH);
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(note.updatedAt));
        JBLabel updated = new JBLabel("更新于 " + date); updated.setForeground(UIUtil.getContextHelpForeground()); footer.add(updated, BorderLayout.SOUTH);
        view.add(footer, BorderLayout.SOUTH); details.add(view, BorderLayout.CENTER);
    }

    private static JBTextArea text(String value, boolean selectable) {
        JBTextArea area = new JBTextArea(value); area.setEditable(false); area.setFocusable(selectable);
        area.setLineWrap(true); area.setWrapStyleWord(true); area.setOpaque(false);
        area.setMinimumSize(JBUI.size(0, 0)); return area;
    }

    private void popup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path != null) tree.setSelectionPath(path);
        JPopupMenu menu = new JPopupMenu();
        if (selected() instanceof Notebook.Note note) {
            item(menu, "查看 / 编辑", () -> NotesUi.edit(project, note.id));
            item(menu, "跳转代码", () -> NotesUi.navigate(project, note.id));
            item(menu, "绑定到当前行…", () -> NotesUi.rebind(project, note.id));
            menu.addSeparator();
        } else {
            item(menu, "新建学习主题", this::addTopic); item(menu, "新建分组", this::addGroup);
            item(menu, "在当前行新建笔记", this::addNote); menu.addSeparator();
            item(menu, "重命名…", this::rename);
        }
        item(menu, "导出所选项…", () -> ShareActions.export(project, tree, selected()));
        item(menu, "删除…", this::deleteSelection); menu.show(tree, event.getX(), event.getY());
    }

    private static void item(JPopupMenu menu, String title, Runnable command) {
        JMenuItem item = new JMenuItem(title); item.addActionListener(e -> command.run()); menu.add(item);
    }

    private void addNote() {
        String group = selected() instanceof Notebook.Group g ? g.id : selected() instanceof Notebook.Note n ? n.groupId : "";
        if (group.isEmpty() && selected() instanceof Notebook.Topic t) {
            group = store.book().snapshot().groups.stream().filter(g -> g.topicId.equals(t.id)).map(g -> g.id).findFirst().orElse("");
            if (group.isEmpty()) { addGroup(); return; }
        }
        NotesUi.create(project, FileEditorManager.getInstance(project).getSelectedTextEditor(), group);
    }

    private void addTopic() {
        String name = Messages.showInputDialog(project, "学习主题名称，例如：登录流程", "新建学习主题", null);
        if (name == null) return;
        change(() -> {
            Notebook.Topic topic = store.book().addTopic(name);
            store.book().addGroup(topic.id, "默认分组"); return topic.id;
        });
    }

    private void addGroup() {
        String topicId = selectedTopic();
        if (topicId == null) { Messages.showInfoMessage(project, "请先选择一个学习主题。", "新建分组"); return; }
        String name = Messages.showInputDialog(project, "分组名称，例如：参数校验", "新建分组", null);
        if (name == null) return;
        change(() -> store.book().addGroup(topicId, name).id);
    }

    private String selectedTopic() {
        Object item = selected();
        if (item instanceof Notebook.Topic t) return t.id;
        Notebook.Data data = store.book().snapshot();
        String groupId = item instanceof Notebook.Group g ? g.id : item instanceof Notebook.Note n ? n.groupId : "";
        return data.groups.stream().filter(g -> g.id.equals(groupId)).map(g -> g.topicId).findFirst()
            .orElse(data.topics.size() == 1 ? data.topics.get(0).id : null);
    }

    private void rename() {
        Object value = selected();
        if (!(value instanceof Notebook.Topic) && !(value instanceof Notebook.Group)) { Messages.showInfoMessage(project, "请选择要重命名的主题或分组。", "重命名"); return; }
        String current = value instanceof Notebook.Topic t ? t.name : ((Notebook.Group) value).name;
        String name = Messages.showInputDialog(project, "新的名称", "重命名", null, current, null);
        if (name == null) return;
        change(() -> { if (value instanceof Notebook.Topic t) store.book().renameTopic(t.id, name); else store.book().renameGroup(((Notebook.Group) value).id, name); return key(value); });
    }

    private void deleteSelection() {
        Object value = selected(); if (value == null) return;
        String name = value instanceof Notebook.Note n ? n.title : value instanceof Notebook.Group g ? g.name : ((Notebook.Topic) value).name;
        String description = value instanceof Notebook.Note ? "删除笔记「" + name + "」？" : "删除「" + name + "」及其下所有笔记？";
        if (Messages.showYesNoDialog(project, description + "\n此操作无法撤销。", "确认删除", "删除", "取消", Messages.getWarningIcon()) != Messages.YES) return;
        if (value instanceof Notebook.Note n) store.book().deleteNote(n.id);
        else if (value instanceof Notebook.Group g) store.book().deleteGroup(g.id);
        else store.book().deleteTopic(((Notebook.Topic) value).id);
        store.changed();
    }

    private void change(java.util.function.Supplier<String> operation) {
        try {
            String id = operation.get(); store.changed();
            DefaultMutableTreeNode node = nodes.get(id);
            if (node != null) { TreePath path = new TreePath(node.getPath()); tree.expandPath(path); tree.setSelectionPath(path); tree.scrollPathToVisible(path); }
        } catch (IllegalArgumentException ex) { Messages.showErrorDialog(project, ex.getMessage(), "操作未完成"); }
    }

    public void selectNote(String id) {
        if (!nodes.containsKey(id) && !search.getText().isEmpty()) search.setText("");
        DefaultMutableTreeNode node = nodes.get(id);
        if (node != null) { TreePath path = new TreePath(node.getPath()); tree.setSelectionPath(path); tree.scrollPathToVisible(path); }
    }

    private Object selected() { return tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node ? node.getUserObject() : null; }
    private static String key(Object value) { return value instanceof Notebook.Topic t ? t.id : value instanceof Notebook.Group g ? g.id : value instanceof Notebook.Note n ? n.id : ""; }
    private static int descendantNotes(DefaultMutableTreeNode node) {
        int count = 0; for (int i = 0; i < node.getChildCount(); i++) count += ((DefaultMutableTreeNode) node.getChildAt(i)).getChildCount(); return count;
    }
    private void expandAll(boolean expand) { for (int i = expand ? 0 : tree.getRowCount() - 1; expand ? i < tree.getRowCount() : i >= 0; i += expand ? 1 : -1) { if (expand) tree.expandRow(i); else tree.collapseRow(i); } }
    private static JToolBar toolbar() {
        JToolBar bar = new JToolBar(); bar.setFloatable(false); bar.setRollover(true); bar.setOpaque(false);
        bar.setBorder(JBUI.Borders.empty()); return bar;
    }
    private static JButton button(String tooltip, Icon icon, Runnable command) {
        JButton button = new JButton(icon); button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip); button.setFocusable(false);
        button.setPreferredSize(JBUI.size(30, 28)); button.setMinimumSize(JBUI.size(28, 28));
        button.setMaximumSize(JBUI.size(30, 28)); button.setMargin(JBUI.emptyInsets());
        button.addActionListener(e -> command.run()); return button;
    }
    @Override public void dispose() { store.book().layout(!splitter.getOrientation(), splitter.getProportion()); }
}
