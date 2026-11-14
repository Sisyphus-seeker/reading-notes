package dev.readingnotes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.readingnotes.*;
import dev.readingnotes.model.Notebook;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/** A real, resizable IDE dialog. Text wraps; width never depends on note length. */
public final class NoteDialog extends DialogWrapper {
    private final Project project;
    private final NotebookService store;
    private final Notebook.Note original;
    private final JComboBox<Notebook.Topic> topics = new JComboBox<>();
    private final JComboBox<Notebook.Group> groups = new JComboBox<>();
    private final JBTextField title = new JBTextField();
    private final JBTextArea body = new JBTextArea();
    private JPanel panel;
    private String savedId;

    public NoteDialog(Project project, Notebook.Note note) {
        super(project, true);
        this.project = project; store = NotebookService.get(project); original = new Notebook.Note(note);
        setTitle(note.id.isEmpty() ? "新建行笔记" : "查看 / 编辑行笔记");
        setOKButtonText("保存笔记"); setCancelButtonText("取消"); setResizable(true);
        title.setText(note.title); body.setText(note.text);
        body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setMargin(JBUI.insets(10));
        body.getEmptyText().setText("记录这段代码的作用、调用关系和你的理解…");
        body.setToolTipText("Ctrl+Enter 保存笔记；Enter 换行");
        body.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "readingNotes.save");
        body.getActionMap().put("readingNotes.save", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { getOKAction().actionPerformed(e); }
        });
        topics.addActionListener(e -> fillGroups(null));
        refill(note.groupId);
        init();
        getWindow().setMinimumSize(JBUI.size(420, 380));
    }

    private void refill(String groupId) {
        Notebook.Data data = store.book().snapshot();
        String desired = groupId == null || groupId.isEmpty() ? data.lastGroupId : groupId;
        String topicId = data.groups.stream().filter(g -> g.id.equals(desired)).map(g -> g.topicId).findFirst().orElse("");
        topics.removeAllItems();
        for (Notebook.Topic topic : data.topics) topics.addItem(topic);
        for (int i = 0; i < topics.getItemCount(); i++) if (topics.getItemAt(i).id.equals(topicId)) topics.setSelectedIndex(i);
        fillGroups(desired);
    }

    private void fillGroups(String groupId) {
        groups.removeAllItems();
        Notebook.Topic selected = (Notebook.Topic) topics.getSelectedItem();
        if (selected == null) return;
        List<Notebook.Group> values = store.book().snapshot().groups.stream().filter(g -> g.topicId.equals(selected.id)).toList();
        for (Notebook.Group value : values) groups.addItem(value);
        for (int i = 0; i < groups.getItemCount(); i++) if (groups.getItemAt(i).id.equals(groupId)) groups.setSelectedIndex(i);
    }

    @Override protected JComponent createCenterPanel() {
        panel = new JPanel(new BorderLayout(0, JBUI.scale(12)));
        panel.setPreferredSize(JBUI.size(620, 520)); panel.setMinimumSize(JBUI.size(360, 300));
        JPanel fields = new JPanel(new GridBagLayout());
        addField(fields, 0, "学习主题", choice(topics, "新建学习主题", this::addTopic));
        addField(fields, 1, "分组", choice(groups, "新建分组", this::addGroup));
        addField(fields, 2, "标题", title);
        panel.add(fields, BorderLayout.NORTH);

        JPanel writing = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        writing.add(new JBLabel("笔记正文"), BorderLayout.NORTH);
        writing.add(new JBScrollPane(body), BorderLayout.CENTER);
        panel.add(writing, BorderLayout.CENTER);

        JPanel source = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        JBTextArea location = new JBTextArea(original.filePath + " : " + original.line + (original.detached ? "  ·  位置待确认" : ""));
        location.setEditable(false); location.setFocusable(false); location.setLineWrap(true); location.setWrapStyleWord(true);
        location.setForeground(UIUtil.getContextHelpForeground()); location.setOpaque(false);
        source.add(location, BorderLayout.NORTH);
        JBTextArea code = new JBTextArea(original.context.isEmpty() ? "（空行）" : original.context);
        code.setEditable(false); code.setLineWrap(true); code.setWrapStyleWord(true); code.setRows(2);
        code.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().getSize()));
        code.setMargin(JBUI.insets(8));
        JBScrollPane preview = new JBScrollPane(code); preview.setPreferredSize(JBUI.size(200, 64));
        source.add(preview, BorderLayout.CENTER);
        panel.add(source, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel choice(JComboBox<?> box, String tooltip, Runnable callback) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        box.setMinimumSize(JBUI.size(40, 28)); row.add(box, BorderLayout.CENTER);
        JButton add = new JButton("＋"); add.setToolTipText(tooltip);
        add.getAccessibleContext().setAccessibleName(tooltip); add.addActionListener(e -> callback.run());
        row.add(add, BorderLayout.EAST); return row;
    }

    private static void addField(JPanel panel, int row, String label, JComponent value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row; c.gridx = 0; c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insets(0, 0, 8, 12); panel.add(new JBLabel(label), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = JBUI.insets(0, 0, 8, 0); panel.add(value, c);
    }

    private void addTopic() {
        String name = Messages.showInputDialog(panel, "学习主题名称", "新建学习主题", null);
        if (name == null) return;
        try {
            Notebook.Topic topic = store.book().addTopic(name);
            Notebook.Group group = store.book().addGroup(topic.id, "默认分组");
            store.changed(); refill(group.id);
        } catch (IllegalArgumentException ex) { Messages.showErrorDialog(panel, ex.getMessage(), "无法新建主题"); }
    }

    private void addGroup() {
        Notebook.Topic topic = (Notebook.Topic) topics.getSelectedItem();
        if (topic == null) { addTopic(); return; }
        String name = Messages.showInputDialog(panel, "在「" + topic.name + "」下新建分组", "新建分组", null);
        if (name == null) return;
        try { Notebook.Group group = store.book().addGroup(topic.id, name); store.changed(); fillGroups(group.id); }
        catch (IllegalArgumentException ex) { Messages.showErrorDialog(panel, ex.getMessage(), "无法新建分组"); }
    }

    @Override protected @Nullable ValidationInfo doValidate() {
        if (title.getText().isBlank()) return new ValidationInfo("请填写笔记标题", title);
        if (title.getText().strip().length() > 160) return new ValidationInfo("标题不能超过 160 个字符", title);
        if (groups.getSelectedItem() == null) return new ValidationInfo("请先创建并选择分组", groups);
        return null;
    }

    @Override protected void doOKAction() {
        NoteAnchors.get(project).flush();
        Notebook.Note current = original.id.isEmpty() ? null : store.book().note(original.id);
        if (!original.id.isEmpty() && current == null) { setErrorText("这条笔记已被删除，请取消后重新打开"); return; }
        Notebook.Note value = current == null ? new Notebook.Note(original) : current;
        value.title = title.getText(); value.text = body.getText();
        value.groupId = ((Notebook.Group) groups.getSelectedItem()).id;
        try {
            savedId = store.book().saveNote(value).id; store.changed(); super.doOKAction();
        } catch (IllegalArgumentException ex) { setErrorText(ex.getMessage()); }
    }

    public String savedId() { return savedId; }
    @Override public JComponent getPreferredFocusedComponent() { return body; }
    @Override protected String getDimensionServiceKey() { return "ReadingNotes.NoteDialog.v1"; }
}
