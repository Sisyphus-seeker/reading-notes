package dev.readingnotes.model;

import java.util.List;

public final class NotebookTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable command, String message) {
        boolean rejected = false;
        try { command.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, message);
    }
    public static void main(String[] args) throws Exception {
        Notebook book = new Notebook();
        Notebook.Data initial = book.snapshot();
        check(initial.topics.size() == 1 && initial.groups.size() == 1 && initial.notes.isEmpty(), "Default empty notebook");
        initial.topics.get(0).name = "mutated";
        check(!book.snapshot().topics.get(0).name.equals("mutated"), "Snapshot must not mutate live data");
        Notebook.Topic topic = book.addTopic("登录流程");
        Notebook.Group group = book.addGroup(topic.id, "参数校验");
        rejects(() -> book.addTopic(" 登录流程 "), "Trimmed duplicate topics must be rejected");
        rejects(() -> book.addGroup(topic.id, "参数校验"), "Duplicate group within a topic");
        rejects(() -> book.addGroup("missing", "group"), "No orphan groups");
        book.addGroup(initial.topics.get(0).id, "参数校验");
        Notebook.Note draft = new Notebook.Note();
        draft.groupId = group.id; draft.title = "验证 Token"; draft.text = "中文笔记\ncheck BEARER";
        draft.filePath = "app/auth.py"; draft.line = 12; draft.context = "return token";
        Notebook.Note saved = book.saveNote(draft);
        check(!saved.id.isEmpty() && saved.createdAt > 0 && saved.line == 12, "Save note metadata");
        saved.text = "外部修改";
        check(book.note(saved.id).text.contains("中文笔记"), "Returned note must be a copy");
        Notebook.Note editing = book.note(saved.id);
        editing.title = "新标题"; editing.groupId = initial.groups.get(0).id;
        book.saveNote(editing);
        check(book.note(saved.id).createdAt == saved.createdAt && book.snapshot().notes.size() == 1, "Edit/move must keep ID and creation date");
        book.position(saved.id, "app/renamed.py", 15, "return verified", "before", "after", false);
        check(book.note(saved.id).line == 15 && book.note(saved.id).filePath.equals("app/renamed.py"), "Update moved code anchor");
        check(Notebook.matches(book.note(saved.id), "bearer") && Notebook.matches(book.note(saved.id), "renamed"), "Search body and file, ignoring case");
        rejects(() -> { Notebook.Note n = book.note(saved.id); n.groupId = "missing"; book.saveNote(n); }, "No orphan notes");
        Notebook restored = new Notebook(); restored.load(book.snapshot());
        check(restored.note(saved.id).text.equals(book.note(saved.id).text), "Persistence round trip");
        Notebook.Data invalid = book.snapshot(); invalid.notes.get(0).groupId = "missing";
        rejects(() -> restored.load(invalid), "Reject corrupt state");
        check(restored.note(saved.id) != null, "Failed load must preserve previous notebook");
        book.deleteTopic(topic.id);
        check(book.snapshot().groups.stream().noneMatch(g -> g.topicId.equals(topic.id)), "Cascade topic -> groups");
        check(book.note(saved.id) != null, "Moved note must survive deletion of its old topic");
        book.deleteGroup(initial.groups.get(0).id);
        check(book.note(saved.id) == null, "Cascade group -> notes");
        check(AnchorResolver.resolve(List.of("a", "target", "b"), 2, "target", "a", "b") == 1, "Unchanged anchor");
        check(AnchorResolver.resolve(List.of("inserted", "a", "target", "b"), 2, "target", "a", "b") == 2, "External insertion relocates anchor");
        check(AnchorResolver.resolve(List.of("a", "target", "b", "x", "target", "y"), 1, "target", "a", "b") == 1, "Neighbor context disambiguates");
        check(AnchorResolver.resolve(List.of("x", "target", "y", "a", "target", "b"), 2, "target", "a", "b") == 4, "Matching text at stale line must not override stronger source context");
        check(AnchorResolver.resolve(List.of("x", "target", "x", "target"), 1, "target", "", "") == -1, "Ambiguous anchors must not jump to a guess");
        check(AnchorResolver.resolve(List.of("a", "b"), 2, "deleted", "a", "b") == -1, "Deleted source needs explicit rebinding");
        check(AnchorResolver.resolve(List.of("a", "", "b"), 2, "", "a", "b") == 1, "Blank-line notes are supported");
        System.out.println("PASS: " + checks + " notebook and anchor checks");
        ShareEngineTest.run();
    }
}
