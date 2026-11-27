package dev.readingnotes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import dev.readingnotes.ui.NotesUi;
import org.jetbrains.annotations.NotNull;
import static dev.readingnotes.model.I18n.text;

public final class AddNoteAction extends DumbAwareAction {
    public AddNoteAction() {
        super(() -> label("新建行笔记"), () -> label("在当前代码行创建学习笔记"), NotesIcons.NOTE);
    }
    private static String label(String key) { LanguageSettings.get(); return text(key); }
    @Override public void update(@NotNull AnActionEvent event) {
        LanguageSettings.get();
        event.getPresentation().setText(text("新建行笔记"));
        event.getPresentation().setDescription(text("在当前代码行创建学习笔记"));
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && event.getData(CommonDataKeys.EDITOR) != null);
    }
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project != null) NotesUi.create(project, event.getData(CommonDataKeys.EDITOR), "");
    }
}
