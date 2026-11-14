package dev.readingnotes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import dev.readingnotes.ui.NotesUi;
import org.jetbrains.annotations.NotNull;

public final class AddNoteAction extends DumbAwareAction {
    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && event.getData(CommonDataKeys.EDITOR) != null);
    }
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project != null) NotesUi.create(project, event.getData(CommonDataKeys.EDITOR), "");
    }
}
