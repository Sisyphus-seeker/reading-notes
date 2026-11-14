package dev.readingnotes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.Alarm;
import dev.readingnotes.model.AnchorResolver;
import dev.readingnotes.model.Notebook;
import dev.readingnotes.ui.NotesUi;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.*;

/** Tracks arbitrary lines, including blank lines, without depending on language PSI or indexes. */
@Service(Service.Level.PROJECT)
public final class NoteAnchors implements Disposable {
    private static final class FileRef {
        String storedPath;
        final Disposable owner;
        final VirtualFilePointer pointer;
        FileRef(String storedPath, Disposable owner, VirtualFilePointer pointer) {
            this.storedPath = storedPath; this.owner = owner; this.pointer = pointer;
        }
    }
    private static final class Track {
        final Document document;
        final VirtualFile file;
        final Map<String, RangeMarker> markers = new HashMap<>();
        final List<RangeHighlighter> icons = new ArrayList<>();
        Track(Document document, VirtualFile file) { this.document = document; this.file = file; }
        void clear() {
            icons.forEach(RangeHighlighter::dispose); icons.clear();
            markers.values().forEach(RangeMarker::dispose); markers.clear();
        }
    }
    private final Project project;
    private final NotebookService store;
    private final Map<Document, Track> tracks = new IdentityHashMap<>();
    private final Map<String, FileRef> files = new HashMap<>();
    private final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private boolean updating;
    private volatile boolean disposed;

