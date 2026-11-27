package dev.readingnotes.model;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class I18nTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public static void run() throws Exception {
        I18n.Language previous = I18n.language();
        try {
            check(I18n.Language.fromCode("en") == I18n.Language.ENGLISH && I18n.Language.fromCode("unknown") == I18n.Language.CHINESE && I18n.Language.fromCode(null) == I18n.Language.CHINESE, "Stored language codes have a safe default");
            I18n.setLanguage(I18n.Language.CHINESE);
            check(I18n.text("保存笔记").equals("保存笔记"), "Chinese UI catalog");
            Notebook book = new Notebook(); Notebook.Data initial = book.snapshot();
            Notebook.Note draft = new Notebook.Note(); draft.groupId = initial.groups.get(0).id;
            draft.title = "保存笔记"; draft.text = "默认分组\nNew Line Note 📖";
            draft.filePath = "src/学习.py"; draft.line = 7; draft.context = "print('中文')";
            Notebook.Note saved = book.saveNote(draft);
            I18n.setLanguage(I18n.Language.ENGLISH);
            check(I18n.text("保存笔记").equals("Save Note"), "English UI catalog");
            check(ShareEngine.Action.REPLACE.toString().equals("Replace Existing"), "Enum labels change after enum initialization");
            check(new ShareEngine.Candidate(7, "print('中文')", 1).toString().equals("Line 7: print('中文')"), "Candidate labels translate while source code is preserved");
            check(I18n.text("将「{0}」绑定到：\n{1} : {2}？", "{1} $ \\ 📖", "src/学习.py", 7).equals("Bind “{1} $ \\ 📖” to:\nsrc/学习.py : 7?"), "Formatting preserves placeholders, dollar signs, backslashes and Unicode inside user content");
            check(book.note(saved.id).title.equals(saved.title) && book.note(saved.id).text.equals(saved.text) && book.note(saved.id).filePath.equals(saved.filePath) && book.note(saved.id).line == saved.line && book.note(saved.id).updatedAt == saved.updatedAt, "Changing language does not mutate note data or anchors");
            check(book.snapshot().topics.get(0).name.equals(initial.topics.get(0).name) && book.snapshot().groups.get(0).name.equals(initial.groups.get(0).name), "Existing topic and group names are preserved");
            check(new Notebook().snapshot().groups.get(0).name.equals("Default Group"), "New default names use the chosen language");
            try { book.addTopic(""); throw new AssertionError("Expected validation failure"); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().equals("Name or title must not be empty"), "Model validation follows the UI language"); }
            check(I18n.text("Reading Notes").equals("Reading Notes"), "Unknown keys retain a readable fallback");
            Pattern placeholders = Pattern.compile("\\{\\d+\\}");
            for (var entry : I18n.englishMessages().entrySet()) {
                Set<String> source = new HashSet<>(placeholders.matcher(entry.getKey()).results().map(MatchResult::group).toList());
                Set<String> target = new HashSet<>(placeholders.matcher(entry.getValue()).results().map(MatchResult::group).toList());
                if (!source.equals(target) || entry.getValue().isBlank() || entry.getValue().codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff)) throw new AssertionError("Incomplete translation: " + entry.getKey());
            }
            check(true, "All English messages are translated and preserve their format parameters");
            // Catch new hard-coded UI phrases omitted from the catalog, without IDE dependencies.
            Pattern literals = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
            try (var files = Files.walk(Path.of("src/main/java/dev/readingnotes"))) {
                for (Path path : files.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().equals("I18n.java")).toList()) {
                    Matcher strings = literals.matcher(Files.readString(path));
                    while (strings.find()) {
                        String key = strings.group().substring(1, strings.group().length() - 1).replace("\\n", "\n");
                        if (key.codePoints().noneMatch(c -> c >= 0x4e00 && c <= 0x9fff) || key.equals("中文 / EN") || key.equals("简体中文")) continue;
                        if (!I18n.englishMessages().containsKey(key)) throw new AssertionError("Missing catalog key in " + path + ": " + key);
                    }
                }
            }
            check(true, "All plugin-owned Chinese UI phrases have English catalog entries");
            I18n.setLanguage(I18n.Language.CHINESE);
            check(ShareEngine.Action.REPLACE.toString().equals("替换当前"), "Switching back restores Chinese enum labels");
        } finally { I18n.setLanguage(previous); }
        System.out.println("PASS: " + checks + " localization checks");
    }
}
