package dev.readingnotes;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import dev.readingnotes.model.Notebook;

public final class NoteLocations {
    private NoteLocations() {}

    public static String path(Project project, VirtualFile file) {
        String base = project.getBasePath();
        String path = file.getPath();
        if (base != null && path.startsWith(base + "/")) return path.substring(base.length() + 1);
        return file.getUrl();
    }

    public static String url(Project project, String path) {
        return path.contains("://") ? path : "file://" + project.getBasePath() + "/" + path;
    }

    public static VirtualFile resolve(Project project, String path) {
        return VirtualFileManager.getInstance().findFileByUrl(url(project, path));
    }

    public static Notebook.Note capture(Project project, Editor editor) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (file == null || !file.isInLocalFileSystem()) throw new IllegalArgumentException("请在本地代码文件中创建笔记");
        Notebook.Note n = new Notebook.Note();
        n.filePath = path(project, file);
        int line = editor.getCaretModel().getLogicalPosition().line;
        updateContext(n, editor.getDocument(), line);
        String selected = editor.getSelectionModel().getSelectedText();
        n.title = (selected == null || selected.isBlank()) ? n.context : selected.lines().findFirst().orElse("");
        if (n.title.isBlank()) n.title = "第 " + n.line + " 行笔记";
        if (n.title.length() > 80) n.title = n.title.substring(0, 80);
        return n;
    }

    public static void updateContext(Notebook.Note note, Document document, int line) {
        int index = Math.max(0, Math.min(line, document.getLineCount() - 1));
        note.line = index + 1;
        note.context = text(document, index);
        note.before = index > 0 ? text(document, index - 1) : "";
        note.after = index + 1 < document.getLineCount() ? text(document, index + 1) : "";
    }

    public static String text(Document document, int line) {
        return document.getImmutableCharSequence().subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line)).toString().strip();
    }
}
