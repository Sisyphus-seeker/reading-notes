package dev.readingnotes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import dev.readingnotes.model.*;
import dev.readingnotes.sharing.NotebookSharing;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Explicit preview before a non-destructive import. Source I/O never runs on the EDT. */
public final class ImportNotesDialog extends DialogWrapper {
    private final Project project;
    private final NoteBundle bundle;
    private final NotebookSharing sharing;
    private final JBTextField root = new JBTextField();
    private final JBLabel summary = new JBLabel("正在匹配代码位置…");
    private final JBTextArea reason = area();
    private final JBTextArea original = area(), target = area(), body = area();
    private final JComboBox<ShareEngine.Candidate> candidates = new JComboBox<>();
    private final JSpinner line = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final JButton adopt = new JButton("采用此行");
    private final JButton match = new JButton("重新匹配");
    private final LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
    private final List<String> files;
    private final JTable paths;
    private final JTable notes;
    private final AbstractTableModel noteModel;
    private ShareEngine.Plan plan;
    private boolean busy, dirty, closed, selecting;
    private int generation;
    private SwingWorker<?, ?> worker;

    public ImportNotesDialog(Project project, NoteBundle bundle) {
        super(project, true); this.project = project; this.bundle = bundle; sharing = new NotebookSharing(project);
        bundle.validate(); files = bundle.notes.stream().map(n -> n.note.filePath).distinct().toList();
        root.setText(sharing.root().toString()); root.getAccessibleContext().setAccessibleName("当前项目内的代码根目录");
        paths = new JTable(new AbstractTableModel() {
            @Override public int getRowCount() { return files.size(); }
            @Override public int getColumnCount() { return 2; }
            @Override public String getColumnName(int c) { return c == 0 ? "分享文件中的路径" : "代码根目录下的目标相对路径"; }
            @Override public Object getValueAt(int r, int c) { String path = files.get(r); return c == 0 ? path : overrides.getOrDefault(path, path); }
            @Override public boolean isCellEditable(int r, int c) { return c == 1 && !busy; }
            @Override public void setValueAt(Object value, int r, int c) { overrides.put(files.get(r), value.toString().strip()); fireTableCellUpdated(r, c); dirty(); }
        });
        noteModel = new AbstractTableModel() {
            @Override public int getRowCount() { return plan == null ? 0 : plan.rows.size(); }
            @Override public int getColumnCount() { return 5; }
            @Override public String getColumnName(int c) { return new String[]{"笔记标题", "目标文件", "代码位置", "内容状态", "导入处理"}[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 4 ? ShareEngine.Action.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 4 && !busy && !dirty; }
            @Override public Object getValueAt(int r, int c) {
                ShareEngine.Row row = plan.rows.get(r);
                return switch (c) {
                    case 0 -> row.entry.note.title;
                    case 1 -> row.path;
                    case 2 -> row.status() + (row.attached() ? " · " + row.chosenLine : "");
                    case 3 -> row.duplicate ? "重复内容" : row.conflict ? "与当前笔记冲突" : "新增";
                    default -> row.action;
                };
            }
            @Override public void setValueAt(Object value, int r, int c) { plan.rows.get(r).action = (ShareEngine.Action) value; fireTableRowsUpdated(r, r); counts(); }
        };
        notes = new JTable(noteModel) {
            @Override public TableCellEditor getCellEditor(int r, int c) {
                if (c != 4) return super.getCellEditor(r, c);
                ShareEngine.Row row = plan.rows.get(convertRowIndexToModel(r));
                ShareEngine.Action[] choices = row.duplicate ? new ShareEngine.Action[]{ShareEngine.Action.SKIP}
                    : row.conflict ? new ShareEngine.Action[]{ShareEngine.Action.SKIP, ShareEngine.Action.REPLACE, ShareEngine.Action.COPY}
                    : new ShareEngine.Action[]{ShareEngine.Action.IMPORT, ShareEngine.Action.SKIP};
                return new DefaultCellEditor(new JComboBox<>(choices));
            }
        };
        setTitle("导入分享笔记"); setOKButtonText("确认合并导入"); setCancelButtonText("取消"); setResizable(true);
        init(); getWindow().setMinimumSize(JBUI.size(640, 500)); setOKActionEnabled(false);
        root.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { dirty(); }
            @Override public void removeUpdate(DocumentEvent e) { dirty(); }
            @Override public void changedUpdate(DocumentEvent e) { dirty(); }
        });
        notes.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) details(); });
        candidates.addActionListener(e -> {
            if (!selecting && candidates.getSelectedItem() instanceof ShareEngine.Candidate c) { line.setValue(c.line()); previewLine(); }
        });
        line.addChangeListener(e -> previewLine());
        adopt.addActionListener(e -> {
            ShareEngine.Row row = selected(); if (row == null) return;
            row.chooseLine((Integer) line.getValue()); row.match = ShareEngine.Match.REVIEW; row.reason = "已手动确认第 " + row.chosenLine + " 行";
            int index = notes.convertRowIndexToModel(notes.getSelectedRow());
            noteModel.fireTableRowsUpdated(index, index); reason.setText(row.reason); counts();
        });
        match.addActionListener(e -> replan());
        replan();
    }

    @Override protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(10))); panel.setPreferredSize(JBUI.size(960, 700));
        JPanel heading = new JPanel(new BorderLayout(8, 8));
        JBLabel rootLabel = new JBLabel("代码根目录"); rootLabel.setLabelFor(root);
        JPanel location = new JPanel(new BorderLayout(8, 0)); location.add(rootLabel, BorderLayout.WEST); location.add(root, BorderLayout.CENTER);
        JButton folder = new JButton("选择目录…"); folder.addActionListener(e -> chooseRoot()); location.add(folder, BorderLayout.EAST);
        heading.add(location, BorderLayout.NORTH);
        JPanel status = new JPanel(new BorderLayout(8, 0)); status.add(summary, BorderLayout.CENTER); status.add(match, BorderLayout.EAST);
        heading.add(status, BorderLayout.SOUTH); panel.add(heading, BorderLayout.NORTH);
        configure(notes, "待导入笔记及定位、冲突处理"); configure(paths, "文件路径映射");
        JTabbedPane tabs = new JTabbedPane();
        JPanel review = new JPanel(new BorderLayout(0, 8));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JBScrollPane(notes), preview()); split.setResizeWeight(.42);
        review.add(split, BorderLayout.CENTER);
        JBTextArea hint = area(); hint.setText("点击“导入处理”选择合并方式；位置待确认的笔记也会保留。导入前自动备份。"); hint.setRows(2); hint.setOpaque(false);
        review.add(hint, BorderLayout.SOUTH); tabs.addTab("笔记预览", review);
        JPanel mapping = new JPanel(new BorderLayout(0, 8)); mapping.add(new JBScrollPane(paths), BorderLayout.CENTER);
        JPanel commands = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        JButton file = new JButton("为所选路径选择文件…"); file.addActionListener(e -> chooseFile());
        JButton prefix = new JButton("批量替换路径前缀…"); prefix.addActionListener(e -> replacePrefix());
        commands.add(file); commands.add(prefix); mapping.add(commands, BorderLayout.SOUTH); tabs.addTab("文件路径映射", mapping);
        panel.add(tabs, BorderLayout.CENTER); return panel;
    }
    private JComponent preview() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        reason.setRows(2); panel.add(reason, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        JPanel code = new JPanel(new BorderLayout(0, 6));
        JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0)); pair.add(labelled("分享时的代码片段", original)); pair.add(labelled("当前文件中的位置", target));
        code.add(pair, BorderLayout.CENTER);
        JPanel choose = new JPanel(new BorderLayout(6, 0));
        candidates.setPreferredSize(JBUI.size(350, 28)); candidates.getAccessibleContext().setAccessibleName("候选代码位置");
        line.getAccessibleContext().setAccessibleName("待确认的目标行号");
        candidates.setMinimumSize(JBUI.size(60, 28));
        JPanel choices = new JPanel(new BorderLayout(6, 0)); choices.add(new JBLabel("候选"), BorderLayout.WEST); choices.add(candidates, BorderLayout.CENTER);
        JPanel confirmation = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0)); confirmation.add(new JBLabel("行号")); confirmation.add(line); confirmation.add(adopt);
        choose.add(choices, BorderLayout.CENTER); choose.add(confirmation, BorderLayout.EAST);
        code.add(choose, BorderLayout.SOUTH); tabs.addTab("代码位置", code); tabs.addTab("笔记正文", new JBScrollPane(body));
        panel.add(tabs, BorderLayout.CENTER); return panel;
    }
    private static JPanel labelled(String label, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, 5)); panel.add(new JBLabel(label), BorderLayout.NORTH); panel.add(new JBScrollPane(area), BorderLayout.CENTER); return panel;
    }
    private static JBTextArea area() {
        JBTextArea area = new JBTextArea(); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setMargin(JBUI.insets(8));
        return area;
    }
    private static void configure(JTable table, String name) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setRowHeight(JBUI.scale(28)); table.setFillsViewportHeight(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        DefaultTableCellRenderer plain = new DefaultTableCellRenderer(); plain.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, plain);
        table.getAccessibleContext().setAccessibleName(name); table.setAutoCreateRowSorter(true);
    }
    private void dirty() {
        if (closed) return;
        generation++; dirty = true; setOKActionEnabled(false); summary.setText("路径已修改，请点击“重新匹配”更新预览。");
        adopt.setEnabled(false);
    }
    public void replan() {
        if (closed || busy) return;
        if (paths.isEditing()) paths.getCellEditor().stopCellEditing();
        if (notes.isEditing()) notes.getCellEditor().stopCellEditing();
        int token = ++generation; Notebook.Data snapshot = sharing.snapshot();
        Map<String, String> mapping = Map.copyOf(overrides); String selectedRoot = root.getText().strip();
        busy = true; root.setEditable(false); dirty = true; match.setEnabled(false); setOKActionEnabled(false); adopt.setEnabled(false); summary.setText("正在匹配代码位置…");
        worker = new SwingWorker<ShareEngine.Plan, Void>() {
            @Override protected ShareEngine.Plan doInBackground() throws Exception { return sharing.plan(snapshot, bundle, Path.of(selectedRoot), mapping); }
            @Override protected void done() {
                if (closed || project.isDisposed()) return;
                busy = false; root.setEditable(true); match.setEnabled(true);
                if (token != generation) { dirty(); return; }
                try {
                    plan = get(); dirty = false; setErrorText(null); noteModel.fireTableDataChanged(); counts();
                    if (!plan.rows.isEmpty()) notes.setRowSelectionInterval(0, 0); else details();
                } catch (Exception e) { plan = null; noteModel.fireTableDataChanged(); summary.setText("匹配未完成，请检查路径后重试。"); setErrorText(ShareActions.message(e)); }
            }
        }; worker.execute();
    }
    private void counts() {
        if (plan == null) return;
        long selected = plan.selectedCount(), attached = plan.rows.stream().filter(r -> r.action != ShareEngine.Action.SKIP && r.attached()).count();
        long replacements = plan.rows.stream().filter(r -> r.action == ShareEngine.Action.REPLACE).count();
        summary.setText("将导入 " + selected + " 条 · 已定位 " + attached + " · 待绑定 " + (selected - attached) + " · 替换 " + replacements + " 条");
        setOKActionEnabled(!busy && !dirty && (selected > 0 || bundle.notes.isEmpty()));
    }
    private ShareEngine.Row selected() {
        int row = notes.getSelectedRow(); return plan == null || row < 0 ? null : plan.rows.get(notes.convertRowIndexToModel(row));
    }
    private void details() {
        ShareEngine.Row row = selected(); selecting = true;
        try {
            candidates.removeAllItems(); adopt.setEnabled(false); original.setText(""); target.setText(""); body.setText(""); reason.setText("");
            if (row == null) return;
            reason.setText(row.reason); body.setText(row.entry.note.text); body.setCaretPosition(0);
            List<String> snippet = new ArrayList<>(row.entry.anchor.before); snippet.add(row.entry.anchor.code); snippet.addAll(row.entry.anchor.after);
            original.setText(String.join("\n", snippet)); original.setCaretPosition(0);
            for (ShareEngine.Candidate candidate : row.candidates) candidates.addItem(candidate);
            int max = Math.max(1, ShareEngine.lines(row.source).size());
            int initial = row.attached() ? row.chosenLine : row.candidates.isEmpty() ? Math.min(row.entry.note.line, max) : row.candidates.get(0).line();
            line.setModel(new SpinnerNumberModel(initial, 1, max, 1));
            adopt.setEnabled(!busy && !dirty && !row.hash.isEmpty()); previewLine();
        } finally { selecting = false; }
    }
    private void previewLine() {
        ShareEngine.Row row = selected(); if (row == null || row.hash.isEmpty()) { target.setText("请在文件路径映射中选择目标文件，再重新匹配。"); return; }
        List<String> lines = ShareEngine.lines(row.source); int at = (Integer) line.getValue() - 1; StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, at - 3); i <= Math.min(lines.size() - 1, at + 3); i++) out.append(i == at ? "▶ " : "  ").append(i + 1).append("  ").append(lines.get(i)).append('\n');
        target.setText(out.toString()); target.setCaretPosition(0);
    }
    private void chooseRoot() {
        if (busy) return;
        if (paths.isEditing() && !paths.getCellEditor().stopCellEditing()) return;
        JFileChooser chooser = new JFileChooser(sharing.root().toFile()); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(getWindow()) == JFileChooser.APPROVE_OPTION) root.setText(chooser.getSelectedFile().toString());
    }
    private void chooseFile() {
        if (busy) return;
        if (paths.isEditing() && !paths.getCellEditor().stopCellEditing()) return;
        int row = paths.getSelectedRow(); if (row < 0) { Messages.showInfoMessage(project, "请先选中需要映射的文件路径。", "选择目标文件"); return; }
        JFileChooser chooser = new JFileChooser(sharing.root().toFile());
        if (chooser.showOpenDialog(getWindow()) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path base = Path.of(root.getText().strip()).toRealPath(), chosen = chooser.getSelectedFile().toPath().toRealPath();
            NoteBundle.require(chosen.startsWith(base), "请选择代码根目录中的文件");
            paths.getModel().setValueAt(base.relativize(chosen).toString().replace('\\', '/'), paths.convertRowIndexToModel(row), 1);
        } catch (Exception e) { setErrorText(ShareActions.message(e)); }
    }
    private void replacePrefix() {
        if (busy) return;
        if (paths.isEditing() && !paths.getCellEditor().stopCellEditing()) return;
        String from = Messages.showInputDialog(project, "原路径前缀，例如 backend/src；留空表示在所有路径前添加目录", "批量映射目录", null);
        if (from == null) return;
        String to = Messages.showInputDialog(project, "新的路径前缀，例如 services/app/src；留空表示移除原前缀", "批量映射目录", null);
        if (to == null) return;
        try {
            String old = from.strip().replace('\\', '/'), replacement = to.strip().replace('\\', '/');
            if (!old.isEmpty()) ShareEngine.relative(old); if (!replacement.isEmpty()) ShareEngine.relative(replacement);
            LinkedHashMap<String, String> changes = new LinkedHashMap<>();
            for (String source : files) {
                String current = overrides.getOrDefault(source, source);
                if (old.isEmpty() || current.startsWith(old + "/")) {
                    String tail = old.isEmpty() ? current : current.substring(old.length() + 1);
                    changes.put(source, ShareEngine.relative(replacement.isEmpty() ? tail : replacement + "/" + tail));
                }
            }
            overrides.putAll(changes); ((AbstractTableModel) paths.getModel()).fireTableDataChanged(); dirty();
        } catch (Exception e) { setErrorText(ShareActions.message(e)); }
    }
    @Override protected void doOKAction() {
        if (plan == null || busy) return;
        if (paths.isEditing() && !paths.getCellEditor().stopCellEditing()) return;
        if (notes.isEditing() && !notes.getCellEditor().stopCellEditing()) return;
        if (dirty) return;
        busy = true; root.setEditable(false); setOKActionEnabled(false); match.setEnabled(false); adopt.setEnabled(false);
        summary.setText("正在校验代码并备份现有笔记…");
        worker = new SwingWorker<NotebookSharing.Prepared, Void>() {
            @Override protected NotebookSharing.Prepared doInBackground() throws Exception { return sharing.prepare(plan); }
            @Override protected void done() {
                if (closed || project.isDisposed()) return;
                busy = false; root.setEditable(true); match.setEnabled(true);
                try {
                    NotebookSharing.Prepared prepared = get(); sharing.commit(prepared);
                    long imported = plan.selectedCount(), pending = plan.rows.stream().filter(r -> r.action != ShareEngine.Action.SKIP && !r.attached()).count();
                    closed = true; ImportNotesDialog.super.doOKAction();
                    Messages.showInfoMessage(project, "已导入 " + imported + " 条笔记，其中 " + pending + " 条位置待确认。\n\n导入前备份：\n" + prepared.backup(), "导入完成");
                } catch (Exception e) { dirty = true; setErrorText(ShareActions.message(e)); summary.setText("导入未完整完成，请检查当前笔记与备份，重新预览后重试。"); }
            }
        }; worker.execute();
    }
    @Override public void doCancelAction() { closed = true; generation++; if (worker != null) worker.cancel(true); super.doCancelAction(); }
    @Override protected String getDimensionServiceKey() { return "ReadingNotes.ImportDialog.v1"; }
    public ShareEngine.Plan currentPlan() { return plan; }
}
