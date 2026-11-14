package dev.readingnotes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public final class NotesStartup implements StartupActivity.DumbAware {
    @Override public void runActivity(@NotNull Project project) { NoteAnchors.get(project).refresh(); }
}
