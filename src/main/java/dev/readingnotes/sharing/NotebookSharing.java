package dev.readingnotes.sharing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.readingnotes.*;
import dev.readingnotes.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class NotebookSharing {
    private final Project project;
    public NotebookSharing(Project project) { this.project = project; }
    public Path root() { return Path.of(Objects.requireNonNull(project.getBasePath(), "项目尚无目录")); }

    /** Call on EDT before starting the background export/preview. */
    public Notebook.Data snapshot() {
        NoteAnchors.get(project).flush(); NoteAnchors.get(project).refresh();
        return NotebookService.get(project).book().snapshot();
    }
    public NoteBundle export(Notebook.Data data, ShareEngine.Scope scope, String selectedId) throws IOException {
        return ShareEngine.export(data, scope, selectedId, root(), this::readSource);
    }
    public ShareEngine.Plan plan(Notebook.Data data, NoteBundle bundle, Path codeRoot, Map<String, String> mapping) throws IOException {
        return ShareEngine.plan(data, bundle, root(), codeRoot, mapping, this::readSource);
    }
    public record Prepared(ShareEngine.Plan plan, Notebook.Data merged, Path backup) {}
    /** All filesystem work is performed in the background before the EDT commits the notebook. */
    public Prepared prepare(ShareEngine.Plan plan) throws IOException {
        ShareEngine.checkSources(plan, this::readSource);
        Notebook.Data merged = ShareEngine.merge(plan);
        Path backup = BundleFiles.backup(root(), plan.base);
        ShareEngine.checkSources(plan, this::readSource);
        return new Prepared(plan, merged, backup);
    }
    public void commit(Prepared prepared) {
        if (project.isDisposed()) throw new IllegalStateException("项目已经关闭");
        NoteAnchors anchors = NoteAnchors.get(project); anchors.flush();
        NotebookService store = NotebookService.get(project);
        store.book().compareAndLoad(prepared.plan.base, prepared.merged);
        for (ShareEngine.Row row : prepared.plan.rows) if (row.action == ShareEngine.Action.REPLACE) anchors.forget(row.entry.note.id);
        store.changed(); project.save();
    }
    private String readSource(Path path) throws IOException {
        if (Files.size(path) > 4 * 1024 * 1024) throw new IOException("源码文件超过 4 MB，请手动绑定该文件中的笔记");
        VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
        if (file == null) return Files.readString(path);
        String text = ApplicationManager.getApplication().isReadAccessAllowed() ? documentText(file)
            : ReadAction.nonBlocking(() -> documentText(file)).expireWith(project).executeSynchronously();
        if (text == null) throw new IOException("目标不是可读取的文本文件");
        return text;
    }
    private static String documentText(VirtualFile file) {
        if (!file.isValid()) return null;
        Document document = FileDocumentManager.getInstance().getDocument(file);
        return document == null ? null : document.getImmutableCharSequence().toString();
    }
}
