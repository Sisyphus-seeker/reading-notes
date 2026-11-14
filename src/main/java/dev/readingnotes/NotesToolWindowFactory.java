package dev.readingnotes;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import dev.readingnotes.ui.NotesPanel;
import org.jetbrains.annotations.NotNull;

public final class NotesToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
        NoteAnchors.get(project).refresh();
        NotesPanel panel = new NotesPanel(project);
        Content content = window.getContentManager().getFactory().createContent(panel, "", false);
        content.setDisposer(panel); content.setPreferredFocusableComponent(panel);
        window.getContentManager().addContent(content);
    }
}
