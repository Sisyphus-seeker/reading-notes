package dev.readingnotes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowManager;
import dev.readingnotes.model.*;
import dev.readingnotes.sharing.BundleFiles;
import dev.readingnotes.ui.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Checks actual components, menu actions and persisted application settings in an isolated IDE. */
public final class LanguageIdeChecks {
    private final Project project;
    private final Path results;
    private final List<String> passed = new ArrayList<>();
    private LanguageIdeChecks(Project project, Path results) { this.project = project; this.results = results; }
    public static void run(Project project, Path results, String id, Runnable done) throws Exception {
        new LanguageIdeChecks(project, results).exercise(id, done);
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); passed.add("PASS " + message); }

    private void exercise(String id, Runnable done) throws Exception {
        LanguageSettings settings = LanguageSettings.get();
        check(settings.language() == I18n.Language.CHINESE, "Existing installations default to Chinese");
        NotesPanel panel = (NotesPanel) ToolWindowManager.getInstance(project).getToolWindow(NotesUi.WINDOW).getContentManager().getContents()[0].getComponent();
        panel.selectNote(id);
        JTextField search = find(panel, JTextField.class, c -> "搜索笔记".equals(c.getAccessibleContext().getAccessibleName()));
        search.setText("检查"); panel.selectNote(id);
        JTree tree = find(panel, JTree.class, c -> true);
        int listeners = tree.getTreeSelectionListeners().length;
        Notebook.Note before = NotebookService.get(project).book().note(id);
        NotesPanel second = new NotesPanel(project);
        try {
            JButton language = find(panel, JButton.class, c -> "中文 / EN".equals(c.getText()));
            check(language != null && language.isShowing() && language.isFocusable(), "Language selector is visible and keyboard focusable");
            language.doClick(); JPopupMenu menu = popup();
            JRadioButtonMenuItem english = find(menu, JRadioButtonMenuItem.class, c -> "English".equals(c.getText()));
            check(english != null && find(menu, JRadioButtonMenuItem.class, c -> "简体中文".equals(c.getText()) && c.isSelected()) != null, "Language menu exposes both native language names and current selection");
            english.doClick(); MenuSelectionManager.defaultManager().clearSelectedPath();
            check(settings.language() == I18n.Language.ENGLISH, "English menu action changes the application preference");
            check("Search notes".equals(search.getAccessibleContext().getAccessibleName()) && find(second, JTextField.class, c -> "Search notes".equals(c.getAccessibleContext().getAccessibleName())) != null, "Open panels refresh translated accessible names immediately");
            check(search.getText().equals("检查") && ((Notebook.Note) ((DefaultMutableTreeNode) tree.getLastSelectedPathComponent()).getUserObject()).id.equals(id), "Language change preserves search text and note selection");
            check(find(panel, JButton.class, c -> "Share Notes: Import / Export".equals(c.getToolTipText())) != null, "Toolbar tooltips switch to English");
            Notebook.Note after = NotebookService.get(project).book().note(id);
            check(before.title.equals(after.title) && before.text.equals(after.text) && before.context.equals(after.context) && before.line == after.line && before.filePath.equals(after.filePath) && before.updatedAt == after.updatedAt, "UI language changes preserve Chinese content and code anchors");
            var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            var gutter = Arrays.stream(DocumentMarkupModel.forDocument(editor.getDocument(), project, true).getAllHighlighters()).map(h -> h.getGutterIconRenderer()).filter(g -> g != null && g.getClass().getName().contains("NoteAnchors")).findFirst().orElseThrow();
            check(gutter.getAccessibleName().equals("Line note") && gutter.getTooltipText().toLowerCase(Locale.ROOT).contains("click to view"), "Gutter labels and tooltips follow English");
            AnActionEvent event = AnActionEvent.createEvent(SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.EDITOR, editor).build(), new Presentation(), ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
            new AddNoteAction().update(event);
            check(event.getPresentation().getText().equals("New Line Note") && ActionManager.getInstance().getAction("ReadingNotes.AddLineNote").getTemplatePresentation().getText().equals("New Line Note"), "Editor action and searchable template switch to English");
            JButton sharing = find(panel, JButton.class, c -> "Share Notes: Import / Export".equals(c.getToolTipText()));
            sharing.doClick(); JPopupMenu shareMenu = popup();
            check(find(shareMenu, JMenuItem.class, c -> "Export All Notes…".equals(c.getText())) != null && find(shareMenu, JMenuItem.class, c -> "Import Share File…".equals(c.getText())) != null, "Share menu offers English import and export actions");
            MenuSelectionManager.defaultManager().clearSelectedPath();
            render(panel, 330, 720, "sidebar-english-narrow.png");
            render(second, 1000, 640, "sidebar-english-wide.png");
            NoteDialog edit = new NoteDialog(project, after);
            try {
                check(edit.getTitle().equals("View / Edit Line Note") && find((Container) edit.getContentPane(), JButton.class, c -> "Save Note".equals(c.getText())) != null, "Note editor title and save action follow English");
                render((JComponent) edit.getContentPane(), 680, 600, "note-dialog-english.png");
            } finally { edit.doCancelAction(); }
            settings.setLanguage(I18n.Language.CHINESE);
            check("搜索笔记".equals(search.getAccessibleContext().getAccessibleName()) && find(panel, JButton.class, c -> "分享笔记：导入 / 导出".equals(c.getToolTipText())) != null, "Switching back restores Chinese panel labels");
            for (int i = 0; i < 4; i++) { settings.setLanguage(I18n.Language.ENGLISH); settings.setLanguage(I18n.Language.CHINESE); }
            check(tree.getTreeSelectionListeners().length == listeners, "Repeated switching does not duplicate tree listeners");
            settings.setLanguage(I18n.Language.ENGLISH);
        } finally { Disposer.dispose(second); }
        ImportNotesDialog imported = new ImportNotesDialog(project, BundleFiles.read(results.resolve("roundtrip.readingnotes.json")));
        imported.setModal(false); imported.show();
        javax.swing.Timer timer = new javax.swing.Timer(100, null); int[] attempts = {0};
        timer.addActionListener(e -> {
            if (imported.currentPlan() == null && ++attempts[0] < 100) return;
            timer.stop();
            try {
                check(imported.currentPlan() != null, "English import preview completes background matching");
                check(imported.getTitle().equals("Import Shared Notes") && find((Container) imported.getContentPane(), JButton.class, c -> "Merge and Import".equals(c.getText())) != null, "Import dialog title and confirmation follow English");
                JTable notes = find((Container) imported.getContentPane(), JTable.class, c -> "Notes to import, locations and conflict actions".equals(c.getAccessibleContext().getAccessibleName()));
                check(notes != null && notes.getColumnName(0).equals("Note title") && notes.getColumnName(4).equals("Import action"), "Import table headers and accessible names follow English");
                check(imported.currentPlan().rows.get(0).reason.equals("File contents are unchanged since sharing") && imported.currentPlan().rows.get(0).status().equals("Reliable match"), "Import matching explanations and location status follow English");
                render((JComponent) imported.getContentPane(), 960, 700, "import-preview-english.png");
                render((JComponent) imported.getContentPane(), 720, 600, "import-preview-english-narrow.png");
                check(true, "English main, note and import views render at narrow and wide sizes");
                ApplicationManager.getApplication().saveSettings();
                Files.write(results.resolve("language-result.txt"), passed);
            } catch (Throwable failure) {
                try { Files.writeString(results.resolve("smoke-failure.txt"), String.join("\n", passed) + "\n" + failure + "\n" + Arrays.toString(failure.getStackTrace())); } catch (Exception ignored) {}
            } finally { imported.doCancelAction(); done.run(); }
        }); timer.start();
    }
    public static void checkRestart(Project project, Path results) throws Exception {
        if (LanguageSettings.get().language() != I18n.Language.ENGLISH || !I18n.text("保存笔记").equals("Save Note")) throw new AssertionError("Language preference did not survive IDE restart");
        NotesPanel panel = new NotesPanel(project);
        try {
            if (find(panel, JTextField.class, c -> "Search notes".equals(c.getAccessibleContext().getAccessibleName())) == null) throw new AssertionError("Restarted panel did not load English");
        } finally { Disposer.dispose(panel); }
        Files.writeString(results.resolve("language-restart.txt"), "PASS English application preference and translated panel survive IDE restart\n");
    }
    private static JPopupMenu popup() {
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) if (element instanceof JPopupMenu menu) return menu;
        throw new AssertionError("Popup menu did not open");
    }
    private static <T extends Component> T find(Container parent, Class<T> type, java.util.function.Predicate<T> test) {
        if (type.isInstance(parent) && test.test(type.cast(parent))) return type.cast(parent);
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && test.test(type.cast(child))) return type.cast(child);
            if (child instanceof Container c) { T found = find(c, type, test); if (found != null) return found; }
        }
        return null;
    }
    private void render(JComponent component, int width, int height, String name) throws Exception {
        component.setSize(width, height); layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); component.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", results.resolve(name).toFile());
    }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container container) layout(container); }
}
