package dev.readingnotes.model;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ShareEngineTest {
    private static int checks;
    private static final String SOURCE = "def authenticate():\n    request = read_request()\n    token = request.token\n    result = verify(token)\n    audit(result)\n    notify(result)\n    return result\n";
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private interface Work { void run() throws Exception; }
    private static void rejects(Work work, String message) throws Exception {
        try { work.run(); } catch (IllegalArgumentException | IllegalStateException | IOException expected) { check(true, message); return; }
        check(false, message);
    }
    private static Notebook sample() {
        Notebook book = new Notebook(); Notebook.Note note = new Notebook.Note();
        note.groupId = book.snapshot().groups.get(0).id; note.title = "验证令牌"; note.text = "中文正文\n多行内容";
        note.filePath = "src/auth.py"; note.line = 4; note.context = "result = verify(token)"; note.before = "token = request.token"; note.after = "audit(result)";
        book.saveNote(note); return book;
    }
    private static NoteBundle bundle(Notebook book, Path root) throws Exception { return ShareEngine.export(book.snapshot(), ShareEngine.Scope.ALL, null, root, Files::readString); }
    private static ShareEngine.Plan plan(Notebook book, NoteBundle bundle, Path root) throws Exception { return ShareEngine.plan(book.snapshot(), bundle, root, root, Map.of(), Files::readString); }

    public static void run() throws Exception {
        Path temporary = Files.createTempDirectory("reading-notes-share-").toRealPath();
        try {
            Path sender = Files.createDirectory(temporary.resolve("sender")), receiver = Files.createDirectory(temporary.resolve("receiver"));
            Files.createDirectories(sender.resolve("src")); Files.createDirectories(receiver.resolve("src"));
            Files.writeString(sender.resolve("src/auth.py"), SOURCE); Files.writeString(receiver.resolve("src/auth.py"), SOURCE);
            Notebook source = sample(); NoteBundle bundle = bundle(source, sender);
            String id = bundle.notes.get(0).note.id;
            check(bundle.notes.get(0).anchor.before.size() == 3 && bundle.notes.get(0).anchor.after.size() == 3, "Export includes three surrounding lines each side");
            check(bundle.notes.get(0).anchor.available && bundle.notes.get(0).anchor.sourceHash.length() == 64, "Source fingerprint is captured");
            check(!bundle.notes.get(0).note.filePath.contains(sender.toString()), "Share path is portable");
            Notebook.Note sibling = source.note(id); sibling.id = ""; sibling.title = "第二条笔记"; source.saveNote(sibling);
            check(ShareEngine.export(source.snapshot(), ShareEngine.Scope.NOTE, id, sender, Files::readString).notes.size() == 1, "Single-note selection excludes siblings");
            Notebook.Data collisions = source.snapshot(); String topic = collisions.topics.get(0).id;
            collisions.topics.get(0).id = id; collisions.groups.stream().filter(g -> g.topicId.equals(topic)).forEach(g -> g.topicId = id);
            check(ShareEngine.export(collisions, ShareEngine.Scope.NOTE, id, sender, Files::readString).notes.size() == 1, "Typed export scope survives cross-type ID collisions");
            check(ShareEngine.export(source.snapshot(), ShareEngine.Scope.GROUP, source.snapshot().groups.get(0).id, sender, Files::readString).notes.size() == 2, "Group export preserves children");
            Notebook recipient = new Notebook(); ShareEngine.Plan exact = plan(recipient, bundle, receiver);
            check(exact.rows.get(0).match == ShareEngine.Match.RELIABLE && exact.rows.get(0).chosenLine == 4, "Unchanged fingerprint restores exact line");
            Notebook.Data imported = ShareEngine.merge(exact); recipient.load(imported);
            check(recipient.note(id) != null && !recipient.note(id).detached, "Stable note ID is preserved on import");
            ShareEngine.Plan duplicate = plan(recipient, bundle, receiver);
            check(duplicate.rows.get(0).duplicate && duplicate.rows.get(0).action == ShareEngine.Action.SKIP, "Identical reimport skips duplicates");
            check(ShareEngine.merge(duplicate).notes.size() == 1, "Repeat import does not add copies");
            Files.writeString(receiver.resolve("src/auth.py"), "# added line\n" + SOURCE);
            ShareEngine.Plan shifted = plan(new Notebook(), bundle, receiver);
            check(shifted.rows.get(0).match == ShareEngine.Match.RELIABLE && shifted.rows.get(0).chosenLine == 5, "Context recovers shifted source line");
            Files.writeString(receiver.resolve("src/auth.py"), SOURCE + SOURCE);
            ShareEngine.Plan ambiguous = plan(new Notebook(), bundle, receiver);
            check(!ambiguous.rows.get(0).attached() && ambiguous.rows.get(0).candidates.size() == 2, "Equal context matches remain unbound despite old line number");
            Files.writeString(receiver.resolve("src/auth.py"), SOURCE.replace("verify(token)", "validate(token)"));
            ShareEngine.Plan changed = plan(new Notebook(), bundle, receiver);
            check(changed.rows.get(0).match == ShareEngine.Match.REVIEW && !changed.rows.get(0).candidates.isEmpty() && !changed.rows.get(0).attached(), "Changed code offers candidates without silently binding");
            changed.rows.get(0).chooseLine(4);
            check(ShareEngine.merge(changed).notes.get(0).context.equals("result = validate(token)"), "Manual confirmation adopts current code context");
            Files.delete(receiver.resolve("src/auth.py"));
            ShareEngine.Plan missing = plan(new Notebook(), bundle, receiver);
            check(missing.rows.get(0).match == ShareEngine.Match.MISSING && ShareEngine.merge(missing).notes.get(0).detached, "Missing source preserves a detached note");
            Files.writeString(receiver.resolve("src/moved.py"), SOURCE);
            ShareEngine.Plan mapped = ShareEngine.plan(new Notebook().snapshot(), bundle, receiver, receiver, Map.of("src/auth.py", "src/moved.py"), Files::readString);
            check(mapped.rows.get(0).attached() && ShareEngine.merge(mapped).notes.get(0).filePath.equals("src/moved.py"), "Explicit file mapping restores renamed files");
            Files.writeString(receiver.resolve("src/auth.py"), SOURCE);
            ShareEngine.Plan bindingChange = ShareEngine.plan(recipient.snapshot(), bundle, receiver, receiver, Map.of("src/auth.py", "src/moved.py"), Files::readString);
            check(bindingChange.rows.get(0).conflict && !bindingChange.rows.get(0).duplicate, "Changed binding is a reviewable conflict");
            bindingChange.rows.get(0).action = ShareEngine.Action.REPLACE;
            check(ShareEngine.merge(bindingChange).notes.get(0).filePath.equals("src/moved.py"), "Confirmed replacement updates only intended binding");
            Notebook.Note localEdit = recipient.note(id); localEdit.text = "接收方已修改"; recipient.saveNote(localEdit);
            ShareEngine.Plan conflict = plan(recipient, bundle, receiver);
            check(conflict.rows.get(0).conflict && conflict.rows.get(0).action == ShareEngine.Action.SKIP, "Content conflicts default to keeping local note");
            conflict.rows.get(0).action = ShareEngine.Action.COPY; Notebook.Data copied = ShareEngine.merge(conflict);
            check(copied.notes.size() == 2 && copied.notes.stream().map(n -> n.id).distinct().count() == 2 && copied.notes.stream().anyMatch(n -> n.text.equals("接收方已修改")), "Keep-both preserves local content and allocates a new ID");
            conflict.rows.get(0).action = ShareEngine.Action.REPLACE;
            check(ShareEngine.merge(conflict).notes.size() == 1 && ShareEngine.merge(conflict).notes.get(0).text.equals(bundle.notes.get(0).note.text), "Replace action is explicit and preserves stable ID");
            ShareEngine.Plan stale = plan(new Notebook(), bundle, receiver);
            Files.writeString(receiver.resolve("src/auth.py"), SOURCE + "# new edit\n");
            rejects(() -> ShareEngine.checkSources(stale, Files::readString), "Code changes after preview invalidate commit");
            Files.delete(receiver.resolve("src/auth.py"));
            rejects(() -> ShareEngine.checkSources(stale, Files::readString), "Deleted source after preview invalidates commit");
            Notebook target = new Notebook(); Notebook.Data expected = target.snapshot(); target.addTopic("新变化");
            rejects(() -> target.compareAndLoad(expected, imported), "Concurrent notebook changes cannot be overwritten");
            check(target.snapshot().topics.size() == 2 && target.snapshot().notes.isEmpty(), "Failed transaction leaves local notes untouched");
            Files.writeString(receiver.resolve("src/auth.py"), SOURCE);
            NoteBundle normalized = bundle(sample(), sender); normalized.topics.get(0).name = " 我的学习 "; normalized.groups.get(0).name = " 默认分组 ";
            ShareEngine.Plan names = plan(new Notebook(), normalized, receiver);
            check(names.hierarchy.topics.size() == 1 && names.hierarchy.groups.size() == 1, "Trimmed names merge without creating duplicate groups");
            for (String unsafe : new String[]{"../secret", "/tmp/file", "C:/file", "file://host/file", "a//b", "a/./b", "a/../b"}) rejects(() -> ShareEngine.relative(unsafe), "Unsafe path rejected: " + unsafe);
            rejects(() -> ShareEngine.plan(new Notebook().snapshot(), bundle, receiver, sender, Map.of(), Files::readString), "Code root cannot escape current project");
            NoteBundle invalid = bundle(sample(), sender); invalid.formatVersion = 999;
            rejects(invalid::validate, "Unknown format version rejected");
            invalid = bundle(sample(), sender); invalid.notes.get(0).note.line = 0; NoteBundle badLine = invalid;
            rejects(badLine::validate, "Invalid anchor line rejected");
            Notebook external = sample(); Notebook.Note externalNote = external.snapshot().notes.get(0); externalNote.filePath = "file:///private/secret/auth.py"; external.saveNote(externalNote);
            NoteBundle portableExternal = bundle(external, sender);
            check(portableExternal.notes.get(0).anchor.external && portableExternal.notes.get(0).note.filePath.equals("external/auth.py"), "External absolute paths are not leaked in a share");
            check(!plan(new Notebook(), portableExternal, receiver).rows.get(0).attached(), "External files require explicit mapping");
            Notebook blankBook = sample(); Notebook.Note blank = blankBook.snapshot().notes.get(0);
            blank.line = 5; blank.context = ""; blank.before = "}"; blank.after = ""; blankBook.saveNote(blank);
            Files.writeString(sender.resolve("src/auth.py"), "alpha();\n\n\n}\n\n");
            NoteBundle blankBundle = bundle(blankBook, sender);
            Files.writeString(receiver.resolve("src/auth.py"), "alpha();\n\n\n}\n\n");
            check(plan(new Notebook(), blankBundle, receiver).rows.get(0).attached(), "Unchanged whole-file fingerprint preserves blank-line notes");
            Files.writeString(receiver.resolve("src/auth.py"), "beta();\n\n\n}\n\n");
            check(!plan(new Notebook(), blankBundle, receiver).rows.get(0).attached(), "Changed-file blank lines remain pending even beside matching punctuation");
            try {
                Path link = receiver.resolve("src/outside.py"); Files.createSymbolicLink(link, sender.resolve("src/auth.py"));
                rejects(() -> ShareEngine.safeFile(receiver, "src/outside.py"), "Symlink cannot escape selected root");
            } catch (UnsupportedOperationException | IOException denied) { System.out.println("SKIP symbolic-link creation is unavailable in this environment"); }
            System.out.println("PASS: " + checks + " sharing, relocation and merge checks");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.normalize().startsWith(temporary)) throw new IllegalStateException("Unsafe test cleanup path");
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
