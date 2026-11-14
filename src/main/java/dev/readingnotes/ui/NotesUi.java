package dev.readingnotes.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import dev.readingnotes.*;
import dev.readingnotes.model.Notebook;

import javax.swing.*;
import java.awt.Component;
import java.util.List;
import java.util.Objects;

public final class NotesUi {
    public static final String WINDOW = "Reading Notes";
    private NotesUi() {}

    public static void create(Project project, Editor editor, String groupId) {
        if (editor == null) { Messages.showInfoMessage(project, "请先打开代码文件，把光标放在要记录的行。", "新建行笔记"); return; }
        try {
            Notebook.Note draft = NoteLocations.capture(project, editor); draft.groupId = groupId == null ? "" : groupId;
            NoteDialog dialog = new NoteDialog(project, draft);
            if (dialog.showAndGet()) reveal(project, dialog.savedId(), false);
        } catch (IllegalArgumentException ex) { Messages.showInfoMessage(project, ex.getMessage(), "新建行笔记"); }
    }

    public static void edit(Project project, String id) {
        NoteAnchors.get(project).flush();
        Notebook.Note note = NotebookService.get(project).book().note(id);
        if (note == null) return;
        NoteDialog dialog = new NoteDialog(project, note);
        if (dialog.showAndGet()) reveal(project, dialog.savedId(), false);
    }

    public static void openLine(Project project, List<String> ids, AnActionEvent event) {
        List<Notebook.Note> notes = ids.stream().map(NotebookService.get(project).book()::note).filter(Objects::nonNull).toList();
        if (notes.isEmpty()) return;
        if (notes.size() == 1) { edit(project, notes.get(0).id); return; }
        JBPopupFactory.getInstance().createPopupChooserBuilder(notes).setTitle("选择本行笔记")
            .setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                    return super.getListCellRendererComponent(list, value instanceof Notebook.Note n ? n.title : value, index, selected, focus);
                }
            }).setItemChosenCallback(note -> edit(project, note.id)).createPopup().showInBestPositionFor(event.getDataContext());
    }

    public static void navigate(Project project, String id) {
        Notebook.Note note = NoteAnchors.get(project).resolveLocation(id);
        if (note == null) return;
        if (note.detached) { Messages.showInfoMessage(project, "原代码位置已变化，请先把光标放在目标行，再选择「绑定到当前行」。", "位置待确认"); return; }
        VirtualFile file = NoteLocations.resolve(project, note.filePath);
        if (file == null) { Messages.showInfoMessage(project, "找不到文件：\n" + note.filePath + "\n\n文件可能已删除或不在当前分支。", "文件不可用"); return; }
        new OpenFileDescriptor(project, file, Math.max(0, note.line - 1), 0).navigate(true);
    }

    public static void rebind(Project project, String id) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) { Messages.showInfoMessage(project, "请先打开代码文件，把光标放在目标行。", "重新绑定笔记"); return; }
        NotebookService store = NotebookService.get(project);
        Notebook.Note note = store.book().note(id);
        if (note == null) return;
        try {
            Notebook.Note location = NoteLocations.capture(project, editor);
            if (Messages.showYesNoDialog(project, "将「" + note.title + "」绑定到：\n" + location.filePath + " : " + location.line + "？", "绑定到当前行", "绑定", "取消", Messages.getQuestionIcon()) != Messages.YES) return;
            NoteAnchors.get(project).forget(id);
            store.book().position(id, location.filePath, location.line, location.context, location.before, location.after, false);
            store.changed();
        } catch (IllegalArgumentException ex) { Messages.showInfoMessage(project, ex.getMessage(), "无法绑定"); }
    }

    public static void reveal(Project project, String id, boolean focus) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        manager.invokeLater(() -> {
            if (project.isDisposed()) return;
            ToolWindow window = manager.getToolWindow(WINDOW);
            if (window == null) return;
            Runnable select = () -> {
                for (Content content : window.getContentManager().getContents())
                    if (content.getComponent() instanceof NotesPanel panel) panel.selectNote(id);
            };
            if (focus) window.activate(select); else window.show(select);
        });
    }
}
