package dev.readingnotes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import dev.readingnotes.model.*;
import dev.readingnotes.sharing.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class ShareActions {
    private ShareActions() {}
    public static void menu(Project project, Component owner, Object selected) {
        JPopupMenu menu = new JPopupMenu();
        add(menu, "导出全部笔记…", () -> export(project, owner, null));
        JMenuItem chosen = add(menu, "导出所选主题 / 分组 / 笔记…", () -> export(project, owner, selected));
        chosen.setEnabled(selected != null);
        menu.addSeparator(); add(menu, "导入分享文件…", () -> importNotes(project, owner));
        menu.show(owner, 0, owner.getHeight());
    }
    private static JMenuItem add(JPopupMenu menu, String label, Runnable command) {
        JMenuItem item = new JMenuItem(label); item.addActionListener(e -> command.run()); menu.add(item); return item;
    }
    public static void export(Project project, Component owner, Object selected) {
        NotebookSharing sharing = new NotebookSharing(project);
        Notebook.Data snapshot = sharing.snapshot();
        String id = selected instanceof Notebook.Note n ? n.id : selected instanceof Notebook.Group g ? g.id : selected instanceof Notebook.Topic t ? t.id : null;
        ShareEngine.Scope scope = selected instanceof Notebook.Note ? ShareEngine.Scope.NOTE : selected instanceof Notebook.Group ? ShareEngine.Scope.GROUP : selected instanceof Notebook.Topic ? ShareEngine.Scope.TOPIC : ShareEngine.Scope.ALL;
        if (Messages.showYesNoDialog(project,
            "分享文件包含笔记正文、相对路径，以及绑定行和前后各 3 行代码。\n不会打包完整源码或电脑绝对路径。\n\n请确认这些片段适合交给接收方。", "导出分享笔记", "选择保存位置", "取消", Messages.getInformationIcon()) != Messages.YES) return;
        JFileChooser chooser = chooser(); chooser.setDialogTitle("导出 Reading Notes 分享文件");
        chooser.setSelectedFile(new java.io.File("reading-notes.readingnotes.json"));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) file = file.resolveSibling(file.getFileName() + ".readingnotes.json");
        if (Files.exists(file) && Messages.showYesNoDialog(project, "替换已有文件？\n" + file, "确认导出位置", "替换", "取消", Messages.getWarningIcon()) != Messages.YES) return;
        Path target = file;
        background(project, () -> { NoteBundle bundle = sharing.export(snapshot, scope, id); BundleFiles.write(target, bundle); return bundle; }, bundle -> {
            long unresolved = bundle.notes.stream().filter(n -> !n.anchor.available).count();
            Messages.showInfoMessage(project, "已导出 " + bundle.notes.size() + " 条笔记。\n" + target +
                (unresolved == 0 ? "" : "\n\n其中 " + unresolved + " 条无法获取当前源码，接收方需确认绑定位置。"), "导出完成");
        });
    }
    public static void importNotes(Project project, Component owner) {
        JFileChooser chooser = chooser(); chooser.setDialogTitle("导入 Reading Notes 分享文件");
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        background(project, () -> BundleFiles.read(file), bundle -> new ImportNotesDialog(project, bundle).show());
    }
    private static JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("Reading Notes 分享文件 (*.json)", "json"));
        chooser.setAcceptAllFileFilterUsed(false); return chooser;
    }
    private static <T> void background(Project project, Callable<T> work, Consumer<T> success) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }
            @Override protected void done() {
                if (project.isDisposed()) return;
                try { success.accept(get()); }
                catch (Exception e) { Messages.showErrorDialog(project, message(e), "分享操作未完成"); }
            }
        }.execute();
    }
    static String message(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage() == null ? failure.toString() : failure.getMessage();
    }
}
