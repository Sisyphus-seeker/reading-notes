package dev.readingnotes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.xmlb.XmlSerializer;
import dev.readingnotes.model.*;
import dev.readingnotes.sharing.*;
import dev.readingnotes.ui.ImportNotesDialog;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Integration-only fixtures: never included in the production plugin. */
public final class SharingIdeChecks {
    private static final String SOURCE = "def authenticate():\n    request = read_request()\n    token = request.token\n    result = verify(token)\n    audit(result)\n    notify(result)\n    return result\n";
    private final List<String> passed = new ArrayList<>();
    private final Project project;
    private final Path results;
    private SharingIdeChecks(Project project, Path results) { this.project = project; this.results = results; }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); passed.add("PASS " + message); }
    private interface Work { void run() throws Exception; }
    private void rejects(Work work, String message) throws Exception {
        try { work.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        check(false, message);
    }
    public static void run(Project project, Path results, Runnable done) throws Exception { new SharingIdeChecks(project, results).exercise(done); }
    private void exercise(Runnable done) throws Exception {
        Path root = Path.of(project.getBasePath()), fixtures = root.resolve("sharing-fixtures"); Files.createDirectories(fixtures);
        Files.writeString(fixtures.resolve("author.py"), SOURCE);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixtures.resolve("author.py"));
        Notebook author = new Notebook(); Notebook.Note note = new Notebook.Note();
        note.groupId = author.snapshot().groups.get(0).id; note.filePath = "sharing-fixtures/author.py"; note.line = 4;
        note.title = "分享后的代码定位"; note.text = "接收方移动文件并插入代码后仍可恢复\n保留中文与 emoji：📖";
        note.context = "result = verify(token)"; note.before = "token = request.token"; note.after = "audit(result)";
        String id = author.saveNote(note).id;
        NotebookSharing sharing = new NotebookSharing(project);
        NoteBundle exported = sharing.export(author.snapshot(), ShareEngine.Scope.ALL, null);
        Path json = results.resolve("roundtrip.readingnotes.json"); BundleFiles.write(json, exported); NoteBundle parsed = BundleFiles.read(json);
        check(parsed.notes.get(0).note.text.equals(note.text) && parsed.notes.get(0).anchor.before.size() == 3 && parsed.notes.get(0).anchor.after.size() == 3, "Share JSON preserves Unicode, multiline notes and seven-line anchors");
        check(!Files.readString(json).contains(root.toString().replace('\\', '/')) && !Files.readString(json).contains(root.toString().replace("\\", "\\\\")), "Share JSON excludes this computer's absolute project path");
        Path invalid = results.resolve("invalid-share.json");
        Files.writeString(invalid, "{\"format\":\"reading-notes.share\",\"formatVersion\":999}");
        rejects(() -> BundleFiles.read(invalid), "Unknown share format versions are rejected");
        Files.writeString(invalid, "{".repeat(40)); rejects(() -> BundleFiles.read(invalid), "Excessive JSON nesting is rejected");
        Files.writeString(invalid, "{\"format\":"); rejects(() -> BundleFiles.read(invalid), "Malformed JSON is rejected before touching notes");
        NoteBundle invalidBundle = sharing.export(author.snapshot(), ShareEngine.Scope.ALL, null); invalidBundle.notes.get(0).note.filePath = "../outside.py";
        rejects(() -> BundleFiles.write(json, invalidBundle), "Unsafe export validation preserves an existing destination file");
        check(BundleFiles.read(json).notes.get(0).note.id.equals(id), "Failed export leaves the prior valid share intact");
        Files.writeString(fixtures.resolve("receiver.py"), "# inserted in receiver\n" + SOURCE);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixtures.resolve("receiver.py"));
        ShareEngine.Plan plan = sharing.plan(sharing.snapshot(), parsed, root, Map.of("sharing-fixtures/author.py", "sharing-fixtures/receiver.py"));
        check(plan.rows.get(0).attached() && plan.rows.get(0).chosenLine == 5, "IDE import relocates a shared note in a renamed and edited file");
        NotebookSharing.Prepared prepared = sharing.prepare(plan);
        Notebook.Data backup = NotebookStorage.decode(XmlSerializer.deserialize(JDOMUtil.load(prepared.backup()).getChild("component"), Notebook.Data.class));
        check(backup.notes.size() == 2 && backup.notes.stream().noneMatch(n -> n.id.equals(id)), "Import backup contains the complete pre-import notebook in IDE XML format");
        sharing.commit(prepared);
        Notebook.Note imported = NotebookService.get(project).book().note(id);
        check(imported != null && imported.line == 5 && imported.filePath.equals("sharing-fixtures/receiver.py") && !imported.detached, "Import commit preserves stable IDs and confirmed source binding");
        ShareEngine.Plan repeated = sharing.plan(sharing.snapshot(), parsed, root, Map.of("sharing-fixtures/author.py", "sharing-fixtures/receiver.py"));
        check(repeated.rows.get(0).duplicate && repeated.selectedCount() == 0, "Repeated IDE import skips the already imported note");
        check(Files.isRegularFile(prepared.backup()), "Pre-import backup exists on disk");
        Path unicodeBackup = BundleFiles.backup(root, NotebookService.get(project).book().snapshot());
        Notebook.Data unicodeData = NotebookStorage.decode(XmlSerializer.deserialize(JDOMUtil.load(unicodeBackup).getChild("component"), Notebook.Data.class));
        check(unicodeData.notes.stream().anyMatch(n -> n.id.equals(id) && n.text.contains("📖")), "XML backups preserve supplementary Unicode through the UTF-8 envelope");
        Files.writeString(results.resolve("imported-note-id.txt"), id);
        ImportNotesDialog dialog = new ImportNotesDialog(project, parsed); dialog.setModal(false); dialog.show();
        javax.swing.Timer timer = new javax.swing.Timer(100, null); int[] attempts = {0};
        timer.addActionListener(event -> {
            if (dialog.currentPlan() == null && ++attempts[0] < 100) return;
            timer.stop();
            try {
                check(dialog.currentPlan() != null, "Import preview calculates a plan asynchronously");
                check(dialog.isResizable() && dialog.getDimensionKey().equals("ReadingNotes.ImportDialog.v1"), "Import preview is resizable and remembers dimensions");
                render((JComponent) dialog.getContentPane(), 960, 700, results.resolve("import-preview.png"));
                render((JComponent) dialog.getContentPane(), 640, 500, results.resolve("import-preview-narrow.png"));
                check(true, "Import preview renders note, source and conflict controls");
                JTable noteTable = table((Container) dialog.getContentPane(), "待导入笔记及定位、冲突处理");
                JTable pathTable = table((Container) dialog.getContentPane(), "文件路径映射");
                JButton confirm = button((Container) dialog.getContentPane(), "确认合并导入");
                check(noteTable != null && pathTable != null && confirm != null, "Import controls have accessible labels and a confirmation action");
                noteTable.getModel().setValueAt(ShareEngine.Action.REPLACE, 0, 4);
                pathTable.editCellAt(0, 1);
                ((JTextField) pathTable.getEditorComponent()).setText("sharing-fixtures/receiver.py");
                confirm.doClick();
                check(!pathTable.isEditing() && pathTable.getValueAt(0, 1).equals("sharing-fixtures/receiver.py") && !confirm.isEnabled(), "Confirm commits pending path edits and requires a fresh preview");
                check(NotebookService.get(project).book().note(id).filePath.equals("sharing-fixtures/receiver.py"), "Dirty path mapping does not write an obsolete import plan");
                dialog.doCancelAction(); Files.write(results.resolve("sharing-result.txt"), passed); done.run();
            } catch (Throwable failure) {
                dialog.doCancelAction();
                try { Files.writeString(results.resolve("smoke-failure.txt"), String.join("\n", passed) + "\n" + failure); } catch (Exception ignored) {}
                done.run();
            }
        }); timer.start();
    }
    public static void checkRestart(Project project, Path results) throws Exception {
        String id = Files.readString(results.resolve("imported-note-id.txt"));
        Notebook.Note note = NotebookService.get(project).book().note(id);
        if (note == null || note.detached || !note.text.contains("📖") || !note.filePath.equals("sharing-fixtures/receiver.py") || NoteAnchors.get(project).resolveLocation(id).detached)
            throw new AssertionError("Imported share did not survive restart");
        Files.writeString(results.resolve("sharing-restart.txt"), "PASS Imported Unicode note, stable ID and relocated binding survive IDE restart\n");
    }
    private static void render(JComponent component, int width, int height, Path file) throws Exception {
        component.setSize(width, height); layout(component); BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); component.printAll(graphics); graphics.dispose(); ImageIO.write(image, "png", file.toFile());
    }
    private static void layout(Container component) { component.doLayout(); for (Component child : component.getComponents()) if (child instanceof Container c) layout(c); }
    private static JTable table(Container parent, String name) {
        if (parent instanceof JTable t && name.equals(t.getAccessibleContext().getAccessibleName())) return t;
        for (Component child : parent.getComponents()) if (child instanceof Container c) { JTable result = table(c, name); if (result != null) return result; }
        return null;
    }
    private static JButton button(Container parent, String name) {
        if (parent instanceof JButton b && name.equals(b.getText())) return b;
        for (Component child : parent.getComponents()) if (child instanceof Container c) { JButton result = button(c, name); if (result != null) return result; }
        return null;
    }
}