    public NoteAnchors(Project project) {
        this.project = project;
        store = NotebookService.get(project);
        project.getMessageBus().connect(this).subscribe(NotebookService.CHANGED, () -> { if (!updating) refresh(); });
        EditorFactory factory = EditorFactory.getInstance();
        factory.addEditorFactoryListener(new EditorFactoryListener() {
            @Override public void editorCreated(@NotNull EditorFactoryEvent e) {
                if (e.getEditor().getProject() == project) later(() -> { refresh(); publish(); });
            }
            @Override public void editorReleased(@NotNull EditorFactoryEvent e) {
                if (e.getEditor().getProject() == project) later(() -> releaseUnused(e.getEditor().getDocument()));
            }
        }, this);
        factory.getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override public void documentChanged(@NotNull DocumentEvent event) {
                Track t = tracks.get(event.getDocument());
                if (t != null) {
                    sync(t);
                    scheduleRefresh();
                }
            }
        }, this);
        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
            @Override public void beforeDocumentSaving(@NotNull Document document) {
                Track t = tracks.get(document);
                if (t != null) sync(t);
            }
        });
        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override public void after(@NotNull List<? extends VFileEvent> events) {
                if (!files.isEmpty()) later(NoteAnchors.this::scheduleRefresh);
            }
        });
    }

    public static NoteAnchors get(Project project) { return project.getService(NoteAnchors.class); }

    private void later(Runnable action) {
        ApplicationManager.getApplication().invokeLater(() -> { if (!project.isDisposed() && !disposed) action.run(); });
    }

    private void scheduleRefresh() {
        if (disposed) return;
        alarm.cancelAllRequests();
        alarm.addRequest(() -> { refresh(); publish(); }, 100);
    }

    public void refresh() {
        if (project.isDisposed()) return;
        if (!ApplicationManager.getApplication().isDispatchThread()) { later(this::refresh); return; }
        Notebook.Data data = store.book().snapshot();
        Set<String> ids = new HashSet<>();
        for (Notebook.Note note : data.notes) {
            ids.add(note.id);
            FileRef ref = files.get(note.id);
            if (ref != null && !ref.storedPath.equals(note.filePath)) {
                Disposer.dispose(ref.owner); files.remove(note.id); ref = null;
            }
            if (ref == null) {
                Disposable owner = Disposer.newDisposable("Reading note file");
                Disposer.register(this, owner);
                ref = new FileRef(note.filePath, owner, VirtualFilePointerManager.getInstance().create(NoteLocations.url(project, note.filePath), owner, null));
                files.put(note.id, ref);
            }
            VirtualFile file = ref.pointer.getFile();
            if (file != null) {
                String actual = NoteLocations.path(project, file);
                if (!actual.equals(note.filePath)) {
                    store.book().position(note.id, actual, note.line, note.context, note.before, note.after, note.detached);
                    note.filePath = actual; ref.storedPath = actual;
                }
            }
        }
        files.entrySet().removeIf(entry -> {
            if (ids.contains(entry.getKey())) return false;
            Disposer.dispose(entry.getValue().owner); return true;
        });
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (editor.isDisposed() || editor.getProject() != project) continue;
            Document doc = editor.getDocument();
            VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
            if (file != null && file.isInLocalFileSystem()) tracks.computeIfAbsent(doc, d -> new Track(d, file));
        }
        for (Track track : tracks.values()) rebuild(track, data.notes);
    }

    private void rebuild(Track track, List<Notebook.Note> notes) {
        track.icons.forEach(RangeHighlighter::dispose); track.icons.clear();
        Set<String> relevant = new HashSet<>();
        Map<Integer, List<String>> byLine = new TreeMap<>();
        List<String> lines = null;
        for (Notebook.Note note : notes) {
            FileRef ref = files.get(note.id);
            if (ref == null || !Objects.equals(ref.pointer.getFile(), track.file)) continue;
            relevant.add(note.id);
            RangeMarker marker = track.markers.get(note.id);
            if (note.detached) {
                if (marker != null) { marker.dispose(); track.markers.remove(note.id); }
                continue;
            }
            if (marker == null) {
                if (lines == null) lines = Arrays.asList(track.document.getText().split("\n", -1));
                int line = AnchorResolver.resolve(lines, note.line, note.context, note.before, note.after);
                if (line < 0) {
                    store.book().position(note.id, note.filePath, note.line, note.context, note.before, note.after, true);
                    continue;
                }
                int offset = track.document.getLineStartOffset(line);
                // Attach to the first character rather than indentation where possible.
                CharSequence text = track.document.getImmutableCharSequence();
                while (offset < track.document.getLineEndOffset(line) && Character.isWhitespace(text.charAt(offset))) offset++;
                marker = track.document.createRangeMarker(offset, offset, true);
                marker.setGreedyToRight(true);
                track.markers.put(note.id, marker);
                NoteLocations.updateContext(note, track.document, line);
                store.book().position(note.id, note.filePath, note.line, note.context, note.before, note.after, false);
            }
            if (marker.isValid()) {
                int line = track.document.getLineNumber(Math.min(marker.getStartOffset(), track.document.getTextLength()));
                byLine.computeIfAbsent(line, ignored -> new ArrayList<>()).add(note.id);
            }
        }
        track.markers.entrySet().removeIf(entry -> {
            if (relevant.contains(entry.getKey())) return false;
            entry.getValue().dispose(); return true;
        });
        MarkupModel markup = DocumentMarkupModel.forDocument(track.document, project, true);
        if (markup == null) return;
        byLine.forEach((line, noteIds) -> {
            RangeHighlighter highlighter = markup.addLineHighlighter(line, HighlighterLayer.ADDITIONAL_SYNTAX, null);
            highlighter.setGutterIconRenderer(new NoteGutter(noteIds));
            track.icons.add(highlighter);
        });
    }

    private void sync(Track track) {
        for (Map.Entry<String, RangeMarker> entry : track.markers.entrySet()) {
            Notebook.Note note = store.book().note(entry.getKey());
            if (note == null) continue;
            RangeMarker marker = entry.getValue();
            if (marker.isValid()) {
                int line = track.document.getLineNumber(Math.min(marker.getStartOffset(), track.document.getTextLength()));
                NoteLocations.updateContext(note, track.document, line);
            } else note.detached = true;
            store.book().position(note.id, note.filePath, note.line, note.context, note.before, note.after, note.detached);
        }
    }

    /** Explicitly rebinding must discard an existing marker even if the file did not change. */
    public void forget(String noteId) {
        for (Track track : tracks.values()) {
            RangeMarker marker = track.markers.remove(noteId);
            if (marker != null) marker.dispose();
        }
    }

    public void flush() { tracks.values().forEach(this::sync); }

    /** Resolve closed files before navigation, so external edits cannot send the caret to an old line. */
    public Notebook.Note resolveLocation(String id) {
        flush(); refresh();
        Notebook.Note note = store.book().note(id);
        if (note == null || note.detached) return note;
        VirtualFile file = NoteLocations.resolve(project, note.filePath);
        if (file == null) return note;
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return note;
        Track track = tracks.computeIfAbsent(document, d -> new Track(d, file));
        rebuild(track, store.book().snapshot().notes);
        sync(track); publish();
        return store.book().note(id);
    }

    private void releaseUnused(Document document) {
        for (Editor editor : EditorFactory.getInstance().getEditors(document, project)) if (!editor.isDisposed()) return;
        Track track = tracks.remove(document);
        if (track != null) { sync(track); track.clear(); }
    }

    private void publish() {
        updating = true;
        try { store.changed(); } finally { updating = false; }
    }

    private final class NoteGutter extends GutterIconRenderer implements DumbAware {
        private final List<String> ids;
        private NoteGutter(List<String> ids) { this.ids = List.copyOf(ids); }
        @Override public @NotNull Icon getIcon() { return NotesIcons.NOTE; }
        @Override public boolean isNavigateAction() { return true; }
        @Override public String getAccessibleName() { return "行笔记"; }
        @Override public String getTooltipText() {
            List<Notebook.Note> notes = ids.stream().map(store.book()::note).filter(Objects::nonNull).toList();
            if (notes.isEmpty()) return "行笔记";
            String title = notes.get(0).title;
            return notes.size() > 1 ? title + " · 共 " + notes.size() + " 条笔记，点击查看" : title + " · 点击查看或编辑";
        }
        @Override public AnAction getClickAction() {
            return new DumbAwareAction("查看行笔记") {
                @Override public void actionPerformed(@NotNull AnActionEvent e) { NotesUi.openLine(project, ids, e); }
            };
        }
        @Override public boolean equals(Object other) { return other instanceof NoteAnchors.NoteGutter gutter && ids.equals(gutter.ids); }
        @Override public int hashCode() { return ids.hashCode(); }
    }

    @Override public void dispose() {
        disposed = true;
        tracks.values().forEach(Track::clear); tracks.clear(); files.clear();
    }
}
