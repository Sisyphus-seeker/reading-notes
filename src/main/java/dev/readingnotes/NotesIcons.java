package dev.readingnotes;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public final class NotesIcons {
    public static final Icon NOTE = IconLoader.getIcon("/icons/note.svg", NotesIcons.class);
    public static final Icon TOOL_WINDOW = IconLoader.getIcon("/icons/toolWindow.svg", NotesIcons.class);
    private NotesIcons() {}
}
