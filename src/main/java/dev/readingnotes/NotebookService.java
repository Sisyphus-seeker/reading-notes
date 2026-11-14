package dev.readingnotes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import dev.readingnotes.model.Notebook;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "ReadingNotes", storages = @Storage("reading-notes.xml"))
public final class NotebookService implements PersistentStateComponent<Notebook.Data>, Disposable {
    public interface Listener { void changed(); }
    public static final Topic<Listener> CHANGED = Topic.create("Reading notes changed", Listener.class);
    private final Notebook notebook = new Notebook();
    private final Project project;

    public NotebookService(Project project) { this.project = project; }
    public static NotebookService get(Project project) { return project.getService(NotebookService.class); }
    public Notebook book() { return notebook; }
    @Override public Notebook.Data getState() { return notebook.snapshot(); }
    @Override public void loadState(@NotNull Notebook.Data state) { notebook.load(state); }
    public void changed() { if (!project.isDisposed()) project.getMessageBus().syncPublisher(CHANGED).changed(); }
    @Override public void dispose() {}
}
