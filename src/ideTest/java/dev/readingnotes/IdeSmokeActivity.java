package dev.readingnotes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.*;
import com.intellij.util.xmlb.XmlSerializer;
import dev.readingnotes.model.Notebook;
import dev.readingnotes.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Loaded only by run-sandbox.ps1 -SmokeTest, never by the distributable ZIP. */
public final class IdeSmokeActivity implements StartupActivity.DumbAware {
    private final List<String> passed = new ArrayList<>();
    private Project project;
    private Path results;
    private String noteId;
    private Editor editor;

    @Override public void runActivity(@NotNull Project project) {
        if (!Boolean.getBoolean("reading.notes.smoke")) return;
        this.project = project;
        results = Path.of(System.getProperty("reading.notes.results"));
        try {
            Files.writeString(results.resolve("environment.txt"),
                "IDE build: " + ApplicationInfo.getInstance().getBuild().asString() + "\n" +
                "Java: " + System.getProperty("java.runtime.version") + "\n" +
                "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + "\n");
        } catch (Exception failure) { throw new IllegalStateException("Cannot record test environment", failure); }
        later(Boolean.getBoolean("reading.notes.restart") ? this::checkRestart : this::exercise);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed.add("PASS " + message);
    }

    private void exercise() throws Exception {
        NotebookService store = NotebookService.get(project);
        Notebook book = store.book();
        book.load(new Notebook().snapshot());
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath() + "/sample.py");
        check(file != null, "Sandbox source exists");
        editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, 2, 0), true);
        check(editor != null, "Editor opened");
        Notebook.Topic topic = book.addTopic("登录流程");
        Notebook.Group group = book.addGroup(topic.id, "身份校验");
        Notebook.Note draft = NoteLocations.capture(project, editor);
        draft.groupId = group.id; draft.title = "检查访问令牌";
        draft.text = "读取请求携带的 token，并在进入业务逻辑前校验身份。\n\n关注点：\n• 无效或过期的令牌会怎样处理？\n• 用户信息如何传递给后续函数？\n\n这是一条较长的中文笔记，用于验证窗口缩放时正文会自动换行，内容可以滚动查看，不会把窗口撑出屏幕。";
        noteId = book.saveNote(draft).id;
        draft.title = "同一行的第二个思考"; book.saveNote(draft);
        store.changed();
        NoteAnchors anchors = NoteAnchors.get(project); anchors.refresh();
        check(gutters(editor.getDocument()).size() == 1, "Two notes on one line use one gutter icon");
        check(gutters(editor.getDocument()).get(0).getGutterIconRenderer().getClickAction() != null, "Gutter icon has a click action");
        WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().insertString(0, "# inserted above notes\n"));
        anchors.flush();
        check(book.note(noteId).line == 4, "Anchor follows line insertion");
        WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().deleteString(0, "# inserted above notes\n".length()));
        anchors.flush();
        check(book.note(noteId).line == 3, "Anchor follows line removal");
        Notebook.Note first = book.note(noteId);
        check(first.context.equals("token = request.headers.get(\"Authorization\")"), "Anchor still refers to original source");
        Notebook.Data restored = XmlSerializer.deserialize(XmlSerializer.serialize(book.snapshot()), Notebook.Data.class);
        Notebook roundTrip = new Notebook(); roundTrip.load(restored);
        check(roundTrip.note(noteId).text.equals(first.text), "IDE XML serializer preserves Chinese multiline notes");
        check(roundTrip.snapshot().topics.size() == 2 && roundTrip.snapshot().groups.size() == 2, "IDE XML serializer preserves topic/group structure");
        String renamed = "renamed-" + System.currentTimeMillis() + ".py";
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try { file.rename(this, renamed); } catch (Exception e) { throw new RuntimeException(e); }
        });
        anchors.refresh();
        check(book.note(noteId).filePath.equals(renamed), "Anchor follows file rename");
        FileDocumentManager.getInstance().saveAllDocuments();
        ApplicationManager.getApplication().saveSettings();
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(NotesUi.WINDOW);
        check(window != null, "Tool window registered");
        window.show(() -> later(this::checkViews));
    }

    private void checkViews() throws Exception {
        NotebookService store = NotebookService.get(project);
        NotesPanel panel = new NotesPanel(project);
        try {
            panel.selectNote(noteId);
            render(panel, 330, 720, "sidebar-narrow.png");
            store.book().layout(true, .42f);
            NotesPanel wide = new NotesPanel(project);
            try { wide.selectNote(noteId); render(wide, 1000, 640, "sidebar-wide.png"); }
            finally { wide.dispose(); }
        } finally { panel.dispose(); }
        check(true, "Rendered narrow/wide layouts without exception");
        NoteDialog dialog = new NoteDialog(project, store.book().note(noteId));
        check(dialog.isResizable(), "Note dialog is resizable");
        check(dialog.getDimensionKey().equals("ReadingNotes.NoteDialog.v1"), "Dialog remembers its dimensions");
        dialog.setModal(false); dialog.show();
        later(() -> {
            render((JComponent) dialog.getContentPane(), 680, 600, "note-dialog.png");
            check(invokeSave(dialog.getContentPane()) && noteId.equals(dialog.savedId()), "Ctrl+Enter uses the validated dialog save action");
            Notebook book = store.book(); NoteAnchors anchors = NoteAnchors.get(project);
            Notebook.Note note = book.note(noteId);
            int start = editor.getDocument().getLineStartOffset(note.line - 1);
            int end = editor.getDocument().getLineStartOffset(note.line);
            WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().deleteString(start, end));
            anchors.flush();
            check(book.note(noteId).detached, "Deleting anchored source marks note detached");
            Notebook.Note location = NoteLocations.capture(project, editor);
            anchors.forget(noteId);
            book.position(noteId, location.filePath, location.line, location.context, location.before, location.after, false);
            store.changed(); anchors.refresh();
            check(!book.note(noteId).detached && !gutters(editor.getDocument()).isEmpty(), "Explicit rebinding restores gutter icon");
            // Check a closed document whose source changed outside the editor.
            VirtualFile closed = LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath() + "/closed.py");
            check(closed != null, "Closed source fixture exists");
            Notebook.Note external = new Notebook.Note();
            external.title = "Closed-file anchor"; external.groupId = book.note(noteId).groupId;
            external.filePath = "closed.py"; external.line = 1; external.context = "return value";
            external.id = book.saveNote(external).id; store.changed();
            check(anchors.resolveLocation(external.id).line == 3, "Closed-file navigation resolves externally shifted code");
            book.deleteNote(external.id); store.changed();
            book.layout(true, .38f);
            FileDocumentManager.getInstance().saveAllDocuments();
            project.save();
            Files.write(results.resolve("smoke-result.txt"), passed);
            NotesUi.reveal(project, noteId, true);
            SharingIdeChecks.run(project, results, () -> later(this::exit));
        });
    }

    private void checkRestart() throws Exception {
        Notebook.Data data = NotebookService.get(project).book().snapshot();
        check(data.notes.size() == 3, "Notes restored after IDE restart");
        check(data.notes.stream().anyMatch(n -> n.title.equals("检查访问令牌") && n.text.contains("中文笔记")), "Chinese multiline content restored after IDE restart");
        check(data.topics.stream().anyMatch(t -> t.name.equals("登录流程")) && data.groups.stream().anyMatch(g -> g.name.equals("身份校验")), "Topics/groups restored after IDE restart");
        check(data.horizontal && Math.abs(data.split - .38f) < .01f, "Layout and split proportion restored after IDE restart");
        Notebook.Note attached = data.notes.stream().filter(n -> !n.detached).findFirst().orElseThrow();
        check(!NoteAnchors.get(project).resolveLocation(attached.id).detached, "Saved source anchor restored after IDE restart");
        Files.write(results.resolve("restart-result.txt"), passed);
        SharingIdeChecks.checkRestart(project, results);
        exit();
    }

    private List<RangeHighlighter> gutters(Document document) {
        return Arrays.stream(DocumentMarkupModel.forDocument(document, project, true).getAllHighlighters())
            .filter(h -> h.getGutterIconRenderer() != null && h.getGutterIconRenderer().getClass().getName().contains("NoteAnchors")).toList();
    }

    private void render(JComponent component, int width, int height, String name) throws Exception {
        component.setSize(width, height); layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); component.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", results.resolve(name).toFile());
    }
    private void layout(Container component) {
        component.doLayout(); for (Component child : component.getComponents()) if (child instanceof Container c) layout(c);
    }
    private boolean invokeSave(Container component) {
        if (component instanceof JComponent c) {
            Action action = c.getActionMap().get("readingNotes.save");
            if (action != null) { action.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, "readingNotes.save")); return true; }
        }
        for (Component child : component.getComponents()) if (child instanceof Container c && invokeSave(c)) return true;
        return false;
    }
    private interface Step { void run() throws Exception; }
    private void later(Step step) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try { step.run(); }
            catch (Throwable failure) {
                try { Files.writeString(results.resolve("smoke-failure.txt"), String.join("\n", passed) + "\n" + failure + "\n" + Arrays.toString(failure.getStackTrace())); }
                catch (Exception ignored) {}
                exit();
            }
        });
    }
    private void exit() { ((ApplicationEx) ApplicationManager.getApplication()).exit(true, true); }
}
